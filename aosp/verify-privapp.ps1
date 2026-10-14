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
$dump = & $adb.Source shell dumpsys package com.safesignal.app 2>$null
if ($pathOut -and $pathOut -match "package:(/system.*priv-app.*SafeSignal.*apk)") {
    $privPath = $Matches[1]
} else {
    # A later `adb install -r` (e.g. scripts/e2e.ps1) turns the app into an updated system app:
    # `pm path` then points at /data/app, but the package keeps its PRIVILEGED flag and the
    # system copy stays registered under "Hidden system packages".
    $systemCopy = $dump | Select-String -Pattern "codePath=(/system/priv-app/SafeSignal)"
    $updatedSystemApp = $dump | Select-String -Pattern "pkgFlags=\[.*UPDATED_SYSTEM_APP"
    $privFlag = $dump | Select-String -Pattern "privateFlags=\[.*PRIVILEGED"
    if (-not $systemCopy -or -not $updatedSystemApp -or -not $privFlag) {
        throw "Verification failed: Package path '$pathOut' is not under /system/priv-app/"
    }
    $privPath = "$($systemCopy.Matches[0].Groups[1].Value) (updated system app, PRIVILEGED)"
}
Write-Host "Path under /system/priv-app/: $privPath"

Write-Host "2. Checking privileged permission READ_PRIVILEGED_PHONE_STATE..."
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

# Restart the app process so it logs its startup line with the device serial.
# `am force-stop` is silently ignored once the app is Device Owner (M13, protected package),
# so a process that survives it is killed as root instead.
& $adb.Source logcat -c
& $adb.Source shell am force-stop com.safesignal.app
Start-Sleep -Seconds 1
$appPid = (& $adb.Source shell pidof com.safesignal.app | Out-String).Trim()
if ($appPid) {
    & $adb.Source root | Out-Null
    & $adb.Source wait-for-device
    & $adb.Source shell kill $appPid
    Start-Sleep -Seconds 1
}
& $adb.Source shell am start --user 0 -n com.safesignal.app/.MainActivity | Out-Null

# Check logcat for deviceId
$reportedSerial = ""
$startTime = [System.DateTime]::UtcNow
while (-not $reportedSerial -and ([System.DateTime]::UtcNow - $startTime).TotalSeconds -lt 15) {
    Start-Sleep -Seconds 1
    $logs = & $adb.Source logcat -d -s SafeSignal 2>$null
    $deviceLogMatch = $logs | Select-String -Pattern "Device:\s*([^\s,]+)"
    if ($deviceLogMatch) { $reportedSerial = $deviceLogMatch.Matches[0].Groups[1].Value }
}

if ($reportedSerial -ne $roSerial) {
    throw "Verification failed: deviceId '$reportedSerial' does not match ro.serialno '$roSerial'"
}

Write-Host "deviceId = ro.serialno ($roSerial)"
Write-Host "M10 PASS"
