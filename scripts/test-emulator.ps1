param([string]$Serial = 'emulator-5556')
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
if ($Serial -notmatch '^emulator-\d+$') { throw 'These destructive test-data checks are restricted to a disposable emulator.' }
$adb = '.tooling/platform-tools/platform-tools/adb.exe'
$version = (ConvertFrom-StringData (Get-Content version.properties -Raw)).versionName
if ((& $adb -s $Serial shell getprop ro.kernel.qemu) -ne '1') { throw 'Refusing to run on a non-emulator device.' }
if ((& $adb -s $Serial shell getprop sys.boot_completed) -ne '1') { throw 'Wait for emulator boot to finish.' }
& $adb -s $Serial install --no-incremental -r "dist/Kiosk-$version.apk"
if ($LASTEXITCODE -ne 0) { throw 'App install failed' }
& $adb -s $Serial install --no-incremental -r build/Kiosk-tests.apk
if ($LASTEXITCODE -ne 0) { throw 'Test install failed' }
$smoke = & $adb -s $Serial shell am instrument -w il.co.kiosk.tests/il.co.kiosk.SmokeTests
$smoke | Write-Output
if (($smoke -join "`n") -notmatch 'PASS: 19 Android smoke checks') { throw 'Smoke tests failed. Use a fresh emulator without existing Device Owner.' }
$locale = & $adb -s $Serial shell am instrument -w il.co.kiosk.tests/il.co.kiosk.LocaleTests
$locale | Write-Output
if (($locale -join "`n") -notmatch 'PASS: 18 language and footer checks') { throw 'Language and footer checks failed.' }
$appearance = & $adb -s $Serial shell am instrument -w il.co.kiosk.tests/il.co.kiosk.AppearanceTests
$appearance | Write-Output
if (($appearance -join "`n") -notmatch 'PASS: 27 appearance and menu checks') { throw 'Appearance and menu checks failed.' }
& $adb -s $Serial shell dpm set-device-owner il.co.kiosk/.KioskAdminReceiver
if ($LASTEXITCODE -ne 0) { throw 'Test Device Owner provisioning failed' }
# Production policy disables ADB. The instrumentation saves its result then restores test connectivity.
& $adb -s $Serial shell am instrument -w il.co.kiosk.tests/il.co.kiosk.OwnerTests
Start-Sleep -Seconds 3
& $adb -s $Serial root
Start-Sleep -Seconds 2
$owner = & $adb -s $Serial shell cat /sdcard/Android/data/il.co.kiosk/files/owner-tests.txt
$owner | Write-Output
if (($owner -join "`n") -notmatch 'PASS: 20 Device Owner checks') { throw 'Device Owner tests failed; inspect the emulator.' }
New-Item -ItemType Directory -Force build/screenshots | Out-Null
& $adb -s $Serial pull /sdcard/Android/data/il.co.kiosk/files/home.png build/screenshots/home.png
& $adb -s $Serial pull /sdcard/Android/data/il.co.kiosk/files/settings.png build/screenshots/settings.png
