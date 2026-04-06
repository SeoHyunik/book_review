param(
    [ValidateSet("qa-structurer", "planner", "step", "curator", "handoff", "all", "workday")]
    [string]$Mode = "planner",

    [string]$RootDir = (Get-Location).Path,

    [string]$DateString = (Get-Date -Format "yyyy-MM-dd"),

    [int]$StepNumber = 1,

    [switch]$SkipGitStatusCheck,

    [switch]$PauseForQa,

    [switch]$DryRun
)

[Console]::InputEncoding  = [System.Text.UTF8Encoding]::new($false)
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
$OutputEncoding = [System.Text.UTF8Encoding]::new($false)

$ErrorActionPreference = "Stop"

$RunStartedAt = Get-Date
$ExecutedStages = New-Object System.Collections.Generic.List[string]

# ==========================================
# 1. Core paths
# ==========================================
$DocsDir         = Join-Path $RootDir "docs"
$OpsDir          = Join-Path $DocsDir "ops"
$ReportsDir      = Join-Path $DocsDir "reports"
$TodayDir        = Join-Path $OpsDir $DateString
$PromptDir       = Join-Path $RootDir ".codex\prompts"

$AgentsFile      = Join-Path $RootDir "AGENTS.md"
$ProjectBrief    = Join-Path $RootDir "PROJECT_BRIEF.md"
$DevLoop         = Join-Path $RootDir "DEV_LOOP.md"
$HarnessRules    = Join-Path $RootDir "HARNESS_RULES.md"

$TodayFmt        = Join-Path $OpsDir "TODAY_STRATEGY_FORMAT.md"
$HandoffFmt      = Join-Path $OpsDir "DAILY_HANDOFF_FORMAT.md"
$FailuresFile    = Join-Path $OpsDir "HARNESS_FAILURES.md"

$QaInbox         = Join-Path $TodayDir "QA_INBOX.md"
$QaStructured    = Join-Path $TodayDir "QA_STRUCTURED.md"
$TodayStrategy   = Join-Path $TodayDir "TODAY_STRATEGY.md"
$DailyHandoff    = Join-Path $TodayDir "DAILY_HANDOFF.md"

$ScriptPath      = $MyInvocation.MyCommand.Path

$ReadableDocsToCheckBeforeHandoff = @(
    $QaInbox,
    $QaStructured,
    $TodayStrategy
)

# ==========================================
# 2. Codex CLI args
# ==========================================
$CodexBaseArgs = 'exec --dangerously-bypass-approvals-and-sandbox -C "{WORKDIR}"'

# ==========================================
# 3. Helpers
# ==========================================
function New-DirectoryIfMissing {
    param([string]$Path)

    if (-not (Test-Path $Path)) {
        New-Item -ItemType Directory -Path $Path | Out-Null
    }
}

function New-TextFileIfMissing {
    param(
        [string]$Path,
        [string]$DefaultContent = ""
    )

    if (-not (Test-Path $Path)) {
        $parent = Split-Path $Path -Parent
        New-DirectoryIfMissing -Path $parent
        [System.IO.File]::WriteAllText($Path, $DefaultContent, [System.Text.UTF8Encoding]::new($false))
    }
}

function Write-PromptFile {
    param(
        [string]$FilePath,
        [string]$Content
    )

    $parent = Split-Path $FilePath -Parent
    New-DirectoryIfMissing -Path $parent
    [System.IO.File]::WriteAllText($FilePath, $Content, [System.Text.UTF8Encoding]::new($false))
}

function Get-LatestPreviousHandoffFile {
    param(
        [string]$OpsRoot,
        [string]$CurrentDate
    )

    if (-not (Test-Path $OpsRoot)) {
        return $null
    }

    $dirs = Get-ChildItem -Path $OpsRoot -Directory |
            Where-Object { $_.Name -match '^\d{4}-\d{2}-\d{2}$' -and $_.Name -lt $CurrentDate } |
            Sort-Object Name -Descending

    foreach ($dir in $dirs) {
        $candidate = Join-Path $dir.FullName "DAILY_HANDOFF.md"
        if (Test-Path $candidate) {
            return $candidate
        }
    }

    return $null
}

