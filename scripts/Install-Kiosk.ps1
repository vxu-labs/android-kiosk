# Installs on exactly one explicitly connected/authorized device. Never resets a device.
$ErrorActionPreference = 'Stop'
$bundleRoot = $PSScriptRoot
$adb = Join-Path $bundleRoot 'platform-tools/adb.exe'
$apks = @(Get-ChildItem -LiteralPath $bundleRoot -Filter 'Kiosk-*.apk')
if (!(Test-Path $adb) -or $apks.Count -ne 1) { throw 'Extract the complete setup ZIP into a new folder before running this script.' }
$apk = $apks[0].FullName
Write-Host 'Kiosk setup for Samsung Galaxy Tab S6 Lite' -ForegroundColor Cyan
Write-Host 'Connect the tablet using USB, enable USB debugging, then accept the computer on the tablet.'
Write-Host 'This script installs Kiosk and configures Device Owner. It does not erase data.'
$rows = @(& $adb devices)
$devices = @($rows | Where-Object { $_ -match '^\S+\s+device$' })
if ($devices.Count -ne 1) { $rows | Write-Host; throw 'Connect exactly one authorized Android device and run again.' }
$serial = ($devices[0] -split '\s+')[0]
$model = & $adb -s $serial shell getprop ro.product.model
Write-Host "Selected device: $model ($serial)"
$answer = Read-Host 'Type INSTALL to install and configure this tablet'
if ($answer -cne 'INSTALL') { Write-Host 'Cancelled.'; exit 0 }
& $adb -s $serial install -r $apk
if ($LASTEXITCODE -ne 0) { throw 'APK installation failed. Check the message above.' }
$ownerResult = & $adb -s $serial shell dpm set-device-owner il.co.kiosk/.KioskAdminReceiver 2>&1
$ownerExit = $LASTEXITCODE
$ownerResult | Write-Host
if ($ownerExit -ne 0) {
    Write-Host 'Device Owner was not enabled (or already exists). Read the tablet setup guide.' -ForegroundColor Yellow
    Write-Host 'Existing accounts, secondary users or another device manager may block enrollment. Do not reset without a backup.'
}
& $adb -s $serial shell am start -n il.co.kiosk/.MainActivity
Write-Host 'On the tablet: choose a private PIN, configure the home page, then enable kiosk.'
Write-Host 'Full lock is active ONLY when Kiosk confirms Device Owner and lock task. Preview does not secure Android.'
Read-Host 'Press Enter to close'
