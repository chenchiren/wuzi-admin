@echo off
REM ============================================================
REM  wuzi backend launcher (run from your own terminal)
REM  Secrets are read from start-backend.local.cmd (gitignored).
REM ============================================================
setlocal
cd /d "%~dp0"

REM Secrets live OUTSIDE this repo: %USERPROFILE%\.wuzi\env  (KEY=VALUE, one per line)
if exist "%USERPROFILE%\.wuzi\env" for /f "usebackq eol=# tokens=1,* delims==" %%a in ("%USERPROFILE%\.wuzi\env") do set "%%a=%%b"
REM Optional per-project override (gitignored).
if exist "%~dp0start-backend.local.cmd" call "%~dp0start-backend.local.cmd"
if not defined WUZI_JWT_SECRET goto :noenv
if not defined WUZI_DB_PASSWORD goto :noenv

cd /d "%~dp0backend"
if not exist "target\wuzi-admin-1.0.0-SNAPSHOT.jar" (
  echo [ERROR] jar not found. Run: mvn package -DskipTests
  pause
  exit /b 1
)

REM --- kill previous instance on 8081 ---
for /f "tokens=5" %%p in ('netstat -ano ^| findstr ":8081 .*LISTENING"') do taskkill /F /PID %%p >nul 2>&1

echo Starting wuzi-admin on http://localhost:8081 ...
"C:\Program Files\Java\jdk-17\bin\java.exe" -jar target\wuzi-admin-1.0.0-SNAPSHOT.jar --server.port=8081 --spring.main.banner-mode=off
pause
exit /b 0

:noenv
echo [ERROR] WUZI_JWT_SECRET / WUZI_DB_PASSWORD not set.
echo         Copy start-backend.local.cmd.example to start-backend.local.cmd and fill it in.
pause
exit /b 1
