# Native vector source, rendered with Windows WPF. Run with powershell.exe -STA.
$ErrorActionPreference='Stop'
Add-Type -AssemblyName PresentationCore,PresentationFramework,WindowsBase
$repo=Split-Path $PSScriptRoot -Parent
[xml]$svg=Get-Content -LiteralPath (Join-Path $repo 'art/icons/orbital/orbital_ems_strike.svg') -Raw
$canvas=New-Object System.Windows.Controls.Canvas
$canvas.Width=256;$canvas.Height=256
foreach($node in $svg.svg.ChildNodes){
 if($node.LocalName -eq 'path'){
  $shape=New-Object System.Windows.Shapes.Path
  $shape.Fill=[System.Windows.Media.BrushConverter]::new().ConvertFromString($node.fill)
  $shape.Data=[System.Windows.Media.Geometry]::Parse($node.d)
 }else{
  $shape=New-Object System.Windows.Shapes.Rectangle
  $shape.Width=[double]$node.width;$shape.Height=[double]$node.height
  $shape.Stroke=[System.Windows.Media.BrushConverter]::new().ConvertFromString($node.stroke)
  $shape.StrokeThickness=[double]$node.'stroke-width'
  [System.Windows.Controls.Canvas]::SetLeft($shape,[double]$node.x)
  [System.Windows.Controls.Canvas]::SetTop($shape,[double]$node.y)
 }
 $canvas.Children.Add($shape)|Out-Null
}
$canvas.LayoutTransform=[System.Windows.Media.ScaleTransform]::new(.25,.25)
$canvas.Measure([System.Windows.Size]::new(64,64));$canvas.Arrange([System.Windows.Rect]::new(0,0,64,64));$canvas.UpdateLayout()
$bitmap=[System.Windows.Media.Imaging.RenderTargetBitmap]::new(64,64,96,96,[System.Windows.Media.PixelFormats]::Pbgra32)
$bitmap.Render($canvas)
$encoder=[System.Windows.Media.Imaging.PngBitmapEncoder]::new();$encoder.Frames.Add([System.Windows.Media.Imaging.BitmapFrame]::Create($bitmap))
$stream=[IO.File]::Create((Join-Path $repo 'src/main/resources/assets/sbw_helldiver/textures/item/orbital_ems_strike_stratagem.png'))
try{$encoder.Save($stream)}finally{$stream.Dispose()}

# Use the same frame inset and thickness as defensive stratagem cards.
& (Join-Path $PSScriptRoot "normalize_red_stratagem_frames.ps1")
