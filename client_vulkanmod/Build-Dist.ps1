# Build a minimal distributable for Valkaw 1.21.11 into dist\.
# Run on a machine that already has 1.21.11 in %AppData%\.minecraft.
# Bundles: only the libraries referenced by the version json + the assets that
# the 1.21.11 asset index references (not the whole .minecraft assets, ~800MB).
$ErrorActionPreference = 'Stop'
$MC        = '1.21.11'
$proj      = $PSScriptRoot
# launcher da tach rieng ra Music\launcher (ngoai project)
$launcher  = Join-Path (Split-Path $proj -Parent) 'launcher'
if (-not (Test-Path (Join-Path $launcher 'Valkaw.exe'))) { $launcher = Join-Path (Split-Path (Split-Path $proj -Parent) -Parent) 'launcher' }
$mcRoot    = Join-Path $env:APPDATA '.minecraft'
$dist      = Join-Path $proj 'dist'
$run       = Join-Path $dist 'runtime'

if (-not (Test-Path (Join-Path $mcRoot "versions\$MC\$MC.jar"))) {
    Write-Host "ERROR: $MC not found in $mcRoot. Run the official launcher once." -ForegroundColor Red
    exit 1
}

# --- reset dist ---
if (Test-Path $dist) { Remove-Item $dist -Recurse -Force }
New-Item -ItemType Directory -Force -Path $run | Out-Null

function Copy-IfExists($src, $dst) {
    if (Test-Path $src) {
        New-Item -ItemType Directory -Force -Path (Split-Path $dst) | Out-Null
        Copy-Item $src $dst -Force
    }
}

function Get-LibPath($lib) {
    if ($lib.downloads -and $lib.downloads.artifact -and $lib.downloads.artifact.path) { return $lib.downloads.artifact.path }
    if (-not $lib.name) { return $null }
    $p = $lib.name.Split(':'); $group = $p[0] -replace '\.', '/'; $art = $p[1]; $ver = $p[2]
    $cls = if ($p.Count -gt 3) { '-' + $p[3] } else { '' }
    return "$group/$art/$ver/$art-$ver$cls.jar"
}

# --- version json (vanilla + fabric loader) ---
$vanilla = Get-Content (Join-Path $mcRoot "versions\$MC\$MC.json") -Raw | ConvertFrom-Json
Copy-IfExists (Join-Path $mcRoot "versions\$MC\$MC.jar")       (Join-Path $run "versions\$MC\$MC.jar")
Copy-IfExists (Join-Path $mcRoot "versions\$MC\$MC.json")     (Join-Path $run "versions\$MC\$MC.json")
$flDir = Get-ChildItem (Join-Path $mcRoot 'versions') -Directory -Filter "fabric-loader-*-$MC" |
         Sort-Object Name -Descending | Select-Object -First 1
if (-not $flDir) { Write-Host "ERROR: fabric-loader for $MC not found." -ForegroundColor Red; exit 1 }
Copy-IfExists (Join-Path $flDir.FullName "$($flDir.Name).json") (Join-Path $run "versions\$($flDir.Name)\$($flDir.Name).json")
$fabric = Get-Content (Join-Path $flDir.FullName "$($flDir.Name).json") -Raw | ConvertFrom-Json

# --- libraries: only those referenced by vanilla + fabric json ---
$libRels = New-Object System.Collections.Generic.HashSet[string]
foreach ($j in @($vanilla.libraries) + @($fabric.libraries)) {
    $rel = Get-LibPath $j
    if ($rel) { [void]$libRels.Add($rel) }
}
$libCount = 0
foreach ($rel in $libRels) {
    $src = Join-Path (Join-Path $mcRoot 'libraries') ($rel -replace '/', '\')
    if (Test-Path $src) { Copy-IfExists $src (Join-Path $run "libraries\$($rel -replace '/','\')"); $libCount++ }
}

# --- assets: only objects referenced by the 1.21.11 asset index ---
$idxId = $vanilla.assetIndex.id
$idxFile = Join-Path $mcRoot "assets\indexes\$idxId.json"
if (Test-Path $idxFile) {
    $idx = Get-Content $idxFile -Raw | ConvertFrom-Json
    Copy-IfExists $idxFile (Join-Path $run "assets\indexes\$idxId.json")
    $hashes = New-Object System.Collections.Generic.HashSet[string]
    foreach ($obj in $idx.objects.PSObject.Properties) { [void]$hashes.Add($obj.Value.hash) }
    $assetCount = 0
    foreach ($h in $hashes) {
        $src = Join-Path $mcRoot "assets\objects\$($h.Substring(0,2))\$h"
        if (Test-Path $src) { Copy-IfExists $src (Join-Path $run "assets\objects\$($h.Substring(0,2))\$h"); $assetCount++ }
    }
}

# --- launcher files, mods, logo ---
Copy-Item (Join-Path $launcher 'Valkaw.exe')   (Join-Path $dist 'Valkaw.exe') -Force
Copy-Item (Join-Path $launcher 'launch.ps1')   (Join-Path $dist 'launch.ps1') -Force
$modsDst = Join-Path $dist 'mods'; New-Item -ItemType Directory -Force -Path $modsDst | Out-Null
Copy-Item (Join-Path $proj 'client\mods\*.jar') $modsDst -Force
$logoDst = Join-Path $dist 'src\main\resources\assets\valkaw-std'
$logoSrc = Join-Path $proj 'src\main\resources\assets\valkaw-std'
if (Test-Path $logoSrc) { Copy-Item $logoSrc $logoDst -Recurse -Force }

$runMB = [math]::Round(((Get-ChildItem $run -Recurse -File | Measure-Object Length -Sum).Sum/1MB), 0)
Write-Host ("Bundled runtime: {0} MB ({1} libs, {2} assets)" -f $runMB, $libCount, $assetCount)
Write-Host "Done -> $dist"
