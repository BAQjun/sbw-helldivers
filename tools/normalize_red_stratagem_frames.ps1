# Align offensive card frames to the actual defensive card, preserving the inner artwork.
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$repo = Split-Path $PSScriptRoot -Parent
$assetRoot = Join-Path $repo 'src/main/resources/assets/sbw_helldiver/textures/item'
$reference = [Drawing.Bitmap]::new((Join-Path $assetRoot 'machine_gun_sentry_stratagem.png'))
try {
    $background = $reference.GetPixel(0, 0).ToArgb()
    $inset = 0
    while ($reference.GetPixel(32, $inset).ToArgb() -eq $background) { $inset++ }
    $thickness = 0
    while ($reference.GetPixel(32, $inset + $thickness).ToArgb() -ne $background) { $thickness++ }
} finally { $reference.Dispose() }
if ($inset -ne 1 -or $thickness -ne 2) { throw 'Unexpected defensive card frame geometry' }
$red = [Drawing.Color]::FromArgb(255, 220, 100, 85)
Get-ChildItem -LiteralPath $assetRoot -Filter '*_stratagem.png' | Where-Object {
    $_.BaseName.StartsWith('orbital_') -or ($_.BaseName.StartsWith('eagle_') -and $_.BaseName -ne 'eagle_rearm_stratagem')
} | ForEach-Object {
    $source = [Drawing.Bitmap]::new($_.FullName)
    try { $bitmap = [Drawing.Bitmap]::new($source) } finally { $source.Dispose() }
    try {
        if ($bitmap.Width -ne 64 -or $bitmap.Height -ne 64) { throw "Unexpected card size: $($_.Name)" }
        $bg = $bitmap.GetPixel(0, 0)
        $oldEnd = -1
        $started = $false
        for ($y = 0; $y -lt 10; $y++) {
            if ($bitmap.GetPixel(32, $y).ToArgb() -ne $bg.ToArgb()) { $started = $true; $oldEnd = $y }
            elseif ($started) { break }
        }
        if (-not $started) { throw "Missing old card frame: $($_.Name)" }
        $changed = $false
        for ($y = 0; $y -lt 64; $y++) { for ($x = 0; $x -lt 64; $x++) {
            $distance = [Math]::Min([Math]::Min($x, 63 - $x), [Math]::Min($y, 63 - $y))
            if ($distance -gt [Math]::Max($oldEnd, $inset + $thickness - 1)) { continue }
            $desired = if ($distance -ge $inset -and $distance -lt $inset + $thickness) { $red } else { $bg }
            if ($bitmap.GetPixel($x, $y).ToArgb() -ne $desired.ToArgb()) {
                $bitmap.SetPixel($x, $y, $desired); $changed = $true
            }
        } }
        if ($changed) { $bitmap.Save($_.FullName, [Drawing.Imaging.ImageFormat]::Png); Write-Output ("Frame aligned: " + $_.Name) }
    } finally { $bitmap.Dispose() }
}
