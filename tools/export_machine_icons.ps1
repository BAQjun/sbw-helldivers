param([string]$Source,[string]$OutputDirectory)
$ErrorActionPreference='Stop'
Add-Type -AssemblyName PresentationCore,WindowsBase
[xml]$document=Get-Content -LiteralPath $Source -Raw
$mapping=@{bioprocessor='MissionProcessors';refinery='MissionIconIndustrialcomplex'}
foreach($machine in $mapping.Keys) {
    $template=$document.DocumentElement.ChildNodes | Where-Object {$_.GetAttribute('Key','http://schemas.microsoft.com/winfx/2006/xaml') -eq ('DataTemplate.Icon.EnumType.'+$mapping[$machine])}
    if(!$template){throw "Missing native icon $machine"}
    $canvas=$template.Viewbox.Canvas
    foreach($marker in @($false,$true)) {
        $visual=New-Object System.Windows.Media.DrawingVisual
        $context=$visual.RenderOpen()
        $glyph=[System.Windows.Media.Brushes]::White
        if(!$marker){
            $context.DrawGeometry(([System.Windows.Media.BrushConverter]::new().ConvertFromString('#F5F3E8')),$null,[System.Windows.Media.Geometry]::Parse('M64,2 L119,34 119,94 64,126 9,94 9,34 Z'))
            $glyph=[System.Windows.Media.BrushConverter]::new().ConvertFromString('#101719')
        }
        $scale=83.0/[Math]::Max([double]$canvas.Width,[double]$canvas.Height)
        $context.PushTransform([System.Windows.Media.TranslateTransform]::new((128-[double]$canvas.Width*$scale)/2,(128-[double]$canvas.Height*$scale)/2))
        $context.PushTransform([System.Windows.Media.ScaleTransform]::new($scale,$scale))
        foreach($path in $canvas.Path) {
            $context.PushTransform([System.Windows.Media.TranslateTransform]::new([double]$path.GetAttribute('Canvas.Left'),[double]$path.GetAttribute('Canvas.Top')))
            $geometry=[System.Windows.Media.Geometry]::Parse($path.Data)
            $fill=$null;$pen=$null
            if($path.HasAttribute('Fill') -and $path.Fill -ne '{x:Null}'){$fill=$glyph}
            if($path.HasAttribute('Stroke')){$pen=[System.Windows.Media.Pen]::new($glyph,[double]$path.StrokeThickness)}
            $context.DrawGeometry($fill,$pen,$geometry)
            $context.Pop()
        }
        $context.Pop();$context.Pop();$context.Close()
        $bitmap=[System.Windows.Media.Imaging.RenderTargetBitmap]::new(128,128,96,96,[System.Windows.Media.PixelFormats]::Pbgra32)
        $bitmap.Render($visual)
        $encoder=[System.Windows.Media.Imaging.PngBitmapEncoder]::new()
        $encoder.Frames.Add([System.Windows.Media.Imaging.BitmapFrame]::Create($bitmap))
        $suffix=if($marker){'_marker'}else{''}
        $stream=[System.IO.File]::Create((Join-Path $OutputDirectory ($machine+$suffix+'.png')))
        try{$encoder.Save($stream)}finally{$stream.Dispose()}
    }
}
