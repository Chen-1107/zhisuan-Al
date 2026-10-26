@echo off
rem 编译启动器 -> ..\ZhiSuan-Al.exe（仅需 .NET Framework 4.x 自带的 csc，无需 Visual Studio）
setlocal
cd /d %~dp0

set "CSC=%WINDIR%\Microsoft.NET\Framework64\v4.0.30319\csc.exe"
if not exist "%CSC%" set "CSC=%WINDIR%\Microsoft.NET\Framework\v4.0.30319\csc.exe"
if not exist "%CSC%" (
  echo [!] csc.exe not found. Install .NET Framework 4.x first.
  exit /b 1
)

"%CSC%" /nologo /target:winexe /codepage:65001 /optimize+ ^
  /win32icon:"..\assets\icons\ZhiSuan-Al.ico" ^
  /out:"..\ZhiSuan-Al.exe" ^
  /r:System.dll /r:System.Windows.Forms.dll /r:System.Drawing.dll ^
  Launcher.cs
if errorlevel 1 (
  echo [FAIL] launcher build failed
  exit /b 1
)

echo [OK] built ZhiSuan-Al.exe
endlocal
exit /b 0
