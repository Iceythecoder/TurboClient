param(
    [ValidateSet('all','chambers','slayer','toa','tob','gauntlet','admin')]
    [string]$Publish = 'all'
)
$ErrorActionPreference = 'Stop'
$logoSource = Join-Path $PSScriptRoot 'assets\turbo-logo.png'
if (Test-Path -LiteralPath $logoSource) { & (Join-Path $PSScriptRoot 'Convert-Logo.ps1') -SourcePath $logoSource }
$jdkCandidates = @(
    Get-ChildItem -Path 'C:\Program Files\Eclipse Adoptium','C:\Program Files\Java' -Filter javac.exe -Recurse -ErrorAction SilentlyContinue
    Get-ChildItem -Path (Join-Path $env:USERPROFILE '.vscode\extensions') -Filter javac.exe -Recurse -ErrorAction SilentlyContinue
) | Where-Object { $_.FullName -notmatch 'jdk1\.8|java-1\.8' } | Sort-Object FullName -Descending
if (-not $jdkCandidates) { throw 'A Java 11+ JDK is required to build Turbo. Install Temurin JDK 21 or enable the VS Code Java runtime.' }
$jdk = $jdkCandidates[0].Directory.FullName
$runtimeDirectory = Join-Path $PSScriptRoot 'runtime'
$classes = Join-Path $PSScriptRoot 'build\classes'
$releaseDirectory = Join-Path $PSScriptRoot ('build\releases\' + [DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss') + '-' + [Guid]::NewGuid().ToString('N').Substring(0,8))
if (Test-Path -LiteralPath $classes) { Remove-Item -LiteralPath $classes -Recurse -Force }
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
$slayerSources = Join-Path $PSScriptRoot 'features\slayer\src'
if (-not (Test-Path -LiteralPath $slayerSources)) { throw 'Turbo Slayer feature source is missing.' }
$sources += @(Get-ChildItem -LiteralPath $slayerSources -Recurse -Filter '*.java' | ForEach-Object FullName)
& (Join-Path $jdk 'javac.exe') -d $classes (Join-Path $PSScriptRoot 'CompileDriver.java')
if ($LASTEXITCODE -ne 0) { throw 'Build driver compilation failed.' }
& (Join-Path $jdk 'java.exe') -cp $classes CompileDriver $clientJar $classes $sources
if ($LASTEXITCODE -ne 0) { throw 'Java compilation failed.' }
$jarPath = Join-Path $releaseDirectory 'TurboChambers.jar'
$jarStream = [System.IO.File]::Open($jarPath,[System.IO.FileMode]::Create)
$jarArchive = New-Object System.IO.Compression.ZipArchive($jarStream,[System.IO.Compression.ZipArchiveMode]::Create)
try {
    foreach ($classFile in (Get-ChildItem -LiteralPath (Join-Path $classes 'owl\nh') -Filter '*.class')) {
        [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($jarArchive,$classFile.FullName,('owl/nh/' + $classFile.Name)) | Out-Null
    }
} finally { $jarArchive.Dispose(); $jarStream.Dispose() }
$slayerHostJar = Join-Path $releaseDirectory 'TurboSlayer.jar'
$toaHostJar = Join-Path $releaseDirectory 'TurboTOAHost.jar'
$adminHostJar = Join-Path $releaseDirectory 'TurboAdmin.jar'
$gauntletHostJar = Join-Path $releaseDirectory 'TurboGauntletHost.jar'
Copy-Item -LiteralPath $jarPath -Destination $slayerHostJar
Copy-Item -LiteralPath $jarPath -Destination $toaHostJar
Copy-Item -LiteralPath $jarPath -Destination $adminHostJar
Copy-Item -LiteralPath $jarPath -Destination $gauntletHostJar
$toaClasses=Join-Path $PSScriptRoot 'build\toa-classes'
$toaSource=Join-Path $PSScriptRoot 'features\toa\src'
if(!(Test-Path -LiteralPath $toaSource)){throw 'ToA module source is missing'}
if(Test-Path -LiteralPath $toaClasses){Remove-Item -LiteralPath $toaClasses -Recurse -Force}
New-Item -ItemType Directory -Path $toaClasses -Force|Out-Null
$raidSharedSources=@(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'features\raids-shared\src') -Filter '*.java' -Recurse|ForEach-Object FullName)
$toaSources=@(Get-ChildItem -LiteralPath $toaSource -Filter '*.java' -Recurse|ForEach-Object FullName)+$raidSharedSources
$toaCompileClasspath = "$clientJar;$classes"
& (Join-Path $jdk 'java.exe') -cp $classes CompileDriver $toaCompileClasspath $toaClasses $toaSources
if($LASTEXITCODE -ne 0){throw 'ToA module compilation failed'}
$toaJar=Join-Path $releaseDirectory 'TurboTOA.jar'
& (Join-Path $jdk 'jar.exe') cf $toaJar -C $toaClasses .
if($LASTEXITCODE -ne 0){throw 'ToA module archive failed'}
$tobClasses=Join-Path $PSScriptRoot 'build\tob-classes'
$tobSource=Join-Path $PSScriptRoot 'features\tob\src'
if(Test-Path -LiteralPath $tobClasses){Remove-Item -LiteralPath $tobClasses -Recurse -Force}
New-Item -ItemType Directory -Path $tobClasses -Force|Out-Null
$tobSources=@(Get-ChildItem -LiteralPath $tobSource -Filter '*.java' -Recurse|ForEach-Object FullName)+$raidSharedSources
& (Join-Path $jdk 'java.exe') -cp $classes CompileDriver $clientJar $tobClasses $tobSources
if($LASTEXITCODE -ne 0){throw 'ToB module compilation failed'}
$tobJar=Join-Path $releaseDirectory 'TurboTOB.jar'
& (Join-Path $jdk 'jar.exe') cf $tobJar -C $tobClasses .
if($LASTEXITCODE -ne 0){throw 'ToB module archive failed'}
$gauntletClasses=Join-Path $PSScriptRoot 'build\gauntlet-classes'
$gauntletSource=Join-Path $PSScriptRoot 'features\gauntlet\src'
if(!(Test-Path -LiteralPath $gauntletSource)){throw 'Gauntlet module source is missing'}
if(Test-Path -LiteralPath $gauntletClasses){Remove-Item -LiteralPath $gauntletClasses -Recurse -Force}
New-Item -ItemType Directory -Path $gauntletClasses -Force|Out-Null
$gauntletSources=@(Get-ChildItem -LiteralPath $gauntletSource -Filter '*.java' -Recurse|ForEach-Object FullName)
& (Join-Path $jdk 'java.exe') -cp $classes CompileDriver "$clientJar;$classes" $gauntletClasses $gauntletSources
if($LASTEXITCODE -ne 0){throw 'Gauntlet module compilation failed'}
$gauntletJar=Join-Path $releaseDirectory 'TurboGauntlet.jar'
& (Join-Path $jdk 'jar.exe') cf $gauntletJar -C $gauntletClasses .
if($LASTEXITCODE -ne 0){throw 'Gauntlet module archive failed'}
$java = Join-Path $env:LOCALAPPDATA 'Orion\jre\bin\java.exe'
$taskTemporary = Join-Path $PSScriptRoot 'build'
& $java "-Djava.io.tmpdir=$taskTemporary" "-Dlogback.configurationFile=$PSScriptRoot\prototype-logback.xml" "-Dowl.project.root=$PSScriptRoot" -cp "$jarPath;$clientJar" owl.nh.Bootstrap --self-test
if ($LASTEXITCODE -ne 0) { throw 'Prototype checks failed.' }
& $java "-Dlogback.configurationFile=$PSScriptRoot\prototype-logback.xml" -cp "$toaJar;$jarPath;$clientJar" turbo.toa.ToaModuleTests
if ($LASTEXITCODE -ne 0) { throw 'ToA module checks failed.' }
& $java "-Dlogback.configurationFile=$PSScriptRoot\prototype-logback.xml" -cp "$toaJar;$jarPath;$clientJar" turbo.toa.ToaCombatPolicyTests
if ($LASTEXITCODE -ne 0) { throw 'ToA combat policy checks failed.' }
& $java "-Dlogback.configurationFile=$PSScriptRoot\prototype-logback.xml" -cp "$tobJar;$clientJar" turbo.tob.TobModuleTests
if ($LASTEXITCODE -ne 0) { throw 'ToB module checks failed.' }
& $java "-Dlogback.configurationFile=$PSScriptRoot\prototype-logback.xml" -cp "$gauntletJar;$jarPath;$clientJar" turbo.gauntlet.GauntletStateTests
if ($LASTEXITCODE -ne 0) { throw 'Gauntlet module checks failed.' }
& $java "-Dlogback.configurationFile=$PSScriptRoot\prototype-logback.xml" -cp "$toaJar;$jarPath;$clientJar" turbo.raid.RaidSetupTests
if ($LASTEXITCODE -ne 0) { throw 'Raid setup checks failed.' }
$compiler = Join-Path $env:WINDIR 'Microsoft.NET\Framework64\v4.0.30319\csc.exe'
& $compiler /nologo /target:exe /r:System.Windows.Forms.dll /out:"$PSScriptRoot\build\LauncherChecks.exe" "$PSScriptRoot\Launcher.cs"
if ($LASTEXITCODE -ne 0) { throw 'Launcher checks compilation failed.' }
& "$PSScriptRoot\build\LauncherChecks.exe" --self-test
if ($LASTEXITCODE -ne 0) { throw 'Launcher reload checks failed.' }
$launcherIcon = Join-Path $PSScriptRoot 'assets\turbo-client.ico'
$iconArguments = @(); if (Test-Path -LiteralPath $launcherIcon) { $iconArguments += "/win32icon:$launcherIcon" }
foreach ($launcherName in @('Turbo Chambers.exe','Turbo Slayer.exe','Turbo TOA.exe','Turbo Gauntlet.exe','Turbo Admin Tool.exe')) {
    $launcherOutput = Join-Path $PSScriptRoot $launcherName
    & $compiler /nologo /target:winexe /r:System.Windows.Forms.dll @iconArguments "/out:$launcherOutput" "$PSScriptRoot\Launcher.cs"
    if ($LASTEXITCODE -ne 0) { throw "$launcherName compilation failed." }
}
# Backward-compatible entry point: the original name now always means Chambers.
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'Turbo Chambers.exe') -Destination (Join-Path $PSScriptRoot 'Turbo Client.exe') -Force
$relativeJar = $jarPath.Substring($PSScriptRoot.Length+1)
$relativeClient = $clientJar.Substring($PSScriptRoot.Length+1)
$relativeSlayerHost = $slayerHostJar.Substring($PSScriptRoot.Length+1)
$relativeToaHost = $toaHostJar.Substring($PSScriptRoot.Length+1)
$relativeAdminHost = $adminHostJar.Substring($PSScriptRoot.Length+1)
$relativeGauntletHost = $gauntletHostJar.Substring($PSScriptRoot.Length+1)
if ($Publish -in @('all','chambers')) {
    [System.IO.File]::WriteAllText((Join-Path $PSScriptRoot 'release-current.txt'),$relativeJar)
    [System.IO.File]::WriteAllText((Join-Path $PSScriptRoot 'release-chambers.txt'),$relativeJar)
    Copy-Item -LiteralPath $jarPath -Destination (Join-Path $PSScriptRoot 'TurboChambers.jar') -Force
}
if ($Publish -in @('all','slayer')) {
    [System.IO.File]::WriteAllText((Join-Path $PSScriptRoot 'release-slayer.txt'),$relativeSlayerHost)
    Copy-Item -LiteralPath $slayerHostJar -Destination (Join-Path $PSScriptRoot 'TurboSlayer.jar') -Force
}
if ($Publish -in @('all','toa')) {
    [System.IO.File]::WriteAllText((Join-Path $PSScriptRoot 'release-toa-host.txt'),$relativeToaHost)
    [System.IO.File]::WriteAllText((Join-Path $PSScriptRoot 'release-toa.txt'),$toaJar.Substring($PSScriptRoot.Length+1))
    Copy-Item -LiteralPath $toaHostJar -Destination (Join-Path $PSScriptRoot 'TurboTOAHost.jar') -Force
    Copy-Item -LiteralPath $toaJar -Destination (Join-Path $PSScriptRoot 'TurboTOA.jar') -Force
}
if ($Publish -in @('all','tob')) {
    [System.IO.File]::WriteAllText((Join-Path $PSScriptRoot 'release-tob.txt'),$tobJar.Substring($PSScriptRoot.Length+1))
    Copy-Item -LiteralPath $tobJar -Destination (Join-Path $PSScriptRoot 'TurboTOB.jar') -Force
}
if ($Publish -in @('all','gauntlet')) {
    [System.IO.File]::WriteAllText((Join-Path $PSScriptRoot 'release-gauntlet-host.txt'),$relativeGauntletHost)
    [System.IO.File]::WriteAllText((Join-Path $PSScriptRoot 'release-gauntlet.txt'),$gauntletJar.Substring($PSScriptRoot.Length+1))
    Copy-Item -LiteralPath $gauntletHostJar -Destination (Join-Path $PSScriptRoot 'TurboGauntletHost.jar') -Force
    Copy-Item -LiteralPath $gauntletJar -Destination (Join-Path $PSScriptRoot 'TurboGauntlet.jar') -Force
}
if ($Publish -in @('all','admin')) {
    [System.IO.File]::WriteAllText((Join-Path $PSScriptRoot 'release-admin.txt'),$relativeAdminHost)
    Copy-Item -LiteralPath $adminHostJar -Destination (Join-Path $PSScriptRoot 'TurboAdmin.jar') -Force
}
[System.IO.File]::WriteAllText((Join-Path $PSScriptRoot 'release-runtime.txt'),$relativeClient)
Write-Output ('Build and checks passed. Published mode: ' + $Publish)
Write-Output 'Use Turbo Chambers.exe, Turbo Slayer.exe, Turbo TOA.exe, Turbo Gauntlet.exe, or Turbo Admin Tool.exe. Turbo Client.exe remains a Chambers-compatible shortcut.'

