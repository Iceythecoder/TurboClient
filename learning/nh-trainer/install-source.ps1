$ErrorActionPreference = 'Stop'
$manifest = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'source-manifest.json') -Raw | ConvertFrom-Json
$upstream = Join-Path $PSScriptRoot 'upstream'
$boundary = [System.IO.Path]::GetFullPath($upstream) + [System.IO.Path]::DirectorySeparatorChar
foreach ($entry in $manifest.sha256.PSObject.Properties) {
    $target = [System.IO.Path]::GetFullPath((Join-Path $upstream $entry.Name))
    if (-not $target.StartsWith($boundary,[System.StringComparison]::OrdinalIgnoreCase)) { throw 'Source entry escapes the upstream directory.' }
    if ((Test-Path -LiteralPath $target) -and (Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash.ToLowerInvariant() -eq $entry.Value) { continue }
    if (Test-Path -LiteralPath $target) { throw ('Existing upstream file differs: ' + $entry.Name + '. Preserve your edits before reinstalling.') }
    [System.IO.Directory]::CreateDirectory([System.IO.Path]::GetDirectoryName($target)) | Out-Null
    $temporary = $target + '.' + [Guid]::NewGuid().ToString('N') + '.download'
    try {
        $uri = 'https://raw.githubusercontent.com/NotCoco/nh-trainer/' + $manifest.commit + '/' + $entry.Name
        Invoke-WebRequest -Uri $uri -OutFile $temporary -UseBasicParsing
        if ((Get-FileHash -LiteralPath $temporary -Algorithm SHA256).Hash.ToLowerInvariant() -ne $entry.Value) { throw ('Downloaded source hash mismatch: ' + $entry.Name) }
        Move-Item -LiteralPath $temporary -Destination $target
    } finally { if (Test-Path -LiteralPath $temporary) { Remove-Item -LiteralPath $temporary -Force } }
}
Write-Output ('NH Trainer source installed at pinned commit ' + $manifest.commit)
