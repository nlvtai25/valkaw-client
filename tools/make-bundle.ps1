# make-bundle.ps1 - gop <Variant>_v<ver>.zip thanh 1 file .jar (Fabric jar-in-jar).
#
#   .\make-bundle.ps1 -Zip C:\Users\Administrator\Music\Release\Sodium_v1.3.zip `
#                     -Out ..\out\Sodium_v1.3.jar -Id valkaw-pack-sodium -Version 1.3
#
# Quy tac:
#  - Chi doc zip nguon, KHONG sua byte nao trong mod jar (license + hash so voi ban goc).
#  - Entry phai la 'mods/<ten>.jar' (forward slash, khop tuyet doi ten goc).
#  - Nested jar thieu entry chi bi warn o dev, production im lang bo qua -> tu verify o day.
#  - PS 5.1 compatible (build-launcher.ps1 cung chay bang powershell.exe 5.1).
param(
  [Parameter(Mandatory = $true)][string]$Zip,
  [Parameter(Mandatory = $true)][string]$Out,
  [Parameter(Mandatory = $true)][string]$Id,
  [Parameter(Mandatory = $true)][string]$Version,
  [string]$Name = '',
  [string[]]$ExtraDir = @(),      # M2: 'config','resourcepacks'
  [switch]$IncludeOptions
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression, System.IO.Compression.FileSystem

# powershell -File chi truyen duoc 1 chuoi -> nhan ca 'config,resourcepacks'
$ExtraDir = @($ExtraDir | ForEach-Object { $_ -split ',' } | Where-Object { $_ })

$zipFull = (Resolve-Path $Zip).Path
$outFull = [IO.Path]::GetFullPath([IO.Path]::Combine((Get-Location).Path, $Out))
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $outFull) | Out-Null
if (-not $Name) { $Name = $Id }

$src = [IO.Compression.ZipFile]::OpenRead($zipFull)
try {
  $mods = @($src.Entries | Where-Object { $_.FullName -like 'mods/*.jar' } | Sort-Object FullName)
  if ($mods.Count -eq 0) { throw "Khong co mods/*.jar trong $zipFull" }

  # entry phu (config/options/resourcepacks) - giu nguyen duong dan goc trong jar
  $extra = @()
  foreach ($d in $ExtraDir) {
    $extra += @($src.Entries | Where-Object { $_.FullName -like "$d/*" -and -not $_.FullName.EndsWith('/') })
  }
  if ($IncludeOptions) {
    $extra += @($src.Entries | Where-Object { $_.FullName -eq 'options.txt' })
  }

  $jars = @()
  foreach ($m in $mods) { $jars += [ordered]@{ file = $m.FullName } }
  $meta = [ordered]@{
    schemaVersion = 1
    id            = $Id
    version       = $Version
    name          = $Name
    description   = 'Valkaw bundle: all mods in one jar.'
    depends       = [ordered]@{ minecraft = '1.21.11' }
    jars          = $jars
  }
  $json = $meta | ConvertTo-Json -Depth 6

  if (Test-Path $outFull) { Remove-Item $outFull -Force }
  $zout = [IO.Compression.ZipFile]::Open($outFull, 'Create')
  try {
    foreach ($e in @($mods) + @($extra)) {
      $dst = $zout.CreateEntry($e.FullName, [IO.Compression.CompressionLevel]::Optimal)
      $os = $dst.Open(); $is = $e.Open()
      try { $is.CopyTo($os) } finally { $is.Close(); $os.Close() }
    }
    $dst = $zout.CreateEntry('fabric.mod.json', [IO.Compression.CompressionLevel]::Optimal)
    $os = $dst.Open(); $bytes = (New-Object System.Text.UTF8Encoding($false)).GetBytes($json)
    try { $os.Write($bytes, 0, $bytes.Length) } finally { $os.Close() }
  } finally { $zout.Dispose() }
} finally { $src.Dispose() }

# verify - loader production bo qua nested jar loi trong im lang, nen phai chan o day
$v = [IO.Compression.ZipFile]::OpenRead($outFull)
try {
  $names = @($v.Entries | ForEach-Object { $_.FullName })
  if ($names -notcontains 'fabric.mod.json') { throw 'thieu fabric.mod.json' }
  $sr = New-Object System.IO.StreamReader($v.GetEntry('fabric.mod.json').Open(), [System.Text.Encoding]::UTF8)
  $back = $sr.ReadToEnd() | ConvertFrom-Json
  $sr.Close()
  if ($back.id -ne $Id) { throw "id trong jar ($($back.id)) khac $Id" }
  $declared = @($back.jars)
  if ($declared.Count -ne $mods.Count) { throw "jars=$($declared.Count) nhung mods=$($mods.Count)" }
  foreach ($j in $declared) { if ($names -notcontains $j.file) { throw "thieu entry $($j.file)" } }
  if (@($names | Select-Object -Unique).Count -ne $names.Count) { throw 'entry trung ten' }
} finally { $v.Dispose() }

$size = [math]::Round((Get-Item $outFull).Length / 1MB, 1)
"OK {0} : {1} mods, {2} extra, {3} MB, id={4} v={5}" -f $outFull, $mods.Count, $extra.Count, $size, $Id, $Version
