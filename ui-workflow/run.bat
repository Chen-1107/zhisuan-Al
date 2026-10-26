@echo off
cd /d %~dp0
start "Workflow UI" javaw -cp out -Dfile.encoding=UTF-8 com.zhisuan.workflow.ui.Main
