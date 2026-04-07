param(
    [ValidateSet("planner", "qa-structurer", "step", "curator", "handoff", "all", "workday")]
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
$PromptRootDir   = Join-Path $RootDir ".codex\prompts"
$PromptDir       = Join-Path $PromptRootDir $DateString
$AgentsDir       = Join-Path $RootDir ".codex\agents"

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
$WorkdayStateFile = Join-Path $TodayDir ".workday-state.json"

$ScriptPath      = $MyInvocation.MyCommand.Path
$GitterToml      = Join-Path $AgentsDir "gitter.toml"

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

function Get-WorkdayState {
    if (-not (Test-Path $WorkdayStateFile)) {
        return @{
            date = $DateString
            qa_structurer_done = $false
            planner_done = $false
            completed_steps = @()
            gitter_steps = @()
            curator_done = $false
            handoff_done = $false
        }
    }

    try {
        $state = Get-Content $WorkdayStateFile -Raw -Encoding UTF8 | ConvertFrom-Json -AsHashtable

        if (-not $state.ContainsKey('date')) { $state.date = $DateString }
        if (-not $state.ContainsKey('qa_structurer_done')) { $state.qa_structurer_done = $false }
        if (-not $state.ContainsKey('planner_done')) { $state.planner_done = $false }
        if (-not $state.ContainsKey('completed_steps')) { $state.completed_steps = @() }
        if (-not $state.ContainsKey('gitter_steps')) { $state.gitter_steps = @() }
        if (-not $state.ContainsKey('curator_done')) { $state.curator_done = $false }
        if (-not $state.ContainsKey('handoff_done')) { $state.handoff_done = $false }

        $state.completed_steps = @($state.completed_steps | ForEach-Object { [int]$_ } | Sort-Object -Unique)
        $state.gitter_steps = @($state.gitter_steps | ForEach-Object { [int]$_ } | Sort-Object -Unique)

        return $state
    }
    catch {
        return @{
            date = $DateString
            qa_structurer_done = $false
            planner_done = $false
            completed_steps = @()
            gitter_steps = @()
            curator_done = $false
            handoff_done = $false
        }
    }
}

function Save-WorkdayState {
    param([hashtable]$State)

    if ($DryRun) {
        return
    }

    $State.date = $DateString
    $State.completed_steps = @($State.completed_steps | ForEach-Object { [int]$_ } | Sort-Object -Unique)
    $State.gitter_steps = @($State.gitter_steps | ForEach-Object { [int]$_ } | Sort-Object -Unique)

    $json = $State | ConvertTo-Json -Depth 5
    [System.IO.File]::WriteAllText($WorkdayStateFile, $json, [System.Text.UTF8Encoding]::new($false))
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

    if ($Content.Contains([char]0xFFFD)) {
        return $true
    }

    if ($Content.Contains(([string][char]0x00EF) + ([string][char]0x00BB) + ([string][char]0x00BF))) {
        return $true
    }

    if ($Content.Contains('???')) {
        return $true
    }

    if ($Content.Contains([char]0x00EC) -and $Content.Contains([char]0x00EB) -and $Content.Contains([char]0x00EA)) {
        return $true
    }

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

function Get-OpsDocumentDateValue {
    param([string]$Path)

    if (-not (Test-Path $Path)) {
        return $null
    }

    $content = Get-ReadableFileText -Path $Path
    $patterns = @(
        '(?im)^\s*##\s*(?:1\.\s*)?Date\s*$\s*(\d{4}-\d{2}-\d{2})\b',
        '(?im)^\s*##\s*Date\s*$\s*(\d{4}-\d{2}-\d{2})\b'
    )

    foreach ($pattern in $patterns) {
        $match = [regex]::Match($content, $pattern)
        if ($match.Success) {
            return $match.Groups[1].Value
        }
    }

    return $null
}

function Test-OpsDocumentHasRequiredSections {
    param(
        [string]$Content,
        [string[]]$RequiredSectionPatterns
    )

    foreach ($pattern in $RequiredSectionPatterns) {
        if (-not ($Content -match $pattern)) {
            return $false
        }
    }

    return $true
}

function Assert-OpsDocumentDatesMatchCurrent {
    param(
        [string[]]$Paths,
        [string]$ExpectedDate
    )

    foreach ($path in $Paths) {
        $actualDate = Get-OpsDocumentDateValue -Path $path
        if ([string]::IsNullOrWhiteSpace($actualDate)) {
            throw "Daily ops document is missing a readable date header: $path"
        }

        if ($actualDate -ne $ExpectedDate) {
            throw "Daily ops document date mismatch for $path. Expected $ExpectedDate but found $actualDate"
        }
    }
}

function Assert-OpsDocumentStructure {
    param(
        [string]$Path,
        [string[]]$RequiredSectionPatterns
    )

    $content = Get-ReadableFileText -Path $Path
    if (-not (Test-OpsDocumentHasRequiredSections -Content $content -RequiredSectionPatterns $RequiredSectionPatterns)) {
        throw "Required section check failed for daily ops document: $Path"
    }
}

function Assert-DailyOpsContextConsistency {
    param(
        [switch]$IncludeTodayStrategy,
        [switch]$IncludeDailyHandoff
    )

    $dateScopedFiles = @(
        $QaInbox,
        $QaStructured
    )

    if ($IncludeTodayStrategy) {
        $dateScopedFiles += $TodayStrategy
    }

    if ($IncludeDailyHandoff) {
        $dateScopedFiles += $DailyHandoff
    }

    Assert-OpsDocumentDatesMatchCurrent -Paths $dateScopedFiles -ExpectedDate $DateString

    if ($IncludeTodayStrategy) {
        Assert-OpsDocumentStructure `
            -Path $TodayStrategy `
            -RequiredSectionPatterns @(
            '(?im)^\s*##\s*2\.\s*Strategy Objective\s*$',
            '(?im)^\s*##\s*4\.\s*Carry-over from Previous Session\s*$',
            '(?im)^\s*##\s*5\.\s*Inputs for Today''s Planning\s*$',
            '(?im)^\s*##\s*11\.\s*Selected Work for Today\s*$',
            '(?im)^\s*##\s*12\.\s*Step Breakdown\s*$',
            '(?im)^\s*##\s*15\.\s*Risks and Constraints\s*$',
            '(?im)^\s*##\s*17\.\s*Definition of Done for Today\s*$',
            '(?im)^\s*##\s*18\.\s*Handoff Requirement\s*$'
        )
    }

    if ($IncludeDailyHandoff) {
        Assert-OpsDocumentStructure `
            -Path $DailyHandoff `
            -RequiredSectionPatterns @(
            '(?im)^\s*##\s*2\.\s*Summary of Today\s*$',
            '(?im)^\s*##\s*3\.\s*Completed Work\s*$',
            '(?im)^\s*##\s*6\.\s*Carry-over Candidates \(CRITICAL\)\s*$',
            '(?im)^\s*##\s*8\.\s*New Findings / Observations\s*$',
            '(?im)^\s*##\s*10\.\s*Documentation State\s*$',
            '(?im)^\s*##\s*11\.\s*Harness Improvements \(Very Important\)\s*$',
            '(?im)^\s*##\s*13\.\s*Next Recommended Steps\s*$',
            '(?im)^\s*##\s*15\.\s*Required Reading for Next Session\s*$'
        )
    }
}

function Test-QaInboxHasActionableItems {
    if (-not (Test-Path $QaInbox)) {
        return $false
    }

    $content = Get-Content $QaInbox -Raw -Encoding UTF8
    if ([string]::IsNullOrWhiteSpace($content)) {
        return $false
    }

    return ($content -match '(?m)^\s*-\s+\S')
}

function Test-QaStructuredHasStructuredItems {
    if (-not (Test-Path $QaStructured)) {
        return $false
    }

    $content = Get-Content $QaStructured -Raw -Encoding UTF8
    if ([string]::IsNullOrWhiteSpace($content)) {
        return $false
    }

    if ($content -match '(?im)^\s*##\s*Normalized Issues\s*$') {
        return ($content -match '(?im)^\s*###\s+Item\s+\d+')
    }

    return ($content -match '(?m)^\s*-\s+\S')
}

function Assert-QaStructuredReadyForPlanning {
    Assert-DailyOpsContextConsistency

    $qaInboxHasActionableItems = Test-QaInboxHasActionableItems
    if (-not $qaInboxHasActionableItems) {
        return
    }

    $qaStructuredPopulated = Test-QaStructuredHasStructuredItems
    if ($qaStructuredPopulated) {
        return
    }

    if ($DryRun) {
        Write-Host "[DryRun] QA_STRUCTURED.md is still empty, but planner gate is bypassed for dry-run validation." -ForegroundColor Yellow
        return
    }

    throw "QA_STRUCTURED.md must be populated before planner can run when QA_INBOX.md contains actionable items."
}

function Assert-PreHandoffReadiness {
    Assert-QaStructuredReadyForPlanning
    Assert-DailyOpsContextConsistency -IncludeTodayStrategy
    Assert-ReadableDailyOpsFiles -Paths $ReadableDocsToCheckBeforeHandoff
}

function Assert-PostHandoffReadiness {
    $required = @($ReadableDocsToCheckBeforeHandoff + $DailyHandoff)
    Assert-DailyOpsContextConsistency -IncludeTodayStrategy -IncludeDailyHandoff
    Assert-ReadableDailyOpsFiles -Paths $required
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
    Write-Host ("Strategy  : {0}" -f $TodayStrategy) -ForegroundColor $summaryColor
    Write-Host ("Handoff   : {0}" -f $DailyHandoff) -ForegroundColor $summaryColor
    Write-Host ("Prompts   : {0}" -f $PromptDir) -ForegroundColor $summaryColor

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
    $cmd = "Get-Content `"$PromptFile`" -Raw | codex $codexArgs"

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

Today: $DateString

Read:
- QA inbox: $QaInbox

Write only:
- $QaStructured

Tasks:
1. Convert raw QA into a normalized structured issue list.
2. Merge duplicates and closely related requests.
3. Separate implementation-ready items from broader product decisions.
4. Mark each item with:
   - category
   - surface
   - symptom
   - requested change
   - impact
   - priority
   - selected today (yes/no)
   - carry-over candidate (yes/no)
5. MUST produce a non-empty structured result if QA_INBOX contains actionable items.
6. Do not implement code.
7. Do not modify any other file.
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

Primary planning input:
- QA structured: $QaStructured

Cross-check input:
- QA inbox: $QaInbox

Read if available:
- format: $TodayFmt
- previous handoff: $PreviousHandoff
- reports: $ReportsDir

Tasks:
1. Read TODAY_STRATEGY_FORMAT.md
2. Create or update exactly: $TodayStrategy
3. Use QA_STRUCTURED.md as the primary input, and QA_INBOX.md only as a cross-check source.
4. If QA_STRUCTURED.md and QA_INBOX.md differ materially, report that mismatch inside the strategy.
5. Keep the plan small, safe, and Codex-executable.
6. Do not implement code.
7. Do not modify any other file.
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

function New-GitterPromptContent {
    param([int]$SelectedStepNumber)

    $gitterHint = if (Test-Path $GitterToml) {
        "Use agent configuration if relevant: $GitterToml"
    }
    else {
        "No gitter.toml file was found; still inspect the git diff carefully."
    }

    return @"
You are running the gitter role for this repository after Step $SelectedStepNumber.

Read first:
1. $ProjectBrief
2. $AgentsFile
3. $DevLoop
4. $HarnessRules
5. $TodayStrategy

Context:
- Step Number: $SelectedStepNumber
- $gitterHint

Tasks:
1. Inspect the current git diff.
2. Determine whether the diff is scoped only to the completed step.
3. Prepare and perform exactly one safe commit for the completed step if the diff is valid.
4. Use a concise conventional commit message.
5. Do not modify code or documentation content as part of this role.
6. If the diff is empty or unsafe, report clearly and do not force a commit.
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
4. Do not invent finished work
5. Do not modify any other file
"@
}

function Get-StagePromptFilePath {
    param([string]$StageSlug)
    return (Join-Path $PromptDir ("{0}-{1}.prompt.txt" -f $DateString, $StageSlug))
}

function Invoke-QaStructurerMode {
    $promptFile = Get-StagePromptFilePath -StageSlug 'qa-structurer'
    $promptContent = New-QaStructurerPromptContent

    Write-PromptFile -FilePath $promptFile -Content $promptContent
    Invoke-CodexFromPrompt -PromptFile $promptFile -WorkDir $RootDir -StageName 'qa-structurer'

    if (-not $DryRun) {
        $state = Get-WorkdayState
        $state.qa_structurer_done = $true
        Save-WorkdayState -State $state
    }
}

function Invoke-PlannerMode {
    Assert-QaStructuredReadyForPlanning

    $promptFile = Get-StagePromptFilePath -StageSlug 'planner'
    $promptContent = New-PlannerPromptContent

    Write-PromptFile -FilePath $promptFile -Content $promptContent
    Invoke-CodexFromPrompt -PromptFile $promptFile -WorkDir $RootDir -StageName 'planner'

    if (-not $DryRun) {
        Assert-DailyOpsContextConsistency -IncludeTodayStrategy
        $state = Get-WorkdayState
        $state.planner_done = $true
        Save-WorkdayState -State $state
    }
}

function Invoke-GitterMode {
    param([int]$SelectedStepNumber)

    $promptFile = Get-StagePromptFilePath -StageSlug ("gitter-step{0}" -f $SelectedStepNumber)
    $promptContent = New-GitterPromptContent -SelectedStepNumber $SelectedStepNumber

    Write-PromptFile -FilePath $promptFile -Content $promptContent
    Invoke-CodexFromPrompt -PromptFile $promptFile -WorkDir $RootDir -StageName ("gitter-step-{0}" -f $SelectedStepNumber)

    if (-not $DryRun) {
        $state = Get-WorkdayState
        if ($state.gitter_steps -notcontains $SelectedStepNumber) {
            $state.gitter_steps += $SelectedStepNumber
            Save-WorkdayState -State $state
        }
    }
}

function Invoke-CuratorMode {
    $promptFile = Get-StagePromptFilePath -StageSlug 'curator'
    $promptContent = New-CuratorPromptContent

    Write-PromptFile -FilePath $promptFile -Content $promptContent
    Invoke-CodexFromPrompt -PromptFile $promptFile -WorkDir $RootDir -StageName 'curator'

    if (-not $DryRun) {
        $state = Get-WorkdayState
        $state.curator_done = $true
        Save-WorkdayState -State $state
    }
}

function Invoke-HandoffMode {
    Assert-PreHandoffReadiness

    $promptFile = Get-StagePromptFilePath -StageSlug 'handoff'
    $promptContent = New-HandoffPromptContent

    Write-PromptFile -FilePath $promptFile -Content $promptContent
    Invoke-CodexFromPrompt -PromptFile $promptFile -WorkDir $RootDir -StageName 'handoff'

    if (-not $DryRun) {
        Assert-PostHandoffReadiness
        $state = Get-WorkdayState
        $state.handoff_done = $true
        Save-WorkdayState -State $state
    }
}

function Get-StepPurposeHint {
    param([int]$SelectedStepNumber)

    if (-not (Test-Path $TodayStrategy)) {
        return 'implementation'
    }

    $content = Get-Content $TodayStrategy -Raw -Encoding UTF8
    $pattern = "(?is)###?\s*Step\s*$SelectedStepNumber\b(.*?)(?=^\s{0,3}(?:#{1,6}\s*)?(?:Step|Phase)\s+\d+\b|\z)"
    $match = [regex]::Match($content, $pattern, [System.Text.RegularExpressions.RegexOptions]::Multiline)

    if (-not $match.Success) {
        return 'implementation'
    }

    $stepBody = $match.Groups[1].Value

    if ($stepBody -match '(?i)expected output\s*[\r\n-:\s]*analysis note') {
        return 'analysis'
    }

    if ($stepBody -match '(?i)expected output\s*[\r\n-:\s]*report') {
        return 'analysis'
    }

    if ($stepBody -match '(?i)\bgoal\b[\r\n-:\s]*.*\btrace\b') {
        return 'analysis'
    }

    return 'implementation'
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
            Where-Object {
                (Normalize-RepoPath -Path $_) -notin $beforeNormalized
            } |
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
                Where-Object {
                    Test-StartsWithNormalizedPath -Path $_ -Prefix 'docs\reports\'
                }
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

    $promptFile = Get-StagePromptFilePath -StageSlug ("step{0}" -f $SelectedStepNumber)
    $promptContent = New-StepPromptContent -SelectedStepNumber $SelectedStepNumber

    Write-PromptFile -FilePath $promptFile -Content $promptContent
    Invoke-CodexFromPrompt -PromptFile $promptFile -WorkDir $RootDir -StageName ("step-{0}" -f $SelectedStepNumber)

    $afterChangedFiles = Get-ChangedFileSnapshot -WorkDir $RootDir
    Assert-StepProducedExpectedArtifact `
        -SelectedStepNumber $SelectedStepNumber `
        -BeforeChangedFiles $beforeChangedFiles `
        -AfterChangedFiles $afterChangedFiles

    if (-not $DryRun) {
        $state = Get-WorkdayState
        if ($state.completed_steps -notcontains $SelectedStepNumber) {
            $state.completed_steps += $SelectedStepNumber
            Save-WorkdayState -State $state
        }
    }
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

function Get-TomorrowDateString {
    return (Get-Date $DateString).AddDays(1).ToString('yyyy-MM-dd')
}

function Initialize-NextDayCarryOverDraft {
    if ($DryRun) {
        return
    }

    $tomorrow = Get-TomorrowDateString
    $nextDir = Join-Path $OpsDir $tomorrow
    $nextQaInbox = Join-Path $nextDir 'QA_INBOX.md'
    $nextQaStructured = Join-Path $nextDir 'QA_STRUCTURED.md'
    $nextTodayStrategy = Join-Path $nextDir 'TODAY_STRATEGY.md'
    $nextDailyHandoff = Join-Path $nextDir 'DAILY_HANDOFF.md'

    New-DirectoryIfMissing -Path $nextDir

    if (Test-QaStructuredHasStructuredItems) {
        $carryOverHeader = @"
# QA_INBOX

## Date
$tomorrow

## Raw Notes
- Carry-over from $DateString session:
  - Review the previous day's QA_STRUCTURED.md and DAILY_HANDOFF.md for unfinished items.
"@

        New-TextFileIfMissing -Path $nextQaInbox -DefaultContent $carryOverHeader

        $structuredHeader = @"
# QA_STRUCTURED

## Date
$tomorrow

## Normalized Issues

### Item 1
- category: carry-over
- surface: to-be-decided
- symptom: Review unfinished items carried from $DateString
- requested change: Re-evaluate deferred / partial / blocked work from previous session
- impact: medium
- priority: P1
- selected today: no
- carry-over candidate: yes
- status: pending
- notes: Source from previous DAILY_HANDOFF and QA_STRUCTURED
"@

        New-TextFileIfMissing -Path $nextQaStructured -DefaultContent $structuredHeader
        New-TextFileIfMissing -Path $nextTodayStrategy -DefaultContent "# TODAY_STRATEGY`n"
        New-TextFileIfMissing -Path $nextDailyHandoff -DefaultContent "# DAILY_HANDOFF`n"
    }
}

function Invoke-WorkdayMode {
    $state = Get-WorkdayState

    if ($state.qa_structurer_done) {
        Write-Host "[SKIP] qa-structurer already done" -ForegroundColor Yellow
        Add-ExecutedStage -StageName 'qa-structurer (skipped)'
    }
    else {
        Invoke-QaStructurerMode
        $state = Get-WorkdayState
    }

    if ($state.planner_done) {
        Write-Host "[SKIP] planner already done" -ForegroundColor Yellow
        Add-ExecutedStage -StageName 'planner (skipped)'
    }
    else {
        Invoke-PlannerMode
        $state = Get-WorkdayState
    }

    if (-not (Test-Path $TodayStrategy)) {
        throw "TODAY_STRATEGY.md not found after planner run: $TodayStrategy"
    }

    $plannedSteps = @(Get-PlannedStepNumbers -TodayStrategyPath $TodayStrategy | Where-Object { $_ -ge $StepNumber })

    if ($plannedSteps.Count -eq 0) {
        throw "No planned steps were found in TODAY_STRATEGY.md starting from Step $StepNumber"
    }

    foreach ($plannedStep in $plannedSteps) {
        $state = Get-WorkdayState

        if ($state.completed_steps -contains $plannedStep) {
            Write-Host ("[SKIP] step-{0} already done" -f $plannedStep) -ForegroundColor Yellow
            Add-ExecutedStage -StageName ("step-{0} (skipped)" -f $plannedStep)
        }
        else {
            Invoke-StepMode -SelectedStepNumber $plannedStep
        }

        $state = Get-WorkdayState

        if ($state.gitter_steps -contains $plannedStep) {
            Write-Host ("[SKIP] gitter-step-{0} already done" -f $plannedStep) -ForegroundColor Yellow
            Add-ExecutedStage -StageName ("gitter-step-{0} (skipped)" -f $plannedStep)
        }
        else {
            Invoke-GitterMode -SelectedStepNumber $plannedStep
        }

        Wait-ForQaCheckpoint -CompletedStepNumber $plannedStep
    }

    $state = Get-WorkdayState

    if ($state.curator_done) {
        Write-Host "[SKIP] curator already done" -ForegroundColor Yellow
        Add-ExecutedStage -StageName 'curator (skipped)'
    }
    else {
        Invoke-CuratorMode
    }

    $state = Get-WorkdayState

    if ($state.handoff_done) {
        Write-Host "[SKIP] handoff already done" -ForegroundColor Yellow
        Add-ExecutedStage -StageName 'handoff (skipped)'
    }
    else {
        Invoke-HandoffMode
    }

    Initialize-NextDayCarryOverDraft
}

# ==========================================
# 4. Validation# ==========================================
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
New-DirectoryIfMissing -Path $PromptRootDir
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

"@

$PreviousHandoff = Get-LatestPreviousHandoffFile -OpsRoot $OpsDir -CurrentDate $DateString

# ==========================================
# 6. Execute by mode
# ==========================================
$runStatus = 'SUCCESS'
$errorMessage = $null

try {
    switch ($Mode) {
        'planner' {
            Invoke-PlannerMode
        }
        'qa-structurer' {
            Invoke-QaStructurerMode
        }
        'step' {
            Invoke-StepMode -SelectedStepNumber $StepNumber
        }
        'curator' {
            Invoke-CuratorMode
        }
        'handoff' {
            Invoke-HandoffMode
        }
        'all' {
            Invoke-QaStructurerMode
            Invoke-PlannerMode
            Invoke-StepMode -SelectedStepNumber $StepNumber
            Invoke-GitterMode -SelectedStepNumber $StepNumber
            Invoke-CuratorMode
            Invoke-HandoffMode
            Initialize-NextDayCarryOverDraft
        }
        'workday' {
            Invoke-WorkdayMode
        }
    }
}
catch {
    $runStatus = 'FAILED'
    $errorMessage = $_.Exception.Message
    throw
}
finally {
    Write-FinalRunSummary -ModeName $Mode -Status $runStatus -ErrorMessage $errorMessage
}
