@echo off
echo === Kazi Sasa - building debug APK ===
echo This uses the Gradle wrapper, so it downloads the pinned Gradle version
echo on first run if it's not already cached - that first run will be slower.
echo.

cd /d "%~dp0"
call gradlew.bat clean assembleDebug

if errorlevel 1 (
    echo.
    echo === BUILD FAILED - see the error above ===
    pause
    exit /b 1
)

echo.
echo === BUILD SUCCEEDED ===
echo APK: app\build\outputs\apk\debug\app-debug.apk
echo Run 03-install-device.cmd to install it on a connected device/emulator.
pause
