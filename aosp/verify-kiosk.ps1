# Verifies that SafeSignal is Device Owner, lock task mode is LOCKED, and HOME stays in MainActivity
# Usage: .\aosp\verify-kiosk.ps1

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

Write-Host "1. Verifying SafeSignal is Device Owner..."
$dpmDump = (& $adb.Source shell "dumpsys device_policy 2>/dev/null") | Out-String
if ($dpmDump -notmatch "admin=ComponentInfo\{com\.safesignal\.app/com\.safesignal\.app\.admin\.SafeSignalAdminReceiver\}") {
    throw "Verification failed: SafeSignal is not configured as Device Owner"
}
Write-Host "App is Device Owner: com.safesignal.app/.admin.SafeSignalAdminReceiver"

Write-Host "2. Verifying LockTaskModeState is LOCKED..."
$activityDump = (& $adb.Source shell "dumpsys activity 2>/dev/null") | Out-String
if ($activityDump -notmatch "mLockTaskModeState=LOCKED") {
    # Attempt to launch MainActivity to trigger startLockTask() if not currently focused
    & $adb.Source shell am start --user 0 -n com.safesignal.app/.MainActivity | Out-Null
    Start-Sleep -Seconds 2
    $activityDump = (& $adb.Source shell "dumpsys activity 2>/dev/null") | Out-String
    if ($activityDump -notmatch "mLockTaskModeState=LOCKED") {
        throw "Verification failed: mLockTaskModeState is not LOCKED"
    }
}
Write-Host "Lock task mode: mLockTaskModeState=LOCKED"

Write-Host "3. Verifying HOME key is locked and stays in MainActivity..."
& $adb.Source shell input keyevent KEYCODE_HOME
Start-Sleep -Milliseconds 800

$windowDump = (& $adb.Source shell "dumpsys window 2>/dev/null") | Out-String
if ($windowDump -notmatch "mCurrentFocus=Window\{.*com\.safesignal\.app/com\.safesignal\.app\.MainActivity\}") {
    # Fallback check for top resumed activity
    $amDump = (& $adb.Source shell "dumpsys activity activities 2>/dev/null") | Out-String
    if ($amDump -notmatch "topResumedActivity=ActivityRecord\{.*com\.safesignal\.app/\.MainActivity") {
        throw "Verification failed: HOME key exited MainActivity or current focus changed"
    }
}
Write-Host "HOME key pressed: HOME stays in MainActivity"

Write-Host ""
Write-Host "Summary: App is Device Owner; mLockTaskModeState=LOCKED; HOME stays in MainActivity"
Write-Host "M13 PASS"
