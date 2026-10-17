param([switch]$GameTests, [switch]$Online)
$ErrorActionPreference = 'Stop'
$buildRoot = $PSScriptRoot
Set-Location -LiteralPath $buildRoot
if (Test-Path -LiteralPath 'D:\JAVA17\JAVA17 (1)\bin\java.exe') { $env:JAVA_HOME = 'D:\JAVA17\JAVA17 (1)' }
$env:GRADLE_USER_HOME = Join-Path $buildRoot '.codex\gradle-user-home'
$env:TEMP = Join-Path $buildRoot '.codex\tmp'
$env:TMP = $env:TEMP
New-Item -ItemType Directory -Path $env:TEMP -Force | Out-Null
$env:JAVA_TOOL_OPTIONS = (($env:JAVA_TOOL_OPTIONS + ' -Djava.io.tmpdir=' + $env:TEMP).Trim())
$gradleArgs = @('build', '--console=plain')
if (-not $Online) { $gradleArgs += @('--offline', '-Dnet.minecraftforge.gradle.check.certs=false') }
if ($GameTests) { $gradleArgs += 'runGameTestServer' }
& (Join-Path $buildRoot 'gradlew.bat') @gradleArgs
exit $LASTEXITCODE
