$ErrorActionPreference = 'Stop'
$projectRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
if ($projectRoot -ne 'D:\Project\sbw_helldiver') { throw 'Unexpected project root' }
$sourceWorld = Join-Path $projectRoot 'run\saves\新的世界'
$testWorld = Join-Path $projectRoot 'run-client-test\saves\IntegrationTest'
$backup = Join-Path $projectRoot ('.codex\backups\client_test_' + (Get-Date -Format 'yyyyMMdd_HHmmss'))
if (-not (Test-Path -LiteralPath (Join-Path $sourceWorld 'level.dat'))) { throw 'Test source world is missing' }
if (Test-Path -LiteralPath $testWorld) {
    $resolved = (Resolve-Path -LiteralPath $testWorld).Path
    if ($resolved -ne 'D:\Project\sbw_helldiver\run-client-test\saves\IntegrationTest' -or
        -not $backup.StartsWith('D:\Project\sbw_helldiver\.codex\backups\')) { throw 'Unsafe test-world move' }
    Move-Item -LiteralPath $resolved -Destination $backup
}
Copy-Item -LiteralPath $sourceWorld -Destination $testWorld -Recurse
$lock = Join-Path $testWorld 'session.lock'
if (Test-Path -LiteralPath $lock) { Remove-Item -LiteralPath $lock }
Write-Output 'Prepared an isolated clean client test world; the original world is unchanged.'
