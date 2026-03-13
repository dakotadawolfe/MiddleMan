# MiddleMan launcher: starts RuneLite with the MiddleMan agent via -javaagent,
# and without -XX:+DisableAttachMechanism.
# Run from the RuneLite project root: .\MiddleMan\launcher\launch.ps1

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$MiddleManDir = Split-Path -Parent $ScriptDir
$Root = Split-Path -Parent $MiddleManDir

$ConfigPath = Join-Path $Root "config.json"
if (-not (Test-Path $ConfigPath)) {
    Write-Error "config.json not found at $ConfigPath"
    exit 1
}

$Config = Get-Content $ConfigPath -Raw | ConvertFrom-Json
$ClassPath = $Config.classPath
if ($ClassPath -is [array]) {
    $Cp = ($ClassPath | ForEach-Object { Join-Path $Root $_ }) -join [System.IO.Path]::PathSeparator
} else {
    $Cp = Join-Path $Root $ClassPath
}
$MainClass = $Config.mainClass
$VmArgs = @($Config.vmArgs) | Where-Object { $_ -notmatch "DisableAttachMechanism" }

$AgentJar = Join-Path $Root "MiddleMan\agent\build\MiddleManAgent.jar"
if (-not (Test-Path $AgentJar)) {
    Write-Error "Agent JAR not found. Build first: cd MiddleMan\agent; .\build.ps1"
    exit 1
}
$VmArgs += "-javaagent:${AgentJar}=8766"

$JreBin = Join-Path $Root "jre\bin\java.exe"
if (-not (Test-Path $JreBin)) {
    $JreBin = Join-Path $Root "jre\bin\java"
}
if (-not (Test-Path $JreBin)) {
    $JreBin = "java"
}

# REFLECT = run client in same JVM as launcher so the MiddleMan agent can see the Client
$Cmd = @($JreBin) + @($VmArgs) + @("-cp", $Cp, $MainClass, "--launch-mode=REFLECT")
Write-Host ""
Write-Host "========== MiddleMan Launcher ==========" -ForegroundColor Cyan
Write-Host "  Starting RuneLite with MiddleMan agent (launch-mode=REFLECT)"
Write-Host "  Agent status is written to: $Root\MiddleMan\middleman.log"
Write-Host "  (Open that file if the dashboard says Failed to connect)"
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Set-Location $Root
$DashboardPath = Join-Path $MiddleManDir "dashboard\index.html"
if (Test-Path $DashboardPath) {
    Start-Process $DashboardPath
}
$exe = $Cmd[0]
$cmdArgs = $Cmd[1..($Cmd.Length-1)]
& $exe @cmdArgs
