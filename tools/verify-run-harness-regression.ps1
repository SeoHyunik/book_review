param(
    [string]$RootDir = (Split-Path $PSScriptRoot -Parent)
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$script:Failures = New-Object System.Collections.Generic.List[string]

function Assert-True {
    param(
        [bool]$Condition,
        [string]$Message
    )

    if (-not $Condition) {
        throw $Message
    }
}

function Assert-Equal {
    param(
        $Expected,
        $Actual,
        [string]$Message
    )

    if ($Expected -ne $Actual) {
        throw ("{0} expected={1} actual={2}" -f $Message, $Expected, $Actual)
    }
}

function Invoke-Test {
    param(
        [string]$Name,
        [scriptblock]$Block
    )

    try {
        & $Block
        Write-Host ("[PASS] {0}" -f $Name) -ForegroundColor Green
    }
    catch {
        $script:Failures.Add(("{0}: {1}" -f $Name, $_.Exception.Message))
        Write-Host ("[FAIL] {0}: {1}" -f $Name, $_.Exception.Message) -ForegroundColor Red
    }
}

$env:HARNESS_LIBRARY_MODE = '1'
try {
    . (Join-Path $RootDir 'tools/run-harness.ps1') -RootDir $RootDir -DateString '2026-04-16' -StepNumber 1 -DryRun

    Invoke-Test -Name 'interruption classification' -Block {
        $cases = @(
            @{ Text = 'codex stderr: token limit reached'; Pattern = 'token' },
            @{ Text = 'codex stderr: usage limit exceeded'; Pattern = 'usage limit' },
            @{ Text = 'codex stderr: quota exceeded'; Pattern = 'quota' },
            @{ Text = 'codex stderr: rate limit exceeded'; Pattern = 'rate limit' }
        )

        foreach ($case in $cases) {
            $info = Get-InterruptionInfo -Text $case.Text -StageName 'step-3'
            Assert-True ($null -ne $info) 'expected interruption info to be detected'
            Assert-True $info.is_resumable 'expected interruption to be resumable'
            Assert-Equal 'resumable_interruption' $info.type 'expected interruption type'
            Assert-Equal 'token_or_usage_limit' $info.reason 'expected interruption reason'
            Assert-Equal 'step-3' $info.stage 'expected stage name'
            Assert-Equal $case.Pattern $info.detected_pattern 'expected matched interruption pattern'
        }
    }

    Invoke-Test -Name 'resume floor narrowing' -Block {
        $state = @{
            stage_status = 'interrupted'
            interruption = @{
                reason = 'token_or_usage_limit'
            }
            next_step = 4
        }

        $resumeFloor = Get-ResumeStepFloor -State $state -RequestedStepNumber 2
        Assert-Equal 4 $resumeFloor 'expected resume floor to narrow to unfinished step'

        $unchangedFloor = Get-ResumeStepFloor -State $state -RequestedStepNumber 6
        Assert-Equal 6 $unchangedFloor 'expected requested step to stay unchanged when already later'
    }

    Invoke-Test -Name 'run status mapping' -Block {
        $completed = Get-RunStatusFromErrorMessage -ErrorMessage $null
        Assert-Equal 'COMPLETED' $completed.runStatus 'expected completed status'
        Assert-Equal 0 $completed.exitCode 'expected completed exit code'

        $interrupted = Get-RunStatusFromErrorMessage -ErrorMessage '[RESUMABLE_INTERRUPTION] Stage step-2 interrupted due to token/usage limit signal.'
        Assert-Equal 'INTERRUPTED' $interrupted.runStatus 'expected interrupted status'
        Assert-Equal 2 $interrupted.exitCode 'expected interrupted exit code'

        $failed = Get-RunStatusFromErrorMessage -ErrorMessage 'unexpected failure'
        Assert-Equal 'FAILED' $failed.runStatus 'expected failed status'
        Assert-Equal 1 $failed.exitCode 'expected failed exit code'
    }

    if ($script:Failures.Count -gt 0) {
        throw ("Run harness regression verification failed:`n- {0}" -f ($script:Failures -join "`n- "))
    }

    Write-Host '[PASS] run-harness regression verification completed' -ForegroundColor Green
}
finally {
    Remove-Item Env:HARNESS_LIBRARY_MODE -ErrorAction SilentlyContinue
}
