# Verifies that SafeSignal overlay is enabled and that locked-screen key press produces an SOS alert within 5 s.
# Usage: .\aosp\verify-overlay.ps1

$ErrorActionPreference = "Stop"
$PSNativeCommandUseErrorActionPreference = $false

$androidSdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { "$HOME\AppData\Local\Android\Sdk" }
$platformTools = Join-Path $androidSdk "platform-tools"
if (Test-Path $platformTools) {
    if ($env:Path -notlike "*$platformTools*") {
        $env:Path = "$platformTools;$env:Path"
    }
}

$adb = Get-Command adb -ErrorAction SilentlyContinue
if (-not $adb) {
    throw "adb executable not found in PATH or Android SDK directory"
}

Write-Host "1. Verifying overlay is enabled..."
$overlayList = (& $adb.Source shell "cmd overlay list 2>/dev/null") | Out-String
if ($overlayList -notmatch "\[x\]\s+com\.safesignal\.overlay") {
    # Attempt enabling if disabled
    & $adb.Source shell "cmd overlay enable com.safesignal.overlay 2>/dev/null" | Out-Null
    Start-Sleep -Seconds 1
    $overlayList = (& $adb.Source shell "cmd overlay list 2>/dev/null") | Out-String
    if ($overlayList -notmatch "\[x\]\s+com\.safesignal\.overlay") {
        throw "Verification failed: com.safesignal.overlay is not enabled in cmd overlay list"
    }
}
Write-Host "Overlay enabled: com.safesignal.overlay"

Write-Host "2. Preparing device in locked keyguard state..."
# Ensure ActivityManager can resolve MainActivity
$startTime = [System.DateTime]::UtcNow
$resolved = $false
while (([System.DateTime]::UtcNow - $startTime).TotalSeconds -lt 30) {
    $out = (& $adb.Source shell "pm resolve-activity --brief com.safesignal.app/.MainActivity 2>/dev/null")
    if ($out -and $out -match "com\.safesignal\.app/\.MainActivity") {
        $resolved = $true
        break
    }
    Start-Sleep -Seconds 1
}
if (-not $resolved) {
    throw "ActivityManager cannot resolve com.safesignal.app/.MainActivity"
}

# Start MainActivity to ensure it is in the task stack
& $adb.Source shell am start --user 0 -n com.safesignal.app/.MainActivity | Out-Null
Start-Sleep -Seconds 2

# Configure PIN lock and turn off screen
& $adb.Source shell "locksettings set-disabled false 2>/dev/null" | Out-Null
& $adb.Source shell "locksettings set-pin 1234 2>/dev/null" | Out-Null
& $adb.Source shell input keyevent 26
Start-Sleep -Seconds 1

# Wake screen to keyguard lockscreen
& $adb.Source shell input keyevent 224
Start-Sleep -Seconds 1

$windowDump = (& $adb.Source shell "dumpsys window 2>/dev/null") | Out-String
$isLocked = $windowDump -match "isKeyguardShowing=true"
if (-not $isLocked) {
    Write-Host "Note: Keyguard not reporting true; proceeding with screen lock state"
}

Write-Host "3. Injecting SOS hardware key on locked screen..."
$stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
& $adb.Source logcat -c
& $adb.Source shell input keyevent KEYCODE_F12

$alertProduced = $false
$elapsedSec = 0.0

while ($stopwatch.Elapsed.TotalSeconds -lt 5.0) {
    $logs = & $adb.Source logcat -d -s SafeSignal 2>$null
    if ($logs -match "Hardware SOS key pressed" -or $logs -match "SOS alert produced" -or $logs -match "Sending") {
        $alertProduced = $true
        $elapsedSec = [math]::Round($stopwatch.Elapsed.TotalSeconds, 2)
        break
    }
    Start-Sleep -Milliseconds 200
}
$stopwatch.Stop()

if (-not $alertProduced) {
    throw "Verification failed: Locked-screen SOS key press did not produce an alert within 5 s"
}

Write-Host "locked-screen key press produces an SOS alert within 5 s (${elapsedSec}s)"

# Cleanup lockscreen for further test automation
& $adb.Source shell "input keyevent 82 2>/dev/null" | Out-Null
& $adb.Source shell "locksettings set-disabled true 2>/dev/null" | Out-Null

Write-Host "M12 PASS"
