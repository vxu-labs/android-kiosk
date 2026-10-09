# Creates an explicit, reviewable public source snapshot. Never copies local DIP history or keys.
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
Set-Location $projectRoot
$version = (ConvertFrom-StringData (Get-Content version.properties -Raw)).versionName
$files = @('.gitignore','README.md','README.he.md','VERIFICATION.md','LICENSE','build.gradle','settings.gradle','version.properties','app/build.gradle')
foreach ($directory in @('app/src','scripts','tests','docs')) {
    $files += Get-ChildItem -LiteralPath $directory -File -Recurse | ForEach-Object { $_.FullName.Substring($projectRoot.Length + 1).Replace('\','/') }
}
$tree = @()
foreach ($relative in ($files | Sort-Object -Unique)) {
    if ($relative -match '(^|/)(\.dip|\.signing|\.tooling|build|dist)(/|$)' -or $relative -match '\.(jks|apk|zip)$') { throw "Disallowed export path: $relative" }
    $tree += @{path=$relative;mode='100644';type='blob';content=[IO.File]::ReadAllText((Join-Path $projectRoot $relative))}
}
New-Item -ItemType Directory -Force build | Out-Null
$json = @{tree=$tree} | ConvertTo-Json -Depth 5
[IO.File]::WriteAllText("$projectRoot/build/public-tree.json",$json)
$tree.path | Set-Content "build/public-files-$version.txt"
Write-Output "Prepared $($tree.Count) public source files in build/public-tree.json; no network write performed."
