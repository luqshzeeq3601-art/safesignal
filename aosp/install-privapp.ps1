# Installs SafeSignal as a privileged system app on AOSP API 34 emulator
# Usage: .\aosp\install-privapp.ps1

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

Write-Host "Ensuring adb root and remounting /system writable..."
& $adb.Source root | Out-Null
Start-Sleep -Seconds 2

& $adb.Source remount | Out-Null

$apkPath = "$PSScriptRoot\..\app\build\outputs\apk\debug\app-debug.apk"
if (-not (Test-Path $apkPath)) {
    Write-Host "APK not found at $apkPath. Building via .\gradlew.bat :app:assembleDebug..."
    & "$PSScriptRoot\..\gradlew.bat" :app:assembleDebug
}

$xmlPath = "$PSScriptRoot\privapp-permissions-com.safesignal.app.xml"
if (-not (Test-Path $xmlPath)) {
    throw "Allowlist XML not found at $xmlPath"
}

Write-Host "Uninstalling any user-installed package to prevent signature conflict..."
& $adb.Source shell pm uninstall com.safesignal.app 2>$null | Out-Null

Write-Host "Creating /system/priv-app/SafeSignal..."
& $adb.Source shell mkdir -p /system/priv-app/SafeSignal

Write-Host "Pushing SafeSignal.apk to /system/priv-app/SafeSignal/..."
& $adb.Source push $apkPath /system/priv-app/SafeSignal/SafeSignal.apk
& $adb.Source shell chmod 644 /system/priv-app/SafeSignal/SafeSignal.apk

Write-Host "Pushing privapp-permissions allowlist to /system/etc/permissions/..."
& $adb.Source push $xmlPath /system/etc/permissions/privapp-permissions-com.safesignal.app.xml
& $adb.Source shell chmod 644 /system/etc/permissions/privapp-permissions-com.safesignal.app.xml

Write-Host "Restarting Android runtime (stop/start) to discover privileged app..."
& $adb.Source shell stop
Start-Sleep -Seconds 2
& $adb.Source shell start

Write-Host "Waiting for package service and MainActivity resolution..."
$startTime = [System.DateTime]::UtcNow
$ready = $false
while (([System.DateTime]::UtcNow - $startTime).TotalSeconds -lt 90) {
    Start-Sleep -Seconds 2
    $svc = (& $adb.Source shell "service check package 2>/dev/null")
    if ($svc -and $svc -match "package: found") {
        $out = (& $adb.Source shell "pm resolve-activity --brief com.safesignal.app/.MainActivity 2>/dev/null")
        if ($out -and $out -match "com\.safesignal\.app/\.MainActivity") {
            $ready = $true
            break
        }
    }
}

if (-not $ready) {
    throw "Timed out waiting for package service and MainActivity resolution."
}

Write-Host "Granting baseline runtime permissions..."
& $adb.Source shell pm grant com.safesignal.app android.permission.ACCESS_FINE_LOCATION 2>$null
& $adb.Source shell pm grant com.safesignal.app android.permission.ACCESS_COARSE_LOCATION 2>$null
& $adb.Source shell pm grant com.safesignal.app android.permission.POST_NOTIFICATIONS 2>$null

Write-Host "Privileged app installation complete."
