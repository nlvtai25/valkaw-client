# Publish Valkaw: launcher -> repo downloads/, client JARs -> GitHub Release v<version>.
# Chay sau khi da build Release. Khong bump version, khong push source.
#   .\publish-github.ps1
#   .\publish-github.ps1 -Version 1.5 -Message "release v1.5"
param(
    [string]$Version = '',
    [string]$Message = '',
    [string]$RepoSlug = 'nlvtai25/valkaw-client'
)
$ErrorActionPreference = 'Stop'
$Music = Split-Path $PSScriptRoot -Parent
$Release = Join-Path $Music 'Release'
$Publish = Join-Path $Music '_publish'
$utf8 = New-Object System.Text.UTF8Encoding($false)

$gh = 'C:\Program Files\GitHub CLI\gh.exe'
if (-not (Test-Path $gh)) { $gh = (Get-Command gh -ErrorAction Stop).Source }

# Version lay tu JAR moi nhat trong Release neu khong truyen.
if (-not $Version) {
    $sodium = Get-ChildItem $Release -Filter 'Sodium_v*.jar' | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if (-not $sodium) { throw "Khong co Sodium_v*.jar trong $Release" }
    $Version = $sodium.BaseName -replace '^Sodium_v', ''
}
$sodiumJar = Join-Path $Release "Sodium_v$Version.jar"
$vulkanJar = Join-Path $Release "Vulkan_v$Version.jar"
if (-not (Test-Path $sodiumJar)) { throw "Thieu $sodiumJar" }
if (-not (Test-Path $vulkanJar)) { throw "Thieu $vulkanJar" }

# Launcher version doc tu Launcher.cs.
$launcherCs = Join-Path $Music 'launcher\Launcher.cs'
$m = [regex]::Match([IO.File]::ReadAllText($launcherCs, $utf8), 'const\s+string\s+AppVersion\s*=\s*"([^"]+)"')
if (-not $m.Success) { throw 'Khong doc duoc AppVersion trong Launcher.cs' }
$appVer = $m.Groups[1].Value
$winLauncher = Join-Path $Release "Valkaw.Launcher-$appVer.exe"
$linuxLauncher = Join-Path $Release "Valkaw.Launcher-linux-$appVer.py"
if (-not (Test-Path $winLauncher)) { throw "Thieu $winLauncher" }
if (-not (Test-Path $linuxLauncher)) { throw "Thieu $linuxLauncher" }

# Repo clone chi de chua downloads/ + README.
if (-not (Test-Path (Join-Path $Publish '.git'))) {
    git clone "https://github.com/$RepoSlug.git" $Publish
}
Push-Location $Publish
try {
    git pull --ff-only
    $downloads = Join-Path $Publish 'downloads'
    New-Item -ItemType Directory -Force -Path $downloads | Out-Null
    Copy-Item $winLauncher (Join-Path $downloads (Split-Path $winLauncher -Leaf)) -Force
    Copy-Item $linuxLauncher (Join-Path $downloads (Split-Path $linuxLauncher -Leaf)) -Force

    # README: ten file, size, SHA-256 cua 2 launcher.
    $readmePath = Join-Path $Publish 'README.md'
    if (Test-Path $readmePath) {
        $readme = [IO.File]::ReadAllText($readmePath, $utf8)
        $readme = $readme -replace 'Valkaw\.Launcher-\d+(?:\.\d+)+\.exe', "Valkaw.Launcher-$appVer.exe"
        $readme = $readme -replace 'Valkaw\.Launcher-linux-\d+(?:\.\d+)+\.py', "Valkaw.Launcher-linux-$appVer.py"
        $readme = $readme -replace 'releases/tag/v\d+(?:\.\d+)+', "releases/tag/v$Version"
        $readme = $readme -replace 'releases/download/v\d+(?:\.\d+)+/', "releases/download/v$Version/"
        $readme = $readme -replace 'Sodium_v\d+(?:\.\d+)+\.jar', "Sodium_v$Version.jar"
        $readme = $readme -replace 'Vulkan_v\d+(?:\.\d+)+\.jar', "Vulkan_v$Version.jar"

        $winSize = (Get-Item $winLauncher).Length.ToString('N0')
        $winHash = (Get-FileHash $winLauncher -Algorithm SHA256).Hash
        $linSize = (Get-Item $linuxLauncher).Length.ToString('N0')
        $linHash = (Get-FileHash $linuxLauncher -Algorithm SHA256).Hash
        $readme = [regex]::Replace($readme,
            '(\| \*\*Windows\*\* \| \[[^\]]+\]\([^)]+\) \| )[\d,]+ bytes( \| `)[0-9A-Fa-f]+(` \|)',
            { param($x) $x.Groups[1].Value + $winSize + ' bytes' + $x.Groups[2].Value + $winHash + $x.Groups[3].Value })
        $readme = [regex]::Replace($readme,
            '(\| \*\*Linux\*\* \| \[[^\]]+\]\([^)]+\) \| )[\d,]+ bytes( \| `)[0-9A-Fa-f]+(` \|)',
            { param($x) $x.Groups[1].Value + $linSize + ' bytes' + $x.Groups[2].Value + $linHash + $x.Groups[3].Value })
        [IO.File]::WriteAllText($readmePath, $readme, $utf8)
    }

    git add downloads README.md
    if (git status --porcelain) {
        if (-not $Message) { $Message = "chore: publish launcher $appVer" }
        git commit -m $Message
        git push origin main
    } else {
        Write-Host 'Repo downloads/ khong doi.'
    }
} finally {
    Pop-Location
}

# Release: tao tag v<version> neu chua co, upload/thay 2 JAR.
& $gh auth status *> $null
if ($LASTEXITCODE -ne 0) { throw "gh chua dang nhap. Chay: `"$gh`" auth login --web --git-protocol https" }
$tag = "v$Version"
& $gh release view $tag --repo $RepoSlug *> $null
if ($LASTEXITCODE -eq 0) {
    & $gh release upload $tag $sodiumJar $vulkanJar --clobber --repo $RepoSlug
} else {
    & $gh release create $tag $sodiumJar $vulkanJar --title $tag --notes "Valkaw client $tag" --repo $RepoSlug --latest
}
if ($LASTEXITCODE -ne 0) { throw "gh release loi (exit $LASTEXITCODE)" }
& $gh release edit $tag --latest --repo $RepoSlug | Out-Null

Write-Host "OK  launcher $appVer -> downloads/; client v$Version -> https://github.com/$RepoSlug/releases/tag/$tag"
