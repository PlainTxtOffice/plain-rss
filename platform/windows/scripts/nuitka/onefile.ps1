<#
.SYNOPSIS
Build Plain RSS as a single Windows executable with Nuitka.

.DESCRIPTION
Runs Nuitka from the Windows project root and writes build/onefile/Plain RSS.exe.
The compiled app keeps its data and logs under %LOCALAPPDATA%, in the folder
named by [tool.plain-rss].app-data-dir in pyproject.toml.

Pass -PythonExecutable to build with an interpreter other than the
Windows project's .venv, for example:
    .\scripts\nuitka\onefile.ps1 -PythonExecutable C:\Python313\python.exe

.PARAMETER ResetRuntimeDefaults
Rename the per-user data folder to a timestamped backup before building so the
smoke test starts with no saved subscriptions or settings.
#>
[CmdletBinding()]
param(
    [string]$PythonExecutable = ".venv/Scripts/python.exe",
    [string]$OutputDirectory = "build/onefile",
    [string]$OutputFilename = "Plain RSS.exe",
    [switch]$ResetRuntimeDefaults
)

$ErrorActionPreference = "Stop"
$projectTitle = "Plain RSS"
Write-Host "Building $projectTitle (onefile)"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
$pythonPath = Join-Path $repoRoot $PythonExecutable
if (-not (Test-Path $pythonPath)) {
    throw "Python interpreter not found: $pythonPath"
}

Push-Location $repoRoot
try {
    & $pythonPath -c "import zstandard" 2>$null
    if ($LASTEXITCODE -ne 0) {
        # Compressed onefile builds need zstandard from Nuitka[onefile].
        throw "zstandard is not installed. Run: $pythonPath -m pip install 'nuitka[onefile]'"
    }

    $appDataDirName = & $pythonPath -c "import tomllib; print(tomllib.load(open('pyproject.toml', 'rb'))['tool']['plain-rss']['app-data-dir'])"
    if ($LASTEXITCODE -ne 0) {
        throw "Could not read [tool.plain-rss].app-data-dir from pyproject.toml."
    }
    $userDataDir = Join-Path $env:LOCALAPPDATA $appDataDirName
    if (Test-Path $userDataDir) {
        if ($ResetRuntimeDefaults) {
            $backupName = "$appDataDirName.bak-$(Get-Date -Format 'yyyyMMdd-HHmmss')"
            try {
                Rename-Item -Path $userDataDir -NewName $backupName
            }
            catch {
                throw "Could not rename $userDataDir. Close any running copies of $projectTitle and try again."
            }
            Write-Warning "Moved existing user data to $(Join-Path $env:LOCALAPPDATA $backupName)."
        }
        else {
            Write-Warning "Existing user data in $userDataDir will be used by smoke tests. Pass -ResetRuntimeDefaults to start clean."
        }
    }

    $nuitkaArguments = @(
        "-m", "nuitka",
        "--onefile",
        "--enable-plugin=pyside6",
        "--windows-console-mode=disable",
        "--windows-icon-from-ico=assets/plain_rss.ico",
        "--include-data-files=assets/plain_rss.ico=assets/plain_rss.ico",
        "--msvc=latest",
        "--output-filename=$OutputFilename",
        "--output-dir=$OutputDirectory",
        "--assume-yes-for-downloads",
        "plain_rss/main.py"
    )

    Write-Host "Output directory: $(Join-Path $repoRoot $OutputDirectory)"
    & $pythonPath @nuitkaArguments
    if ($LASTEXITCODE -ne 0) {
        throw "Nuitka failed with exit code $LASTEXITCODE."
    }
}
finally {
    Pop-Location
}
