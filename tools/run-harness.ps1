param(
    [ValidateSet("planner", "step", "curator", "handoff", "all", "workday")]
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
$StepOutcomes = New-Object System.Collections.Generic.List[object]

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
$DailyOpsDocNames = @('QA_INBOX.md', 'QA_STRUCTURED.md', 'TODAY_STRATEGY.md', 'DAILY_HANDOFF.md')
$ReadableDocsToCheckBeforeHandoff = @($QaInbox, $QaStructured, $TodayStrategy)

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

    return ($relativePath -replace '/', '\\')
}

function Normalize-RelativePath {
    param([string]$Path)

    if ([string]::IsNullOrWhiteSpace($Path)) {
        return $null
    }

    return (($Path -replace '/', '\\').Trim() -replace '^\.\\', '').ToLowerInvariant()
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

        return @($output | ForEach-Object {
            if ($_.Length -ge 4) {
                $_.Substring(3).Trim()
            }
        } | Where-Object { $_ })
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

function Get-NewChangedFiles {
    param(
        [string[]]$Before = @(),
        [string[]]$After = @()
    )

    $beforeSet = @{}
    foreach ($item in $Before) {
        $normalized = Normalize-RelativePath -Path $item
        if ($normalized) {
            $beforeSet[$normalized] = $true
        }
    }

    $result = New-Object System.Collections.Generic.List[string]
    foreach ($item in $After) {
        $normalized = Normalize-RelativePath -Path $item
        if (-not $normalized) {
            continue
        }
        if (-not $beforeSet.ContainsKey($normalized)) {
            $result.Add($item) | Out-Null
        }
    }

    return @($result)
}

function Test-IsOpsOrPromptFile {
    param([string]$RelativePath)

    $normalized = Normalize-RelativePath -Path $RelativePath
    if (-not $normalized) {
        return $false
    }

    return ($normalized -match '^docs\\ops\\' -or $normalized -match '^\.codex\\prompts\\')
}

function Test-IsDailyOpsDoc {
    param([string]$RelativePath)

    $normalized = Normalize-RelativePath -Path $RelativePath
    if (-not $normalized) {
        return $false
    }

    if ($normalized -notmatch '^docs\\ops\\\d{4}-\d{2}-\d{2}\\') {
        return $false
    }

    foreach ($name in $DailyOpsDocNames) {
        $normalizedName = $name.ToLowerInvariant()
        if ($normalized.EndsWith("\\$normalizedName")) {
            return $true
        }
    }

    return $false
}

function Test-IsReportsFile {
    param([string]$RelativePath)

    $normalized = Normalize-RelativePath -Path $RelativePath
    if (-not $normalized) {
        return $false
    }

    return ($normalized -match '^docs\\reports\\')
}

function Get-CodeLikeChangedFiles {
    param(
        [string[]]$ChangedFiles = @(),
        [string[]]$AdditionalAllowedPaths = @()
    )

    $normalizedAllowed = @($AdditionalAllowedPaths | Where-Object { $_ } | ForEach-Object { Normalize-RelativePath -Path $_ })

    return @($ChangedFiles | Where-Object {
        $normalized = Normalize-RelativePath -Path $_
        if (-not $normalized) {
            return $false
        }
        if ($normalized -in $normalizedAllowed) {
            return $false
        }
        if (Test-IsOpsOrPromptFile -RelativePath $_) {
            return $false
        }
        return $true
    })
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

    $unexpected = @(Get-CodeLikeChangedFiles -ChangedFiles $changedFiles -AdditionalAllowedPaths $AdditionalAllowedPaths)

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

function Get-StepSectionContent {
    param(
        [string]$TodayStrategyPath,
        [int]$SelectedStepNumber
    )

    if (-not (Test-Path $TodayStrategyPath)) {
        return $null
    }

    $content = Get-Content $TodayStrategyPath -Raw -Encoding UTF8
    if ([string]::IsNullOrWhiteSpace($content)) {
        return $null
    }

    $pattern = '(?ims)^\s{0,3}(?:#{1,6}\s*)?(?:step|phase)\s*[-:]?\s*' + [regex]::Escape($SelectedStepNumber.ToString()) + '\b.*?(?=^\s{0,3}(?:#{1,6}\s*)?(?:step|phase)\s*[-:]?\s*\d+\b|\z)'
    $match = [regex]::Match($content, $pattern)

    if ($match.Success) {
        return $match.Value
    }

    return $null
}

function Test-StepAllowsAnalysisArtifact {
    param(
        [string]$TodayStrategyPath,
        [int]$SelectedStepNumber
    )

    $section = Get-StepSectionContent -TodayStrategyPath $TodayStrategyPath -SelectedStepNumber $SelectedStepNumber
    if ([string]::IsNullOrWhiteSpace($section)) {
        return $false
    }

    $lower = $section.ToLowerInvariant()
    return ($lower -match 'expected output' -and ($lower -match 'analysis note' -or $lower -match 'report'))
}

function Get-ReadableFileText {
    param([string]$Path)

    if (-not (Test-Path $Path)) {
        throw "Required file not found: $Path"
    }

    $content = Get-Content $Path -Raw -Encoding UTF8
    if ($null -eq $content) {
        throw "File could not be read as UTF-8 text: $Path"
    }

    return $content
}

function Test-TextLooksCorrupted {
    param([string]$Content)

    if ([string]::IsNullOrWhiteSpace($Content)) {
        return $false
    }

    return (
    $Content.Contains([char]0xFFFD) -or
            $Content -match 'Ã.|Â.|ì.|ë.|ê.|ìž|ìš|ì•|ì„|í•|í—' -or
            $Content -match '\?\?\?'
    )
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

function Assert-PreHandoffReadiness {
    Assert-ReadableDailyOpsFiles -Paths $ReadableDocsToCheckBeforeHandoff
}

function Assert-PostHandoffReadiness {
    $required = @($ReadableDocsToCheckBeforeHandoff + $DailyHandoff)
    Assert-ReadableDailyOpsFiles -Paths $required
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
2. Extract the exact goal / target area / likely files / forbidden scope / validation / expected output for Step $SelectedStepNumber
3. Analyze the code paths needed for only that step
4. If the step expects implementation, implement only the smallest safe change for that step
5. If the step expects an analysis note or report instead of implementation, do NOT no-op; create exactly one analysis artifact under docs/reports/ that documents the traced path and findings for Step $SelectedStepNumber
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

function Get-StepPromptFilePath {
    param([int]$SelectedStepNumber)

    return (Join-Path $PromptDir "$DateString-step$SelectedStepNumber.prompt.txt")
}

function Add-StepOutcome {
    param(
        [int]$StepNumber,
        [bool]$RequiresAnalysisArtifact,
        [string[]]$AllNewFiles,
        [string[]]$CodeLikeFiles,
        [string[]]$ArtifactFiles
    )

    $StepOutcomes.Add([pscustomobject]@{
        StepNumber = $StepNumber
        RequiresAnalysisArtifact = $RequiresAnalysisArtifact
        AllNewFiles = @($AllNewFiles)
        CodeLikeFiles = @($CodeLikeFiles)
        ArtifactFiles = @($ArtifactFiles)
    }) | Out-Null
}

function Assert-StepProducedExpectedChange {
    param(
        [int]$SelectedStepNumber,
        [string[]]$NewChangedFiles,
        [bool]$AllowsAnalysisArtifact,
        [string[]]$AdditionalAllowedPaths = @()
    )

    $codeLikeFiles = @(Get-CodeLikeChangedFiles -ChangedFiles $NewChangedFiles -AdditionalAllowedPaths $AdditionalAllowedPaths)
    $artifactFiles = @($NewChangedFiles | Where-Object {
        Test-IsReportsFile -RelativePath $_
    })

    if ($AllowsAnalysisArtifact) {
        if ($codeLikeFiles.Count -eq 0 -and $artifactFiles.Count -eq 0) {
            throw "No implementation files or analysis artifact were created after step-$SelectedStepNumber. The step may have been skipped."
        }
    }
    else {
        if ($codeLikeFiles.Count -eq 0) {
            throw "No non-ops file changes detected after step-$SelectedStepNumber. Implementation may have been skipped."
        }
    }

    Add-StepOutcome `
        -StepNumber $SelectedStepNumber `
        -RequiresAnalysisArtifact $AllowsAnalysisArtifact `
        -AllNewFiles $NewChangedFiles `
        -CodeLikeFiles $codeLikeFiles `
        -ArtifactFiles $artifactFiles
}

function Invoke-StepMode {
    param([int]$SelectedStepNumber)

    $stepPromptFile = Get-StepPromptFilePath -SelectedStepNumber $SelectedStepNumber
    $stepPromptContent = New-StepPromptContent -SelectedStepNumber $SelectedStepNumber
    $allowedChangedPaths = @(Get-AllowedChangedFiles -WorkDir $RootDir -CurrentScriptPath $ScriptPath)
    $beforeChangedFiles = @(Get-ChangedFileList -WorkDir $RootDir)
    $allowsAnalysisArtifact = Test-StepAllowsAnalysisArtifact -TodayStrategyPath $TodayStrategy -SelectedStepNumber $SelectedStepNumber

    Write-PromptFile -FilePath $stepPromptFile -Content $stepPromptContent

    Assert-StepExecutionReady `
        -TodayStrategyPath $TodayStrategy `
        -WorkDir $RootDir `
        -SkipGitCheck:$SkipGitStatusCheck `
        -AdditionalAllowedPaths $allowedChangedPaths

    Invoke-CodexFromPrompt -PromptFile $stepPromptFile -WorkDir $RootDir -StageName ("step-{0}" -f $SelectedStepNumber)

    if ($DryRun) {
        return
    }

    $afterChangedFiles = @(Get-ChangedFileList -WorkDir $RootDir)
    $newChangedFiles = @(Get-NewChangedFiles -Before $beforeChangedFiles -After $afterChangedFiles)

    Assert-StepProducedExpectedChange `
        -SelectedStepNumber $SelectedStepNumber `
        -NewChangedFiles $newChangedFiles `
        -AllowsAnalysisArtifact:$allowsAnalysisArtifact `
        -AdditionalAllowedPaths $allowedChangedPaths
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
    Invoke-CodexFromPrompt -PromptFile $PlannerPromptFile -WorkDir $RootDir -StageName "planner"

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

    if (-not $DryRun) {
        Assert-PreHandoffReadiness
    }

    Invoke-CodexFromPrompt -PromptFile $CuratorPromptFile -WorkDir $RootDir -StageName "curator"
    Invoke-CodexFromPrompt -PromptFile $HandoffPromptFile -WorkDir $RootDir -StageName "handoff"

    if (-not $DryRun) {
        Assert-PostHandoffReadiness
    }
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
    Write-Host ""
    $elapsedText = $elapsed.ToString("mm\:ss")
    Write-Host ("[OK] {0} finished in {1}" -f $StageName, $elapsedText) -ForegroundColor Green
}

function Write-StepOutcomeSummary {
    if ($StepOutcomes.Count -eq 0) {
        return
    }

    Write-Host ("Step Results:") -ForegroundColor DarkCyan
    foreach ($outcome in $StepOutcomes) {
        $kind = if ($outcome.RequiresAnalysisArtifact) { "analysis-or-impl" } else { "implementation" }
        $files = if ($outcome.AllNewFiles.Count -gt 0) { $outcome.AllNewFiles -join ', ' } else { '(none)' }
        Write-Host ("  - step-{0} [{1}] => {2}" -f $outcome.StepNumber, $kind, $files) -ForegroundColor DarkCyan
    }
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

    if ($ModeName -eq 'workday' -or $ModeName -eq 'planner' -or $ModeName -eq 'all') {
        Write-Host ("Strategy  : {0}" -f $TodayStrategy) -ForegroundColor $summaryColor
    }

    if ($ModeName -eq 'workday' -or $ModeName -eq 'handoff' -or $ModeName -eq 'all') {
        Write-Host ("Handoff   : {0}" -f $DailyHandoff) -ForegroundColor $summaryColor
    }

    if ($StepOutcomes.Count -gt 0) {
        Write-StepOutcomeSummary
    }

    if (-not [string]::IsNullOrWhiteSpace($ErrorMessage)) {
        Write-Host ("Error     : {0}" -f $ErrorMessage) -ForegroundColor Red
    }

    Write-Host "==========================================" -ForegroundColor $summaryColor
    Write-Host ""
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
New-DirectoryIfMissing -Path $ReportsDir

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

## Structured Items

-
"@

$PreviousHandoff = Get-LatestPreviousHandoffFile -OpsRoot $OpsDir -CurrentDate $DateString

# ==========================================
# 6. Prompt contents
# ==========================================
$PlannerPrompt = @"
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

Tasks:
1. Read TODAY_STRATEGY_FORMAT.md
2. Create or update exactly: $TodayStrategy
3. Keep the plan small, safe, and Codex-executable
4. Do not implement code
5. Do not modify any other file
"@

$StepPrompt = New-StepPromptContent -SelectedStepNumber $StepNumber

$CuratorPrompt = @"
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

$HandoffPrompt = @"
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
- MUST NOT claim a step was completed unless a real implementation file or analysis artifact was changed during this run

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
5. If this run produced no implementation change for a step, say so explicitly
6. Do not modify any other file
"@

# ==========================================
# 7. Write prompt files
# ==========================================
$PlannerPromptFile = Join-Path $PromptDir "$DateString-planner.prompt.txt"
$StepPromptFile    = Get-StepPromptFilePath -SelectedStepNumber $StepNumber
$CuratorPromptFile = Join-Path $PromptDir "$DateString-curator.prompt.txt"
$HandoffPromptFile = Join-Path $PromptDir "$DateString-handoff.prompt.txt"

Write-PromptFile -FilePath $PlannerPromptFile -Content $PlannerPrompt
Write-PromptFile -FilePath $StepPromptFile -Content $StepPrompt
Write-PromptFile -FilePath $CuratorPromptFile -Content $CuratorPrompt
Write-PromptFile -FilePath $HandoffPromptFile -Content $HandoffPrompt

# ==========================================
# 8. Execute by mode
# ==========================================
$runStatus = "SUCCESS"
$errorMessage = $null

try {
    switch ($Mode) {
        "planner" {
            Invoke-CodexFromPrompt -PromptFile $PlannerPromptFile -WorkDir $RootDir -StageName "planner"
        }
        "step" {
            Invoke-StepMode -SelectedStepNumber $StepNumber
        }
        "curator" {
            Invoke-CodexFromPrompt -PromptFile $CuratorPromptFile -WorkDir $RootDir -StageName "curator"
        }
        "handoff" {
            if (-not $DryRun) {
                Assert-PreHandoffReadiness
            }
            Invoke-CodexFromPrompt -PromptFile $HandoffPromptFile -WorkDir $RootDir -StageName "handoff"
            if (-not $DryRun) {
                Assert-PostHandoffReadiness
            }
        }
        "all" {
            Invoke-CodexFromPrompt -PromptFile $PlannerPromptFile -WorkDir $RootDir -StageName "planner"
            Invoke-StepMode -SelectedStepNumber $StepNumber
            if (-not $DryRun) {
                Assert-PreHandoffReadiness
            }
            Invoke-CodexFromPrompt -PromptFile $CuratorPromptFile -WorkDir $RootDir -StageName "curator"
            Invoke-CodexFromPrompt -PromptFile $HandoffPromptFile -WorkDir $RootDir -StageName "handoff"
            if (-not $DryRun) {
                Assert-PostHandoffReadiness
            }
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
