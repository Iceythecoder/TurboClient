$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.Security
$sealed=[IO.File]::ReadAllBytes((Join-Path $PSScriptRoot 'private\device-key.bin'))
$userUnsealed=[Security.Cryptography.ProtectedData]::Unprotect($sealed,$null,[Security.Cryptography.DataProtectionScope]::CurrentUser)
$device=[Text.Encoding]::UTF8.GetString([Security.Cryptography.ProtectedData]::Unprotect($userUnsealed,$null,[Security.Cryptography.DataProtectionScope]::LocalMachine))
Invoke-WebRequest -UseBasicParsing -Uri 'http://127.0.0.1:8764/shutdown' -Method Post -Headers @{'X-Turbo-Device'=$device} -TimeoutSec 5 | Out-Null
$device=$null
Write-Host 'Admin monitoring stopped. Active reports have been cleared.'
