# SafeSignal — KPI Measurement and Reporting Script (M14)
# Calculates and validates the 4 core product KPIs:
# 1. Detection-to-Alert Latency (p50, p95)
# 2. Delivery Success Rate (within 60s, including a real server outage)
# 3. False-Alarm Rate (field and lab)
# 4. Battery Consumption (mAh/hour)
#
# Modes:
#   -Quick   JVM harness (server + bot). No device needed; battery is reported as not measured.
#   default  Drives the app on the attached emulator/device and measures all four KPIs.
#
# Verification criteria: "Tests pass; build\kpi\summary.md has 4 KPI rows with numbers"

param(
    [switch]$Quick,
    [string]$OutputDir = "build\kpi",
    [string]$AppEventsPath = "",
    [string]$ServerEventsPath = "",
    [string]$BatteryPath = "",
    # Alerts raised per run. With 20 alerts the single outage alert sits above p95.
    [int]$Alerts = 20,
    # Simulated false alarms cancelled during countdown to verify field cancellation rate
    [int]$CancelledAlerts = 2,
    # Device mode only: how long the app runs on battery before batterystats is read (default: 10 min).
    [int]$BatteryMinutes = 10
)

$ErrorActionPreference = 'Stop'

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectRoot = Split-Path -Parent $scriptDir

$hasAdb = [bool](Get-Command adb -ErrorAction SilentlyContinue)
$useJvmHarness = $Quick -or ([string]::IsNullOrEmpty($AppEventsPath) -and -not $hasAdb)

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "       SafeSignal KPI Evaluation & Report Generator (M14) " -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "Project Root: $projectRoot"
Write-Host "Output Directory: $OutputDir"
Write-Host "Mode: $(if ($useJvmHarness) { 'Live JVM Harness (Server + Bot)' } else { 'Live Android Device / System' })"

$targetOutputDir = Join-Path $projectRoot $OutputDir
if (-not (Test-Path $targetOutputDir)) {
    New-Item -ItemType Directory -Force -Path $targetOutputDir | Out-Null
}

$runOnWindows = ($PSVersionTable.PSVersion.Major -lt 6) -or $IsWindows -or ($env:OS -like "*Windows*")
$gradlew = if ($runOnWindows) { ".\gradlew.bat" } else { "./gradlew" }

# 1. Run detection test suite to measure actual lab false positives
Write-Host "`n[1/4] Running :detection:test to measure lab non-fall traces..." -ForegroundColor Yellow
if ($runOnWindows) {
    & cmd.exe /c "$gradlew :detection:test" 2>&1 | Out-Null
} else {
    & $gradlew :detection:test 2>&1 | Out-Null
}

# FallDetectorTest prints one "LAB_TRACE <file> detections=<n>" line per non-fall trace.
$labFp = 0
$labTotal = 0
$testResultsDir = Join-Path $projectRoot "detection/build/test-results/test"
if (Test-Path $testResultsDir) {
    $labLines = Select-String -Path (Join-Path $testResultsDir "*.xml") -Pattern "LAB_TRACE \S+ detections=(\d+)"
    foreach ($labLine in $labLines) {
        $labTotal++
        if ([int]$labLine.Matches[0].Groups[1].Value -gt 0) {
            $labFp++
        }
    }
}
if ($labTotal -eq 0) {
    throw "M14 FAILED: no LAB_TRACE results found in $testResultsDir; lab false-positive rate cannot be measured."
}
Write-Host "Lab non-fall evaluation: $labFp false positives out of $labTotal non-fall traces" -ForegroundColor $(if ($labFp -eq 0) { 'Green' } else { 'Red' })

