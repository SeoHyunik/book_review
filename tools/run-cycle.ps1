param(
    [string]$WorkspaceRoot = "D:\harness-workspaces\auziraum",

    [string]$Date = (Get-Date -Format "yyyy-MM-dd"),

    [string]$KitRoot = "D:\harness-kit",

    [string]$ProjectRoot = "S:\dev\project\auziraum",

    [string]$Profile = "corp-default",

    [switch]$RunDoctor,

    [switch]$RunOpsValidation,

    [switch]$SkipDoctor,

    [switch]$SkipOpsValidation,

    [switch]$ValidateForClosure
)

$ErrorActionPreference = "Stop"

function Write-Step {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Message
    )

    Write-Host "==> $Message" -ForegroundColor Cyan
}

function Write-Ok {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Message
    )

    Write-Host "[OK] $Message" -ForegroundColor Green
}

function Resolve-FullPathSafe {
    param(
        [Parameter(Mandatory = $true)]
        [string]$PathValue
    )

    if ([string]::IsNullOrWhiteSpace($PathValue)) {
        return $PathValue
    }

    if ([System.IO.Path]::IsPathRooted($PathValue)) {
        return [System.IO.Path]::GetFullPath($PathValue)
    }

    return [System.IO.Path]::GetFullPath((Join-Path (Get-Location).Path $PathValue))
}

function Test-ParameterSupported {
    param(
        [Parameter(Mandatory = $true)]
        [System.Management.Automation.CommandInfo]$CommandInfo,

        [Parameter(Mandatory = $true)]
        [string]$ParameterName
    )

    return $CommandInfo.Parameters.ContainsKey($ParameterName)
}

$resolvedWorkspaceRoot = Resolve-FullPathSafe -PathValue $WorkspaceRoot
$resolvedKitRoot = Resolve-FullPathSafe -PathValue $KitRoot

$resolvedProjectRoot = ""
if (-not [string]::IsNullOrWhiteSpace($ProjectRoot)) {
    $resolvedProjectRoot = Resolve-FullPathSafe -PathValue $ProjectRoot
}

$coreRunCycleScript = Join-Path $resolvedKitRoot "scripts/run-cycle.ps1"

if (-not (Test-Path $coreRunCycleScript)) {
    throw "SSD core run-cycle script not found: $coreRunCycleScript"
}

Write-Host "=== Bridge Run Cycle ===" -ForegroundColor Cyan
Write-Host "WorkspaceRoot : $resolvedWorkspaceRoot"
Write-Host "Date          : $Date"
Write-Host "KitRoot       : $resolvedKitRoot"
Write-Host "ProjectRoot   : $resolvedProjectRoot"
Write-Host "Profile       : $Profile"
Write-Host "CoreScript    : $coreRunCycleScript"
Write-Host ""

$effectiveRunDoctor = $false
if ($RunDoctor.IsPresent) {
    $effectiveRunDoctor = $true
}
if ($SkipDoctor.IsPresent) {
    $effectiveRunDoctor = $false
}

$effectiveRunOpsValidation = $false
if ($RunOpsValidation.IsPresent) {
    $effectiveRunOpsValidation = $true
}
if ($SkipOpsValidation.IsPresent) {
    $effectiveRunOpsValidation = $false
}

Write-Step "Delegating execution to SSD core run-cycle"

$coreCommand = Get-Command -Name $coreRunCycleScript -CommandType ExternalScript

$invokeParams = @{}

if (Test-ParameterSupported -CommandInfo $coreCommand -ParameterName "WorkspaceRoot") {
    $invokeParams["WorkspaceRoot"] = $resolvedWorkspaceRoot
}

if (Test-ParameterSupported -CommandInfo $coreCommand -ParameterName "Date") {
    $invokeParams["Date"] = $Date
}

if (Test-ParameterSupported -CommandInfo $coreCommand -ParameterName "KitRoot") {
    $invokeParams["KitRoot"] = $resolvedKitRoot
}

if ((-not [string]::IsNullOrWhiteSpace($resolvedProjectRoot)) -and (Test-ParameterSupported -CommandInfo $coreCommand -ParameterName "ProjectRoot")) {
    $invokeParams["ProjectRoot"] = $resolvedProjectRoot
}

if (Test-ParameterSupported -CommandInfo $coreCommand -ParameterName "Profile") {
    $invokeParams["Profile"] = $Profile
}

if (Test-ParameterSupported -CommandInfo $coreCommand -ParameterName "RunDoctor") {
    $invokeParams["RunDoctor"] = $effectiveRunDoctor
}
elseif (Test-ParameterSupported -CommandInfo $coreCommand -ParameterName "SkipDoctor") {
    $invokeParams["SkipDoctor"] = (-not $effectiveRunDoctor)
}

if (Test-ParameterSupported -CommandInfo $coreCommand -ParameterName "RunOpsValidation") {
    $invokeParams["RunOpsValidation"] = $effectiveRunOpsValidation
}
elseif (Test-ParameterSupported -CommandInfo $coreCommand -ParameterName "SkipOpsValidation") {
    $invokeParams["SkipOpsValidation"] = (-not $effectiveRunOpsValidation)
}

if ($ValidateForClosure.IsPresent -and (Test-ParameterSupported -CommandInfo $coreCommand -ParameterName "ValidateForClosure")) {
    $invokeParams["ValidateForClosure"] = $true
}

& $coreRunCycleScript @invokeParams

Write-Host ""
Write-Ok "Bridge run-cycle delegated successfully."
Write-Host "Current day workspace: $(Join-Path $resolvedWorkspaceRoot $Date)" -ForegroundColor Green
