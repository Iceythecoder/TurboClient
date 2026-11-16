$ErrorActionPreference = 'Stop'
$uv = Join-Path $PSScriptRoot 'tools\uv\uv.exe'
if (-not (Test-Path -LiteralPath $uv)) {
    New-Item -ItemType Directory -Path (Join-Path $PSScriptRoot 'tools') -Force | Out-Null
    $archive = Join-Path $PSScriptRoot 'tools\uv.zip'
    Invoke-WebRequest -Uri 'https://github.com/astral-sh/uv/releases/download/0.12.23/uv-x86_64-pc-windows-msvc.zip' -OutFile $archive -UseBasicParsing
    Expand-Archive -LiteralPath $archive -DestinationPath (Join-Path $PSScriptRoot 'tools\uv') -Force
}
$env:UV_PYTHON_INSTALL_DIR = Join-Path $PSScriptRoot 'python'
$env:UV_CACHE_DIR = Join-Path $PSScriptRoot 'cache'
$env:UV_PYTHON_BIN_DIR = Join-Path $PSScriptRoot 'tools\bin'
$env:UV_NO_MODIFY_PATH = '1'
& $uv python install 3.12
if ($LASTEXITCODE -ne 0) { throw 'Local Python installation failed.' }
$python = Join-Path $PSScriptRoot '.venv\Scripts\python.exe'
if (-not (Test-Path -LiteralPath $python)) {
    & $uv venv --python 3.12 (Join-Path $PSScriptRoot '.venv')
    if ($LASTEXITCODE -ne 0) { throw 'Environment creation failed.' }
}
& $uv pip install --python $python 'torch==2.14.1+cpu' --index-url 'https://download.pytorch.org/whl/cpu'
if ($LASTEXITCODE -ne 0) { throw 'CPU PyTorch installation failed.' }
& $uv pip install --python $python --requirement (Join-Path $PSScriptRoot 'requirements.lock.txt')
if ($LASTEXITCODE -ne 0) { throw 'RL dependency installation failed.' }
& $python (Join-Path $PSScriptRoot 'verify_install.py')
if ($LASTEXITCODE -ne 0) { throw 'RL import verification failed.' }
