param([string]$Source,[string]$OutputDirectory)
$ErrorActionPreference='Stop'
Add-Type -AssemblyName PresentationCore,WindowsBase
[xml]$document=Get-Content -LiteralPath $Source -Raw
foreach($tier in @('Common','Rare','Super')) {
    $template=$document.DocumentElement.ChildNodes | Where-Object {$_.GetAttribute('Key','http://schemas.microsoft.com/winfx/2006/xaml') -eq ('DataTemplate.Icon.SampleGroupType.'+$tier)}
    $canvas=$template.Viewbox.Canvas
    $scale=112.0/[Math]::Max([double]$canvas.Width,[double]$canvas.Height)
    $visual=New-Object System.Windows.Media.DrawingVisual
    $context=$visual.RenderOpen()
    $context.PushTransform((New-Object System.Windows.Media.TranslateTransform ((128-[double]$canvas.Width*$scale)/2),((128-[double]$canvas.Height*$scale)/2)))
    $context.PushTransform((New-Object System.Windows.Media.ScaleTransform $scale,$scale))
    foreach($path in $canvas.Path) {
        $geometry=[System.Windows.Media.Geometry]::Parse($path.Data)
        $brush=New-Object System.Windows.Media.SolidColorBrush ([System.Windows.Media.ColorConverter]::ConvertFromString($path.Fill))
        $context.DrawGeometry($brush,$null,$geometry)
    }
    $context.Close()
    $bitmap=New-Object System.Windows.Media.Imaging.RenderTargetBitmap 128,128,96,96,([System.Windows.Media.PixelFormats]::Pbgra32)
    $bitmap.Render($visual)
    $encoder=New-Object System.Windows.Media.Imaging.PngBitmapEncoder
    $encoder.Frames.Add([System.Windows.Media.Imaging.BitmapFrame]::Create($bitmap))
    $target=Join-Path $OutputDirectory ($tier.ToLowerInvariant()+'_sample.png')
    $stream=[System.IO.File]::Create($target)
    try {$encoder.Save($stream)} finally {$stream.Dispose()}
}
