# Custom launcher cho 1.21.11 — nạp sẵn mod, KHÔNG cần chạy modloader thủ công.
# Dùng libraries/assets sẵn có trong .minecraft; gameDir riêng để cô lập mods/.
param(
  [string]$GameDir = (Join-Path $PSScriptRoot 'client'),
  [string]$MC       = '1.21.11',
  [string]$Ram      = '6G',
  [string]$Username = 'Player',
  [string]$Priority = 'Normal'
)
$ErrorActionPreference = 'Stop'

# Log: <root>\log_launcher\latest.log (đè mỗi lần Play) + <dd-MM-yyyy HH-mm-ss>.log (mỗi lần Play 1 file)
try { [Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false) } catch { }
$logDir = Join-Path $PSScriptRoot 'log_launcher'
New-Item -ItemType Directory -Force -Path $logDir | Out-Null
$now = Get-Date
$logEnc = New-Object System.Text.UTF8Encoding($false)
$logLatest = New-Object System.IO.StreamWriter((Join-Path $logDir 'latest.log'), $false, $logEnc)
$logRun = New-Object System.IO.StreamWriter((Join-Path $logDir ($now.ToString('dd-MM-yyyy HH-mm-ss') + '.log')), $true, $logEnc)
$logLatest.AutoFlush = $true
$logRun.AutoFlush = $true
function Write-Log([string]$line) {
  $logLatest.WriteLine($line)
  $logRun.WriteLine($line)
  Write-Host $line
}
# Cửa sổ powershell bị ẩn -> lỗi phải vào log, không được chết im lặng
trap { Write-Log ("ERROR: {0}" -f $_); $logLatest.Close(); $logRun.Close(); exit 1 }
Write-Log ("=== Valkaw launcher | {0} ===" -f $now.ToString('dd-MM-yyyy HH:mm:ss'))
Write-Log ("Ram={0} Priority={1} User={2} GameDir={3}" -f $Ram, $Priority, $Username, $GameDir)

# Runtime ISO: bundled copy ships as <launcher>\minecraft; on dev fall back to .minecraft
$bundleRoot = Join-Path $PSScriptRoot 'minecraft'
$mcRoot = if (Test-Path (Join-Path $bundleRoot "versions\$MC\$MC.json")) { $bundleRoot } else { Join-Path $env:APPDATA '.minecraft' }
# Java 21+: JAVA_HOME/PATH trên máy có thể là JDK 8 -> phải kiểm tra version thật
function Get-JavaMajor([string]$exe) {
  $old = $ErrorActionPreference
  $ErrorActionPreference = 'Continue'   # java -version ghi ra stderr
  $major = 0
  try {
    $out = & $exe -version 2>&1
    $first = if ($out -is [array]) { $out[0] } else { $out }
    if ("$first" -match 'version "(\d+)') { $major = [int]$Matches[1] }
  } catch { }
  $ErrorActionPreference = $old
  return $major
}
$cands = @()
if ($env:JAVA_HOME) { $cands += (Join-Path $env:JAVA_HOME 'bin\java.exe') }
$onPath = Get-Command 'java' -ErrorAction SilentlyContinue
if ($onPath) { $cands += $onPath.Source }
$cands += (Get-ChildItem "$env:ProgramFiles\*\*\bin\java.exe", "${env:ProgramFiles(x86)}\*\*\bin\java.exe", "$env:LOCALAPPDATA\Programs\*\*\bin\java.exe" -ErrorAction SilentlyContinue | Select-Object -ExpandProperty FullName)
$jdk = $null
foreach ($c in $cands) { if ($c -and (Test-Path $c) -and (Get-JavaMajor $c) -eq 21) { $jdk = $c; break } }
if (-not $jdk) {
  foreach ($c in $cands) { if ($c -and (Test-Path $c) -and (Get-JavaMajor $c) -ge 21) { $jdk = $c; break } }
}
if (-not $jdk) {
  Write-Log "ERROR: Khong tim thay Java 21 (JDK). Cai tai https://adoptium.net/ roi mo lai launcher."
  exit 1
}
Write-Log ("Java: {0} (major {1})" -f $jdk, (Get-JavaMajor $jdk))
$natives = Join-Path $GameDir 'natives'
New-Item -ItemType Directory -Force -Path $GameDir, (Join-Path $GameDir 'mods'), $natives | Out-Null
Set-Location $GameDir   # CWD = gameDir (để mod ghi config đúng chỗ, vd Reflex)

function Get-LibPath($lib) {
  if ($lib.downloads -and $lib.downloads.artifact -and $lib.downloads.artifact.path) {
    return $lib.downloads.artifact.path
  }
  if (-not $lib.name) { return $null }
  $p = $lib.name.Split(':')
  $group = $p[0] -replace '\.', '/'; $art = $p[1]; $ver = $p[2]
  $cls = if ($p.Count -gt 3) { '-' + $p[3] } else { '' }
  return "$group/$art/$ver/$art-$ver$cls.jar"
}

