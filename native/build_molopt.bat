@echo off
rem 编译 molopt.cpp -> molopt.exe（需在 Visual Studio 开发者命令行环境，或先执行 vcvars64.bat）
cd /d %~dp0
where cl >nul 2>&1
if errorlevel 1 (
  echo [!] cl.exe not found. Open a "x64 Native Tools Command Prompt" and run this script again.
  exit /b 1
)
cl /nologo /O2 /EHsc /utf-8 /DUNICODE /D_UNICODE /Fo"molopt.obj" /Fe"molopt.exe" molopt.cpp
exit /b %errorlevel%
