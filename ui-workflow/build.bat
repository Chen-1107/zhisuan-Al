@echo off
cd /d %~dp0
if not exist out mkdir out
dir /s /b src\*.java > sources.txt
javac -encoding UTF-8 -d out @sources.txt
if %errorlevel%==0 (
  echo [OK] compiled
) else (
  echo [FAIL] compile failed
)
