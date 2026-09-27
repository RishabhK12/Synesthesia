param(
    [ValidateSet('com.hackgt.behindalert', 'com.hackgt.behindalert.diagnostics')]
    [string]$Package = 'com.hackgt.behindalert.diagnostics',
    [switch]$Compass
)
$ErrorActionPreference = 'Stop'
$adb = (Get-Command adb -ErrorAction Stop).Source
$dataKind = if ($Compass) { 'compass' } else { 'diagnostics' }
$outDir = Join-Path $PSScriptRoot ($dataKind + '-data\' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Path $outDir -Force | Out-Null
$archive = Join-Path $outDir ('phone-' + $dataKind + '.tar')
$start = [Diagnostics.ProcessStartInfo]::new()
$start.FileName = $adb
$start.Arguments = "exec-out run-as $Package tar -cf - files/$dataKind"
$start.UseShellExecute = $false
$start.CreateNoWindow = $true
$start.RedirectStandardOutput = $true
$start.RedirectStandardError = $true
$process = [Diagnostics.Process]::Start($start)
$errorTask = $process.StandardError.ReadToEndAsync()
$file = [IO.File]::Create($archive)
try { $process.StandardOutput.BaseStream.CopyTo($file) } finally { $file.Dispose() }
$process.WaitForExit()
$errorText = $errorTask.GetAwaiter().GetResult()
$exitCode = $process.ExitCode
$process.Dispose()
if ($exitCode -ne 0) { throw "Could not collect $dataKind recordings: $errorText" }
& tar -xf $archive -C $outDir
if ($LASTEXITCODE -ne 0) { throw "Could not extract $dataKind recordings" }
Write-Output "Collected $dataKind sessions: $outDir\files\$dataKind"
