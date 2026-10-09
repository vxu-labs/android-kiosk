$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
$version = (ConvertFrom-StringData (Get-Content version.properties -Raw)).versionName
$bundle = "build/windows-setup-$version"
New-Item -ItemType Directory -Force $bundle | Out-Null
Copy-Item "dist/Kiosk-$version.apk",dist/SHA256SUMS.txt,README.md,README.he.md,VERIFICATION.md,LICENSE,scripts/Install-Kiosk.ps1,scripts/Install-Kiosk.cmd -Destination $bundle -Force
Copy-Item .tooling/platform-tools/platform-tools -Destination $bundle -Recurse -Force
New-Item -ItemType Directory -Force "$bundle/docs" | Out-Null
Copy-Item docs/INSTALL-HE.html -Destination "$bundle/docs" -Force
if (Test-Path "$bundle/INSTALL-HE.html") { Remove-Item -LiteralPath "$bundle/INSTALL-HE.html" }
Compress-Archive -Path "$bundle/*" -DestinationPath "dist/Kiosk-Windows-Setup-$version.zip" -Force
Write-Output "Created dist/Kiosk-Windows-Setup-$version.zip"
