param([switch]$GameTests, [switch]$Online)
$ErrorActionPreference = 'Stop'
$buildRoot = $PSScriptRoot
Set-Location -LiteralPath $buildRoot
# NeoForge 1.21.1 requires Java 21. Respect an explicitly selected JAVA_HOME.
if (-not $env:JAVA_HOME -and (Test-Path -LiteralPath 'D:\Codex\tools\zulu21.54.107-ca-jdk21.0.13-win_x64\bin\java.exe')) {
    $env:JAVA_HOME = 'D:\Codex\tools\zulu21.54.107-ca-jdk21.0.13-win_x64'
}
$env:GRADLE_USER_HOME = Join-Path $buildRoot '.codex\gradle-user-home'
$env:TEMP = Join-Path $buildRoot '.codex\tmp'
$env:TMP = $env:TEMP
New-Item -ItemType Directory -Path $env:TEMP -Force | Out-Null
$env:JAVA_TOOL_OPTIONS = (($env:JAVA_TOOL_OPTIONS + ' -Djava.io.tmpdir=' + $env:TEMP).Trim())
$gradleArgs = @('build', '--console=plain')
if (-not $Online) { $gradleArgs += @('--offline') }
if ($GameTests) { $gradleArgs += @('runGameTestServer', '-PportTests') }
& (Join-Path $buildRoot 'gradlew.bat') @gradleArgs
exit $LASTEXITCODE
