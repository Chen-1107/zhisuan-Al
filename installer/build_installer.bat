@echo off
rem 生成 ZhiSuan-Al-Setup.exe（内嵌 payload.zip，.NET Framework 4.x 自带编译工具）
setlocal
cd /d "%~dp0"

set CSC=%WINDIR%\Microsoft.NET\Framework64\v4.0.30319\csc.exe
if not exist "%CSC%" set CSC=%WINDIR%\Microsoft.NET\Framework\v4.0.30319\csc.exe
if not exist "%CSC%" (
  echo [FAIL] 未找到 csc.exe，需要 .NET Framework 4.x
  exit /b 1
)

echo [1/2] 打包载荷...
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0make_payload.ps1"
if errorlevel 1 exit /b 1

echo [2/2] 编译安装包...
"%CSC%" /nologo /target:winexe /codepage:65001 /optimize+ ^
  /win32icon:"%~dp0..\assets\icons\ZhiSuan-Al.ico" ^
  /out:"%~dp0..\ZhiSuan-Al-Setup.exe" ^
  /resource:"%~dp0payload.zip",payload.zip ^
  /r:System.dll /r:System.Windows.Forms.dll /r:System.Drawing.dll ^
  Installer.cs
if errorlevel 1 exit /b 1

echo [OK] ZhiSuan-Al-Setup.exe
endlocal
