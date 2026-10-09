$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
$version = (ConvertFrom-StringData (Get-Content version.properties -Raw)).versionName
& ./scripts/build.ps1
& ./scripts/package.ps1
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [IO.Compression.ZipFile]::OpenRead((Resolve-Path "dist/Kiosk-Windows-Setup-$version.zip"))
try {
    $names = @($zip.Entries | ForEach-Object { $_.FullName.Replace('\','/') })
    foreach ($name in @("Kiosk-$version.apk",'SHA256SUMS.txt','Install-Kiosk.cmd','Install-Kiosk.ps1','docs/INSTALL-HE.html','README.md','VERIFICATION.md','LICENSE','platform-tools/adb.exe','platform-tools/AdbWinApi.dll','platform-tools/AdbWinUsbApi.dll','platform-tools/NOTICE.txt')) {
        if ($names -notcontains $name) { throw "Missing bundle file: $name" }
    }
    if (@($names | Where-Object { $_ -match 'tests\.apk|\.jks$|password\.txt|\.signing' }).Count -gt 0) { throw 'Private or test files included in distribution' }
    $entry = $zip.Entries | Where-Object { $_.FullName -eq "Kiosk-$version.apk" }
    $stream = $entry.Open()
    $sha = [Security.Cryptography.SHA256]::Create()
    try { $bundledHash = ([BitConverter]::ToString($sha.ComputeHash($stream))).Replace('-','').ToLower() } finally { $stream.Dispose(); $sha.Dispose() }
    if ($bundledHash -ne (Get-FileHash "dist/Kiosk-$version.apk" -Algorithm SHA256).Hash.ToLower()) { throw 'Bundled APK does not match release APK' }
} finally { $zip.Dispose() }
Write-Output 'PASS: installation bundle complete; embedded APK matches release; no signing keys or test APKs'
