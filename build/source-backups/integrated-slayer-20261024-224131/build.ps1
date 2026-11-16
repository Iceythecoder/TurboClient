$ErrorActionPreference = 'Stop'
$logoSource = Join-Path $PSScriptRoot 'assets\turbo-logo.png'
if (Test-Path -LiteralPath $logoSource) { & (Join-Path $PSScriptRoot 'Convert-Logo.ps1') -SourcePath $logoSource }
$jdk = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot\bin'
$runtimeDirectory = Join-Path $PSScriptRoot 'runtime'
$classes = Join-Path $PSScriptRoot 'build\classes'
$releaseDirectory = Join-Path $PSScriptRoot ('build\releases\' + [DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss') + '-' + [Guid]::NewGuid().ToString('N').Substring(0,8))
New-Item -ItemType Directory -Path $runtimeDirectory,$classes,$releaseDirectory -Force | Out-Null
$sourceClient = Join-Path $env:USERPROFILE '.orion\repository2\client.jar'
$clientJar = Join-Path $runtimeDirectory 'orion-client.jar'
if (-not (Test-Path -LiteralPath $clientJar)) {
    Copy-Item -LiteralPath $sourceClient -Destination $clientJar
} elseif ((Get-FileHash -LiteralPath $sourceClient).Hash -ne (Get-FileHash -LiteralPath $clientJar).Hash) {
    $clientJar = Join-Path $releaseDirectory 'orion-client.jar'
    Copy-Item -LiteralPath $sourceClient -Destination $clientJar
}
# Compile against the unchanged local client; no downloaded dependencies.
Add-Type -AssemblyName System.IO.Compression.FileSystem
Add-Type -AssemblyName System.IO.Compression
$sources = @(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'src\owl\nh') -Filter '*.java' | ForEach-Object FullName)
$slayerRoot = Join-Path (Split-Path $PSScriptRoot -Parent) 'Turbo Slayer'
$slayerSources = Join-Path $slayerRoot 'features\npc-fighter\src'
if (-not (Test-Path -LiteralPath $slayerSources)) { throw 'Turbo Slayer feature source is missing.' }
$sources += @(Get-ChildItem -LiteralPath $slayerSources -Recurse -Filter '*.java' | ForEach-Object FullName)
& (Join-Path $jdk 'javac.exe') -d $classes (Join-Path $PSScriptRoot 'CompileDriver.java')
if ($LASTEXITCODE -ne 0) { throw 'Build driver compilation failed.' }
& (Join-Path $jdk 'java.exe') -cp $classes CompileDriver $clientJar $classes $sources
if ($LASTEXITCODE -ne 0) { throw 'Java compilation failed.' }
$jarPath = Join-Path $releaseDirectory 'OrionNH.jar'
$jarStream = [System.IO.File]::Open($jarPath,[System.IO.FileMode]::Create)
$jarArchive = New-Object System.IO.Compression.ZipArchive($jarStream,[System.IO.Compression.ZipArchiveMode]::Create)
try {
    foreach ($classFile in (Get-ChildItem -LiteralPath (Join-Path $classes 'owl\nh') -Filter '*.class')) {
        [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($jarArchive,$classFile.FullName,('owl/nh/' + $classFile.Name)) | Out-Null
    }
} finally { $jarArchive.Dispose(); $jarStream.Dispose() }
$java = Join-Path $env:LOCALAPPDATA 'Orion\jre\bin\java.exe'
$taskTemporary = Join-Path $PSScriptRoot 'build'
& $java "-Djava.io.tmpdir=$taskTemporary" "-Dlogback.configurationFile=$PSScriptRoot\prototype-logback.xml" "-Dowl.project.root=$PSScriptRoot" -cp "$jarPath;$clientJar" owl.nh.Bootstrap --self-test
if ($LASTEXITCODE -ne 0) { throw 'Prototype checks failed.' }
$compiler = Join-Path $env:WINDIR 'Microsoft.NET\Framework64\v4.0.30319\csc.exe'
& $compiler /nologo /target:exe /r:System.Windows.Forms.dll /out:"$PSScriptRoot\build\LauncherChecks.exe" "$PSScriptRoot\Launcher.cs"
if ($LASTEXITCODE -ne 0) { throw 'Launcher checks compilation failed.' }
& "$PSScriptRoot\build\LauncherChecks.exe" --self-test
if ($LASTEXITCODE -ne 0) { throw 'Launcher reload checks failed.' }
$launcherIcon = Join-Path $PSScriptRoot 'assets\turbo-client.ico'
$iconArguments = @(); if (Test-Path -LiteralPath $launcherIcon) { $iconArguments += "/win32icon:$launcherIcon" }
& $compiler /nologo /target:winexe /r:System.Windows.Forms.dll @iconArguments /out:"$PSScriptRoot\Turbo Client.exe" "$PSScriptRoot\Launcher.cs"
if ($LASTEXITCODE -ne 0) { throw 'Launcher compilation failed.' }
& $compiler /nologo /target:winexe /r:System.Windows.Forms.dll @iconArguments /out:"$slayerRoot\Turbo Slayer.exe" "$slayerRoot\app\SlayerLauncher.cs"
if ($LASTEXITCODE -ne 0) { throw 'Slayer launcher compilation failed.' }
$relativeJar = $jarPath.Substring($PSScriptRoot.Length+1)
$relativeClient = $clientJar.Substring($PSScriptRoot.Length+1)
[System.IO.File]::WriteAllText((Join-Path $PSScriptRoot 'release-current.txt'),$relativeJar)
[System.IO.File]::WriteAllText((Join-Path $PSScriptRoot 'release-runtime.txt'),$relativeClient)
Write-Output 'Build and checks passed. Open Turbo Client.exe to launch Orion with the companion.'
Write-Output ('Selected release: ' + $relativeJar)

