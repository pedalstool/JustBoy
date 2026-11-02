param([string]$AndroidSdk = $env:ANDROID_HOME)
$ErrorActionPreference = 'Stop'
if (-not $AndroidSdk) { throw 'Pass -AndroidSdk or set ANDROID_HOME.' }
$resolvedSdk = (Resolve-Path $AndroidSdk).Path.Replace('\','/')
Set-Content (Join-Path $PSScriptRoot 'local.properties') "sdk.dir=$resolvedSdk"
& (Join-Path $PSScriptRoot 'gradlew.bat') -p $PSScriptRoot testDebugUnitTest assembleRelease --no-daemon
if ($LASTEXITCODE -ne 0) { throw 'Build or tests failed.' }
Write-Host 'APK: app/build/outputs/apk/release/app-release.apk'
