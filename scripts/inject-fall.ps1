# SafeSignal — Injected Fall Verification Script (M7)
# Validates that an injected fall transitions the background foreground service
# through Monitoring -> Countdown(FALL) -> Sending.

param(
    [int]$CountdownSeconds = 3,
    [int]$WaitSeconds = 6
)

$ErrorActionPreference = 'Stop'

Write-Host "=== SafeSignal: Fall Injection & Foreground Service Verification ===" -ForegroundColor Cyan

# 1. Check adb device
$deviceState = (adb get-state 2>&1).Trim()
if ($deviceState -ne 'device') {
    Write-Error "No active adb device found (state: $deviceState). Please run scripts/emulator-start.ps1 first."
}
Write-Host "Target device online: $deviceState" -ForegroundColor Green

# 2. Clear logcat buffers
adb logcat -c
Write-Host "Logcat buffer cleared."

# 3. Ensure latest APK is installed
Write-Host "Installing latest app build..."
$installResult = adb install -r "app\build\outputs\apk\debug\app-debug.apk" 2>&1
Write-Host "Install: $installResult"

# Grant necessary runtime permissions for Android 14 FGS location
adb shell pm grant com.safesignal.app android.permission.ACCESS_FINE_LOCATION 2>&1 | Out-Null
adb shell pm grant com.safesignal.app android.permission.ACCESS_COARSE_LOCATION 2>&1 | Out-Null
adb shell pm grant com.safesignal.app android.permission.POST_NOTIFICATIONS 2>&1 | Out-Null

# 4. Bring activity to foreground and start foreground monitoring service
Write-Host "Launching MainActivity and starting MonitorService in foreground..."
adb shell am start -W -n com.safesignal.app/.MainActivity | Out-Null
Start-Sleep -Seconds 1
adb shell am start-foreground-service -a com.safesignal.app.START_MONITORING com.safesignal.app/.service.MonitorService | Out-Null
Start-Sleep -Seconds 2

# 5. Inject synthetic fall event with configured countdown
Write-Host "Broadcasting INJECT_FALL intent with countdown=${CountdownSeconds}s..."
adb shell am broadcast -a com.safesignal.app.INJECT_FALL -p com.safesignal.app --ei countdown_seconds $CountdownSeconds | Out-Null

# 6. Wait for fall detection, countdown ticking, and expiration into Sending
Write-Host "Waiting ${WaitSeconds}s for countdown and sending transition..."
Start-Sleep -Seconds $WaitSeconds

# 7. Dump logcat and verify required patterns
$logs = adb logcat -d -s SafeSignal
Write-Host "`n--- Captured SafeSignal Logs ---" -ForegroundColor Yellow
Write-Host $logs
Write-Host "--------------------------------`n" -ForegroundColor Yellow

$hasForeground = $logs -match "service isForeground=true"
$hasCountdown  = $logs -match "Monitoring -> Countdown\(FALL\)"
$hasSending    = $logs -match "-> Sending"

Write-Host "Verification Checklist:"
Write-Host "  [1] service isForeground=true : $(if ($hasForeground) { 'PASS' } else { 'FAIL' })" -ForegroundColor $(if ($hasForeground) { 'Green' } else { 'Red' })
Write-Host "  [2] Monitoring -> Countdown(FALL) : $(if ($hasCountdown) { 'PASS' } else { 'FAIL' })" -ForegroundColor $(if ($hasCountdown) { 'Green' } else { 'Red' })
Write-Host "  [3] -> Sending : $(if ($hasSending) { 'PASS' } else { 'FAIL' })" -ForegroundColor $(if ($hasSending) { 'Green' } else { 'Red' })

if ($hasForeground -and $hasCountdown -and $hasSending) {
    Write-Host "`nM7 VERIFICATION SUCCESSFUL: Foreground fall monitoring and sending verified!" -ForegroundColor Green
    exit 0
} else {
    Write-Error "M7 VERIFICATION FAILED: One or more required logcat signatures missing."
    exit 1
}
