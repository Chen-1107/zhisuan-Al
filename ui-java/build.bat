@echo off
rem 编译工作流画布（ui-workflow）与主 GUI（ui-java）
setlocal
cd /d %~dp0

pushd ..\ui-workflow
echo [1/2] compiling ui-workflow ...
if not exist out mkdir out
dir /s /b src\*.java > sources.txt
javac -encoding UTF-8 -d out @sources.txt
if errorlevel 1 (
  popd
  echo [FAIL] ui-workflow
  exit /b 1
)
popd

echo [2/2] compiling ui-java ...
if not exist out mkdir out
dir /s /b src\*.java > sources.txt
javac -encoding UTF-8 -cp "lib\jlatexmath-1.0.7.jar;..\ui-workflow\out" -d out @sources.txt
if errorlevel 1 (
  echo [FAIL] ui-java
  exit /b 1
)

echo [OK] compiled to out\
endlocal
exit /b 0
