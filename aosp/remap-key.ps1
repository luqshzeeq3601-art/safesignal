# Installs custom KeyLayoutFile remapping scancode 88 to KEYCODE_F12
# Usage: .\aosp\remap-key.ps1

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

$klPath = "$PSScriptRoot\qwerty2.kl"
if (-not (Test-Path $klPath)) {
    throw "Key layout file not found at $klPath"
}

Write-Host "Pushing qwerty2.kl to /vendor/usr/keylayout/ and /system/usr/keylayout/..."
& $adb.Source push $klPath /vendor/usr/keylayout/qwerty2.kl
& $adb.Source shell chmod 644 /vendor/usr/keylayout/qwerty2.kl
& $adb.Source push $klPath /system/usr/keylayout/qwerty2.kl
& $adb.Source shell chmod 644 /system/usr/keylayout/qwerty2.kl

Write-Host "Configuring qwerty2.idc keyboard.layout = qwerty2..."
& $adb.Source shell "sed -i 's/keyboard.layout = .*/keyboard.layout = qwerty2/' /system/usr/idc/qwerty2.idc 2>/dev/null"
& $adb.Source shell "sed -i 's/keyboard.layout = .*/keyboard.layout = qwerty2/' /vendor/usr/idc/qwerty2.idc 2>/dev/null"

Write-Host "Restarting Android runtime (stop/start) to reload EventHub key layouts..."
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
    throw "Timed out waiting for Android runtime restart."
}

Write-Host "Key layout remapping complete."
