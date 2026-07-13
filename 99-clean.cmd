@echo off
echo === Kazi Sasa - clean build outputs ===
echo This removes build/ and .gradle/ caches. Your source code is untouched.
echo.

cd /d "%~dp0"
call gradlew.bat clean

if exist ".gradle" (
    echo Removing .gradle cache...
    rmdir /s /q ".gradle"
)

echo.
echo === CLEAN DONE ===
pause
