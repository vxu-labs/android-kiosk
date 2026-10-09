$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
$projectRoot = (Get-Location).Path
$jdkRoot = (Get-ChildItem .tooling/jdk -Directory | Select-Object -First 1).FullName
$buildTools = (Get-ChildItem .tooling/build-tools-35.0.0 -Directory | Select-Object -First 1).FullName
$platform = (Get-ChildItem .tooling/platforms-android-35 -Directory | Select-Object -First 1).FullName
function Run([string]$Program, [string[]]$Arguments) { & $Program @Arguments; if ($LASTEXITCODE -ne 0) { throw "$Program failed" } }
New-Item -ItemType Directory -Force build/android-tests/classes,build/android-tests/dex | Out-Null
Run "$buildTools/aapt2.exe" @('link','-o','build/android-tests/resources.apk','-I',"$platform/android.jar",'--manifest','tests/android/AndroidManifest.xml')
$sources = @(Get-ChildItem tests/android -Filter '*.java' | ForEach-Object {$_.FullName})
Run "$jdkRoot/bin/javac.exe" (@('-encoding','UTF-8','--release','8','-cp',"$platform/android.jar;build/classes",'-d','build/android-tests/classes') + $sources)
Run "$jdkRoot/bin/jar.exe" @('cf','build/android-tests/classes.jar','-C','build/android-tests/classes','.')
Run "$jdkRoot/bin/java.exe" @('-cp',"$buildTools/lib/d8.jar",'com.android.tools.r8.D8','--min-api','28','--lib',"$platform/android.jar",'--classpath','build/classes.jar','--output','build/android-tests/dex','build/android-tests/classes.jar')
Copy-Item build/android-tests/resources.apk build/android-tests/unsigned.apk -Force
Run "$jdkRoot/bin/jar.exe" @('uf','build/android-tests/unsigned.apk','-C','build/android-tests/dex','classes.dex')
Run "$buildTools/zipalign.exe" @('-f','4','build/android-tests/unsigned.apk','build/android-tests/aligned.apk')
Run "$jdkRoot/bin/java.exe" @('-jar',"$buildTools/lib/apksigner.jar",'sign','--ks','.signing/release.jks','--ks-pass','file:.signing/password.txt','--out','build/Kiosk-tests.apk','build/android-tests/aligned.apk')
