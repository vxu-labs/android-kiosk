$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
New-Item -ItemType Directory -Force .tooling | Out-Null
function Fetch([string]$Url, [string]$File, [string]$Hash, [string]$Algorithm) {
    curl.exe -L --fail --silent --show-error $Url -o $File
    if ($LASTEXITCODE -ne 0) { throw "Download failed: $Url" }
    if ((Get-FileHash $File -Algorithm $Algorithm).Hash.ToLower() -ne $Hash.ToLower()) { throw "Checksum mismatch: $File" }
}
$jdk = @(Invoke-RestMethod 'https://api.adoptium.net/v3/assets/latest/17/hotspot?architecture=x64&image_type=jdk&os=windows&vendor=eclipse')[0].binary.package
Fetch $jdk.link '.tooling/jdk.zip' $jdk.checksum 'SHA256'
Expand-Archive .tooling/jdk.zip .tooling/jdk -Force
[xml]$repo = (Invoke-WebRequest 'https://dl.google.com/android/repository/repository2-1.xml').Content
foreach ($path in @('platforms;android-35','build-tools;35.0.0','platform-tools')) {
    $package = @($repo.'sdk-repository'.remotePackage | Where-Object { $_.path -eq $path -and $_.channelRef.ref -eq 'channel-0' })[0]
    $archive = @($package.archives.archive | Where-Object { $_.'host-os' -eq 'windows' -or -not $_.'host-os' })[0]
    $name = $path.Replace(';','-')
    Fetch ('https://dl.google.com/android/repository/' + $archive.complete.url) ".tooling/$name.zip" ([string]$archive.complete.checksum) 'SHA1'
    Expand-Archive ".tooling/$name.zip" ".tooling/$name" -Force
}
Write-Output 'Toolchain ready. Run ./scripts/build.ps1'
