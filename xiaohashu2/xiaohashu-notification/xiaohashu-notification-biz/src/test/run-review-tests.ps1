param([switch]$WithMySql, [string]$MySqlMetadataPath)
$ErrorActionPreference = 'Stop'
$taskModuleRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$taskBackendRoot = [IO.Path]::GetFullPath((Join-Path $taskModuleRoot '../..'))
$taskMavenCache = Join-Path $env:USERPROFILE '.m2/repository'
# Explicit artifact coordinates, matching this project's Boot 3.0.2 / Java 17 baseline.
$taskSpecs = @(
    'org/springframework/boot|spring-boot|3.0.2', 'org/springframework/boot|spring-boot-autoconfigure|3.0.2',
    'org/springframework/boot|spring-boot-test|3.0.2',
    'org/junit/jupiter|junit-jupiter-api|5.9.2', 'org/junit/jupiter|junit-jupiter-engine|5.9.2',
    'org/junit/platform|junit-platform-commons|1.9.2', 'org/junit/platform|junit-platform-engine|1.9.2',
    'org/junit/platform|junit-platform-launcher|1.9.2', 'org/apiguardian|apiguardian-api|1.1.2', 'org/opentest4j|opentest4j|1.2.0',
    'org/assertj|assertj-core|3.23.1', 'org/mockito|mockito-core|4.8.1', 'net/bytebuddy|byte-buddy|1.12.22',
    'net/bytebuddy|byte-buddy-agent|1.12.22', 'org/objenesis|objenesis|3.2',
    'com/fasterxml/jackson/core|jackson-databind|2.16.1', 'com/fasterxml/jackson/core|jackson-core|2.16.1',
    'com/fasterxml/jackson/core|jackson-annotations|2.14.1', 'com/fasterxml/jackson/datatype|jackson-datatype-jsr310|2.14.1',
    'org/slf4j|slf4j-api|2.0.6', 'ch/qos/logback|logback-core|1.4.5', 'ch/qos/logback|logback-classic|1.4.5',
    'jakarta/servlet|jakarta.servlet-api|6.0.0', 'jakarta/annotation|jakarta.annotation-api|2.1.1',
    'com/alibaba|transmittable-thread-local|2.14.2', 'org/apache/commons|commons-lang3|3.12.0',
    'io/projectreactor|reactor-core|3.5.2', 'org/reactivestreams|reactive-streams|1.0.4',
    'io/micrometer|micrometer-observation|1.10.3', 'io/micrometer|micrometer-commons|1.10.3',
    'org/springframework/data|spring-data-redis|3.0.1', 'org/springframework/data|spring-data-keyvalue|3.0.1',
    'org/springframework/data|spring-data-commons|3.0.1', 'org/apache/rocketmq|rocketmq-common|4.9.4',
    'io/github/openfeign|feign-core|12.1', 'org/mybatis|mybatis|3.5.13', 'org/mybatis|mybatis-spring|3.0.2',
    'mysql|mysql-connector-java|8.0.29', 'org/apache/tomcat/embed|tomcat-embed-core|10.1.5',
    'org/hamcrest|hamcrest|2.2',
    'com/jayway/jsonpath|json-path|2.7.0', 'net/minidev|json-smart|2.4.8', 'net/minidev|accessors-smart|2.4.8', 'org/ow2/asm|asm|9.1'
)
foreach ($taskSpringArtifact in @('spring-core','spring-jcl','spring-beans','spring-context','spring-expression','spring-aop','spring-web','spring-webmvc','spring-test','spring-tx','spring-jdbc')) {
    $taskSpecs += "org/springframework|$taskSpringArtifact|6.0.4"
}
$taskClasspath = @(
    (Join-Path $taskModuleRoot 'target/classes'), (Join-Path $taskModuleRoot 'target/test-classes'),
    (Join-Path $taskBackendRoot 'xiaohashu-notification/xiaohashu-notification-api/target/classes'),
    (Join-Path $taskBackendRoot 'xiaoha-framework/xiaoha-spring-boot-starter-biz-context/target/classes'),
    (Join-Path $taskBackendRoot 'xiaoha-framework/xiaoha-common/target/classes'),
    (Join-Path $taskBackendRoot 'xiaohashu-user/xiaohashu-user-api/target/classes'),
    (Join-Path $taskBackendRoot 'xiaohashu-note/xiaohashu-note-api/target/classes'),
    (Join-Path $taskBackendRoot 'xiaohashu-comment/xiaohashu-comment-api/target/classes')
)
foreach ($taskSpec in $taskSpecs) {
    $taskParts = $taskSpec.Split('|')
    $taskJar = Join-Path $taskMavenCache "$($taskParts[0])/$($taskParts[1])/$($taskParts[2])/$($taskParts[1])-$($taskParts[2]).jar"
    if (-not (Test-Path -LiteralPath $taskJar -PathType Leaf)) { throw "Missing cached test artifact: $taskSpec" }
    $taskClasspath += $taskJar
}
$taskJavaArgs = @('--class-path')
$taskJavaArgs += '"' + (($taskClasspath -join ';') -replace '\\','/') + '"'
if ($WithMySql) {
    if (-not $MySqlMetadataPath) { throw 'Provide metadata for an independently initialized temporary MySQL instance.' }
    $taskMySql = Get-Content -LiteralPath $MySqlMetadataPath -Raw | ConvertFrom-Json
    $taskJavaArgs += '-Dnotification.mysql.test.enabled=true'
    $taskJavaArgs += '"-Dnotification.mysql.test.url=' + $taskMySql.testUrl + '"'
    $taskJavaArgs += '"-Dnotification.mysql.test.datadir=' + ($taskMySql.dataDir -replace '\\','/') + '"'
}
$taskArgsPath = Join-Path $taskModuleRoot 'target/review-tests.args'
$taskCountsPath = Join-Path $taskModuleRoot 'target/review-test-counts.json'
Set-Content -LiteralPath $taskArgsPath -Value $taskJavaArgs -Encoding utf8
$taskJavaCommand = 'java'
if ($env:JAVA_HOME) {
    $taskConfiguredJava = Join-Path $env:JAVA_HOME 'bin/java.exe'
    if (Test-Path -LiteralPath $taskConfiguredJava -PathType Leaf) { $taskJavaCommand = $taskConfiguredJava }
}
& $taskJavaCommand "@$taskArgsPath" (Join-Path $PSScriptRoot 'ReviewTestLauncher.java') $taskCountsPath
if ($LASTEXITCODE -ne 0) { throw "Review tests failed (exit $LASTEXITCODE)." }
Get-Content -LiteralPath $taskCountsPath -Raw
