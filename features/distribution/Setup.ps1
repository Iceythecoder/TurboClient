param([string]$ClientPath,[string]$JavaPath)
$ErrorActionPreference='Stop'
$taskRoot=$PSScriptRoot
if(!$ClientPath){$ClientPath=Join-Path $env:USERPROFILE '.orion\repository2\client.jar'}
if(!(Test-Path -LiteralPath $ClientPath)){throw 'Install and launch official Orion once, then run setup again. Its client.jar was not found.'}
if(!$JavaPath){$JavaPath=Join-Path $env:LOCALAPPDATA 'Orion\jre\bin\java.exe';if(!(Test-Path -LiteralPath $JavaPath) -and $env:JAVA_HOME){$JavaPath=Join-Path $env:JAVA_HOME 'bin\java.exe'}}
if(!(Test-Path -LiteralPath $JavaPath)){throw 'Java was not found. Install and launch official Orion once, or provide -JavaPath to Java 11 or newer.'}
$pointer=Join-Path $taskRoot 'release-current.txt'
$relative=if(Test-Path -LiteralPath $pointer){[IO.File]::ReadAllText($pointer).Trim()}elseif(Test-Path -LiteralPath (Join-Path $taskRoot 'TurboChambers.jar')){'TurboChambers.jar'}else{'OrionNH.jar'}
$companion=[IO.Path]::GetFullPath((Join-Path $taskRoot $relative))
if(!$companion.StartsWith($taskRoot.TrimEnd('\')+'\',[StringComparison]::OrdinalIgnoreCase) -or !(Test-Path -LiteralPath $companion)){throw 'Turbo companion JAR is missing. Extract the whole package or use the updater.'}
& $JavaPath '-Dlogback.configurationFile=prototype-logback.xml' -cp ($companion+';'+$ClientPath) owl.nh.ClientCompatibility
if($LASTEXITCODE -ne 0){throw 'Client compatibility preflight failed. See the exact missing Java/API requirement above. No setup files changed.'}
$javaw=Join-Path (Split-Path $JavaPath -Parent) 'javaw.exe'
if(!(Test-Path -LiteralPath $javaw)){throw 'The selected Java installation is missing javaw.exe.'}
$id='setup-'+[DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss')+'-'+[guid]::NewGuid().ToString('N').Substring(0,8)
$relativeClient='runtime\'+$id+'\orion-client.jar'
$clientTarget=Join-Path $taskRoot $relativeClient
New-Item -ItemType Directory -Path (Split-Path $clientTarget -Parent) -Force|Out-Null
Copy-Item -LiteralPath $ClientPath -Destination $clientTarget
[IO.File]::WriteAllText((Join-Path $taskRoot 'release-runtime.txt'),$relativeClient)
[IO.File]::WriteAllText((Join-Path $taskRoot 'java-runtime.txt'),$javaw)
Write-Host 'Setup complete. Client API compatibility passed; a different file hash is not treated as incompatibility.'
Write-Host 'Open Turbo Client.exe. Live gameplay compatibility still requires testing.'
