@echo off
REM One-click deploy script for the Kazi Sasa app repo (dansamuka/Kazi-sasa).
REM
REM HOW TO USE:
REM   1. Extract the zip Claude gives you.
REM   2. Make sure this file (deploy.bat) sits inside the extracted
REM      "kazi-sasa" folder, next to README.md and build.gradle.kts.
REM   3. Double-click this file.
REM   4. If a browser window pops up asking you to confirm your GitHub
REM      login, click Authorize - that's normal, not a token prompt.
REM
REM This does NOT need a token or any typing - it uses the same
REM browser-based GitHub login (Git Credential Manager) that has worked
REM for every previous push in this project.

echo ============================================
echo  Kazi Sasa App - Deploy Script
echo ============================================
echo.
echo This will push everything in this folder to:
echo   https://github.com/dansamuka/Kazi-sasa
echo.
echo Press any key to continue, or close this window to cancel.
pause

cd /d "%~dp0"

echo.
echo [1/6] Initializing git repository...
git init
if errorlevel 1 goto :error

echo.
echo [2/6] Setting branch to main...
git branch -M main
if errorlevel 1 goto :error

echo.
echo [3/6] Connecting to GitHub repo...
git remote add origin https://github.com/dansamuka/Kazi-sasa.git 2>nul
git remote set-url origin https://github.com/dansamuka/Kazi-sasa.git
if errorlevel 1 goto :error

echo.
echo [4/6] Staging all files...
git add -A
if errorlevel 1 goto :error

echo.
echo [5/6] Committing...
set /p COMMITMSG="Describe this change (or press Enter for a default message): "
if "%COMMITMSG%"=="" set COMMITMSG=Update Kazi Sasa app
git commit -m "%COMMITMSG%"
if errorlevel 1 (
    echo.
    echo Nothing new to commit, or commit failed - continuing to push anyway
    echo in case a previous attempt already committed.
)

echo.
echo [6/6] Pushing to GitHub...
echo A browser window may open asking you to confirm your GitHub login -
echo that's normal, just click Authorize.
git push origin main --force
if errorlevel 1 goto :error

echo.
echo ============================================
echo  SUCCESS - pushed to GitHub.
echo ============================================
echo.
echo Next: go to the Actions tab on the repo and watch the
echo "Build debug APK" workflow run against this change.
echo.
pause
exit /b 0

:error
echo.
echo ============================================
echo  Something went wrong - see the error above.
echo ============================================
echo.
echo Copy the full text of this window and share it with Claude so
echo it can help diagnose it.
pause
exit /b 1
