@echo off
setlocal
chcp 65001 >nul
cd /d "%~dp0"

REM Secrets live OUTSIDE this repo: %USERPROFILE%\.wuzi\env  (KEY=VALUE, one per line)
if exist "%USERPROFILE%\.wuzi\env" for /f "usebackq eol=# tokens=1,* delims==" %%a in ("%USERPROFILE%\.wuzi\env") do set "%%a=%%b"
REM Optional per-project override (gitignored).
if exist "%~dp0start-backend.local.cmd" call "%~dp0start-backend.local.cmd"
if not defined WUZI_JWT_SECRET (
  echo [ERROR] WUZI_JWT_SECRET is not set.
  echo         Copy start-backend.local.cmd.example to start-backend.local.cmd and fill it in.
  exit /b 1
)
if not defined WUZI_DB_PASSWORD (
  echo [ERROR] WUZI_DB_PASSWORD is not set. See above.
  exit /b 1
)

cd /d "%~dp0backend"
echo [dev] starting wuzi-admin with AI assistant on :8081 ...
java -Dfile.encoding=UTF-8 -jar target/wuzi-admin-1.0.0-SNAPSHOT.jar --server.port=8081 --spring.main.banner-mode=off
