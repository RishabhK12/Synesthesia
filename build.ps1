param(
    [string]$Sdk = "$env:LOCALAPPDATA\Android\Sdk",
    [switch]$RunTests,
    [switch]$SideBySide
)
$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath($PSScriptRoot)
$buildRoot = Join-Path $projectRoot 'build'
$work = Join-Path $buildRoot 'windows'
$tools = Join-Path $Sdk 'build-tools\35.0.0'
$androidJar = Join-Path $Sdk 'platforms\android-34\android.jar'
if (-not (Test-Path -LiteralPath $androidJar)) { throw "Android SDK platform 34 missing in $Sdk" }
if (-not (Test-Path -LiteralPath $tools)) { throw "Android build-tools 35.0.0 missing in $Sdk" }
function Invoke-Native([string]$File, [string[]]$Arguments) {
    & $File @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$File failed with exit code $LASTEXITCODE" }
}
if (Test-Path -LiteralPath $buildRoot) {
    if ((Get-Item -LiteralPath $buildRoot).Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'Build directory must not be a link' }
}
if (Test-Path -LiteralPath $work) {
    $resolvedWork = (Resolve-Path -LiteralPath $work).Path
    if ($resolvedWork -ne [IO.Path]::GetFullPath((Join-Path $projectRoot 'build\windows'))) { throw 'Unexpected build path' }
    if ((Get-Item -LiteralPath $work).Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'Build workspace must not be a link' }
    Remove-Item -LiteralPath $resolvedWork -Recurse -Force
}
$classes = Join-Path $work 'classes'
$dex = Join-Path $work 'dex'
New-Item -ItemType Directory -Path $classes,$dex -Force | Out-Null
$sources = @(Get-ChildItem -LiteralPath (Join-Path $projectRoot 'src') -Filter '*.java' -Recurse | ForEach-Object FullName)
Invoke-Native 'javac' (@('--release','8','-encoding','UTF-8','-classpath',$androidJar,'-d',$classes) + $sources)
if (-not (Test-Path -LiteralPath (Join-Path $classes 'com\hackgt\behindalert\DiagnosticActivity.class'))) { throw 'Compiler did not produce the diagnostic activity' }
if (-not (Test-Path -LiteralPath (Join-Path $classes 'com\hackgt\behindalert\CompassActivity.class'))) { throw 'Compiler did not produce the compass activity' }
if ($RunTests) {
    $tests = Join-Path $work 'tests'
    New-Item -ItemType Directory -Path $tests -Force | Out-Null
    $testSources = @(Get-ChildItem -LiteralPath (Join-Path $projectRoot 'test') -Filter '*.java' | ForEach-Object FullName)
    Invoke-Native 'javac' (@('--release','8','-encoding','UTF-8','-classpath',$classes,'-d',$tests) + $testSources)
    foreach ($testName in @('DspTest','DirTest','DiagnosticTest','CompassTest')) {
        Invoke-Native 'java' @('-cp',"$classes;$tests",$testName)
    }
}
$classFiles = @(Get-ChildItem -LiteralPath $classes -Filter '*.class' -Recurse | ForEach-Object FullName)
Invoke-Native (Join-Path $tools 'd8.bat') (@('--min-api','24','--lib',$androidJar,'--output',$dex) + $classFiles)
$unsigned = Join-Path $work 'unsigned.apk'
$manifest = Join-Path $projectRoot 'AndroidManifest.xml'
if ($SideBySide) {
    $manifestText = Get-Content -LiteralPath $manifest -Raw
    $manifestText = $manifestText.Replace('package="com.hackgt.behindalert"', 'package="com.hackgt.behindalert.diagnostics"')
    $manifestText = $manifestText.Replace('android:name=".MainActivity"', 'android:name="com.hackgt.behindalert.MainActivity"')
    $manifestText = $manifestText.Replace('android:name=".DiagnosticActivity"', 'android:name="com.hackgt.behindalert.DiagnosticActivity"')
    $manifestText = $manifestText.Replace('android:name=".CompassActivity"', 'android:name="com.hackgt.behindalert.CompassActivity"')
    $manifestText = $manifestText.Replace('android:label="Sound Direction"', 'android:label="Sound Direction Test"')
    $manifest = Join-Path $work 'AndroidManifest.xml'
    [IO.File]::WriteAllText($manifest, $manifestText)
}
Invoke-Native (Join-Path $tools 'aapt2.exe') @('link','-o',$unsigned,'--manifest',$manifest,'-I',$androidJar,'--min-sdk-version','24','--target-sdk-version','34','--debug-mode')
Invoke-Native 'jar' @('uf',$unsigned,'-C',$dex,'classes.dex')
$aligned = Join-Path $work 'aligned.apk'
Invoke-Native (Join-Path $tools 'zipalign.exe') @('-f','4',$unsigned,$aligned)
$key = Join-Path $projectRoot 'debug.keystore'
if (-not (Test-Path -LiteralPath $key)) {
    Invoke-Native 'keytool' @('-genkeypair','-keystore',$key,'-storepass','android','-keypass','android','-alias','key','-keyalg','RSA','-keysize','2048','-validity','10000','-dname','CN=Behind Alert, O=HackGT')
}
$apk = Join-Path $buildRoot $(if ($SideBySide) { 'SoundDirectionTest.apk' } else { 'BehindAlert.apk' })
Invoke-Native (Join-Path $tools 'apksigner.bat') @('sign','--ks',$key,'--ks-pass','pass:android','--key-pass','pass:android','--out',$apk,$aligned)
Invoke-Native (Join-Path $tools 'apksigner.bat') @('verify','--verbose',$apk)
Write-Output "APK ready: $apk"
