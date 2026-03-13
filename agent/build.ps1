$ErrorActionPreference = "Stop"
$Src = "src/main/java"
$Out = "build/classes"
$Jar = "build/MiddleManAgent.jar"
$Manifest = "build/manifest.txt"

New-Item -ItemType Directory -Force -Path $Out | Out-Null
New-Item -ItemType Directory -Force -Path "build" | Out-Null

Write-Host "Compiling (Java 11 bytecode for RuneLite JRE)..."
javac -source 11 -target 11 -d $Out -encoding UTF-8 "$Src/middleman/agent/*.java"
if ($LASTEXITCODE -ne 0) { exit 1 }

@"
Premain-Class: middleman.agent.MiddleManAgent
Agent-Class: middleman.agent.MiddleManAgent
Can-Redefine-Classes: false
Can-Retransform-Classes: false

"@ | Set-Content -Path $Manifest -NoNewline

Write-Host "Building JAR..."
jar cfm $Jar $Manifest -C $Out .
if ($LASTEXITCODE -ne 0) { exit 1 }

Write-Host "Built $Jar"
