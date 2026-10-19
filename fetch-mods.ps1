# Tải mod .jar đã build sẵn (Fabric) cho 1.21.11 vào libs/
$ErrorActionPreference = 'Stop'
$libs = Join-Path $PSScriptRoot 'libs'
New-Item -ItemType Directory -Force -Path $libs | Out-Null

$slugs = @(
  'fabric-api','sodium','lithium','ferrite-core','entityculling',
  'immediatelyfast','krypton','dynamic-fps',
  'iris','sodium-extra','modmenu',
  'reeses-sodium-options','sodium-shadowy-path-blocks','moreculling',
  'fabric-language-kotlin','cloth-config','architectury-api',
  'threatengl','lomka','sf-plus','not-enough-framerate','reflex-antilag',
  'lazy-language-loader','better-block-entities','fast-ip-ping',
  'adaptivecaps','zenyfast'
  # Bỏ 'smoothgl' (bản thay thế Sodium, id=sodium) và 'optcraft_fabric'
  # (crash mixin: class_761/LevelRenderer loaded too early).
  # Bỏ 'gputape' (GPUBooster): phá pipeline shader core (position_tex_color /
  # mojang_logo) và làm mất toàn bộ GL extension tùy chọn -> hại hiệu năng.
)
$game = @('1.21.11')   # chỉ lấy đúng 1.21.11; thiếu thì báo MISS, không kéo nhầm bản khác

foreach ($slug in $slugs) {
  $ok = $false
  foreach ($gv in $game) {
    $url = "https://api.modrinth.com/v2/project/$slug/version?loaders=%5B%22fabric%22%5D&game_versions=%5B%22$gv%22%5D"
    try {
      $vers = Invoke-RestMethod -Uri $url -Headers @{ 'User-Agent'='mc-client-bundler/1.0' }
      if ($vers.Count -gt 0) {
        $file = $vers[0].files[0]
        $out = Join-Path $libs $file.filename
        Invoke-WebRequest -Uri $file.url -OutFile $out -Headers @{ 'User-Agent'='mc-client-bundler/1.0' }
        Write-Host ("OK  {0,-16} {1}  {2}" -f $slug, $gv, $file.filename)
        $ok = $true
        break
      }
    } catch { }
  }
  if (-not $ok) { Write-Host ("MISS {0,-16} (không có build Fabric phù hợp)" -f $slug) }
}

# WI Zoom không có trên Modrinth -> tải từ GitHub release (bản 1.21.11)
$wizUrl = 'https://github.com/Wurst-Imperium-MCX/WI-Zoom/releases/download/v1.7/WI-Zoom-1.7-MC1.21.11.jar'
Invoke-WebRequest -Uri $wizUrl -OutFile (Join-Path $libs 'WI-Zoom-1.7-MC1.21.11.jar') -Headers @{ 'User-Agent'='mc-client-bundler/1.0' }
Write-Host "OK  wi-zoom          (GitHub)  WI-Zoom-1.7-MC1.21.11.jar"

Write-Host "`nĐã tải vào: $libs"
