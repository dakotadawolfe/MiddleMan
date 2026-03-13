# MiddleMan launcher: starts RuneLite without -XX:+DisableAttachMechanism.
# With agent (default): adds -javaagent and REFLECT. Use from MiddleMan.exe or launch.bat.
# -AttachableOnly: start RuneLite without agent so MiddleMan.exe can attach later.

param([switch]$AttachableOnly)

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

if (-not $AttachableOnly) {
    $AgentJar = Join-Path $Root "MiddleMan\agent\build\MiddleManAgent.jar"
    if (-not (Test-Path $AgentJar)) {
        Write-Error "Agent JAR not found. Build first: cd MiddleMan\agent; .\build.bat"
        exit 1
    }
    $VmArgs += "-javaagent:${AgentJar}=8765"
}

$JreBin = Join-Path $Root "jre\bin\java.exe"
if (-not (Test-Path $JreBin)) {
    $JreBin = Join-Path $Root "jre\bin\java"
}
if (-not (Test-Path $JreBin)) {
    $JreBin = "java"
}

$Cmd = @($JreBin) + @($VmArgs) + @("-cp", $Cp, $MainClass, "--launch-mode=REFLECT")
if (-not $AttachableOnly) {
    Write-Host ""
    Write-Host "========== MiddleMan Launcher ==========" -ForegroundColor Cyan
    Write-Host "  Starting RuneLite with MiddleMan agent (launch-mode=REFLECT)"
    Write-Host "  Agent status: $Root\MiddleMan\middleman.log"
    Write-Host "========================================" -ForegroundColor Cyan
    Write-Host ""
    $DashboardPath = Join-Path $MiddleManDir "dashboard\index.html"
    if (Test-Path $DashboardPath) {
        Start-Process $DashboardPath
    }
}
Set-Location $Root
$exe = $Cmd[0]
$cmdArgs = $Cmd[1..($Cmd.Length-1)]
& $exe @cmdArgs
