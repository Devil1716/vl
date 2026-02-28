@echo off
setlocal

:: This script requires the Android SDK to be installed.
:: Ensure %ANDROID_HOME% is set correctly.

if "%ANDROID_HOME%"=="" (
    echo [ERROR] ANDROID_HOME environment variable is not set.
    echo Please point it to your Android SDK folder e.g. C:\Users\User\AppData\Local\Android\Sdk
    exit /b 1
)

set PLATFORM=%ANDROID_HOME%\platforms\android-34\android.jar
if not exist "%PLATFORM%" (
    echo [ERROR] Could not find android-34 platform. Please install it via SDK Manager.
    exit /b 1
)

set BUILD_DIR=build
if not exist "%BUILD_DIR%" mkdir "%BUILD_DIR%"

echo [1/3] Compiling Java sources...
javac -source 1.8 -target 1.8 -cp "%PLATFORM%" -d "%BUILD_DIR%" src\com\projector\*.java
if %errorlevel% neq 0 (
    echo [ERROR] Compilation failed.
    exit /b 1
)

echo [2/3] Converting to DEX (using d8)...
:: Find d8.bat in the build-tools folder
for /d %%I in ("%ANDROID_HOME%\build-tools\*") do set D8_PATH=%%I\d8.bat
if not exist "%D8_PATH%" (
    echo [ERROR] Could not find d8.bat in build-tools. Please install build-tools.
    exit /b 1
)

call "%D8_PATH%" --output . "%BUILD_DIR%\com\projector\*.class"
if %errorlevel% neq 0 (
    echo [ERROR] DEX conversion failed.
    exit /b 1
)

echo [3/3] Packaging to server.jar...
if exist server.jar del server.jar
:: A .dex file just needs to be zipped into a .jar to be run by app_process
jar cvf server.jar classes.dex

echo =========================================
echo BUILD SUCCESS: server.jar
echo =========================================
endlocal
