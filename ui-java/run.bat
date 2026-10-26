@echo off
rem 启动主 GUI（--mode=audit|dispatch 可切换装配模式，--port=8899 指定服务端口）
cd /d %~dp0
if not exist out (
  echo [!] out not found, run build.bat first.
  exit /b 1
)
start "ZhiSuan UI" javaw -cp "out;lib\jlatexmath-1.0.7.jar;..\ui-workflow\out;..\assets" -Dfile.encoding=UTF-8 zhisuan.Main %*
