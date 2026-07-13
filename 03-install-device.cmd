@echo off
echo === Kazi Sasa - installing debug APK ===
echo.

cd /d "%~dp0"

if not exist "app\build\outputs\apk\debug\app-debug.apk" (
    echo APK not found. Run 02-build-debug.cmd first.
    pause
    exit /b 1
)

where adb >nul 2>nul
if errorlevel 1 (
    echo adb not found on PATH. It lives under %%ANDROID_HOME%%\platform-tools -
    echo add that folder to PATH, or run 01-check-environment.cmd for details.
    pause
    exit /b 1
)

echo Connected devices:
adb devices
echo.

adb install -r "app\build\outputs\apk\debug\app-debug.apk"

if errorlevel 1 (
    echo.
    echo === INSTALL FAILED - is a device/emulator connected and authorised? ===
    pause
    exit /b 1
)

echo.
echo === INSTALLED ===
pause
