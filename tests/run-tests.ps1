param([string]$JavaHome=$env:JAVA_HOME)
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$javac=if($JavaHome){Join-Path $JavaHome 'bin/javac.exe'}else{'javac'}
$java=if($JavaHome){Join-Path $JavaHome 'bin/java.exe'}else{'java'}
$out=Join-Path $root '.test-build'
New-Item -ItemType Directory -Force $out | Out-Null
& $javac -encoding UTF-8 -d $out "$root/app/src/main/java/cn/pencillink/ActionPolicy.java" "$root/app/src/main/java/cn/pencillink/PressResolver.java" "$PSScriptRoot/PressResolverTest.java"
if($LASTEXITCODE -ne 0){throw 'Test compilation failed'}
& $java -cp $out cn.pencillink.PressResolverTest
if($LASTEXITCODE -ne 0){throw 'Policy regression tests failed'}
