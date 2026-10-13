# SafeSignal — JVM End-to-End Test Script
# Runs live Ktor server and WorkerBot on the JVM to prove delivery, retry, and acknowledgement.
# Verification criteria: "5/5 delivered, 5/5 acknowledged, retries>0; exit code 0"

$ErrorActionPreference = 'Stop'

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectRoot = Split-Path -Parent $scriptDir

Write-Host "=== SafeSignal JVM End-to-End Test ==="
Write-Host "Project Root: $projectRoot"

$serverProcess = $null
try {
    # 1. Start Ktor server in background
    Write-Host "[1/4] Starting SafeSignal server on port 8080..."
    $serverStartInfo = New-Object System.Diagnostics.ProcessStartInfo
    $serverStartInfo.FileName = "cmd.exe"
    $serverStartInfo.Arguments = "/c .\gradlew.bat :server:run"
    $serverStartInfo.WorkingDirectory = $projectRoot
    $serverStartInfo.UseShellExecute = $false
    $serverStartInfo.CreateNoWindow = $true

    $serverProcess = [System.Diagnostics.Process]::Start($serverStartInfo)
    Write-Host "Server process started (PID: $($serverProcess.Id))"

    # 2. Wait for server health endpoint
    Write-Host "[2/4] Waiting for server health check on http://localhost:8080/health..."
    $healthy = $false
    $timeoutSec = 30
    $startWait = Get-Date

    while (-not $healthy -and ((Get-Date) - $startWait).TotalSeconds -lt $timeoutSec) {
        try {
            $response = curl.exe -s http://localhost:8080/health
            if ($response -eq "OK") {
                $healthy = $true
                break
            }
        } catch {
            # retry
        }
        Start-Sleep -Milliseconds 800
    }

    if (-not $healthy) {
        throw "Server failed to start within $timeoutSec seconds."
    }
    Write-Host "Server is ready! Health check OK."

    # 3. Run WorkerBot JVM CLI
    Write-Host "[3/4] Running WorkerBot (5 alerts, 1 simulated retry, auto-ack enabled)..."
    $botCmd = ".\gradlew.bat :bot:run --args=`"--server http://localhost:8080 --count 5 --simulate-retries 1 --auto-ack true`""
    
    $botOutput = & cmd.exe /c $botCmd 2>&1
    $botOutputStr = $botOutput -join "`n"
    Write-Host $botOutputStr

    # 4. Validate acceptance criteria
    Write-Host "[4/4] Validating acceptance criteria..."
    $pattern = "5/5 delivered, 5/5 acknowledged, retries=([1-9]\d*)"
    if ($botOutputStr -match $pattern) {
        $retriesFound = $Matches[1]
        Write-Host "=========================================================="
        Write-Host "E2E JVM PASS: 5/5 delivered, 5/5 acknowledged, retries=$retriesFound (retries>0)"
        Write-Host "=========================================================="
        $exitCode = 0
    } else {
        throw "E2E JVM FAILED: Output did not meet criteria '5/5 delivered, 5/5 acknowledged, retries>0'"
    }

} finally {
    # Clean up server process tree
    if ($serverProcess -and -not $serverProcess.HasExited) {
        Write-Host "Stopping server process tree..."
        Stop-Process -Id $serverProcess.Id -Force -ErrorAction SilentlyContinue
        # Kill any child java processes on port 8080 if lingering
        $portPids = Get-NetTCPConnection -LocalPort 8080 -ErrorAction SilentlyContinue | Select-Object -ExpandProperty OwningProcess -Unique
        foreach ($pidToKill in $portPids) {
            Stop-Process -Id $pidToKill -Force -ErrorAction SilentlyContinue
        }
    }
}

exit $exitCode
