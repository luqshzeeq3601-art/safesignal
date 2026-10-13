# Installs and enables the SafeSignal Runtime Resource Overlay (RRO)
# Usage: .\aosp\install-overlay.ps1

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

Write-Host "Ensuring adb root and remounting /system and /vendor writable..."
& $adb.Source root | Out-Null
Start-Sleep -Seconds 2
& $adb.Source remount | Out-Null

$overlayApk = "$PSScriptRoot\..\overlay\build\outputs\apk\debug\overlay-debug.apk"
if (-not (Test-Path $overlayApk)) {
    Write-Host "Overlay APK not found at $overlayApk. Building via .\gradlew.bat :overlay:assembleDebug..."
    & "$PSScriptRoot\..\gradlew.bat" :overlay:assembleDebug
}

Write-Host "Pushing SafeSignalOverlay.apk to /vendor/overlay/SafeSignalOverlay/..."
& $adb.Source shell mkdir -p /vendor/overlay/SafeSignalOverlay
& $adb.Source push $overlayApk /vendor/overlay/SafeSignalOverlay/SafeSignalOverlay.apk
& $adb.Source shell chmod 644 /vendor/overlay/SafeSignalOverlay/SafeSignalOverlay.apk

Write-Host "Restarting Android runtime (stop/start) to discover overlay..."
& $adb.Source shell stop
Start-Sleep -Seconds 2
& $adb.Source shell start

Write-Host "Waiting for package service, overlay manager, and MainActivity resolution..."
$startTime = [System.DateTime]::UtcNow
$ready = $false
while (([System.DateTime]::UtcNow - $startTime).TotalSeconds -lt 90) {
    Start-Sleep -Seconds 2
    $svc = (& $adb.Source shell "service check overlay 2>/dev/null")
    if ($svc -and $svc -match "overlay: found") {
        $out = (& $adb.Source shell "pm resolve-activity --brief com.safesignal.app/.MainActivity 2>/dev/null")
        if ($out -and $out -match "com\.safesignal\.app/\.MainActivity") {
            $ready = $true
            break
        }
    }
}

if (-not $ready) {
    throw "Timed out waiting for overlay service."
}

Start-Sleep -Seconds 2
Write-Host "Enabling com.safesignal.overlay via cmd overlay..."
& $adb.Source shell "cmd overlay enable com.safesignal.overlay 2>/dev/null"
Start-Sleep -Seconds 1

$overlayList = (& $adb.Source shell "cmd overlay list 2>/dev/null") | Out-String
if ($overlayList -notmatch "\[x\]\s+com\.safesignal\.overlay") {
    throw "Failed to enable com.safesignal.overlay"
}

Write-Host "Overlay enabled: com.safesignal.overlay"
