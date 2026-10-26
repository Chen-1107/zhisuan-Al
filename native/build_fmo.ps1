# 编译 fmo_render_cpp.cpp -> fmo_render_cpp.exe
# 自动通过 vswhere 定位 Visual Studio 的 vcvars64.bat，无需硬编码安装路径
$ErrorActionPreference = "Stop"
Set-Location -Path $PSScriptRoot

$vswhere = Join-Path ${env:ProgramFiles(x86)} "Microsoft Visual Studio\Installer\vswhere.exe"
if (-not (Test-Path $vswhere)) {
    throw "vswhere.exe not found. Install Visual Studio with the C++ build tools."
}
$vs = & $vswhere -latest -products * -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath
if (-not $vs) { throw "No Visual Studio installation with C++ tools found." }

$vcvars = Join-Path $vs "VC\Auxiliary\Build\vcvars64.bat"
cmd /c "`"$vcvars`" >nul 2>&1 && cl /nologo /O2 /std:c++17 /EHsc /MT /utf-8 /Fe:fmo_render_cpp.exe fmo_render_cpp.cpp"
exit $LASTEXITCODE
