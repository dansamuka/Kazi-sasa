@echo off
setlocal enabledelayedexpansion
echo === Kazi Sasa - environment check ===
echo.

set OK=1

echo [1/5] Java...
where java >nul 2>nul
if errorlevel 1 (
    echo   NOT FOUND. Install a JDK 17+ ^(Android Studio bundles one at
    echo   %%LOCALAPPDATA%%\Android\Sdk or under the Android Studio install dir^).
    set OK=0
) else (
    java -version 2>&1 | findstr /R "version" 
    echo   found.
)
echo.

echo [2/5] ANDROID_HOME / ANDROID_SDK_ROOT...
if defined ANDROID_HOME (
    echo   ANDROID_HOME=%ANDROID_HOME%
) else if defined ANDROID_SDK_ROOT (
    echo   ANDROID_SDK_ROOT=%ANDROID_SDK_ROOT%
) else (
    echo   NEITHER is set. Set one to your Android SDK path - Android Studio
    echo   shows it under Settings ^> Languages ^& Frameworks ^> Android SDK.
    set OK=0
)
echo.

echo [3/5] adb ^(Android SDK platform-tools^)...
where adb >nul 2>nul
if errorlevel 1 (
    echo   NOT on PATH. Not required to build, but 03-install-device.cmd needs it.
    echo   It lives under %%ANDROID_HOME%%\platform-tools.
) else (
    echo   found.
)
echo.

echo [4/5] Gradle wrapper...
if exist "%~dp0gradlew.bat" (
    echo   found - gradlew.bat present.
) else (
    echo   MISSING gradlew.bat. Something is wrong with this checkout/zip.
    set OK=0
)
echo.

echo [5/5] Connected devices/emulators...
where adb >nul 2>nul
if not errorlevel 1 (
    adb devices
) else (
    echo   ^(skipped - adb not on PATH^)
)
echo.

if "%OK%"=="1" (
    echo === Environment looks OK. Run 02-build-debug.cmd next. ===
) else (
    echo === Fix the items marked above before building. ===
)
endlocal
pause
