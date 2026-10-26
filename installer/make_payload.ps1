# 生成安装包载荷 installer\payload.zip
# 用法：powershell -NoProfile -ExecutionPolicy Bypass -File installer\make_payload.ps1
$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$stage = Join-Path $PSScriptRoot 'stage'
$zip = Join-Path $PSScriptRoot 'payload.zip'

if (Test-Path $stage) { Remove-Item $stage -Recurse -Force }
New-Item -ItemType Directory -Path $stage | Out-Null

$dirs = @('server', 'ui-java', 'ui-workflow', 'native', 'skills', 'examples', 'launcher', 'assets')
$files = @('LICENSE', 'README.md', 'start.bat', 'ZhiSuan-Al.exe')
$excludeDirs = @('out', '__pycache__', 'workspace', 'logs', '.git')
$excludeFiles = @('*.class', '*.pyc', '*.log', 'sources.txt')

foreach ($d in $dirs) {
    $src = Join-Path $root $d
    if (-not (Test-Path $src)) { throw "缺少目录：$src" }
    $dst = Join-Path $stage $d
    $rcArgs = @($src, $dst, '/E', '/NFL', '/NDL', '/NJH', '/NJS', '/NP', '/XD') + $excludeDirs + @('/XF') + $excludeFiles
    & robocopy @rcArgs | Out-Null
    if ($LASTEXITCODE -ge 8) { throw "复制失败：$d (robocopy $LASTEXITCODE)" }
}

foreach ($f in $files) {
    $src = Join-Path $root $f
    if (-not (Test-Path $src)) { throw "缺少文件：$src" }
    Copy-Item $src -Destination (Join-Path $stage $f) -Force
}

# 安装后的首次启动由启动器自行编译，故不打包 out/ 目录
if (Test-Path $zip) { Remove-Item $zip -Force }
& tar.exe -a -c -f $zip -C $stage .
if ($LASTEXITCODE -ne 0) { throw "压缩失败：tar $LASTEXITCODE" }

$size = (Get-Item $zip).Length
Write-Host ("[OK] payload.zip  {0:N0} bytes  ({1:N1} MB)" -f $size, ($size / 1MB))
