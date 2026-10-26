@echo off
rem 一键启动：编译 GUI -> 启动 Python 服务层 -> 健康检查 -> 启动 GUI
setlocal
cd /d %~dp0

set "PY=python"
set "PORT=8899"

if not exist ui-java\out (
  call ui-java\build.bat
  if errorlevel 1 exit /b 1
)

echo [1/2] starting server on port %PORT% ...
start "ZhiSuan Server" cmd /c "%PY% server\agent_server.py %PORT%"

echo [2/2] waiting for health check ...
powershell -NoProfile -Command "for($i=0;$i -lt 40;$i++){ try { Invoke-WebRequest -UseBasicParsing 'http://127.0.0.1:%PORT%/api/health' -TimeoutSec 1 | Out-Null; exit 0 } catch { Start-Sleep -Milliseconds 500 } }; exit 1"
if errorlevel 1 (
  echo [!] server did not become healthy; GUI will start in offline mode.
)

call ui-java\run.bat %*
endlocal
