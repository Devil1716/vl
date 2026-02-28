@rem Gradle wrapper script for Windows
@if "%DEBUG%"=="" @echo off
setlocal

set DIRNAME=%~dp0
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%

set DEFAULT_JVM_OPTS=
set JAVA_EXE="C:\Program Files\Android\Android Studio\jbr\bin\java.exe"

set CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar

%JAVA_EXE% %DEFAULT_JVM_OPTS% -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*

:end
endlocal
