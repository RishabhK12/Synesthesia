param(
    [string]$Sdk = "$env:LOCALAPPDATA\Android\Sdk",
    [switch]$RunTests,
    [switch]$SideBySide
)
$ErrorActionPreference = 'Stop'
$env:ANDROID_HOME = $Sdk
$env:GRADLE_USER_HOME = Join-Path $PSScriptRoot '.gradle-user'
$variant = if ($SideBySide) { 'SideBySideDebug' } else { 'StandardDebug' }
$flavor = if ($SideBySide) { 'sideBySide' } else { 'standard' }
$tasks = @("assemble$variant")
if ($RunTests) { $tasks += "test${variant}UnitTest" }
Push-Location $PSScriptRoot
try {
    & .\gradlew.bat @tasks --no-daemon
    if ($LASTEXITCODE -ne 0) { throw 'Android build failed' }
    $source = Get-ChildItem -LiteralPath "build\outputs\apk\$flavor\debug" -Filter '*.apk' | Select-Object -First 1
    if (!$source) { throw 'APK was not produced' }
    $destination = Join-Path $PSScriptRoot $(if ($SideBySide) { 'build\SoundDirectionTest.apk' } else { 'build\BehindAlert.apk' })
    Copy-Item -LiteralPath $source.FullName -Destination $destination -Force
    Write-Output "APK ready: $destination"
} finally { Pop-Location }
