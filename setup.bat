@echo off
setlocal EnableExtensions
cd /d "%~dp0"

rem ============================================================
rem  Serein Stats - build and install over Wi-Fi (Windows)
rem    setup.bat        build + install + launch
rem    setup.bat pair   force the one-time wireless pairing flow
rem ============================================================

set "APP_ID=com.serein.stats"
set "APP_ACTIVITY=com.serein.stats/.ui.MainActivity"
set "ADDR_FILE=.wireless-device"
set "FORCE_PAIR="
if /i "%~1"=="pair" set "FORCE_PAIR=1"

echo.
echo === Serein Stats : wireless install ===
echo.

if not exist gradlew.bat goto no_project

rem ---------- 1. Java ----------
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "PATH=%JAVA_HOME%\bin;%PATH%"
where java >nul 2>&1
if errorlevel 1 goto no_java
echo [1/5] Java found.

rem ---------- 2. Android SDK ----------
set "SDK="
if defined ANDROID_HOME if exist "%ANDROID_HOME%\platform-tools" set "SDK=%ANDROID_HOME%"
if not defined SDK if defined ANDROID_SDK_ROOT if exist "%ANDROID_SDK_ROOT%\platform-tools" set "SDK=%ANDROID_SDK_ROOT%"
if not defined SDK if exist "%LOCALAPPDATA%\Android\Sdk\platform-tools" set "SDK=%LOCALAPPDATA%\Android\Sdk"
if not defined SDK if exist "%USERPROFILE%\android-sdk\platform-tools" set "SDK=%USERPROFILE%\android-sdk"
if not defined SDK if exist "%USERPROFILE%\AppData\Local\Android\Sdk\platform-tools" set "SDK=%USERPROFILE%\AppData\Local\Android\Sdk"
if not defined SDK goto ask_sdk
goto sdk_ok

:ask_sdk
echo Android SDK not found automatically.
set "SDK="
set /p "SDK=Paste the full path to your Android SDK folder: "
if not defined SDK goto no_sdk
set "SDK=%SDK:"=%"
if not exist "%SDK%\platform-tools" goto no_sdk
goto sdk_ok

:sdk_ok
set "ANDROID_HOME=%SDK%"
set "ANDROID_SDK_ROOT=%SDK%"
set "PATH=%SDK%\platform-tools;%PATH%"
set "SDK_FWD=%SDK:\=/%"
>local.properties echo sdk.dir=%SDK_FWD%
echo [2/5] Android SDK: %SDK%

if exist "%SDK%\platforms\android-34" if exist "%SDK%\build-tools\34.0.0" goto wrapper
set "SDKM=%SDK%\cmdline-tools\latest\bin\sdkmanager.bat"
if not exist "%SDKM%" goto sdk_no_manager
echo Installing Android platform 34 and build-tools 34.0.0 ...
set "YESFILE=%TEMP%\serein-yes.txt"
if exist "%YESFILE%" del "%YESFILE%"
for /l %%i in (1,1,30) do >>"%YESFILE%" echo y
call "%SDKM%" --sdk_root="%SDK%" "platforms;android-34" "build-tools;34.0.0" < "%YESFILE%"
goto wrapper

:sdk_no_manager
echo Note: sdkmanager not found. Gradle will try to fetch missing SDK parts itself.
goto wrapper

rem ---------- 3. Gradle wrapper ----------
:wrapper
if not exist gradle\wrapper mkdir gradle\wrapper
if exist gradle\wrapper\gradle-wrapper.properties goto wrapper_jar
>gradle\wrapper\gradle-wrapper.properties echo distributionBase=GRADLE_USER_HOME
>>gradle\wrapper\gradle-wrapper.properties echo distributionPath=wrapper/dists
>>gradle\wrapper\gradle-wrapper.properties echo distributionUrl=https\://services.gradle.org/distributions/gradle-8.4-bin.zip
>>gradle\wrapper\gradle-wrapper.properties echo zipStoreBase=GRADLE_USER_HOME
>>gradle\wrapper\gradle-wrapper.properties echo zipStorePath=wrapper/dists

:wrapper_jar
if exist gradle\wrapper\gradle-wrapper.jar goto wrapper_ok
echo Downloading gradle-wrapper.jar ...
curl.exe -L -s -o gradle\wrapper\gradle-wrapper.jar https://raw.githubusercontent.com/gradle/gradle/v8.4.0/gradle/wrapper/gradle-wrapper.jar
if not exist gradle\wrapper\gradle-wrapper.jar goto no_wrapper

:wrapper_ok
echo [3/5] Gradle wrapper ready.

rem ---------- 4. Phone connection ----------
adb start-server >nul 2>&1
if errorlevel 1 goto no_adb
if defined FORCE_PAIR goto pair_flow

