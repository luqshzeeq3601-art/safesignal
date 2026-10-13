# Sets SafeSignal as Device Owner and provisions kiosk mode
# Usage: .\aosp\set-device-owner.ps1

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

Write-Host "Ensuring user 0 is running and unlocked..."
& $adb.Source shell "am switch-user 0 2>/dev/null" | Out-Null
& $adb.Source shell "input keyevent 82 2>/dev/null" | Out-Null
Start-Sleep -Seconds 1

Write-Host "Setting SafeSignal as Device Owner..."
$dpmOut = (& $adb.Source shell "dpm set-device-owner com.safesignal.app/.admin.SafeSignalAdminReceiver 2>&1") | Out-String
if ($dpmOut -notmatch "Success" -and $dpmOut -notmatch "already set") {
    throw "Failed to set device owner: $dpmOut"
}

Write-Host "Starting MainActivity to activate lock task mode..."
& $adb.Source shell am start --user 0 -n com.safesignal.app/.MainActivity | Out-Null
Start-Sleep -Seconds 2

Write-Host "App is Device Owner: com.safesignal.app/.admin.SafeSignalAdminReceiver"
