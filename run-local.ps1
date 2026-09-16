[CmdletBinding()]
param(
    [switch]$InitializeOnly
)

$ErrorActionPreference = 'Stop'

$projectRoot = $PSScriptRoot
$environmentFile = Join-Path $projectRoot '.env.local'
$allowedKeys = @(
    'DB_PASSWORD',
    'DB_ROOT_PASSWORD',
    'JWT_ACTIVE_KEY_ID',
    'ROUTY_JWT_KEYS_0_ID',
    'ROUTY_JWT_KEYS_0_SECRET'
)

function New-RandomBytes([int]$byteLength) {
    $bytes = [byte[]]::new($byteLength)
    $generator = [Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $generator.GetBytes($bytes)
    }
    finally {
        $generator.Dispose()
    }
    return $bytes
}

function New-HexSecret([int]$byteLength) {
    $bytes = New-RandomBytes $byteLength
    return ([BitConverter]::ToString($bytes) -replace '-', '').ToLowerInvariant()
}

function Get-ExistingMysqlPasswords {
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
        return $null
    }

    $previousErrorPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $containerEnvironment = & docker inspect --format '{{range .Config.Env}}{{println .}}{{end}}' travel-mysql 2>$null
        $inspectExitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previousErrorPreference
    }
    if ($inspectExitCode -ne 0) {
        return $null
    }

    $values = @{}
    foreach ($entry in $containerEnvironment) {
        if ($entry -match '^MYSQL_PASSWORD=(.*)$') {
            $values.DB_PASSWORD = $Matches[1]
        }
        elseif ($entry -match '^MYSQL_ROOT_PASSWORD=(.*)$') {
            $values.DB_ROOT_PASSWORD = $Matches[1]
        }
    }

    if ($values.DB_PASSWORD -and $values.DB_ROOT_PASSWORD) {
        return $values
    }
    return $null
}

function New-LocalEnvironmentFile {
    $existingPasswords = Get-ExistingMysqlPasswords
    $databasePassword = if ($existingPasswords) { $existingPasswords.DB_PASSWORD } else { New-HexSecret 24 }
    $rootPassword = if ($existingPasswords) { $existingPasswords.DB_ROOT_PASSWORD } else { New-HexSecret 24 }
    $jwtSecret = [Convert]::ToBase64String((New-RandomBytes 32))

    $lines = @(
        "DB_PASSWORD=$databasePassword",
        "DB_ROOT_PASSWORD=$rootPassword",
        'JWT_ACTIVE_KEY_ID=local-key',
        'ROUTY_JWT_KEYS_0_ID=local-key',
        "ROUTY_JWT_KEYS_0_SECRET=$jwtSecret"
    )
    [IO.File]::WriteAllLines($environmentFile, $lines, [Text.UTF8Encoding]::new($false))
    Write-Host 'Created local secrets in .env.local (ignored by Git).'
}

function Sync-ExistingMysqlPasswords {
    $existingPasswords = Get-ExistingMysqlPasswords
    if (-not $existingPasswords) {
        return
    }

    $updatedLines = foreach ($line in [IO.File]::ReadAllLines($environmentFile)) {
        if ($line.StartsWith('DB_PASSWORD=')) {
            "DB_PASSWORD=$($existingPasswords.DB_PASSWORD)"
        }
        elseif ($line.StartsWith('DB_ROOT_PASSWORD=')) {
            "DB_ROOT_PASSWORD=$($existingPasswords.DB_ROOT_PASSWORD)"
        }
        else {
            $line
        }
    }
    [IO.File]::WriteAllLines($environmentFile, $updatedLines, [Text.UTF8Encoding]::new($false))
}

function Import-LocalEnvironmentFile {
    foreach ($line in [IO.File]::ReadAllLines($environmentFile)) {
        $trimmed = $line.Trim()
        if (-not $trimmed -or $trimmed.StartsWith('#')) {
            continue
        }

        $separator = $trimmed.IndexOf('=')
        if ($separator -le 0) {
            throw "Invalid .env.local entry. Expected KEY=VALUE."
        }

        $key = $trimmed.Substring(0, $separator).Trim()
        $value = $trimmed.Substring($separator + 1)
        if ($key -notin $allowedKeys) {
            throw "Unsupported .env.local key: $key"
        }
        if ([string]::IsNullOrWhiteSpace($value)) {
            throw "Missing value for .env.local key: $key"
        }
        Set-Item -LiteralPath "Env:$key" -Value $value
    }

    foreach ($key in $allowedKeys) {
        if (-not (Test-Path -LiteralPath "Env:$key")) {
            throw "Missing required .env.local key: $key"
        }
    }
    $env:SPRING_PROFILES_ACTIVE = 'local'
}

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

function Start-LocalMysql {
    $previousErrorPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $containerStatus = & docker inspect --format '{{.State.Status}}' travel-mysql 2>$null
        $inspectExitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previousErrorPreference
    }

    if ($inspectExitCode -eq 0) {
        if ($containerStatus -ne 'running') {
            Invoke-NativeCommand 'docker' @('start', 'travel-mysql')
            if ($script:nativeExitCode -ne 0) {
                throw 'Failed to start the existing local MySQL container.'
            }
        }
        else {
            Write-Host 'Using the existing travel-mysql container.'
        }
        return
    }

    Invoke-NativeCommand 'docker' @('compose', 'up', '-d', 'mysql')
    if ($script:nativeExitCode -ne 0) {
        throw 'Failed to start the local MySQL container.'
    }
}

if (-not (Test-Path -LiteralPath $environmentFile)) {
    New-LocalEnvironmentFile
}
Sync-ExistingMysqlPasswords
Import-LocalEnvironmentFile

if ($InitializeOnly) {
    Write-Host 'Local environment is ready.'
    exit 0
}

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw 'Docker command was not found. Install or start Docker Desktop first.'
}

Push-Location $projectRoot
try {
    Start-LocalMysql

    Write-Host 'Starting Routy at http://localhost:8080/Routy/index.html#/'
    Invoke-NativeCommand '.\gradlew.bat' @('bootRun')
    if ($script:nativeExitCode -ne 0) {
        throw 'Spring Boot exited with an error.'
    }
}
finally {
    Pop-Location
}
