$ErrorActionPreference='Stop'
$flameProject=Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $flameProject
$env:JAVA_HOME='D:\JAVA17\JAVA17 (1)'
$env:GRADLE_USER_HOME=Join-Path $flameProject '.codex\gradle-user-home'
$env:TEMP=Join-Path $flameProject '.codex\tmp'
$env:TMP=$env:TEMP
$env:JAVA_TOOL_OPTIONS='-Djava.io.tmpdir='+$env:TEMP
$flameArguments=@('runClient','-PflameSmokeTest','--offline','--console=plain','-Dnet.minecraftforge.gradle.check.certs=false')
& (Join-Path $flameProject 'gradlew.bat') @flameArguments
exit $LASTEXITCODE
