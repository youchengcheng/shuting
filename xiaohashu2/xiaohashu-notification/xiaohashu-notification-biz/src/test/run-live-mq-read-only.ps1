param(
    [string]$OutputPath = (Join-Path $PSScriptRoot '..\..\target\live-rocketmq-read-only.json'),
    [switch]$CompileOnly
)
$ErrorActionPreference = 'Stop'
$javaRoot = 'C:\Program Files\Java\jdk-17'
$mavenRoot = Join-Path $env:USERPROFILE '.m2\repository'
$jarPaths = @(
    'org\apache\rocketmq\rocketmq-common\4.9.4\rocketmq-common-4.9.4.jar',
    'org\apache\rocketmq\rocketmq-remoting\4.9.4\rocketmq-remoting-4.9.4.jar',
    'com\alibaba\fastjson\1.2.69_noneautotype\fastjson-1.2.69_noneautotype.jar',
    'com\fasterxml\jackson\core\jackson-annotations\2.18.1\jackson-annotations-2.18.1.jar',
    'com\fasterxml\jackson\core\jackson-core\2.18.1\jackson-core-2.18.1.jar',
    'com\fasterxml\jackson\core\jackson-databind\2.18.1\jackson-databind-2.18.1.jar'
)
$classPath = (($jarPaths | ForEach-Object {
    $path = Join-Path $mavenRoot $_
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Required cached library missing: $_" }
    $path
}) -join [IO.Path]::PathSeparator)
$classRoot = Join-Path $PSScriptRoot '..\..\target\live-mq-read-only-classes'
New-Item -ItemType Directory -Path $classRoot -Force | Out-Null
& (Join-Path $javaRoot 'bin\javac.exe') -encoding UTF-8 -cp $classPath -d $classRoot (Join-Path $PSScriptRoot 'LiveRocketMqReadOnly.java')
if ($LASTEXITCODE -ne 0) { throw 'Read-only MQ probe compilation failed.' }
if (-not $CompileOnly) {
    $absoluteOutput = [IO.Path]::GetFullPath($OutputPath)
    New-Item -ItemType Directory -Path ([IO.Path]::GetDirectoryName($absoluteOutput)) -Force | Out-Null
    & (Join-Path $javaRoot 'bin\java.exe') -cp ($classRoot + [IO.Path]::PathSeparator + $classPath) LiveRocketMqReadOnly $absoluteOutput
    if ($LASTEXITCODE -ne 0) { throw 'Read-only MQ probe execution failed.' }
}
