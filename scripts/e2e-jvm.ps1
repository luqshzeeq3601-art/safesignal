# SafeSignal — JVM End-to-End Test Script
# Runs live Ktor server and WorkerBot on the JVM to prove delivery, retry, and acknowledgement.
# The server is stopped before the bot raises its first alert and restarted while the bot is
# retrying, so the retries come from a real server outage (connection refused), not a simulated one.
# Verification criteria: "5/5 delivered, 5/5 acknowledged, retries>0; exit code 0" (with the default -Count 5)

param(
    # Number of alerts the bot raises.
    [int]$Count = 5,
    # Optional path (relative to the :bot module) where the bot writes its KPI events CSV.
    [string]$KpiCsv = ""
)

$ErrorActionPreference = 'Stop'

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectRoot = Split-Path -Parent $scriptDir

Write-Host "=== SafeSignal JVM End-to-End Test ==="
Write-Host "Project Root: $projectRoot"

$runOnWindows = ($PSVersionTable.PSVersion.Major -lt 6) -or $IsWindows -or ($env:OS -like "*Windows*")
$healthUrl = "http://localhost:8080/health"

function Start-Background([string]$gradleCommand) {
    $startInfo = New-Object System.Diagnostics.ProcessStartInfo
    if ($runOnWindows) {
        $startInfo.FileName = "cmd.exe"
        $startInfo.Arguments = "/c .\gradlew.bat $gradleCommand"
    } else {
        $startInfo.FileName = "/bin/bash"
        $startInfo.Arguments = "-c `"./gradlew $($gradleCommand.Replace('"', '\"'))`""
    }
    $startInfo.WorkingDirectory = $projectRoot
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    return [System.Diagnostics.Process]::Start($startInfo)
}

function Test-ServerHealthy {
    try {
        return (Invoke-RestMethod -Uri $healthUrl -TimeoutSec 2 -ErrorAction Stop) -eq "OK"
    } catch {
        return $false
    }
}

function Wait-ServerState([bool]$healthy, [int]$timeoutSec) {
    $startWait = Get-Date
    while (((Get-Date) - $startWait).TotalSeconds -lt $timeoutSec) {
        if ((Test-ServerHealthy) -eq $healthy) { return $true }
        Start-Sleep -Milliseconds 500
    }
    return $false
}

function Stop-Server($process) {
    if ($process -and -not $process.HasExited) {
        Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
    }
    # The Gradle client is only a wrapper; stop the forked server JVM itself.
    if ($runOnWindows) {
        $portPids = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue | Select-Object -ExpandProperty OwningProcess -Unique
        foreach ($pidToKill in $portPids) {
            Stop-Process -Id $pidToKill -Force -ErrorAction SilentlyContinue
        }
    } else {
        & pkill -9 -f "com.safesignal.server.ApplicationKt" 2>&1 | Out-Null
    }
}

$serverProcess = $null
$botProcess = $null
$exitCode = 1
try {
    # 1. Start the server once so it is built and known to work
    Write-Host "[1/5] Starting SafeSignal server on port 8080..."
    $serverProcess = Start-Background ":server:run"
    if (-not (Wait-ServerState $true 60)) {
        throw "Server failed to start within 60 seconds."
    }
    Write-Host "Server is ready! Health check OK."

    # 2. Take the server down: this is the outage
    Write-Host "[2/5] Stopping the server to create a real outage..."
    Stop-Server $serverProcess
    if (-not (Wait-ServerState $false 20)) {
        throw "Server is still answering health checks; outage could not be created."
    }
    Write-Host "Server is down."

    # 3. Start the bot while the server is down
    Write-Host "[3/5] Running WorkerBot ($Count alerts, auto-ack enabled) against the stopped server..."
    $logDir = Join-Path $projectRoot "build/e2e"
    New-Item -ItemType Directory -Force -Path $logDir | Out-Null
    $botLog = Join-Path $logDir "bot.log"
    Remove-Item $botLog -Force -ErrorAction SilentlyContinue

    $botArgs = "--server http://localhost:8080 --count $Count --simulate-retries 0 --max-attempts 25 --auto-ack true"
    if (-not [string]::IsNullOrEmpty($KpiCsv)) {
        $botArgs += " --kpi-csv $KpiCsv"
    }
    $botProcess = Start-Background ":bot:run --args=`"$botArgs`" > build/e2e/bot.log 2>&1"

    $sawFailure = $false
    $startWait = Get-Date
    while (((Get-Date) - $startWait).TotalSeconds -lt 120 -and -not $botProcess.HasExited) {
        if ((Test-Path $botLog) -and (Select-String -Path $botLog -Pattern "Attempt 1 failed" -Quiet)) {
            $sawFailure = $true
            break
        }
        Start-Sleep -Milliseconds 300
    }
    if (-not $sawFailure) {
        throw "Bot did not report a failed first attempt while the server was down."
    }
    Write-Host "Bot's first attempt failed against the stopped server."

    # 4. Bring the server back while the bot is retrying
    Write-Host "[4/5] Restarting the server..."
    $serverProcess = Start-Background ":server:run"
    if (-not (Wait-ServerState $true 60)) {
        throw "Server failed to restart within 60 seconds."
    }
    Write-Host "Server is back."

    if (-not $botProcess.WaitForExit(180000)) {
        throw "Bot did not finish within 180 seconds."
    }
    $botOutputStr = Get-Content $botLog -Raw
    Write-Host $botOutputStr

    # 5. Validate acceptance criteria
    Write-Host "[5/5] Validating acceptance criteria..."
    $pattern = "$Count/$Count delivered, $Count/$Count acknowledged, retries=([1-9]\d*)"
    if ($botOutputStr -match $pattern) {
        $retriesFound = $Matches[1]
        Write-Host "=========================================================="
        Write-Host "E2E JVM PASS: $Count/$Count delivered, $Count/$Count acknowledged, retries=$retriesFound (retries>0, real server outage)"
        Write-Host "=========================================================="
        $exitCode = 0
    } else {
        throw "E2E JVM FAILED: Output did not meet criteria '$Count/$Count delivered, $Count/$Count acknowledged, retries>0'"
    }

} finally {
    Write-Host "Stopping server process tree..."
    Stop-Server $serverProcess
    if ($botProcess -and -not $botProcess.HasExited) {
        Stop-Process -Id $botProcess.Id -Force -ErrorAction SilentlyContinue
    }
}

exit $exitCode
