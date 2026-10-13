# Verifies SafeSignal is installed as a privileged system app with READ_PRIVILEGED_PHONE_STATE
# and matches ro.serialno.
# Usage: .\aosp\verify-privapp.ps1

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

Write-Host "1. Checking APK installation path..."
$pathOut = & $adb.Source shell pm path com.safesignal.app 2>$null
if (-not $pathOut -or $pathOut -notmatch "package:(/system.*priv-app.*SafeSignal.*apk)") {
    throw "Verification failed: Package path '$pathOut' is not under /system/priv-app/"
}
$privPath = $Matches[1]
Write-Host "Path under /system/priv-app/: $privPath"

Write-Host "2. Checking privileged permission READ_PRIVILEGED_PHONE_STATE..."
$dump = & $adb.Source shell dumpsys package com.safesignal.app 2>$null
$permMatch = $dump | Select-String -Pattern "android\.permission\.READ_PRIVILEGED_PHONE_STATE:\s*granted=true"
if (-not $permMatch) {
    throw "Verification failed: READ_PRIVILEGED_PHONE_STATE is not granted=true"
}
Write-Host "permission android.permission.READ_PRIVILEGED_PHONE_STATE: granted=true"

Write-Host "3. Verifying deviceId matches ro.serialno..."
$roSerial = (& $adb.Source shell getprop ro.serialno).Trim()
if (-not $roSerial) {
    throw "Failed to read ro.serialno from device"
}

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

# Force stop app, clear logcat, and launch MainActivity
& $adb.Source shell am force-stop com.safesignal.app
Start-Sleep -Seconds 1
& $adb.Source logcat -c
& $adb.Source shell am start --user 0 -n com.safesignal.app/.MainActivity | Out-Null
Start-Sleep -Seconds 3

# Check logcat for deviceId
$logs = & $adb.Source logcat -d -s SafeSignal 2>$null
$deviceLogMatch = $logs | Select-String -Pattern "Device:\s*([^\s,]+)"
$reportedSerial = if ($deviceLogMatch) { $deviceLogMatch.Matches[0].Groups[1].Value } else { "" }

if ($reportedSerial -ne $roSerial) {
    throw "Verification failed: deviceId '$reportedSerial' does not match ro.serialno '$roSerial'"
}

Write-Host "deviceId = ro.serialno ($roSerial)"
Write-Host "M10 PASS"
