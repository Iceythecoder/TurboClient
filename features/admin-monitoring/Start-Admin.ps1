$ErrorActionPreference='Stop'
$taskRoot=$PSScriptRoot
Add-Type -AssemblyName System.Security
$sealed=[IO.File]::ReadAllBytes((Join-Path $taskRoot 'private\device-key.bin'))
$userUnsealed=[Security.Cryptography.ProtectedData]::Unprotect($sealed,$null,[Security.Cryptography.DataProtectionScope]::CurrentUser)
$device=[Text.Encoding]::UTF8.GetString([Security.Cryptography.ProtectedData]::Unprotect($userUnsealed,$null,[Security.Cryptography.DataProtectionScope]::LocalMachine))
try{$existing=Invoke-WebRequest -UseBasicParsing -Uri 'http://127.0.0.1:8764/launch' -Method Post -Headers @{'X-Turbo-Device'=$device} -TimeoutSec 2 -ErrorAction Stop;if($existing.StatusCode -eq 200){Start-Process $existing.Content;Write-Host 'Existing admin panel reopened.';exit 0}}catch{}
$java=Join-Path $env:LOCALAPPDATA 'Orion\jre\bin\java.exe'
$info=New-Object Diagnostics.ProcessStartInfo
$info.FileName=$java
$info.Arguments='-cp "'+(Join-Path $taskRoot 'admin.jar')+';'+(Join-Path $env:USERPROFILE '.orion\repository2\client.jar')+'" owl.admin.AdminServer "'+(Join-Path $taskRoot 'private')+'"'
$info.UseShellExecute=$false;$info.CreateNoWindow=$true;$info.RedirectStandardInput=$true;$info.RedirectStandardOutput=$true;$info.RedirectStandardError=$true
$server=[Diagnostics.Process]::Start($info)
$server.StandardInput.WriteLine($device);$server.StandardInput.Close();$device=$null
$errors=$server.StandardError.ReadToEndAsync()
$line=$server.StandardOutput.ReadLine()
if(!$line -or !$line.StartsWith('ADMIN_URL=')){throw 'Admin service did not start. It may already be running, or device authentication failed.'}
Start-Process $line.Substring(10)
$tunnelInfo=New-Object Diagnostics.ProcessStartInfo
$tunnelInfo.FileName=Join-Path $taskRoot 'cloudflared.exe'
$tunnelInfo.Arguments='tunnel --no-autoupdate --url http://127.0.0.1:8765'
$tunnelInfo.UseShellExecute=$false;$tunnelInfo.CreateNoWindow=$true;$tunnelInfo.RedirectStandardError=$true;$tunnelInfo.RedirectStandardOutput=$true
$tunnel=$null
try{
 $tunnel=[Diagnostics.Process]::Start($tunnelInfo)
 $discard=$tunnel.StandardOutput.ReadToEndAsync()
 $address=$null
 while(!$tunnel.HasExited){$message=$tunnel.StandardError.ReadLine();if($null -eq $message){break};if($message -match 'https://[a-z0-9-]+\.trycloudflare\.com'){$address=$Matches[0];break}}
 if(!$address){throw 'Cloudflare did not supply an address.'}
 $discardErrors=$tunnel.StandardError.ReadToEndAsync()
 $config=Get-Content -LiteralPath (Join-Path $taskRoot 'private\admin-config.json') -Raw|ConvertFrom-Json
 $connection='endpoint='+$address+"`n"+'token='+$config.collectorToken+"`n"
 @{server=$server.Id;tunnel=$tunnel.Id} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $taskRoot 'private\service-pids.json') -Encoding ASCII
 [IO.File]::WriteAllText((Join-Path $taskRoot 'usage-endpoint.properties'),$connection,[Text.Encoding]::ASCII)
 $client=Join-Path (Split-Path $taskRoot -Parent) 'Turbo Client'
 if(Test-Path -LiteralPath $client){[IO.File]::WriteAllText((Join-Path $client 'usage-endpoint.properties'),$connection,[Text.Encoding]::ASCII)}
 Write-Host 'Admin service and public reporting tunnel are running.'
 Write-Host 'Share ONLY usage-endpoint.properties with friends; place it next to Turbo Client.exe and reopen Turbo.'
 Write-Host 'Keep this window open. Closing it stops monitoring. Restarting generates a new reporting address.'
 $server.WaitForExit()
}finally{if($tunnel -and !$tunnel.HasExited){$tunnel.Kill()};if(!$server.HasExited){$server.Kill()}}
