param(
    [Parameter(Mandatory=$true)][string]$JavaHome,
    [Parameter(Mandatory=$true)][string]$AndroidJar,
    [Parameter(Mandatory=$true)][string]$BuildTools,
    [string]$BuildDir = (Join-Path $PSScriptRoot '../../work/PencilLinkBuild'),
    [string]$OutputApk = (Join-Path $PSScriptRoot '../PencilLink-0.3.1.apk')
)
$ErrorActionPreference = 'Stop'
function Run([string]$exe, [string[]]$arguments) {
    & $exe @arguments
    if ($LASTEXITCODE -ne 0) { throw "Command failed: $exe ($LASTEXITCODE)" }
}
$env:JAVA_HOME = $JavaHome
$env:PATH = "$JavaHome/bin;" + $env:PATH
New-Item -ItemType Directory -Force $BuildDir | Out-Null
$BuildDir = (Resolve-Path $BuildDir).Path
foreach ($d in @('generated','classes','dex')) { New-Item -ItemType Directory -Force "$BuildDir/$d" | Out-Null }
$main = Join-Path $PSScriptRoot 'app/src/main'
[xml]$manifest = Get-Content "$main/AndroidManifest.xml" -Raw
$manifest.manifest.SetAttribute('package','cn.pencillink')
$manifest.Save("$BuildDir/AndroidManifest.xml")
Run "$BuildTools/aapt2.exe" @('compile','--dir',"$main/res",'-o',"$BuildDir/resources.zip")
Run "$BuildTools/aapt2.exe" @('link','-o',"$BuildDir/base.apk",'--manifest',"$BuildDir/AndroidManifest.xml",'-I',$AndroidJar,'--java',"$BuildDir/generated",'--min-sdk-version','26','--target-sdk-version','35','--version-code','4','--version-name','0.3.1',"$BuildDir/resources.zip")
$sources = @(Get-ChildItem "$main/java","$BuildDir/generated" -Recurse -Filter '*.java' | ForEach-Object FullName)
Run "$JavaHome/bin/javac.exe" (@('-encoding','UTF-8','-source','8','-target','8','-classpath',$AndroidJar,'-d',"$BuildDir/classes") + $sources)
Run "$JavaHome/bin/jar.exe" @('cf',"$BuildDir/classes.jar",'-C',"$BuildDir/classes",'.')
Run "$JavaHome/bin/java.exe" @('-cp',"$BuildTools/lib/d8.jar",'com.android.tools.r8.D8','--lib',$AndroidJar,'--min-api','26','--output',"$BuildDir/dex", "$BuildDir/classes.jar")
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [IO.Compression.ZipFile]::Open("$BuildDir/base.apk",[IO.Compression.ZipArchiveMode]::Update)
try { [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip,"$BuildDir/dex/classes.dex",'classes.dex') | Out-Null } finally { $zip.Dispose() }
Run "$BuildTools/zipalign.exe" @('-f','4',"$BuildDir/base.apk","$BuildDir/aligned.apk")
if (!(Test-Path "$BuildDir/debug.jks")) {
    Run "$JavaHome/bin/keytool.exe" @('-genkeypair','-keystore',"$BuildDir/debug.jks",'-storepass','android','-keypass','android','-alias','androiddebugkey','-dname','CN=PencilLink Test','-keyalg','RSA','-keysize','2048','-validity','10000')
}
Run "$JavaHome/bin/java.exe" @('-jar',"$BuildTools/lib/apksigner.jar",'sign','--ks',"$BuildDir/debug.jks",'--ks-pass','pass:android','--out',$OutputApk,"$BuildDir/aligned.apk")
Run "$JavaHome/bin/java.exe" @('-jar',"$BuildTools/lib/apksigner.jar",'verify','--verbose',$OutputApk)
Write-Output "APK: $OutputApk"
