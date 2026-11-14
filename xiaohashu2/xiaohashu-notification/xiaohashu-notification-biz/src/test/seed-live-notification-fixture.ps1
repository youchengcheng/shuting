param(
    [string]$BackendRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..\..\..')).Path,
    [string]$FixturePath = (Join-Path $PSScriptRoot '..\..\target\notification-live-fixture\fixture.json'),
    [string]$ReviewedSchemaPath = (Join-Path $PSScriptRoot '..\..\target\notification-live-fixture\schema-probe.json'),
    [string]$OutputPath = (Join-Path $PSScriptRoot '..\..\target\notification-live-fixture\seed-report.json'),
    [switch]$CompileOnly
)
# Invoke in the same PowerShell process as the live test runner. Tokens are never returned or printed.
$ErrorActionPreference = 'Stop'
$javaRoot = 'C:\Program Files\Java\jdk-17'
$mavenRoot = Join-Path $env:USERPROFILE '.m2\repository'
$jarPaths = @(
    'mysql\mysql-connector-java\8.0.29\mysql-connector-java-8.0.29.jar',
    'com\fasterxml\jackson\core\jackson-annotations\2.18.1\jackson-annotations-2.18.1.jar',
    'com\fasterxml\jackson\core\jackson-core\2.18.1\jackson-core-2.18.1.jar',
    'com\fasterxml\jackson\core\jackson-databind\2.18.1\jackson-databind-2.18.1.jar'
)
$classPath = (($jarPaths | ForEach-Object {
    $path = Join-Path $mavenRoot $_
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Required cached library missing: $_" }
    $path
}) -join [IO.Path]::PathSeparator)
$classRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..\target\live-fixture-classes'))
New-Item -ItemType Directory -Path $classRoot -Force | Out-Null
& (Join-Path $javaRoot 'bin\javac.exe') -encoding UTF-8 -cp $classPath -d $classRoot (Join-Path $PSScriptRoot 'LiveNotificationFixtureSchema.java') (Join-Path $PSScriptRoot 'LiveNotificationFixtureSeed.java')
if ($LASTEXITCODE -ne 0) { throw 'Fixture seed compilation failed.' }
if ($CompileOnly) { return }

$absoluteFixture = [IO.Path]::GetFullPath($FixturePath)
$absoluteSchema = [IO.Path]::GetFullPath($ReviewedSchemaPath)
$absoluteOutput = [IO.Path]::GetFullPath($OutputPath)
$fixture = Get-Content -LiteralPath $absoluteFixture -Raw | ConvertFrom-Json
$schema = Get-Content -LiteralPath $absoluteSchema -Raw | ConvertFrom-Json
$fixtureHash = (Get-FileHash -LiteralPath $absoluteFixture -Algorithm SHA256).Hash.ToLowerInvariant()
if (-not $fixture.dedicated -or -not $schema.healthy -or -not $schema.candidateIdsAndNamesFree -or -not $schema.candidateIdsBelowNextAutoIncrement -or $schema.fixtureSha256 -cne $fixtureHash) {
    throw 'Fixture must match the successful reviewed schema probe.'
}
$tokenStore = [IO.Path]::GetFullPath([string]$fixture.tokenDpapiPath)
if (Test-Path -LiteralPath $tokenStore) { throw 'Existing encrypted fixture token store is preserved; do not reseed.' }
foreach ($actor in @('A', 'B', 'C')) {
    $tokenName = [string]$fixture.actors.$actor.tokenEnv
    if ($tokenName -cne "NOTIFICATION_LIVE_TOKEN_$actor" -or [Environment]::GetEnvironmentVariable($tokenName, 'Process')) {
        throw 'Fixture token environment names must be unused dedicated names.'
    }
}
New-Item -ItemType Directory -Path ([IO.Path]::GetDirectoryName($absoluteOutput)) -Force | Out-Null
New-Item -ItemType Directory -Path ([IO.Path]::GetDirectoryName($tokenStore)) -Force | Out-Null
$privateTokens = @{}
$rng = [Security.Cryptography.RandomNumberGenerator]::Create()
$alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_-'
try {
    foreach ($actor in @('A', 'B', 'C')) {
        $randomBytes = New-Object byte[] 128
        $rng.GetBytes($randomBytes)
        $characters = New-Object char[] 128
        for ($index = 0; $index -lt 128; $index++) { $characters[$index] = $alphabet[($randomBytes[$index] -band 63)] }
        $privateTokens[[string]$fixture.actors.$actor.tokenEnv] = -join $characters
        [Array]::Clear($randomBytes, 0, $randomBytes.Length)
        [Array]::Clear($characters, 0, $characters.Length)
    }
} finally { $rng.Dispose() }
Add-Type -AssemblyName System.Security
$privateBytes = [Text.Encoding]::UTF8.GetBytes(($privateTokens | ConvertTo-Json -Compress))
try {
    $encryptedBytes = [Security.Cryptography.ProtectedData]::Protect($privateBytes, $null, [Security.Cryptography.DataProtectionScope]::CurrentUser)
    $storeStream = [IO.File]::Open($tokenStore, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None)
    try { $storeStream.Write($encryptedBytes, 0, $encryptedBytes.Length) } finally { $storeStream.Dispose() }
} finally {
    [Array]::Clear($privateBytes, 0, $privateBytes.Length)
    if ($encryptedBytes) { [Array]::Clear($encryptedBytes, 0, $encryptedBytes.Length) }
}

$credentialEnv = & (Join-Path $PSScriptRoot 'Get-LiveRuntimeEnv.ps1') -BackendRoot $BackendRoot
$previousEnv = @{}
$seedSucceeded = $false
try {
    foreach ($key in $credentialEnv.Keys) {
        $previousEnv[$key] = [Environment]::GetEnvironmentVariable($key, 'Process')
        [Environment]::SetEnvironmentVariable($key, $credentialEnv[$key], 'Process')
    }
    foreach ($key in $privateTokens.Keys) { [Environment]::SetEnvironmentVariable($key, $privateTokens[$key], 'Process') }
    & (Join-Path $javaRoot 'bin\java.exe') -cp ($classRoot + [IO.Path]::PathSeparator + $classPath) LiveNotificationFixtureSeed $absoluteFixture $absoluteSchema $absoluteOutput
    if ($LASTEXITCODE -ne 0) { throw 'Fixture seed failed; sanitized report and encrypted recovery tokens are preserved.' }
    $seedSucceeded = $true
} finally {
    foreach ($key in $previousEnv.Keys) { [Environment]::SetEnvironmentVariable($key, $previousEnv[$key], 'Process') }
    if (-not $seedSucceeded) { foreach ($key in $privateTokens.Keys) { [Environment]::SetEnvironmentVariable($key, $null, 'Process') } }
    $credentialEnv.Clear()
    $previousEnv.Clear()
    $privateTokens.Clear()
}
