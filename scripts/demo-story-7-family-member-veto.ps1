param(
    [string]$BashPath
)

$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Resolve-Path (Join-Path $scriptDir "..")
$storyScript = Join-Path $scriptDir "demo-story-7-family-member-veto.sh"

$candidates = @()
if ($BashPath) {
    $candidates += $BashPath
}
$candidates += @(
    "C:\Program Files\Git\bin\bash.exe",
    "C:\Program Files\Git\usr\bin\bash.exe",
    "C:\Program Files (x86)\Git\bin\bash.exe",
    "C:\Program Files (x86)\Git\usr\bin\bash.exe"
)

$gitBash = $candidates | Where-Object { Test-Path $_ } | Select-Object -First 1

if (-not $gitBash) {
    Write-Error "Git Bash was not found. Install Git for Windows or pass -BashPath 'C:\path\to\bash.exe'."
}

Push-Location $repoRoot
try {
    & $gitBash $storyScript
    exit $LASTEXITCODE
}
finally {
    Pop-Location
}
