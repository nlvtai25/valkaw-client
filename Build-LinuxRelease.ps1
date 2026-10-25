# Dong goi Valkaw Launcher (Linux).
# Launcher Linux chi la 1 file valkaw.py. Ten asset phat hanh PHAI co version
# (Valkaw.Launcher-linux-<ver>.py) - ban cu doc version trong ten de biet co ban moi;
# upload sai ten la khong ai tu cap nhat duoc.
$ErrorActionPreference = 'Stop'

$root = $PSScriptRoot
$src  = Join-Path $root 'launcher\linux\valkaw.py'
$m = [regex]::Match((Get-Content $src -Raw), '(?m)^APP_VERSION\s*=\s*"([^"]+)"')
if (-not $m.Success) { throw 'Khong doc duoc APP_VERSION trong launcher\linux\valkaw.py' }

$out = Join-Path $root ('Release\Valkaw.Launcher-linux-{0}.py' -f $m.Groups[1].Value)

# Chan dong goi ban loi cu phap (launcher tu choi apply file hong, nhung dung phat hanh file hong).
python -m py_compile $src
if ($LASTEXITCODE -ne 0) { throw 'valkaw.py loi cu phap - khong dong goi' }

Copy-Item $src $out -Force
Write-Host ('Release asset -> {0} ({1} B)' -f $out, (Get-Item $out).Length)