function Test-GitAvailable {
    try {
        $null = Get-Command git -ErrorAction Stop
        return $true
    }
    catch {
        return $false
    }
}

function Get-RelativePath {
    param(
        [string]$BasePath,
        [string]$TargetPath
    )

    if ([string]::IsNullOrWhiteSpace($BasePath) -or [string]::IsNullOrWhiteSpace($TargetPath)) {
        return $null
    }

    $baseFull = [System.IO.Path]::GetFullPath($BasePath)
    $targetFull = [System.IO.Path]::GetFullPath($TargetPath)

    if (-not $baseFull.EndsWith([System.IO.Path]::DirectorySeparatorChar)) {
        $baseFull += [System.IO.Path]::DirectorySeparatorChar
    }

    $baseUri = [System.Uri]::new($baseFull)
    $targetUri = [System.Uri]::new($targetFull)
    $relativeUri = $baseUri.MakeRelativeUri($targetUri)
    $relativePath = [System.Uri]::UnescapeDataString($relativeUri.ToString())

    return ($relativePath -replace '/', '\')
}

function Normalize-RepoPath {
    param([string]$Path)

    if ([string]::IsNullOrWhiteSpace($Path)) {
        return $null
    }

    return (($Path -replace '/', '\').Trim()).ToLowerInvariant()
}

function Test-StartsWithNormalizedPath {
    param(
        [string]$Path,
        [string]$Prefix
    )

    $normalizedPath = Normalize-RepoPath -Path $Path
    $normalizedPrefix = Normalize-RepoPath -Path $Prefix

    if ([string]::IsNullOrWhiteSpace($normalizedPath) -or [string]::IsNullOrWhiteSpace($normalizedPrefix)) {
        return $false
    }

    return $normalizedPath.StartsWith($normalizedPrefix)
}

function Get-ChangedFileList {
    param([string]$WorkDir)

    if (-not (Test-GitAvailable)) {
        return @()
    }

    Push-Location $WorkDir
    try {
        $output = git status --short 2>$null
        if (-not $output) {
            return @()
        }

        return @(
        $output |
                ForEach-Object {
                    if ($_.Length -ge 4) {
                        $_.Substring(3).Trim()
                    }
                } |
                Where-Object { $_ }
        )
    }
    finally {
        Pop-Location
    }
}

function Get-AllowedChangedFiles {
    param(
        [string]$WorkDir,
        [string]$CurrentScriptPath
    )

    $allowed = @()

    if (-not [string]::IsNullOrWhiteSpace($CurrentScriptPath)) {
        $relativeScriptPath = Get-RelativePath -BasePath $WorkDir -TargetPath $CurrentScriptPath
        if (-not [string]::IsNullOrWhiteSpace($relativeScriptPath)) {
            $allowed += $relativeScriptPath
            $allowed += ($relativeScriptPath -replace '\\', '/')
        }
    }

    return $allowed
}

function Test-IsAllowedDirtyPath {
    param(
        [string]$RepoRelativePath,
        [string[]]$AdditionalAllowedPaths = @()
    )

    if ([string]::IsNullOrWhiteSpace($RepoRelativePath)) {
        return $false
    }

    if (Test-StartsWithNormalizedPath -Path $RepoRelativePath -Prefix 'docs\ops\') {
        return $true
    }

    if (Test-StartsWithNormalizedPath -Path $RepoRelativePath -Prefix 'docs\reports\') {
        return $true
    }

    if (Test-StartsWithNormalizedPath -Path $RepoRelativePath -Prefix '.codex\prompts\') {
        return $true
    }

    $normalizedCurrent = Normalize-RepoPath -Path $RepoRelativePath
    $normalizedAllowed = @(
    $AdditionalAllowedPaths |
            Where-Object { $_ } |
            ForEach-Object { Normalize-RepoPath -Path $_ }
    )

    return ($normalizedCurrent -in $normalizedAllowed)
}

function Assert-StepExecutionReady {
    param(
        [string]$TodayStrategyPath,
        [string]$WorkDir,
        [switch]$SkipGitCheck,
        [string[]]$AdditionalAllowedPaths = @()
    )

    if (-not (Test-Path $TodayStrategyPath)) {
        throw "TODAY_STRATEGY.md not found: $TodayStrategyPath"
    }

    if ($SkipGitCheck) {
        return
    }

    $changedFiles = Get-ChangedFileList -WorkDir $WorkDir
    if ($changedFiles.Count -eq 0) {
        return
    }

    $unexpected = @(
    $changedFiles |
            Where-Object {
                -not (Test-IsAllowedDirtyPath -RepoRelativePath $_ -AdditionalAllowedPaths $AdditionalAllowedPaths)
            }
    )

    if ($unexpected.Count -gt 0) {
        $joined = ($unexpected -join ', ')
        throw "Step execution blocked because non-ops changes already exist in the worktree: $joined"
    }
}

function Get-PlannedStepNumbers {
    param([string]$TodayStrategyPath)

    if (-not (Test-Path $TodayStrategyPath)) {
        return @()
    }

    $content = Get-Content $TodayStrategyPath -Raw -Encoding UTF8
    if ([string]::IsNullOrWhiteSpace($content)) {
        return @()
    }

    $matches = [regex]::Matches(
            $content,
            '(?im)^\s{0,3}(?:#{1,6}\s*)?(?:step|phase)\s*[-:]?\s*(\d+)\b'
    )

    if ($matches.Count -eq 0) {
        return @()
    }

    return @(
    $matches |
            ForEach-Object { [int]$_.Groups[1].Value } |
            Sort-Object -Unique
    )
}

function Get-ReadableFileText {
    param([string]$Path)

    if (-not (Test-Path $Path)) {
        throw "Required daily ops file not found: $Path"
    }

    return [System.IO.File]::ReadAllText($Path, [System.Text.UTF8Encoding]::new($false))
}

function Test-TextLooksCorrupted {
    param([string]$Content)

    if ([string]::IsNullOrWhiteSpace($Content)) {
        return $false
    }

    if ($Content.Contains([char]0xFFFD)) { return $true }
    if ($Content.Contains('ï»¿')) { return $true }
    if ($Content.Contains('???')) { return $true }
    if ($Content.Contains('ì') -and $Content.Contains('ë') -and $Content.Contains('ê')) { return $true }

    return $false
}

function Assert-ReadableDailyOpsFiles {
    param([string[]]$Paths)

    foreach ($path in $Paths) {
        $content = Get-ReadableFileText -Path $path
        if (Test-TextLooksCorrupted -Content $content) {
            throw "Readable UTF-8 validation failed for daily ops document: $path"
        }
    }
}

function Test-HasActionableQaInbox {
    param([string]$Path)

    if (-not (Test-Path $Path)) {
        return $false
    }

    $content = Get-Content $Path -Raw -Encoding UTF8
    if ([string]::IsNullOrWhiteSpace($content)) {
        return $false
    }

    $matches = [regex]::Matches($content, '(?m)^\s*-\s+\S+')
    return ($matches.Count -gt 0)
}

function Test-IsMeaningfullyStructuredQa {
    param([string]$Path)

    if (-not (Test-Path $Path)) {
        return $false
    }

    $content = Get-Content $Path -Raw -Encoding UTF8
    if ([string]::IsNullOrWhiteSpace($content)) {
        return $false
    }

    $normalized = $content.Trim()
    if ($normalized -eq "# QA_STRUCTURED`r`n`r`n## Date`r`n$DateString`r`n`r`n## Structured Items`r`n`r`n-" -or
            $normalized -eq "# QA_STRUCTURED`n`n## Date`n$DateString`n`n## Structured Items`n`n-") {
        return $false
    }

    if ($content -match '(?im)^###\s+Item\s+\d+' -or $content -match '(?im)^-\s+category:' -or $content -match '(?im)^##\s+Normalized\s+Issues') {
        return $true
    }

    $bulletMatches = [regex]::Matches($content, '(?m)^\s*-\s+\S+')
    return ($bulletMatches.Count -ge 3)
}

function Assert-QaStructuredReady {
    if (Test-HasActionableQaInbox -Path $QaInbox) {
        if (-not (Test-IsMeaningfullyStructuredQa -Path $QaStructured)) {
            throw "QA_STRUCTURED.md is empty or not meaningfully normalized while QA_INBOX.md contains actionable items. Run qa-structurer before planner."
        }
    }
}

function Assert-PreHandoffReadiness {
    Assert-ReadableDailyOpsFiles -Paths $ReadableDocsToCheckBeforeHandoff
    Assert-QaStructuredReady
}

function Assert-PostHandoffReadiness {
    $required = @($ReadableDocsToCheckBeforeHandoff + $DailyHandoff)
    Assert-ReadableDailyOpsFiles -Paths $required
    Assert-QaStructuredReady
}

function Add-ExecutedStage {
    param([string]$StageName)

    if (-not [string]::IsNullOrWhiteSpace($StageName)) {
        $ExecutedStages.Add($StageName) | Out-Null
    }
}

function Write-StageBanner {
    param(
        [string]$Title,
        [string]$Color = "Cyan"
    )

    Write-Host ""
    Write-Host "==========================================" -ForegroundColor $Color
    Write-Host $Title -ForegroundColor $Color
    Write-Host "==========================================" -ForegroundColor $Color
}

function Write-StageCompletion {
    param(
        [string]$StageName,
        [DateTime]$StartedAt
    )

    $elapsed = (Get-Date) - $StartedAt
    $elapsedText = $elapsed.ToString("mm\:ss")

    Write-Host ""
    Write-Host ("[OK] {0} finished in {1}" -f $StageName, $elapsedText) -ForegroundColor Green
}

function Write-FinalRunSummary {
    param(
        [string]$ModeName,
        [string]$Status,
        [string]$ErrorMessage = $null
    )

    $elapsed = (Get-Date) - $RunStartedAt
    $elapsedText = $elapsed.ToString("hh\:mm\:ss")
    $executed = if ($ExecutedStages.Count -gt 0) { $ExecutedStages -join ' -> ' } else { '(none)' }
    $summaryColor = if ($Status -eq 'SUCCESS') { 'Green' } else { 'Red' }

    Write-Host ""
    Write-Host "==========================================" -ForegroundColor $summaryColor
    Write-Host ("HARNESS RUN {0}" -f $Status) -ForegroundColor $summaryColor
    Write-Host "==========================================" -ForegroundColor $summaryColor
    Write-Host ("Mode      : {0}" -f $ModeName) -ForegroundColor $summaryColor
    Write-Host ("Date      : {0}" -f $DateString) -ForegroundColor $summaryColor
    Write-Host ("Elapsed   : {0}" -f $elapsedText) -ForegroundColor $summaryColor
    Write-Host ("Stages    : {0}" -f $executed) -ForegroundColor $summaryColor

    if ($ModeName -in @('workday', 'planner', 'all')) {
        Write-Host ("Strategy  : {0}" -f $TodayStrategy) -ForegroundColor $summaryColor
    }

    if ($ModeName -in @('workday', 'handoff', 'all')) {
        Write-Host ("Handoff   : {0}" -f $DailyHandoff) -ForegroundColor $summaryColor
    }

    if ($ModeName -in @('workday', 'qa-structurer', 'all')) {
        Write-Host ("QA File   : {0}" -f $QaStructured) -ForegroundColor $summaryColor
    }

    if (-not [string]::IsNullOrWhiteSpace($ErrorMessage)) {
        Write-Host ("Error     : {0}" -f $ErrorMessage) -ForegroundColor Red
    }

    Write-Host "==========================================" -ForegroundColor $summaryColor
    Write-Host ""
}

function Invoke-CodexFromPrompt {
    param(
        [string]$PromptFile,
        [string]$WorkDir,
        [string]$StageName = "codex"
    )

    $codexArgs = $CodexBaseArgs.Replace('{WORKDIR}', $WorkDir)
    $cmd = "Get-Content `\"$PromptFile`\" -Raw | codex $codexArgs"

    Write-StageBanner -Title ("Running {0}" -f $StageName) -Color "Cyan"
    Write-Host $cmd
    Write-Host ""

    if ($DryRun) {
        Write-Host "[DryRun] Execution skipped." -ForegroundColor Yellow
        Add-ExecutedStage -StageName ("{0} (dryrun)" -f $StageName)
        return
    }

    $stageStartedAt = Get-Date
    Invoke-Expression $cmd
    Add-ExecutedStage -StageName $StageName
    Write-StageCompletion -StageName $StageName -StartedAt $stageStartedAt
}

function New-QaStructurerPromptContent {
    return @"
You are running the QA normalization role for this repository.

Read first:
1. $ProjectBrief
2. $AgentsFile
3. $DevLoop
4. $HarnessRules
5. $QaInbox

Today: $DateString

Write only:
- $QaStructured

Tasks:
1. Normalize raw QA notes into a structured issue list.
2. Merge duplicates and split unrelated concerns.
3. Separate immediate implementation-ready issues from broader product decisions.
4. Use a concise, machine-readable structure.
5. Preserve the user's Korean wording where appropriate.
6. Do not implement code.
7. Do not modify any file other than QA_STRUCTURED.md.

Required output structure:
# QA_STRUCTURED

## Date
$DateString

## Normalized Issues

### Item 1
- category: UI/UX | reliability | localization | content tone | admin | ingestion | market-data | SEO | infra | product-decision
- surface: specific page or system surface
- symptom: concise problem statement
- user impact: high | medium | low
- requested change: concise action
- scope hint: template | controller | service | provider | docs | product-decision | infra
- priority: P1 | P2 | P3
- selected today: yes | no
- notes: optional concise note
"@
}

function New-PlannerPromptContent {
    return @"
You are running the planner role for this repository.

Read first:
1. $ProjectBrief
2. $AgentsFile
3. $DevLoop
4. $HarnessRules

Today: $DateString

Ops rules:
- Daily docs must be created in docs/ops/YYYY-MM-DD/
- NEVER create TODAY_STRATEGY.md in root or directly under docs/ops/
- MUST read the strategy format before writing
- MUST write only to: $TodayStrategy
- If the file already exists, update it carefully instead of creating a second file

Read if available:
- format: $TodayFmt
- QA inbox: $QaInbox
- QA structured: $QaStructured
- previous handoff: $PreviousHandoff
- reports: $ReportsDir

Hard requirements:
- QA_STRUCTURED.md is the primary planning input.
- QA_INBOX.md is only a cross-check / completeness input.
- If QA_INBOX.md contains actionable items and QA_STRUCTURED.md is empty or unnormalized, STOP and report that planning cannot proceed safely.
- Explicitly reflect structured QA priorities in selection logic.

Tasks:
1. Read TODAY_STRATEGY_FORMAT.md
2. Create or update exactly: $TodayStrategy
3. Keep the plan small, safe, and Codex-executable
4. Do not implement code
5. Do not modify any other file
"@
}

function New-StepPromptContent {
    param([int]$SelectedStepNumber)

    return @"
You are executing exactly one planned development step for this repository.

Read first:
1. $ProjectBrief
2. $AgentsFile
3. $DevLoop
4. $HarnessRules
5. $TodayStrategy

Today: $DateString
Step Number: $SelectedStepNumber

Critical rules:
- Read TODAY_STRATEGY.md and locate exactly Step $SelectedStepNumber
- Execute ONLY that step
- If Step $SelectedStepNumber does not exist, stop and report that clearly
- Do NOT execute any other step
- Do NOT modify TODAY_STRATEGY.md, DAILY_HANDOFF.md, QA_INBOX.md, QA_STRUCTURED.md, or HARNESS_FAILURES.md
- Do NOT modify unrelated files
- Keep changes minimal, safe, and scoped to the selected step
- Follow any Forbidden Scope and Validation instructions written for Step $SelectedStepNumber

Execution flow:
1. Read TODAY_STRATEGY.md carefully
2. Extract the exact goal / target area / likely files / forbidden scope / validation for Step $SelectedStepNumber
3. Analyze the code paths needed for only that step
4. If the step is implementation-oriented, implement only the smallest safe change for that step
5. If the step is analysis-oriented, do NOT no-op; create exactly one concise analysis artifact under docs/reports/
6. Run relevant validation for that step if available
7. Report:
   - what changed
   - why it changed
   - what was not changed
   - risks
   - next possible step

Do not generate handoff or curator outputs in this run.
Do not widen scope.
"@
}

function New-CuratorPromptContent {
    return @"
You are running the curator role for this repository.

Read first:
1. $ProjectBrief
2. $AgentsFile
3. $DevLoop
4. $HarnessRules

Today: $DateString

Read if available:
- TODAY_STRATEGY: $TodayStrategy
- DAILY_HANDOFF: $DailyHandoff
- QA_INBOX: $QaInbox
- QA_STRUCTURED: $QaStructured
- reports: $ReportsDir

You must update only:
- $FailuresFile

Tasks:
1. Detect meaningful repeated mistakes or weak harness points
2. Append a concise entry to HARNESS_FAILURES.md
3. Do not modify code
4. Do not modify format files
5. Do not modify any other file
"@
}

function New-HandoffPromptContent {
    return @"
You are generating the daily handoff for this repository.

Read first:
1. $ProjectBrief
2. $AgentsFile
3. $DevLoop
4. $HarnessRules

Today: $DateString

Ops rules:
- MUST read the handoff format file first
- MUST write only to: $DailyHandoff
- MUST NOT overwrite the format file
- If the file already exists, update it carefully instead of creating a duplicate file

Read if available:
- format: $HandoffFmt
- TODAY_STRATEGY: $TodayStrategy
- QA_INBOX: $QaInbox
- QA_STRUCTURED: $QaStructured
- HARNESS_FAILURES: $FailuresFile
- reports: $ReportsDir

Tasks:
1. Read DAILY_HANDOFF_FORMAT.md
2. Create or update exactly: $DailyHandoff
3. Distinguish completed / partial / deferred / risks / next steps
4. Explicitly state whether QA_STRUCTURED.md was normalized and usable for planning
5. Do not invent finished work
6. Do not modify any other file
"@
}

function Get-StepPromptFilePath {
    param([int]$SelectedStepNumber)
    return (Join-Path $PromptDir "$DateString-step$SelectedStepNumber.prompt.txt")
}

function Invoke-QaStructurerMode {
    $qaPromptFile = Join-Path $PromptDir "$DateString-qa-structurer.prompt.txt"
    $qaPromptContent = New-QaStructurerPromptContent

    Write-PromptFile -FilePath $qaPromptFile -Content $qaPromptContent
    Invoke-CodexFromPrompt -PromptFile $qaPromptFile -WorkDir $RootDir -StageName "qa-structurer"

    if (-not $DryRun) {
        Assert-QaStructuredReady
    }
}

function Invoke-PlannerMode {
    Assert-QaStructuredReady

    $plannerPromptFile = Join-Path $PromptDir "$DateString-planner.prompt.txt"
    $plannerPromptContent = New-PlannerPromptContent

    Write-PromptFile -FilePath $plannerPromptFile -Content $plannerPromptContent
    Invoke-CodexFromPrompt -PromptFile $plannerPromptFile -WorkDir $RootDir -StageName "planner"
}

function Invoke-CuratorMode {
    $curatorPromptFile = Join-Path $PromptDir "$DateString-curator.prompt.txt"
    $curatorPromptContent = New-CuratorPromptContent

    Write-PromptFile -FilePath $curatorPromptFile -Content $curatorPromptContent
    Invoke-CodexFromPrompt -PromptFile $curatorPromptFile -WorkDir $RootDir -StageName "curator"
}

function Invoke-HandoffMode {
    Assert-PreHandoffReadiness

    $handoffPromptFile = Join-Path $PromptDir "$DateString-handoff.prompt.txt"
    $handoffPromptContent = New-HandoffPromptContent

    Write-PromptFile -FilePath $handoffPromptFile -Content $handoffPromptContent
    Invoke-CodexFromPrompt -PromptFile $handoffPromptFile -WorkDir $RootDir -StageName "handoff"

    if (-not $DryRun) {
        Assert-PostHandoffReadiness
    }
}

function Get-StepPurposeHint {
    param([int]$SelectedStepNumber)

    if (-not (Test-Path $TodayStrategy)) {
        return "implementation"
    }

    $content = Get-Content $TodayStrategy -Raw -Encoding UTF8
    $pattern = "(?is)###?\s*Step\s*$SelectedStepNumber\b(.*?)(?=^\s{0,3}(?:#{1,6}\s*)?(?:Step|Phase)\s+\d+\b|\z)"
    $match = [regex]::Match($content, $pattern, [System.Text.RegularExpressions.RegexOptions]::Multiline)

    if (-not $match.Success) {
        return "implementation"
    }

    $stepBody = $match.Groups[1].Value

    if ($stepBody -match '(?i)expected output\s*[\r\n-:\s]*analysis note') { return "analysis" }
    if ($stepBody -match '(?i)expected output\s*[\r\n-:\s]*report') { return "analysis" }
    if ($stepBody -match '(?i)\bgoal\b[\r\n-:\s]*.*\btrace\b') { return "analysis" }

    return "implementation"
}

function Get-ChangedFileSnapshot {
    param([string]$WorkDir)
    return @(Get-ChangedFileList -WorkDir $WorkDir | Sort-Object -Unique)
}

function Get-NewlyChangedFiles {
    param(
        [string[]]$Before,
        [string[]]$After
    )

    $beforeNormalized = @($Before | ForEach-Object { Normalize-RepoPath -Path $_ })
    return @(
    $After |
            Where-Object { (Normalize-RepoPath -Path $_) -notin $beforeNormalized } |
            Sort-Object -Unique
    )
}

function Assert-StepProducedExpectedArtifact {
    param(
        [int]$SelectedStepNumber,
        [string[]]$BeforeChangedFiles,
        [string[]]$AfterChangedFiles
    )

    if ($DryRun) {
        return
    }

    $newlyChanged = @(Get-NewlyChangedFiles -Before $BeforeChangedFiles -After $AfterChangedFiles)
    $stepPurpose = Get-StepPurposeHint -SelectedStepNumber $SelectedStepNumber

    if ($stepPurpose -eq 'analysis') {
        $analysisArtifacts = @(
        $newlyChanged |
                Where-Object { Test-StartsWithNormalizedPath -Path $_ -Prefix 'docs\reports\' }
        )

        if ($analysisArtifacts.Count -eq 0) {
            throw "Step $SelectedStepNumber looks like an analysis step, but no new docs/reports artifact was created."
        }

        return
    }

    $meaningfulChanges = @(
    $newlyChanged |
            Where-Object {
                -not (Test-StartsWithNormalizedPath -Path $_ -Prefix 'docs\ops\') -and
                        -not (Test-StartsWithNormalizedPath -Path $_ -Prefix '.codex\prompts\')
            }
    )

    if ($meaningfulChanges.Count -eq 0) {
        throw "Step $SelectedStepNumber finished without any non-ops file change."
    }
}

function Invoke-StepMode {
    param([int]$SelectedStepNumber)

    $beforeChangedFiles = Get-ChangedFileSnapshot -WorkDir $RootDir

    Assert-StepExecutionReady `
        -TodayStrategyPath $TodayStrategy `
        -WorkDir $RootDir `
        -SkipGitCheck:$SkipGitStatusCheck `
        -AdditionalAllowedPaths (Get-AllowedChangedFiles -WorkDir $RootDir -CurrentScriptPath $ScriptPath)

    $stepPromptFile = Get-StepPromptFilePath -SelectedStepNumber $SelectedStepNumber
    $stepPromptContent = New-StepPromptContent -SelectedStepNumber $SelectedStepNumber

    Write-PromptFile -FilePath $stepPromptFile -Content $stepPromptContent
    Invoke-CodexFromPrompt -PromptFile $stepPromptFile -WorkDir $RootDir -StageName ("step-{0}" -f $SelectedStepNumber)

    $afterChangedFiles = Get-ChangedFileSnapshot -WorkDir $RootDir
    Assert-StepProducedExpectedArtifact `
        -SelectedStepNumber $SelectedStepNumber `
        -BeforeChangedFiles $beforeChangedFiles `
        -AfterChangedFiles $afterChangedFiles
}

function Wait-ForQaCheckpoint {
    param([int]$CompletedStepNumber)

    if (-not $PauseForQa) {
        return
    }

    Write-Host ""
    Write-Host "==========================================" -ForegroundColor DarkYellow
    Write-Host "QA Checkpoint" -ForegroundColor DarkYellow
    Write-Host "==========================================" -ForegroundColor DarkYellow
    Write-Host "Step $CompletedStepNumber execution finished." -ForegroundColor Yellow
    Write-Host "Run your QA, then press Enter to continue to the next step." -ForegroundColor Yellow
    Write-Host "Press Ctrl+C to stop here and resume later." -ForegroundColor Yellow
    Write-Host ""

    if (-not $DryRun) {
        Read-Host | Out-Null
    }
}

function Invoke-WorkdayMode {
    Invoke-QaStructurerMode
    Invoke-PlannerMode

    if (-not (Test-Path $TodayStrategy)) {
        throw "TODAY_STRATEGY.md not found after planner run: $TodayStrategy"
    }

    $plannedSteps = @(Get-PlannedStepNumbers -TodayStrategyPath $TodayStrategy | Where-Object { $_ -ge $StepNumber })
    if ($plannedSteps.Count -eq 0) {
        throw "No planned steps were found in TODAY_STRATEGY.md starting from Step $StepNumber"
    }

    foreach ($plannedStep in $plannedSteps) {
        Invoke-StepMode -SelectedStepNumber $plannedStep
        Wait-ForQaCheckpoint -CompletedStepNumber $plannedStep
    }

    Invoke-CuratorMode
    Invoke-HandoffMode
}

# ==========================================
# 4. Validation
# ==========================================
$requiredFiles = @(
    $AgentsFile,
    $ProjectBrief,
    $DevLoop,
    $HarnessRules,
    $TodayFmt,
    $HandoffFmt
)

foreach ($file in $requiredFiles) {
    if (-not (Test-Path $file)) {
        throw "Required file not found: $file"
    }
}

# ==========================================
# 5. Prepare safe ops structure
# ==========================================
New-DirectoryIfMissing -Path $OpsDir
New-DirectoryIfMissing -Path $TodayDir
New-DirectoryIfMissing -Path $PromptDir

New-TextFileIfMissing -Path $FailuresFile -DefaultContent "# HARNESS_FAILURES`n"

New-TextFileIfMissing -Path $QaInbox -DefaultContent @"
# QA_INBOX

## Date
$DateString

## Raw Notes

-
"@

New-TextFileIfMissing -Path $QaStructured -DefaultContent @"
# QA_STRUCTURED

## Date
$DateString

## Normalized Issues

-
"@

$PreviousHandoff = Get-LatestPreviousHandoffFile -OpsRoot $OpsDir -CurrentDate $DateString

# ==========================================
# 6. Execute by mode
# ==========================================
$runStatus = "SUCCESS"
$errorMessage = $null

try {
    switch ($Mode) {
        "qa-structurer" {
            Invoke-QaStructurerMode
        }
        "planner" {
            Invoke-PlannerMode
        }
        "step" {
            Invoke-StepMode -SelectedStepNumber $StepNumber
        }
        "curator" {
            Invoke-CuratorMode
        }
        "handoff" {
            Invoke-HandoffMode
        }
        "all" {
            Invoke-QaStructurerMode
            Invoke-PlannerMode
            Invoke-StepMode -SelectedStepNumber $StepNumber
            Invoke-CuratorMode
            Invoke-HandoffMode
        }
        "workday" {
            Invoke-WorkdayMode
        }
    }
}
catch {
    $runStatus = "FAILED"
    $errorMessage = $_.Exception.Message
    throw
}
finally {
    Write-FinalRunSummary -ModeName $Mode -Status $runStatus -ErrorMessage $errorMessage
}
