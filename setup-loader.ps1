# Cài Fabric Loader 1.21.11 + chép mod vào instance (1 lần)
$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$inst = Join-Path $root 'client'                 # instance dir
$mc   = '1.21.11'
$jdk  = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot\bin\java.exe'

New-Item -ItemType Directory -Force -Path $inst, (Join-Path $inst 'mods') | Out-Null

# 1) Tải Fabric Installer
$installerJar = Join-Path $root 'fabric-installer.jar'
if (-not (Test-Path $installerJar)) {
  $meta = Invoke-RestMethod 'https://meta.fabricmc.net/v2/versions/installer'
  $url  = $meta[0].url
  Write-Host "Tải Fabric Installer: $url"
  Invoke-WebRequest $url -OutFile $installerJar
}

# 2) Cài client loader cho 1.21.11 vào $inst
& $jdk -jar $installerJar client -dir $inst -mcversion $mc -noprofile

# 3) Chép mod đã tải (libs/) vào mods/ của instance
Copy-Item (Join-Path $root 'libs\*.jar') (Join-Path $inst 'mods') -Force
# client mod của mình (sau khi build Loom) cũng vào đây:
if (Test-Path (Join-Path $root 'build\libs')) {
  Copy-Item (Join-Path $root 'build\libs\*.jar') (Join-Path $inst 'mods') -Force
}
Write-Host "`nXong. Instance: $inst  (mods: $((Get-ChildItem (Join-Path $inst 'mods')).Count) file)"
