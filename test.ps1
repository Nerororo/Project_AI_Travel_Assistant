[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

$projectRoot = $PSScriptRoot
$gradleImage = 'gradle:9.7.1-jdk21'

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

if (-not (Get-Command wsl.exe -ErrorAction SilentlyContinue)) {
    throw 'WSL is required to run the Docker-backed integration tests.'
}

Invoke-NativeCommand 'wsl.exe' @('-e', 'sh', '-lc', 'test -S /var/run/docker.sock')
if ($script:nativeExitCode -ne 0) {
    throw 'Docker socket /var/run/docker.sock is unavailable in WSL. Start Docker Desktop with WSL integration enabled.'
}

$previousErrorPreference = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
try {
    $wslProjectRoot = (& wsl.exe -e wslpath -a $projectRoot) | Select-Object -First 1
    $wslPathExitCode = $LASTEXITCODE
}
finally {
    $ErrorActionPreference = $previousErrorPreference
}
if ($wslPathExitCode -ne 0 -or [string]::IsNullOrWhiteSpace($wslProjectRoot)) {
    throw 'Failed to resolve the project path in WSL.'
}
$wslProjectRoot = $wslProjectRoot.Trim()

$dockerArguments = @(
    'run', '--rm', '--pull=missing',
    '--cap-add=SYS_PTRACE',
    '-e', 'TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal',
    '-e', 'JAVA_TOOL_OPTIONS=-XX:+StartAttachListener -XX:+EnableDynamicAgentLoading',
    '-v', '/var/run/docker.sock:/var/run/docker.sock',
    '-v', "${wslProjectRoot}:/workspace",
    '-w', '/workspace',
    $gradleImage,
    'gradle', 'test', '--no-daemon',
    '--project-cache-dir', '/tmp/routy-project-cache'
)

Write-Host 'Running the complete Gradle test suite with isolated Testcontainers MySQL.'
Invoke-NativeCommand 'wsl.exe' (@('-e', 'docker') + $dockerArguments)
if ($script:nativeExitCode -ne 0) {
    throw "Gradle tests failed with exit code $($script:nativeExitCode)."
}
