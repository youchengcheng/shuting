param(
    [string]$BackendRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..\..\..')).Path,
    [string]$OutputPath = (Join-Path $PSScriptRoot '..\..\target\live-infrastructure-read-only.json'),
    [switch]$CompileOnly,
    [switch]$CreateMissingNotificationTables
)
$ErrorActionPreference = 'Stop'
$javaRoot = 'C:\Program Files\Java\jdk-17'
$mavenRoot = Join-Path $env:USERPROFILE '.m2\repository'
$jarPaths = @(
    'org\yaml\snakeyaml\1.33\snakeyaml-1.33.jar',
    'com\alibaba\druid\1.2.23\druid-1.2.23.jar',
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
$classRoot = Join-Path $PSScriptRoot '..\..\target\live-infra-read-only-classes'
New-Item -ItemType Directory -Path $classRoot -Force | Out-Null
& (Join-Path $javaRoot 'bin\javac.exe') -encoding UTF-8 -cp $classPath -d $classRoot (Join-Path $PSScriptRoot 'LiveInfrastructureReadOnly.java') (Join-Path $PSScriptRoot 'LiveRuntimeCredentials.java')
if ($LASTEXITCODE -ne 0) { throw 'Read-only probe compilation failed.' }
if (-not $CompileOnly) {
    $absoluteOutput = [IO.Path]::GetFullPath($OutputPath)
    New-Item -ItemType Directory -Path ([IO.Path]::GetDirectoryName($absoluteOutput)) -Force | Out-Null
    $probeArguments = @('LiveInfrastructureReadOnly', $BackendRoot, $absoluteOutput)
    if ($CreateMissingNotificationTables) { $probeArguments += '--create-missing' }
    & (Join-Path $javaRoot 'bin\java.exe') -cp ($classRoot + [IO.Path]::PathSeparator + $classPath) @probeArguments
    if ($LASTEXITCODE -ne 0) { throw 'Read-only probe execution failed.' }
}
