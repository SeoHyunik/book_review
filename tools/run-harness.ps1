param(
    [ValidateSet("planner", "curator", "handoff", "all")]
    [string]$Mode = "planner",

    [string]$RootDir = (Get-Location).Path,

    [string]$DateString = (Get-Date -Format "yyyy-MM-dd"),

    [switch]$DryRun
)

[Console]::InputEncoding  = [System.Text.UTF8Encoding]::new($false)
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
$OutputEncoding = [System.Text.UTF8Encoding]::new($false)

$ErrorActionPreference = "Stop"

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

# ==========================================
# 2. Codex CLI args
# ==========================================
# Non-interactive execution:
# codex exec --dangerously-bypass-approvals-and-sandbox -C "<workdir>"
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

        # Important:
        # only create if missing
        # do NOT rewrite existing files
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

    # Prompt files are newly generated operational artifacts.
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

function Invoke-CodexFromPrompt {
    param(
        [string]$PromptFile,
        [string]$WorkDir
    )

    $codexArgs = $CodexBaseArgs.Replace("{WORKDIR}", $WorkDir)
    $cmd  = "Get-Content `"$PromptFile`" -Raw | codex $codexArgs"

    Write-Host ""
    Write-Host "==========================================" -ForegroundColor Cyan
    Write-Host "Codex Command" -ForegroundColor Cyan
    Write-Host "==========================================" -ForegroundColor Cyan
    Write-Host $cmd
    Write-Host ""

    if ($DryRun) {
        Write-Host "[DryRun] Execution skipped." -ForegroundColor Yellow
        return
    }

    Invoke-Expression $cmd
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

# Create only if missing. Never rewrite existing daily docs.
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

# ==========================================
# 7. Write prompt files (safe to overwrite)
# ==========================================
$PlannerPromptFile = Join-Path $PromptDir "$DateString-planner.prompt.txt"
$CuratorPromptFile = Join-Path $PromptDir "$DateString-curator.prompt.txt"
$HandoffPromptFile = Join-Path $PromptDir "$DateString-handoff.prompt.txt"

Write-PromptFile -FilePath $PlannerPromptFile -Content $PlannerPrompt
Write-PromptFile -FilePath $CuratorPromptFile -Content $CuratorPrompt
Write-PromptFile -FilePath $HandoffPromptFile -Content $HandoffPrompt

# ==========================================
# 8. Execute by mode
# ==========================================
switch ($Mode) {
    "planner" {
        Invoke-CodexFromPrompt -PromptFile $PlannerPromptFile -WorkDir $RootDir
    }
    "curator" {
        Invoke-CodexFromPrompt -PromptFile $CuratorPromptFile -WorkDir $RootDir
    }
    "handoff" {
        Invoke-CodexFromPrompt -PromptFile $HandoffPromptFile -WorkDir $RootDir
    }
    "all" {
        Invoke-CodexFromPrompt -PromptFile $PlannerPromptFile -WorkDir $RootDir
        Invoke-CodexFromPrompt -PromptFile $CuratorPromptFile -WorkDir $RootDir
        Invoke-CodexFromPrompt -PromptFile $HandoffPromptFile -WorkDir $RootDir
    }
}