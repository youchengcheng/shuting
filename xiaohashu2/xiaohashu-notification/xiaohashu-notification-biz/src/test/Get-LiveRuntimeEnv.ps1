param(
    [string]$BackendRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..\..\..')).Path
)
# Invoke only as: $credentialEnv = & <this-script>. Never print the returned hashtable.
# Java stdout is captured here; no credentials are put on command lines or written to disk.
$ErrorActionPreference = 'Stop'
$null = & (Join-Path $PSScriptRoot 'run-live-infra-read-only.ps1') -CompileOnly 2>$null
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
$classPath = (($jarPaths | ForEach-Object { Join-Path $mavenRoot $_ }) -join [IO.Path]::PathSeparator)
$classRoot = Join-Path $PSScriptRoot '..\..\target\live-infra-read-only-classes'
$credentialJson = & (Join-Path $javaRoot 'bin\java.exe') -cp ($classRoot + [IO.Path]::PathSeparator + $classPath) LiveRuntimeCredentials $BackendRoot
if ($LASTEXITCODE -ne 0) { throw 'Runtime connection credential resolution failed.' }
$resolved = $credentialJson | ConvertFrom-Json
$credentialEnv = @{}
foreach ($property in $resolved.PSObject.Properties) { $credentialEnv[$property.Name] = [string]$property.Value }
$credentialJson = $null
$resolved = $null
return $credentialEnv