call :detect
if defined DEV goto have_device

set "SAVED="
if exist "%ADDR_FILE%" set /p SAVED=<"%ADDR_FILE%"
if not defined SAVED goto ask_paired
echo Trying saved phone address %SAVED% ...
adb connect %SAVED% >nul 2>&1
call :detect
if defined DEV goto have_device

:ask_paired
echo.
echo No phone connected yet.
echo On the phone: Settings ^> Developer options ^> Wireless debugging = ON
echo (phone and PC must be on the same Wi-Fi)
echo.
choice /c YN /m "Have you already paired this PC with the phone before"
if errorlevel 2 goto pair_flow
goto connect_flow

:pair_flow
echo.
echo --- One-time pairing ---
echo On the phone: Wireless debugging ^> "Pair device with pairing code"
echo It shows an IP:port and a 6-digit code.
echo.
set "PAIR_ADDR="
set "PAIR_CODE="
set /p "PAIR_ADDR=Pairing IP:port : "
set /p "PAIR_CODE=Pairing code    : "
adb pair %PAIR_ADDR% %PAIR_CODE%
if not errorlevel 1 goto connect_flow
echo.
echo Pairing failed.
choice /c YN /m "Try again"
if errorlevel 2 goto fail
goto pair_flow

:connect_flow
echo.
echo --- Connect ---
echo Use the IP:port shown on the MAIN Wireless debugging screen
echo (not the pairing one - the port is different).
echo.
set "CONN_ADDR="
set /p "CONN_ADDR=Connect IP:port : "
adb connect %CONN_ADDR%
call :detect
if defined DEV goto save_addr
echo.
echo Could not connect.
choice /c YN /m "Try again"
if errorlevel 2 goto fail
goto connect_flow

:save_addr
>"%ADDR_FILE%" echo %CONN_ADDR%

:have_device
echo [4/5] Phone connected: %DEV%
set "LAST_ADDR=%DEV%"

rem ---------- 5. Build, then (re)connect, then install ----------
echo [5/5] Building (the phone may sleep meanwhile - that's fine) ...
call gradlew.bat assembleDebug
if errorlevel 1 goto build_error

:reconnect
call :detect
if defined DEV goto do_install
echo Phone dropped the connection during the build. Reconnecting ...
adb connect %LAST_ADDR% >nul 2>&1
call :detect
if defined DEV goto do_install
echo.
echo Wake the phone and make sure Wireless debugging is ON.
echo The port changes whenever it is toggled - enter the current IP:port.
set "CONN_ADDR="
set /p "CONN_ADDR=Connect IP:port : "
if not defined CONN_ADDR goto fail
adb connect %CONN_ADDR%
set "LAST_ADDR=%CONN_ADDR%"
>"%ADDR_FILE%" echo %CONN_ADDR%
goto reconnect

:do_install
set "ANDROID_SERIAL=%DEV%"
echo Installing on %DEV% ...
call gradlew.bat installDebug
if errorlevel 1 goto install_failed
goto launch

:build_error
echo.
echo Build failed - see the errors above.
goto fail

:install_failed
echo.
echo Install failed. If the error mentioned INSTALL_FAILED_UPDATE_INCOMPATIBLE
echo the installed copy was signed with a different key (e.g. a CI-built APK).
echo.
choice /c YN /m "Uninstall the app from the phone and retry (app data is lost)"
if errorlevel 2 goto fail
adb -s %DEV% uninstall %APP_ID%
call gradlew.bat installDebug
if errorlevel 1 goto fail

:launch
adb -s %DEV% shell am start -n %APP_ACTIVITY% >nul
echo.
echo Done. Serein Stats installed and launched on your phone.
echo Next time just run:  setup.bat
exit /b 0

rem ---------- errors ----------
:no_project
echo ERROR: gradlew.bat not found. Put setup.bat in the serein-stats project root.
exit /b 1

:no_java
echo ERROR: Java not found. Install JDK 17, reopen the terminal, and rerun:
echo   winget install EclipseAdoptium.Temurin.17.JDK
exit /b 1

:no_sdk
echo ERROR: Android SDK not found. Install Android Studio or the command-line tools,
echo or set ANDROID_HOME to the SDK folder, then rerun.
exit /b 1

:no_wrapper
echo ERROR: could not download gradle-wrapper.jar. Check your internet connection.
exit /b 1

:no_adb
echo ERROR: adb could not start. Check "%SDK%\platform-tools" exists.
exit /b 1

:fail
echo Stopped.
exit /b 1

rem ---------- helper: set DEV to first ready device ----------
:detect
set "DEV="
for /f "skip=1 tokens=1,2" %%a in ('adb devices') do if "%%b"=="device" set "DEV=%%a"
exit /b 0