# 1) Gộp thư viện vanilla + fabric, dựng classpath
$vanilla = Get-Content (Join-Path $mcRoot "versions\$MC\$MC.json") -Raw | ConvertFrom-Json
$flJson  = Get-ChildItem (Join-Path $mcRoot 'versions') -Directory -Filter "fabric-loader-*-$MC" |
           Sort-Object Name -Descending | Select-Object -First 1
if (-not $flJson) { throw "Không thấy Fabric loader cho $MC trong .minecraft\versions" }
$fabric = Get-Content (Join-Path $flJson.FullName "$($flJson.Name).json") -Raw | ConvertFrom-Json

$cp = New-Object System.Collections.Generic.List[string]
$cp.Add((Join-Path $mcRoot "versions\$MC\$MC.jar"))
foreach ($lib in @($vanilla.libraries) + @($fabric.libraries)) {
  $rel = Get-LibPath $lib
  if ($rel) {
    $full = Join-Path (Join-Path $mcRoot 'libraries') ($rel -replace '/', '\')
    if (Test-Path $full) { $cp.Add($full) }
  }
}

# 2) Natives (LWJGL) — giải nén vào natives/
Get-ChildItem (Join-Path $mcRoot 'libraries') -Recurse -File -Filter '*natives-windows*.jar' | ForEach-Object {
  try { Expand-Archive -Path $_.FullName -DestinationPath $natives -Force } catch {}
}

$cpString = ($cp | Select-Object -Unique) -join ';'
$clientJar = Join-Path $mcRoot "versions\$MC\$MC.jar"
$assets = Join-Path $mcRoot 'assets'

Write-Log "GameDir : $GameDir"
Write-Log "Mods    : $((Get-ChildItem (Join-Path $GameDir 'mods') -File).Count) file"
Write-Log "Classpath: $($cp.Count) entries"

# 3) Chạy Fabric KnotClient (offline) — loader + mods nạp ngay, không cần bước ngoài
$extraJvm = @()
$jvmFile = Join-Path $PSScriptRoot 'jvmargs.txt'
if (Test-Path $jvmFile) {
  $jvmTxt = Get-Content $jvmFile -Raw      # file rỗng -> $null (không được .Trim() thẳng)
  if ($jvmTxt) { $extraJvm = $jvmTxt.Trim() -split '\s+' }
} elseif ($env:VALKAW_JVM) {
  $extraJvm = $env:VALKAW_JVM -split '\s+'
}
# shortcut: priority set trên powershell (cha), java con kế thừa; Realtime cần admin
try { (Get-Process -Id $PID).PriorityClass = $Priority } catch { }

# 4) Chạy game
Write-Log ("JVM args: {0}" -f ($extraJvm -join ' '))

$eap = $ErrorActionPreference
$ErrorActionPreference = 'Continue'   # stderr của java không được làm script dừng
& $jdk `
  "-Djava.library.path=$natives" `
  "-Dfabric.gameJarPath=$clientJar" `
  "-Dfabric.development=false" `
  "-Dstdout.encoding=UTF-8" `
  "-Dstderr.encoding=UTF-8" `
  "--add-modules=jdk.incubator.vector" `
  "-Xms512m" "-Xmx$Ram" `
  "-XX:+UseG1GC" `
  "-XX:+ParallelRefProcEnabled" `
  "-XX:MaxGCPauseMillis=200" `
  "-XX:+UnlockExperimentalVMOptions" `
  "-XX:+DisableExplicitGC" `
  "-XX:+AlwaysPreTouch" `
  "-XX:+UseNUMA" `
  "-XX:G1NewSizePercent=30" `
  "-XX:G1MaxNewSizePercent=40" `
  "-XX:G1HeapRegionSize=8M" `
  "-XX:G1ReservePercent=20" `
  "-XX:G1HeapWastePercent=5" `
  "-XX:G1MixedGCCountTarget=4" `
  "-XX:InitiatingHeapOccupancyPercent=15" `
  "-XX:G1MixedGCLiveThresholdPercent=90" `
  "-XX:G1RSetUpdatingPauseTimePercent=5" `
  "-XX:SurvivorRatio=32" `
  "-XX:+PerfDisableSharedMem" `
  "-XX:MaxTenuringThreshold=1" `
  "-Dusing.aikars.flags=https://mcflags.emc.gs" `
  @extraJvm `
  -cp $cpString `
  $fabric.mainClass `
  --username "$Username" `
  --version $MC `
  --gameDir $GameDir `
  --assetsDir $assets `
  --assetIndex $vanilla.assetIndex.id `
  --uuid "00000000000000000000000000000001" `
  --accessToken "0" `
  --userType legacy `
  --width 1600 --height 900 2>&1 | ForEach-Object { Write-Log ([string]$_) }
$code = $LASTEXITCODE
$ErrorActionPreference = $eap
Write-Log ("=== Ket thuc: exit={0} | {1} ===" -f $code, (Get-Date).ToString('HH:mm:ss'))
$logLatest.Close(); $logRun.Close()