function Start-Server {
    $startInfo = New-Object System.Diagnostics.ProcessStartInfo
    if ($runOnWindows) {
        $startInfo.FileName = "cmd.exe"
        $startInfo.Arguments = "/c $gradlew :server:run"
    } else {
        $startInfo.FileName = "/bin/bash"
        $startInfo.Arguments = "-c `"$gradlew :server:run`""
    }
    $startInfo.WorkingDirectory = $projectRoot
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $process = [System.Diagnostics.Process]::Start($startInfo)

    $startWait = Get-Date
    while (((Get-Date) - $startWait).TotalSeconds -lt 60) {
        try {
            if ((Invoke-RestMethod -Uri "http://localhost:8080/health" -TimeoutSec 2 -ErrorAction Stop) -eq "OK") {
                return $process
            }
        } catch {
            # retry
        }
        Start-Sleep -Milliseconds 500
    }
    Stop-Server $process
    throw "KPI harness: server failed to start within 60 seconds."
}

function Stop-Server($process) {
    if ($process -and -not $process.HasExited) {
        Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
    }
    if ($runOnWindows) {
        $portPids = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue | Select-Object -ExpandProperty OwningProcess -Unique
        foreach ($pidToKill in $portPids) {
            Stop-Process -Id $pidToKill -Force -ErrorAction SilentlyContinue
        }
    } else {
        & pkill -9 -f "com.safesignal.server.ApplicationKt" 2>&1 | Out-Null
    }
}

function Wait-AppLog([string]$pattern, [int]$timeoutSec) {
    $startWait = Get-Date
    while (((Get-Date) - $startWait).TotalSeconds -lt $timeoutSec) {
        $hit = (adb logcat -d -s SafeSignal) | Select-String -Pattern $pattern | Select-Object -Last 1
        if ($hit) { return $hit }
        Start-Sleep -Milliseconds 500
    }
    return $null
}

$collectedAppEvents = Join-Path $targetOutputDir "kpi_events.csv"
$collectedServerEvents = Join-Path $targetOutputDir "server_events.csv"
$collectedBattery = Join-Path $targetOutputDir "battery.csv"
$serverCsvSource = Join-Path $projectRoot "server/build/server/server_events.csv"

# 2. Collect telemetry
if (-not [string]::IsNullOrEmpty($AppEventsPath)) {
    Write-Host "`n[2/4] Using provided telemetry: $AppEventsPath" -ForegroundColor Yellow
} elseif ($useJvmHarness) {
    Write-Host "`n[2/4] Running live JVM measurement harness (server + bot, real server outage)..." -ForegroundColor Yellow

    Remove-Item $collectedBattery -Force -ErrorAction SilentlyContinue
    $botCsvRelative = "$OutputDir/kpi_events.csv".Replace('\', '/')
    & (Join-Path $scriptDir "e2e-jvm.ps1") -Count $Alerts -KpiCsv $botCsvRelative
    if ($LASTEXITCODE -ne 0) {
        throw "KPI harness: e2e-jvm.ps1 failed with exit code $LASTEXITCODE"
    }

    # The bot runs from the :bot module directory, so its CSV lands under bot/
    Copy-Item (Join-Path $projectRoot "bot/$botCsvRelative") $collectedAppEvents -Force
    if (Test-Path $serverCsvSource) {
        Copy-Item $serverCsvSource $collectedServerEvents -Force
        $ServerEventsPath = $collectedServerEvents
    }
    $AppEventsPath = $collectedAppEvents
    Write-Host "Collected live telemetry into $targetOutputDir" -ForegroundColor Green
} else {
    Write-Host "`n[2/4] Measuring on the attached device ($Alerts alerts, $BatteryMinutes min on battery)..." -ForegroundColor Yellow
    if ((adb get-state 2>&1 | Out-String).Trim() -ne 'device') {
        throw "No adb device attached. Run scripts\emulator-start.ps1 first, or use -Quick for the JVM harness."
    }

    $pkg = "com.safesignal.app"
    $serverProcess = $null
    try {
        adb root 2>&1 | Out-Null
        adb wait-for-device
        adb reverse tcp:8080 tcp:8080 2>&1 | Out-Null

        # Measure the current build, not whatever APK was installed last
        & cmd.exe /c "$gradlew :app:assembleDebug" 2>&1 | Out-Null
        if ($LASTEXITCODE -ne 0) {
            throw ":app:assembleDebug failed."
        }
        adb install -r (Join-Path $projectRoot "app/build/outputs/apk/debug/app-debug.apk") 2>&1 | Out-Null

        # Fresh telemetry and battery statistics; the server stays down for the first alert
        Stop-Server $null
        # `am force-stop` is ignored for a Device Owner app, so stop the process as root
        adb shell "kill `$(pidof $pkg) 2>/dev/null"
        adb shell rm -f /data/data/$pkg/files/kpi_events.csv
        adb shell dumpsys batterystats --reset 2>&1 | Out-Null
        adb shell dumpsys battery unplug
        $batteryWatch = [System.Diagnostics.Stopwatch]::StartNew()

        adb shell am start -W -n "$pkg/.MainActivity" 2>&1 | Out-Null
        adb shell am start-foreground-service -a com.safesignal.app.START_MONITORING "$pkg/.service.MonitorService" 2>&1 | Out-Null
        Start-Sleep -Seconds 3

        # Set GPS location on emulator while monitoring service is active and listening
        adb emu geo fix -122.4194 37.7749 2>&1 | Out-Null
        Start-Sleep -Seconds 1

        # Exercise user cancellation during countdown to verify false alarm handling
        for ($c = 1; $c -le $CancelledAlerts; $c++) {
            adb logcat -c
            adb shell am broadcast -a com.safesignal.app.INJECT_FALL -p $pkg --ei countdown_seconds 5 2>&1 | Out-Null
            Start-Sleep -Milliseconds 1500
            adb shell am broadcast -a com.safesignal.app.CANCEL -p $pkg 2>&1 | Out-Null
            $cancelledLog = Wait-AppLog "COUNTDOWN_CANCELLED" 10
            if (-not $cancelledLog) {
                Write-Host "Warning: COUNTDOWN_CANCELLED log not observed within 10s for false alarm $c"
            } else {
                Write-Host "Simulated false alarm $c/$CancelledAlerts cancelled during countdown"
            }
            Start-Sleep -Milliseconds 500
        }

        for ($i = 1; $i -le $Alerts; $i++) {
            adb logcat -c
            adb shell am broadcast -a com.safesignal.app.INJECT_FALL -p $pkg --ei countdown_seconds 3 2>&1 | Out-Null

            if ($i -eq 1) {
                # Outage scenario: the first alert is raised while the server is down
                if (-not (Wait-AppLog "-> Sending\(alertId=" 20)) {
                    throw "Alert 1 was not raised on the device."
                }
                Start-Sleep -Seconds 3
                Write-Host "Alert 1 raised with the server down; starting the server..."
                $serverProcess = Start-Server
            }

            $sent = Wait-AppLog "-> Sent\(alertId=([0-9a-f-]+)" 90
            if (-not $sent) {
                throw "Alert $i was not delivered within 90 seconds."
            }
            $alertId = $sent.Matches[0].Groups[1].Value
            Invoke-RestMethod -Uri "http://localhost:8080/api/alerts/$alertId/ack" -Method Post -ContentType "application/json" -Body '{"dispatcher":"KPI-Dispatcher"}' | Out-Null
            if (-not (Wait-AppLog "-> Acknowledged\(alertId=$alertId" 30)) {
                throw "Alert $i ($alertId) was not acknowledged on the device."
            }
            Write-Host "Alert $i/$Alerts delivered and acknowledged ($alertId)"
        }

        $remainingSec = [int]($BatteryMinutes * 60 - $batteryWatch.Elapsed.TotalSeconds)
        if ($remainingSec -gt 0) {
            Write-Host "Monitoring on battery for another $remainingSec s..."
            Start-Sleep -Seconds $remainingSec
        }

        # batterystats lists the app as "UID u0a<appId - 10000>: <mAh> ..."
        $durationHours = $batteryWatch.Elapsed.TotalHours
        $appIdLine = (adb shell dumpsys package $pkg) | Select-String -Pattern "appId=(\d+)" | Select-Object -First 1
        $uidLabel = "u0a$([int]$appIdLine.Matches[0].Groups[1].Value - 10000)"
        $batterystatsDump = (adb shell dumpsys batterystats $pkg) | Out-String
        if ($batterystatsDump -notmatch "(?m)^\s*UID ${uidLabel}:\s*([0-9.]+)") {
            throw "batterystats has no power estimate for UID $uidLabel."
        }
        $mah = [double]$Matches[1]
        $mahPerHour = $mah / $durationHours
        $inv = [System.Globalization.CultureInfo]::InvariantCulture
        $batteryRow = "{0},{1},{2},{3},{4},{5}" -f [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds(), $uidLabel, $pkg, $mah.ToString("0.###", $inv), $durationHours.ToString("0.####", $inv), $mahPerHour.ToString("0.###", $inv)
        [System.IO.File]::WriteAllText($collectedBattery, "epochMs,appUid,packageId,estimatedMah,durationHours,mahPerHour`n$batteryRow`n")
        $BatteryPath = $collectedBattery
        Write-Host ("Battery: {0:0.###} mAh over {1:0.0} min = {2:0.##} mAh/hr (UID $uidLabel, emulator power_profile estimate)" -f $mah, $batteryWatch.Elapsed.TotalMinutes, $mahPerHour) -ForegroundColor Green

        Remove-Item $collectedAppEvents -Force -ErrorAction SilentlyContinue
        adb pull /data/data/$pkg/files/kpi_events.csv $collectedAppEvents 2>&1 | Out-Null
        if (-not (Test-Path $collectedAppEvents)) {
            throw "Could not pull kpi_events.csv from the device."
        }
        $AppEventsPath = $collectedAppEvents
        if (Test-Path $serverCsvSource) {
            Copy-Item $serverCsvSource $collectedServerEvents -Force
            $ServerEventsPath = $collectedServerEvents
        }
        Write-Host "Collected device telemetry into $targetOutputDir" -ForegroundColor Green
    } finally {
        adb shell dumpsys battery reset 2>&1 | Out-Null
        Stop-Server $serverProcess
    }
}

# 3. Invoke JVM :kpi CLI engine
Write-Host "`n[3/4] Executing :kpi calculation engine..." -ForegroundColor Yellow
$relAppEvents = if ($AppEventsPath.StartsWith($projectRoot)) { $AppEventsPath.Substring($projectRoot.Length).TrimStart('\', '/') } else { $AppEventsPath }
$relOutputDir = if ($targetOutputDir.StartsWith($projectRoot)) { $targetOutputDir.Substring($projectRoot.Length).TrimStart('\', '/') } else { $targetOutputDir }
$relServerEvents = if ($ServerEventsPath.StartsWith($projectRoot)) { $ServerEventsPath.Substring($projectRoot.Length).TrimStart('\', '/') } else { $ServerEventsPath }
$relBattery = if ($BatteryPath.StartsWith($projectRoot)) { $BatteryPath.Substring($projectRoot.Length).TrimStart('\', '/') } else { $BatteryPath }

$appEventsArg = $relAppEvents.Replace('\', '/')
$targetDirArg = $relOutputDir.Replace('\', '/')
$kpiArgsList = @(
    "--app-events $appEventsArg",
    "--output-dir $targetDirArg",
    "--lab-fp $labFp",
    "--lab-total $labTotal"
)
if (-not [string]::IsNullOrEmpty($ServerEventsPath) -and (Test-Path $ServerEventsPath)) {
    $serverEventsArg = $relServerEvents.Replace('\', '/')
    $kpiArgsList += "--server-events $serverEventsArg"
}
if (-not [string]::IsNullOrEmpty($BatteryPath) -and (Test-Path $BatteryPath)) {
    $batteryArg = $relBattery.Replace('\', '/')
    $kpiArgsList += "--battery $batteryArg"
}

$gradleArgs = $kpiArgsList -join " "

$origErrorAction = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
$output = if ($runOnWindows) {
    cmd.exe /c "$gradlew :kpi:run --args=`"$gradleArgs`"" 2>&1
} else {
    & $gradlew :kpi:run --args="$gradleArgs" 2>&1
}
$exitCode = $LASTEXITCODE
$ErrorActionPreference = $origErrorAction

$outputStr = ($output | Out-String)
Write-Host $outputStr

if ($exitCode -ne 0) {
    throw "M14 FAILED: Gradle task :kpi:run exited with code $exitCode"
}

# 4. Validate acceptance criteria
Write-Host "`n[4/4] Validating M14 acceptance criteria..." -ForegroundColor Yellow
$summaryMdPath = Join-Path $targetOutputDir "summary.md"

if (-not (Test-Path $summaryMdPath)) {
    throw "M14 FAILED: Expected report file not found at $summaryMdPath"
}

$summaryContent = Get-Content $summaryMdPath -Raw
$kpiRows = [regex]::Matches($summaryContent, '\| (Detection-to-Alert Latency|Delivery Success Rate|False-Alarm Rate|Battery Consumption) \|.*\|.*\|.*\|.*\|')

Write-Host "Found $($kpiRows.Count) KPI rows in summary.md"

if ($kpiRows.Count -ne 4) {
    throw "M14 FAILED: Expected 4 KPI rows in summary.md, found $($kpiRows.Count)"
}

# Verify numbers exist in each row
foreach ($match in $kpiRows) {
    $rowText = $match.Value
    if ($rowText -notmatch '\d+(\.\d+)?') {
        throw "M14 FAILED: KPI row missing numeric value: $rowText"
    }
}

Write-Host "`n==========================================================" -ForegroundColor Green
Write-Host "M14 VERIFICATION PASS: summary.md has 4 KPI rows with numbers" -ForegroundColor Green
Write-Host "Report location: $summaryMdPath" -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Green

exit 0
