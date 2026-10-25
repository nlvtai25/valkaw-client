# Build lai Valkaw.exe tu Launcher.cs bang csc (C# 5, .NET Framework 4.x).
# Nhung: valkaw_logo.jpg + valkaw_icon.ico (manifest resource), launch.ps1 (resource ten
# "launch.ps1_text"), va valkaw_icon.ico lam icon cua exe.
# Xong thi thay ban cu: Valkaw.exe -> Valkaw.exe.old -> xoa .old.
$ErrorActionPreference = 'Stop'
$here = $PSScriptRoot

$csc = Join-Path $env:WINDIR 'Microsoft.NET\Framework\v4.0.30319\csc.exe'
if (-not (Test-Path $csc)) { $csc = Join-Path $env:WINDIR 'Microsoft.NET\Framework64\v4.0.30319\csc.exe' }
if (-not (Test-Path $csc)) { throw 'Khong tim thay csc.exe - can .NET Framework 4.x' }

foreach ($f in 'Launcher.cs', 'launch.ps1', 'valkaw_logo.jpg', 'valkaw_icon.ico') {
    if (-not (Test-Path (Join-Path $here $f))) { throw "Thieu file: $f" }
}

$out = Join-Path $here 'Valkaw Launcher.new.exe'
Remove-Item $out -Force -ErrorAction SilentlyContinue

# Changelog: 1 nguon duy nhat o launcher\linux\valkaw.py (khoi CHANGELOG) -> nhung vao exe
# thanh resource "changelog_text" de o Changelog cua 2 ban giong nhau.
# Phai doc bang UTF-8: file la UTF-8 khong BOM, Get-Content -Raw se doc theo ANSI -> loi dau tieng Viet.
$utf8 = New-Object Text.UTF8Encoding($false)
$pysrc = [IO.File]::ReadAllText((Join-Path $here 'linux\valkaw.py'), $utf8)
$mcl = [regex]::Match($pysrc, '(?s)CHANGELOG\s*=\s*"""(.*?)"""')
if (-not $mcl.Success) { throw 'Khong doc duoc khoi CHANGELOG trong launcher\linux\valkaw.py' }
$clfile = Join-Path $env:TEMP 'valkaw-changelog.txt'
[IO.File]::WriteAllText($clfile, $mcl.Groups[1].Value, $utf8)

& $csc /nologo /target:winexe /platform:anycpu /out:"$out" /win32icon:"$here\valkaw_icon.ico" `
    /resource:"$here\valkaw_logo.jpg" `
    /resource:"$here\valkaw_icon.ico" `
    "/resource:$here\launch.ps1,launch.ps1_text" `
    "/resource:$clfile,changelog_text" `
    /r:System.dll /r:System.Windows.Forms.dll /r:System.Drawing.dll `
    /r:System.IO.Compression.dll /r:System.IO.Compression.FileSystem.dll `
    /r:System.Web.Extensions.dll `
    "$here\Launcher.cs"
if ($LASTEXITCODE -ne 0) {
    Remove-Item $out -Force -ErrorAction SilentlyContinue
    throw "csc that bai (exit $LASTEXITCODE)"
}

# Tu kiem: exe moi phai nhung du 4 resource nhu ban cu (Load tu byte[] de khong khoa file)
$want  = 'valkaw_logo.jpg', 'valkaw_icon.ico', 'launch.ps1_text', 'changelog_text'
$asm   = [Reflection.Assembly]::Load([IO.File]::ReadAllBytes($out))
$have  = @($asm.GetManifestResourceNames())
$refs  = @($asm.GetReferencedAssemblies() | ForEach-Object { $_.Name })
$asm   = $null
$missing = @($want | Where-Object { $have -notcontains $_ })
if ($missing.Count -gt 0) {
    Remove-Item $out -Force
    throw "Thieu resource: $($missing -join ', ')"
}
Write-Host ("OK  {0} B | resource: {1} | refs: {2}" -f (Get-Item $out).Length, ($have -join ', '), ($refs -join ', '))

# Ten asset phat hanh phai co version: launcher doc version trong ten de biet co ban moi.
$m = [regex]::Match([IO.File]::ReadAllText((Join-Path $here 'Launcher.cs'), $utf8),
                    'const\s+string\s+AppVersion\s*=\s*"([^"]+)"')
if (-not $m.Success) { Remove-Item $out -Force; throw 'Khong doc duoc AppVersion trong Launcher.cs' }
$ver = $m.Groups[1].Value

$exe = Join-Path $here 'Valkaw Launcher.exe'
if (Test-Path $exe) {
    Remove-Item "$exe.old" -Force -ErrorAction SilentlyContinue
    try { Rename-Item $exe "$exe.old" -ErrorAction Stop }
    catch { Remove-Item $out -Force; throw 'Khong doi duoc exe - launcher dang chay? Dong launcher roi chay lai script.' }
    Move-Item $out $exe
    Remove-Item "$exe.old" -Force -ErrorAction SilentlyContinue
} else {
    Move-Item $out $exe
}
Write-Host "Done -> $exe"

# Ban phat hanh: Release\Valkaw.Launcher-<ver>.exe (upload file nay len release).
$rel = Join-Path (Split-Path $here) 'Release'
New-Item -ItemType Directory -Force -Path $rel | Out-Null
$asset = Join-Path $rel "Valkaw.Launcher-$ver.exe"
Copy-Item $exe $asset -Force
Write-Host ("Release asset -> {0} ({1} B)" -f $asset, (Get-Item $asset).Length)
