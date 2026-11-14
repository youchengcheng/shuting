param(
    [string]$BackendRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..\..\..')).Path,
    [string]$FixturePath = (Join-Path $PSScriptRoot '..\..\target\notification-live-fixture\fixture.json'),
    [string]$OutputPath = (Join-Path $PSScriptRoot '..\..\target\notification-live-fixture\schema-probe.json'),
    [switch]$CompileOnly
)
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
& (Join-Path $javaRoot 'bin\javac.exe') -encoding UTF-8 -cp $classPath -d $classRoot (Join-Path $PSScriptRoot 'LiveNotificationFixtureSchema.java')
if ($LASTEXITCODE -ne 0) { throw 'Fixture schema probe compilation failed.' }
if ($CompileOnly) { return }

$absoluteFixture = [IO.Path]::GetFullPath($FixturePath)
$absoluteOutput = [IO.Path]::GetFullPath($OutputPath)
New-Item -ItemType Directory -Path ([IO.Path]::GetDirectoryName($absoluteOutput)) -Force | Out-Null
$credentialEnv = & (Join-Path $PSScriptRoot 'Get-LiveRuntimeEnv.ps1') -BackendRoot $BackendRoot
$previousEnv = @{}
try {
    foreach ($key in $credentialEnv.Keys) {
        $previousEnv[$key] = [Environment]::GetEnvironmentVariable($key, 'Process')
        [Environment]::SetEnvironmentVariable($key, $credentialEnv[$key], 'Process')
    }
    & (Join-Path $javaRoot 'bin\java.exe') -cp ($classRoot + [IO.Path]::PathSeparator + $classPath) LiveNotificationFixtureSchema $absoluteFixture $absoluteOutput
    if ($LASTEXITCODE -ne 0) { throw 'Fixture schema probe failed; see sanitized report.' }
} finally {
    foreach ($key in $previousEnv.Keys) { [Environment]::SetEnvironmentVariable($key, $previousEnv[$key], 'Process') }
    $credentialEnv.Clear()
    $previousEnv.Clear()
}
