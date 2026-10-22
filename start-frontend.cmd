@echo off
REM ============================================================
REM  wuzi frontend launcher (PC 5173 + mobile 5174)
REM ============================================================
setlocal
set "NODE=C:\Users\user\.workbuddy\binaries\node\versions\22.22.2-6\node.exe"

start "wuzi-pc-5173" cmd /k "cd /d E:\aicode\relex\物资管理系统\frontend && "%NODE%" node_modules\vite\bin\vite.js --port 5173 --strictPort"
start "wuzi-mobile-5174" cmd /k "cd /d E:\aicode\relex\物资管理系统\frontend\mobile && "%NODE%" node_modules\vite\bin\vite.js --port 5174 --strictPort"

echo Started: PC http://localhost:5173  |  Mobile http://localhost:5174
echo (close the two windows to stop)
pause
