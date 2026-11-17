@echo off
@if not "%ECHO%" == ""  echo %ECHO%
@if "%OS%" == "Windows_NT"  setlocal

set ENV_PATH=.\
if "%OS%" == "Windows_NT" set ENV_PATH=%~dp0%

set conf_dir=%ENV_PATH%\..\conf
set canal_conf=%conf_dir%\canal.properties
@rem set canal_conf=%conf_dir%\canal_local.properties
if "%1" == "local" set canal_conf=%conf_dir%\canal_local.properties
set logback_configurationFile=%conf_dir%\logback.xml

set CLASSPATH=%conf_dir%
set CLASSPATH=%conf_dir%\..\lib\*;%CLASSPATH%

set JAVA_MEM_OPTS= -Xms128m -Xmx512m
set JAVA_OPTS_EXT= -Djava.awt.headless=true -Djava.net.preferIPv4Stack=true -Dapplication.codeset=UTF-8 -Dfile.encoding=UTF-8
set JAVA_DEBUG_OPT= -server -Xdebug -Xnoagent -Djava.compiler=NONE -Xrunjdwp:transport=dt_socket,address=9099,server=y,suspend=n
set CANAL_OPTS= -DappName=otter-canal -Dlogback.configurationFile="%logback_configurationFile%" -Dcanal.conf="%canal_conf%"

set JAVA_OPTS= %JAVA_MEM_OPTS% %JAVA_OPTS_EXT% %JAVA_DEBUG_OPT% %CANAL_OPTS%

set CMD_STR= java %JAVA_OPTS% -classpath "%CLASSPATH%" java %JAVA_OPTS% -classpath "%CLASSPATH%" com.alibaba.otter.canal.deployer.CanalLauncher
echo start cmd : %CMD_STR%

@rem Before startup: verify the saved binlog position is still available, otherwise rewind to the oldest retained binlog.
powershell -NoProfile -ExecutionPolicy Bypass -File "%ENV_PATH%canal-position-guard.ps1"

java %JAVA_OPTS% -classpath "%CLASSPATH%" com.alibaba.otter.canal.deployer.CanalLauncher
