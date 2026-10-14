# SafeSignal — Emulator End-to-End Test Script (M9)
# Validates that an injected fall on the Android emulator produces an alert
# with location and battery on the dispatcher server, and that the worker
# receives and displays the dispatcher's acknowledgement.
#
# Verification criteria: "E2E PASS (4/4)"

param(
    [int]$CountdownSeconds = 3,
    [int]$AckPollTimeoutSec = 15,
    [string]$ServerPort = "8080"
)

$ErrorActionPreference = 'Stop'

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectRoot = Split-Path -Parent $scriptDir

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "       SafeSignal Emulator End-to-End Test (M9)" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "Project Root: $projectRoot"

$serverProcess = $null
$spawnedServer = $false
$exitCode = 1

try {
    # 0. Check emulator state
    Write-Host "`n[Setup] Checking target Android emulator..."
    $deviceState = (adb get-state 2>&1).Trim()
    if ($deviceState -ne 'device') {
        throw "No active adb device found (state: $deviceState). Please run scripts/emulator-start.ps1 first."
    }
    Write-Host "Target emulator online: $deviceState" -ForegroundColor Green

    # Set up reverse port forwarding so emulator can access host server on localhost:8080 as well as 10.0.2.2:8080
    adb reverse tcp:8080 tcp:8080 2>&1 | Out-Null
    Write-Host "Configured adb reverse port forwarding: tcp:8080 -> tcp:8080"

    # Set GPS location on emulator
    adb emu geo fix -122.4194 37.7749 2>&1 | Out-Null
    Write-Host "Set emulator GPS fix to coordinates (-122.4194, 37.7749)"

    # 1. Start Server if not already running
    Write-Host "`n[Setup] Checking SafeSignal Dispatcher Server on port $ServerPort..."
    $serverHealthy = $false
    try {
        $healthResp = curl.exe -s http://localhost:$ServerPort/health
        if ($healthResp -eq "OK") {
            $serverHealthy = $true
            Write-Host "Existing server detected and healthy." -ForegroundColor Green
        }
    } catch {
        # Not running
    }

    if (-not $serverHealthy) {
        Write-Host "Starting SafeSignal Ktor server on port $ServerPort..."
        $serverStartInfo = New-Object System.Diagnostics.ProcessStartInfo
        $serverStartInfo.FileName = "cmd.exe"
        $serverStartInfo.Arguments = "/c .\gradlew.bat :server:run"
        $serverStartInfo.WorkingDirectory = $projectRoot
        $serverStartInfo.UseShellExecute = $false
        $serverStartInfo.CreateNoWindow = $true

        $serverProcess = [System.Diagnostics.Process]::Start($serverStartInfo)
        $spawnedServer = $true
        Write-Host "Server process started (PID: $($serverProcess.Id))"

        $timeoutSec = 35
        $startWait = Get-Date
        while (-not $serverHealthy -and ((Get-Date) - $startWait).TotalSeconds -lt $timeoutSec) {
            try {
                $response = curl.exe -s http://localhost:$ServerPort/health
                if ($response -eq "OK") {
                    $serverHealthy = $true
                    break
                }
            } catch {
                # retry
            }
            Start-Sleep -Milliseconds 800
        }

        if (-not $serverHealthy) {
            throw "Server failed to respond on http://localhost:$ServerPort/health within $timeoutSec s."
        }
    }

    # 2. Install latest APK and grant required runtime permissions
    Write-Host "`n[Setup] Installing and configuring SafeSignal app..."
    $apkPath = Join-Path $projectRoot "app\build\outputs\apk\debug\app-debug.apk"
    # Always rebuild so the test never runs against a stale APK (Gradle skips it when up to date)
    Write-Host "Building app-debug.apk..."
    & cmd.exe /c ".\gradlew.bat :app:assembleDebug" | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw ":app:assembleDebug failed."
    }
    adb install -r $apkPath 2>&1 | Out-Null

    adb shell pm grant com.safesignal.app android.permission.ACCESS_FINE_LOCATION 2>&1 | Out-Null
    adb shell pm grant com.safesignal.app android.permission.ACCESS_COARSE_LOCATION 2>&1 | Out-Null
    adb shell pm grant com.safesignal.app android.permission.POST_NOTIFICATIONS 2>&1 | Out-Null

    # Clear logcat buffers
    adb logcat -c
    Write-Host "Cleared logcat buffer."

    # Launch app into foreground and start foreground monitoring service
    Write-Host "Launching MainActivity and starting MonitorService..."
    adb shell am start -W -n com.safesignal.app/.MainActivity 2>&1 | Out-Null
    Start-Sleep -Seconds 1
    adb shell am start-foreground-service -a com.safesignal.app.START_MONITORING com.safesignal.app/.service.MonitorService 2>&1 | Out-Null
    Start-Sleep -Seconds 2

    # Set GPS location on emulator while monitoring service is active and listening
    adb emu geo fix -122.4194 37.7749 2>&1 | Out-Null
    Write-Host "Set emulator GPS fix to coordinates (-122.4194, 37.7749)"
    Start-Sleep -Seconds 1

    # --- GATE 1: Service & Server Ready ---
    Write-Host "`n--- Gate 1/4: Verifying Service & Server Ready ---" -ForegroundColor Yellow
    $gate1Logs = adb logcat -d -s SafeSignal
    $hasForeground = $gate1Logs -match "service isForeground=true"
    if (-not $hasForeground -or -not $serverHealthy) {
        throw "GATE 1 FAILED: MonitorService not foreground or Server not healthy."
    }
    Write-Host "[GATE 1 PASS] Server healthy and MonitorService foreground active." -ForegroundColor Green

    # --- GATE 2: Fall Triggered & Escalated ---
    Write-Host "`n--- Gate 2/4: Injecting Synthetic Fall & Verifying Escalation ---" -ForegroundColor Yellow
    Write-Host "Broadcasting INJECT_FALL intent (countdown=${CountdownSeconds}s)..."
    adb shell am broadcast -a com.safesignal.app.INJECT_FALL -p com.safesignal.app --ei countdown_seconds $CountdownSeconds 2>&1 | Out-Null

    $waitSec = $CountdownSeconds + 4
    Write-Host "Waiting ${waitSec}s for countdown expiration and distress dispatch..."
    Start-Sleep -Seconds $waitSec

    $gate2Logs = adb logcat -d -s SafeSignal
    $hasCountdown = $gate2Logs -match "Monitoring -> Countdown\(FALL\)"
    $hasSending = $gate2Logs -match "-> Sending"

    if (-not $hasCountdown -or -not $hasSending) {
        throw "GATE 2 FAILED: Fall did not escalate through Countdown into Sending. Logs: `n$gate2Logs"
    }
    Write-Host "[GATE 2 PASS] Fall successfully triggered and escalated to Sending." -ForegroundColor Green

    # --- GATE 3: Dispatcher Received Alert with GPS & Battery ---
    Write-Host "`n--- Gate 3/4: Verifying Dispatcher Ingestion with Location & Battery ---" -ForegroundColor Yellow
    $alertsJson = curl.exe -s http://localhost:$ServerPort/api/alerts
    Write-Host "Alerts from server: $alertsJson"

    if (-not $alertsJson -or $alertsJson -eq "[]") {
        throw "GATE 3 FAILED: Dispatcher server has no alerts."
    }

    $alerts = $alertsJson | ConvertFrom-Json
    $fallRecord = $alerts | Where-Object { $_.alert.type -eq "FALL" -and $_.alert.workerId -eq "W-1042" } | Select-Object -First 1

    if (-not $fallRecord) {
        $fallRecord = $alerts | Where-Object { $_.alert.type -eq "FALL" } | Select-Object -First 1
    }

    if (-not $fallRecord) {
        throw "GATE 3 FAILED: No FALL alert found on dispatcher server."
    }

    $alertId = $fallRecord.alert.alertId
    $lat = $fallRecord.alert.location.lat
    $lon = $fallRecord.alert.location.lon
    $battery = $fallRecord.alert.batteryPercent

    Write-Host "Received Alert ID   : $alertId"
    Write-Host "Alert Type          : $($fallRecord.alert.type)"
    Write-Host "Location Lat / Lon  : $lat, $lon"
    Write-Host "Battery Level       : $battery%"

    if ($null -eq $lat -or $null -eq $lon -or $null -eq $battery) {
        throw "GATE 3 FAILED: Alert missing GPS coordinates or battery level."
    }

    # Verify coordinates are from the emulator's GPS fix and not the hardcoded fallback (3.1390, 101.6869)
    if ([math]::Abs($lat - 3.1390) -lt 0.001 -and [math]::Abs($lon - 101.6869) -lt 0.001) {
        throw "GATE 3 FAILED: Alert sent fallback coordinates (3.139, 101.6869) rather than the emulator's GPS fix."
    }

    $expectedLat = 37.7749
    $expectedLon = -122.4194
    if ([math]::Abs($lat - $expectedLat) -gt 0.05 -or [math]::Abs($lon - $expectedLon) -gt 0.05) {
        throw "GATE 3 FAILED: Received location ($lat, $lon) does not match expected emulator GPS fix ($expectedLat, $expectedLon)."
    }

    Write-Host "[GATE 3 PASS] Dispatcher received FALL alert with verified emulator GPS fix ($lat, $lon) and battery ($battery%)." -ForegroundColor Green

    # --- GATE 4: Dispatcher Acknowledges Alert & Worker Sees Ack ---
    Write-Host "`n--- Gate 4/4: Dispatcher Acknowledges Alert & Worker Receives Ack ---" -ForegroundColor Yellow
    $dispatcherName = "Dispatcher-Sarah"
    $ackPayload = '{"dispatcher":"' + $dispatcherName + '"}'
    Write-Host "Posting acknowledgement to /api/alerts/$alertId/ack..."

    $ackRecord = Invoke-RestMethod -Uri "http://localhost:$ServerPort/api/alerts/$alertId/ack" -Method Post -ContentType "application/json" -Body $ackPayload
    Write-Host "Server Ack Response: Status=$($ackRecord.status), AckBy=$($ackRecord.acknowledgedBy)"

    # Wait for worker's pollForAck loop to detect acknowledgement and transition
    Write-Host "Waiting up to ${AckPollTimeoutSec}s for worker device to poll and register acknowledgement..."
    $ackSeenOnWorker = $false
    $startAckWait = Get-Date

    while (-not $ackSeenOnWorker -and ((Get-Date) - $startAckWait).TotalSeconds -lt $AckPollTimeoutSec) {
        $recentLogs = adb logcat -d -s SafeSignal
        if ($recentLogs -match "Acknowledged" -or $recentLogs -match "ACK_SEEN") {
            $ackSeenOnWorker = $true
            break
        }
        Start-Sleep -Seconds 1
    }

    if (-not $ackSeenOnWorker) {
        $finalLogs = adb logcat -d -s SafeSignal
        throw "GATE 4 FAILED: Worker beacon did not transition to Acknowledged within $AckPollTimeoutSec s. Logs: `n$finalLogs"
    }

    Write-Host "[GATE 4 PASS] Worker beacon received acknowledgement from $dispatcherName." -ForegroundColor Green

    # Summary
    Write-Host "`n==========================================================" -ForegroundColor Green
    Write-Host "                    E2E PASS (4/4)" -ForegroundColor Green
    Write-Host "==========================================================" -ForegroundColor Green
    Write-Host "Gate 1/4: MonitorService foreground & Server ready        [PASS]" -ForegroundColor Green
    Write-Host "Gate 2/4: Fall detected & escalated to distress signal    [PASS]" -ForegroundColor Green
    Write-Host "Gate 3/4: Dispatcher received alert with GPS & Battery    [PASS]" -ForegroundColor Green
    Write-Host "Gate 4/4: Dispatcher ack received & acknowledged on worker[PASS]" -ForegroundColor Green

    $exitCode = 0

} catch {
    Write-Host "`n==========================================================" -ForegroundColor Red
    Write-Host "                    E2E FAILED" -ForegroundColor Red
    Write-Host "==========================================================" -ForegroundColor Red
    Write-Host "Error: $($_.Exception.Message)" -ForegroundColor Red
    $exitCode = 1
} finally {
    if ($spawnedServer -and $serverProcess -and -not $serverProcess.HasExited) {
        Write-Host "`nCleaning up spawned server process (PID: $($serverProcess.Id))..."
        Stop-Process -Id $serverProcess.Id -Force -ErrorAction SilentlyContinue
        $portPids = Get-NetTCPConnection -LocalPort $ServerPort -ErrorAction SilentlyContinue | Select-Object -ExpandProperty OwningProcess -Unique
        foreach ($pidToKill in $portPids) {
            Stop-Process -Id $pidToKill -Force -ErrorAction SilentlyContinue
        }
    }
}

exit $exitCode
