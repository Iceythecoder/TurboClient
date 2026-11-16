$ErrorActionPreference='Stop'
$selectedJar=Join-Path $PSScriptRoot ((Get-Content -LiteralPath (Join-Path $PSScriptRoot 'release-current.txt') -Raw).Trim())
$selectedClient=Join-Path $PSScriptRoot ((Get-Content -LiteralPath (Join-Path $PSScriptRoot 'release-runtime.txt') -Raw).Trim())
$javaViewer=Join-Path $env:LOCALAPPDATA 'Orion\jre\bin\javaw.exe'
$viewerArgs=@('"-Dowl.project.root='+$PSScriptRoot+'"','"-Dlogback.configurationFile='+(Join-Path $PSScriptRoot 'prototype-logback.xml')+'"','-cp','"'+$selectedJar+';'+$selectedClient+'"','owl.nh.ScriptedFightPreview')
Start-Process -FilePath $javaViewer -ArgumentList $viewerArgs -WorkingDirectory $PSScriptRoot
