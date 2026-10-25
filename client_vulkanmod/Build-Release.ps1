# Dong goi ZIP staging: mods/ (tat ca jar, gom client vua build)
# + settings mac dinh (options.txt, config/, resourcepacks/). tools\make-bundle.ps1 gop thanh 1 JAR.
#   .\Build-Release.ps1 -Variant Sodium    -ModsDir .\client\mods -DefaultsDir .\client
#   .\Build-Release.ps1 -Variant VulkanMod -ModsDir ..\client_vulkanmod\client\mods -DefaultsDir ..\client_vulkanmod\client
param(
    [Parameter(Mandatory = $true)][ValidateSet('Sodium', 'VulkanMod')][string]$Variant,
    [Parameter(Mandatory = $true)][string]$ModsDir,
    [Parameter(Mandatory = $true)][string]$DefaultsDir,
    [string]$OutDir = ''
)
$ErrorActionPreference = 'Stop'
if (-not $OutDir) { $OutDir = Join-Path (Split-Path -Parent $MyInvocation.MyCommand.Path) '..\_release-staging' }
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

$baseJar = Join-Path $PSScriptRoot 'build\libs\mc-client-standalone-12111-1.0.0.jar'
if (-not (Test-Path $baseJar)) { throw "Thieu $baseJar - chay .\gradlew.bat build truoc." }
$clientJar = Split-Path $baseJar -Leaf
$mods = (Resolve-Path $ModsDir).Path
$def = (Resolve-Path $DefaultsDir).Path
$opt = Join-Path $def 'options.txt'
if (-not (Test-Path $opt)) { throw "Thieu $opt" }

New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
# Ten file: Sodium_v1.5.zip / Vulkan_v1.5.zip (Variant VulkanMod -> ten ngan "Vulkan")
$packName = if ($Variant -eq 'VulkanMod') { 'Vulkan' } else { 'Sodium' }
$out = Join-Path $OutDir ('{0}_v1.5.zip' -f $packName)
Remove-Item $out -Force -ErrorAction SilentlyContinue

# Mods nhung kem: tat ca jar trong ModsDir (client jar lay tu build moi, khong lay ban cu)
$embed = @(Get-ChildItem $mods -Filter *.jar | Where-Object { $_.Name -ne $clientJar })
if ($embed.Count -eq 0) { throw "Khong co mod nao trong $mods" }

$zip = [IO.Compression.ZipFile]::Open($out, 'Create')
try {
    foreach ($j in $embed) {
        [void][IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, $j.FullName, 'mods/' + $j.Name, [IO.Compression.CompressionLevel]::Optimal)
    }
    [void][IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, $baseJar, 'mods/' + $clientJar, [IO.Compression.CompressionLevel]::Optimal)
    [void][IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, $opt, 'options.txt', [IO.Compression.CompressionLevel]::Optimal)
    foreach ($sub in 'config', 'resourcepacks') {
        $dir = Join-Path $def $sub
        if (-not (Test-Path $dir)) { continue }
        foreach ($f in Get-ChildItem $dir -Recurse -File) {
            $rel = $f.FullName.Substring($def.Length).TrimStart('\', '/') -replace '\\', '/'
            # Khong dong goi anh skin/cape ca nhan (xem AGENTS.md - Dong goi ZIP)
            if ($rel -match '^config/(skins-local|capes-local|adaptivecapes)/.*\.(png|jpe?g|gif|webp|bmp)$') { continue }
            [void][IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, $f.FullName, $rel, [IO.Compression.CompressionLevel]::Optimal)
        }
    }
} finally { $zip.Dispose() }

# Kiem: client jar co mat, khong con kotlin, so mod khop
$z = [IO.Compression.ZipFile]::OpenRead($out)
try {
    $names = @($z.Entries | ForEach-Object { $_.FullName })
    $nMods = @($names | Where-Object { $_ -like 'mods/*.jar' }).Count
} finally { $z.Dispose() }
if ($names -notcontains ('mods/' + $clientJar)) { throw "Thieu client jar trong zip" }
if ($names -match 'fabric-language-kotlin') { throw "Van con fabric-language-kotlin - xoa khoi ModsDir" }
if (-not ($names -contains 'options.txt')) { throw 'Thieu options.txt' }
if (-not ($names | Where-Object { $_ -like 'resourcepacks/*' })) { throw 'Thieu resourcepacks trong zip' }
Write-Host ('OK  {0}  ({1} mods, {2} files, {3} MB)' -f $out, $nMods, $names.Count, [math]::Round((Get-Item $out).Length / 1MB, 1))
