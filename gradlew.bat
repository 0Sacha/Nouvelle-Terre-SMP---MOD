@echo off
setlocal

set APP_HOME=%~dp0
set JAVA_OPTS=%JAVA_OPTS%

java %JAVA_OPTS% -classpath "%APP_HOME%gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
