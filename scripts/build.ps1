param([switch]$TestOnly)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$projectRoot = Split-Path $PSScriptRoot -Parent
Set-Location $projectRoot
$releaseVersion = ConvertFrom-StringData (Get-Content version.properties -Raw)
$versionName = $releaseVersion.versionName
$versionCode = $releaseVersion.versionCode
$apkPath = "dist/Kiosk-$versionName.apk"
$jdkRoot = (Get-ChildItem "$projectRoot/.tooling/jdk" -Directory | Select-Object -First 1).FullName
$buildTools = (Get-ChildItem "$projectRoot/.tooling/build-tools-35.0.0" -Directory | Select-Object -First 1).FullName
$platform = (Get-ChildItem "$projectRoot/.tooling/platforms-android-35" -Directory | Select-Object -First 1).FullName
$androidJar = Join-Path $platform 'android.jar'
$env:JAVA_HOME = $jdkRoot
$env:PATH = "$jdkRoot/bin;$env:PATH"
function Run([string]$Program, [string[]]$Arguments) {
    & $Program @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Program failed ($LASTEXITCODE)" }
}
New-Item -ItemType Directory -Force build/core-tests | Out-Null
$core = @('app/src/main/java/il/co/kiosk/PinCrypto.java', 'app/src/main/java/il/co/kiosk/UrlPolicy.java', 'app/src/main/java/il/co/kiosk/AttemptPolicy.java', 'tests/CoreTests.java')
Run "$jdkRoot/bin/javac.exe" (@('-encoding','UTF-8','--release','8','-d','build/core-tests') + $core)
Run "$jdkRoot/bin/java.exe" @('-cp','build/core-tests','il.co.kiosk.CoreTests')
Run 'node' @('tests/resources.mjs')
if ($TestOnly) { exit 0 }
New-Item -ItemType Directory -Force build/generated,build/classes,build/dex,dist,.signing | Out-Null
Run "$buildTools/aapt2.exe" @('compile','--dir','app/src/main/res','-o','build/resources.zip')
$manifest = (Get-Content app/src/main/AndroidManifest.xml -Raw).Replace('<manifest ', '<manifest package="il.co.kiosk" ')
[IO.File]::WriteAllText("$projectRoot/build/AndroidManifest.xml", $manifest)
Run "$buildTools/aapt2.exe" @('link','-o','build/resources.apk','-I',$androidJar,'--manifest','build/AndroidManifest.xml','--java','build/generated','--version-code',$versionCode,'--version-name',$versionName,'build/resources.zip')
$sources = @(Get-ChildItem app/src/main/java,build/generated -Recurse -Filter '*.java' | ForEach-Object { '"' + $_.FullName.Replace('\','/') + '"' })
[IO.File]::WriteAllLines("$projectRoot/build/sources.txt", $sources)
Run "$jdkRoot/bin/javac.exe" @('-encoding','UTF-8','--release','8','-classpath',$androidJar,'-d','build/classes','@build/sources.txt')
Run "$jdkRoot/bin/jar.exe" @('cf','build/classes.jar','-C','build/classes','.')
Run "$jdkRoot/bin/java.exe" @('-cp',"$buildTools/lib/d8.jar",'com.android.tools.r8.D8','--release','--min-api','28','--lib',$androidJar,'--output','build/dex','build/classes.jar')
Copy-Item build/resources.apk build/unsigned.apk -Force
Run "$jdkRoot/bin/jar.exe" @('--update','--file','build/unsigned.apk','--date=2026-01-01T00:00:00Z','-C','build/dex','classes.dex')
Run "$buildTools/zipalign.exe" @('-f','-p','4','build/unsigned.apk','build/aligned.apk')
if (!(Test-Path .signing/release.jks)) {
    $random = New-Object byte[] 32
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    $rng.GetBytes($random); $rng.Dispose()
    $secret = [Convert]::ToBase64String($random)
    [IO.File]::WriteAllText("$projectRoot/.signing/password.txt",$secret)
    Run "$jdkRoot/bin/keytool.exe" @('-genkeypair','-keystore','.signing/release.jks','-storetype','JKS','-storepass:file','.signing/password.txt','-keypass:file','.signing/password.txt','-alias','kiosk','-keyalg','RSA','-keysize','3072','-validity','10000','-dname','CN=Kiosk Local Release, O=Kiosk, C=IL')
}
Run "$jdkRoot/bin/java.exe" @('-jar',"$buildTools/lib/apksigner.jar",'sign','--ks','.signing/release.jks','--ks-key-alias','kiosk','--ks-pass','file:.signing/password.txt','--out',$apkPath,'build/aligned.apk')
Run "$jdkRoot/bin/java.exe" @('-jar',"$buildTools/lib/apksigner.jar",'verify','--verbose',$apkPath)
Run "$buildTools/zipalign.exe" @('-c','-p','4',$apkPath)
$hash = (Get-FileHash $apkPath -Algorithm SHA256).Hash.ToLower()
[IO.File]::WriteAllText("$projectRoot/dist/SHA256SUMS.txt", "$hash  Kiosk-$versionName.apk`n")
Write-Output "APK ready: $projectRoot/$apkPath"
