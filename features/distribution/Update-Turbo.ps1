param([string]$InstallPath,[string]$ClientPath,[string]$JavaPath)
$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.Windows.Forms
if(!$InstallPath){$dialog=New-Object Windows.Forms.FolderBrowserDialog;$dialog.Description='Select your existing Turbo Client folder';$dialog.SelectedPath=Join-Path $env:USERPROFILE 'Apps\Turbo Client';if($dialog.ShowDialog() -ne [Windows.Forms.DialogResult]::OK){exit 0};$InstallPath=$dialog.SelectedPath}
$install=[IO.Path]::GetFullPath($InstallPath).TrimEnd('\')
if(!(Test-Path -LiteralPath (Join-Path $install 'Turbo Client.exe'))){throw 'Select the existing folder containing Turbo Client.exe.'}
$payload=Join-Path $PSScriptRoot 'payload'
$manifest=Get-Content -LiteralPath (Join-Path $PSScriptRoot 'manifest.json') -Raw|ConvertFrom-Json
$allowed=@('Turbo Client.exe','TurboChambers.jar','TurboTOA.jar','TurboTOB.jar','prototype-logback.xml','shared-starter.json','usage-endpoint.properties','Setup.ps1','Setup.cmd')
if(@($manifest.files).Count -ne $allowed.Count){throw 'Invalid update manifest'}
foreach($entry in $manifest.files){if($allowed -notcontains $entry.name){throw 'Unexpected update file'};$file=Join-Path $payload $entry.name;if((Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash -ne $entry.sha256){throw ('Update file failed integrity check: '+$entry.name)}}
if(!$ClientPath){$ClientPath=Join-Path $env:USERPROFILE '.orion\repository2\client.jar'}
if(!(Test-Path -LiteralPath $ClientPath)){throw 'Install and launch official Orion once, then rerun the updater. Its client.jar was not found.'}
if(!$JavaPath){$JavaPath=Join-Path $env:LOCALAPPDATA 'Orion\jre\bin\java.exe';if(!(Test-Path -LiteralPath $JavaPath) -and $env:JAVA_HOME){$JavaPath=Join-Path $env:JAVA_HOME 'bin\java.exe'}}
if(!(Test-Path -LiteralPath $JavaPath)){throw 'Java was not found. Install and launch official Orion once, or provide -JavaPath.'}
& $JavaPath '-Dlogback.configurationFile=prototype-logback.xml' -cp ((Join-Path $payload 'TurboChambers.jar')+';'+$ClientPath) owl.nh.ClientCompatibility
if($LASTEXITCODE -ne 0){throw 'Compatibility preflight failed. See the missing requirement above. Your installation has not been changed.'}
$javaw=Join-Path (Split-Path $JavaPath -Parent) 'javaw.exe'
if(!(Test-Path -LiteralPath $javaw)){throw 'Selected Java installation has no javaw.exe'}
$id=[DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss')+'-'+[guid]::NewGuid().ToString('N').Substring(0,8)
$backup=Join-Path $install ('update-backups\'+$id)
$release=Join-Path $install ('build\releases\update-'+$id)
New-Item -ItemType Directory -Path $backup,$release -Force|Out-Null
$replace=@('Turbo Client.exe','prototype-logback.xml','release-current.txt','release-toa.txt','release-tob.txt','release-runtime.txt','java-runtime.txt','usage-endpoint.properties','profiles\shared-starter.json','Setup.ps1','Setup.cmd')
$original=@{};$changed=New-Object Collections.Generic.List[string]
foreach($relative in $replace){$path=Join-Path $install $relative;$original[$relative]=Test-Path -LiteralPath $path;if($original[$relative]){$copy=Join-Path $backup $relative;New-Item -ItemType Directory -Path (Split-Path $copy -Parent) -Force|Out-Null;Copy-Item -LiteralPath $path -Destination $copy}}
try{
 Copy-Item -LiteralPath (Join-Path $payload 'TurboChambers.jar') -Destination (Join-Path $release 'TurboChambers.jar')
 Copy-Item -LiteralPath (Join-Path $payload 'TurboTOA.jar') -Destination (Join-Path $release 'TurboTOA.jar')
 Copy-Item -LiteralPath (Join-Path $payload 'TurboTOB.jar') -Destination (Join-Path $release 'TurboTOB.jar')
 Copy-Item -LiteralPath $ClientPath -Destination (Join-Path $release 'orion-client.jar')
 foreach($name in @('Turbo Client.exe','prototype-logback.xml','usage-endpoint.properties','Setup.ps1','Setup.cmd')){$changed.Add($name);Copy-Item -LiteralPath (Join-Path $payload $name) -Destination (Join-Path $install $name)}
 # A later updater must never replace the one-time starter, user profiles, or learned runs.
 $starter=Join-Path $install 'profiles\shared-starter.json'
 if(!(Test-Path -LiteralPath $starter)){New-Item -ItemType Directory -Path (Split-Path $starter -Parent) -Force|Out-Null;$changed.Add('profiles\shared-starter.json');Copy-Item -LiteralPath (Join-Path $payload 'shared-starter.json') -Destination $starter}
 # New installations without any setup get a usable starting template once.
 $candidate=Join-Path $install 'profiles\olm-demo2-candidate.json'
 if(!(Test-Path -LiteralPath $candidate)){$changed.Add('profiles\olm-demo2-candidate.json');$original['profiles\olm-demo2-candidate.json']=$false;Copy-Item -LiteralPath (Join-Path $payload 'shared-starter.json') -Destination $candidate}
 $changed.Add('release-tob.txt');[IO.File]::WriteAllText((Join-Path $install 'release-tob.txt'),'build\releases\update-'+$id+'\TurboTOB.jar')
 $changed.Add('release-toa.txt');[IO.File]::WriteAllText((Join-Path $install 'release-toa.txt'),'build\releases\update-'+$id+'\TurboTOA.jar')
 $changed.Add('release-runtime.txt');[IO.File]::WriteAllText((Join-Path $install 'release-runtime.txt'),'build\releases\update-'+$id+'\orion-client.jar')
 $changed.Add('java-runtime.txt');[IO.File]::WriteAllText((Join-Path $install 'java-runtime.txt'),$javaw)
 $changed.Add('release-current.txt');$pointer=Join-Path $install 'release-current.update.tmp'
 [IO.File]::WriteAllText($pointer,'build\releases\update-'+$id+'\TurboChambers.jar',[Text.Encoding]::ASCII)
 Move-Item -LiteralPath $pointer -Destination (Join-Path $install 'release-current.txt') -Force
 Write-Host 'Update complete. Your profiles, gear choices, run history and privacy preferences were preserved.'
 Write-Host 'Close any open Orion client once, then open Turbo Client.exe to use the checked client build.'
 Write-Host ('Backup: '+$backup)
 Write-Host 'Use Learning data > Export learned behaviour to save a file you can send back manually.'
}catch{
 foreach($relative in $changed){$path=Join-Path $install $relative;if($original[$relative]){Copy-Item -LiteralPath (Join-Path $backup $relative) -Destination $path -Force}elseif(Test-Path -LiteralPath $path){Remove-Item -LiteralPath $path -Force}}
 throw 'Update failed and replaced files were rolled back. Close Turbo Client and try again; your profiles were not overwritten.'
}finally{$pointer=Join-Path $install 'release-current.update.tmp';if(Test-Path -LiteralPath $pointer){Remove-Item -LiteralPath $pointer -Force}}
