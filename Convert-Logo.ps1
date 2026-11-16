param([string]$SourcePath = (Join-Path $PSScriptRoot 'assets\turbo-logo.png'))
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$assetDirectory = Join-Path $PSScriptRoot 'assets'
New-Item -ItemType Directory -Path $assetDirectory -Force | Out-Null
$sourceImage = [Drawing.Image]::FromFile((Resolve-Path -LiteralPath $SourcePath).Path)
$frames = @()
try {
    foreach ($size in @(16,24,32,48,64,128,256)) {
        $bitmap = New-Object Drawing.Bitmap($size,$size)
        $graphics = [Drawing.Graphics]::FromImage($bitmap)
        $stream = New-Object IO.MemoryStream
        try {
            $graphics.Clear([Drawing.Color]::Transparent)
            $graphics.InterpolationMode = [Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
            $graphics.PixelOffsetMode = [Drawing.Drawing2D.PixelOffsetMode]::HighQuality
            $scale = [Math]::Min($size / $sourceImage.Width,$size / $sourceImage.Height)
            $width = [int][Math]::Round($sourceImage.Width * $scale)
            $height = [int][Math]::Round($sourceImage.Height * $scale)
            $graphics.DrawImage($sourceImage,[int](($size-$width)/2),[int](($size-$height)/2),$width,$height)
            $bitmap.Save($stream,[Drawing.Imaging.ImageFormat]::Png)
            $frames += @{ Size=$size; Bytes=$stream.ToArray() }
        } finally { $stream.Dispose(); $graphics.Dispose(); $bitmap.Dispose() }
    }
} finally { $sourceImage.Dispose() }
$output = New-Object IO.MemoryStream
$writer = New-Object IO.BinaryWriter($output)
try {
    $writer.Write([uint16]0); $writer.Write([uint16]1); $writer.Write([uint16]$frames.Count)
    $offset = 6 + 16 * $frames.Count
    foreach ($frame in $frames) {
        $dimension = if ($frame.Size -eq 256) { 0 } else { $frame.Size }
        $writer.Write([byte]$dimension); $writer.Write([byte]$dimension)
        $writer.Write([byte]0); $writer.Write([byte]0)
        $writer.Write([uint16]1); $writer.Write([uint16]32)
        $writer.Write([uint32]$frame.Bytes.Length); $writer.Write([uint32]$offset)
        $offset += $frame.Bytes.Length
    }
    foreach ($frame in $frames) { $writer.Write([byte[]]$frame.Bytes) }
    $writer.Flush()
    [IO.File]::WriteAllBytes((Join-Path $assetDirectory 'turbo-client.ico'),$output.ToArray())
} finally { $writer.Dispose(); $output.Dispose() }
Write-Output 'Created assets\turbo-client.ico (16-256 px).'
