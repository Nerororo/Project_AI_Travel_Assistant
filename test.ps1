[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

$projectRoot = $PSScriptRoot

function Invoke-NativeCommand([string]$command, [string[]]$arguments) {
    $previousErrorPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        & $command @arguments
        $script:nativeExitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previousErrorPreference
    }
}

if (-not (Get-Command docker.exe -ErrorAction SilentlyContinue)) {
    throw 'Docker CLI is required to run the Docker-backed integration tests.'
}

Invoke-NativeCommand 'docker.exe' @('info')
if ($script:nativeExitCode -ne 0) {
    throw 'Docker Desktop is unavailable through the Windows named pipe. Start Docker Desktop and verify the active Docker context.'
}

Write-Host 'Running the complete Gradle test suite with isolated Testcontainers MySQL.'
Invoke-NativeCommand (Join-Path $projectRoot 'gradlew.bat') @('test', '--no-daemon')
if ($script:nativeExitCode -ne 0) {
    throw "Gradle tests failed with exit code $($script:nativeExitCode)."
}
