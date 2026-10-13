# Verifies custom KeyLayoutFile is loaded, key logs KEYCODE_F12, and volume is unchanged.
# Usage: .\aosp\verify-key.ps1

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

Write-Host "1. Checking loaded KeyLayoutFile in dumpsys input..."
$inputDump = & $adb.Source shell "dumpsys input 2>/dev/null"
$klMatch = $inputDump | Select-String -Pattern "KeyLayoutFile:\s*(.*qwerty2\.kl)"
if (-not $klMatch) {
    throw "Verification failed: qwerty2.kl is not loaded in dumpsys input"
}
$loadedKl = $klMatch.Matches[0].Groups[1].Value.Trim()
Write-Host "New KeyLayoutFile loaded: $loadedKl"

Write-Host "2. Verifying key logs KEYCODE_F12 in SafeSignal..."
# Launch MainActivity
& $adb.Source shell am start --user 0 -n com.safesignal.app/.MainActivity | Out-Null
Start-Sleep -Seconds 2

# Clear logcat and inject KEYCODE_F12
& $adb.Source logcat -c
& $adb.Source shell input keyevent KEYCODE_F12
Start-Sleep -Seconds 1

$logs = & $adb.Source logcat -d -s SafeSignal 2>$null
$f12Match = $logs | Select-String -Pattern "KEYCODE_F12"
if (-not $f12Match) {
    throw "Verification failed: KEYCODE_F12 not found in logcat after key press"
}
Write-Host "key logs KEYCODE_F12"

Write-Host "3. Verifying volume key behaviour is unchanged..."
# Clear logcat and inject KEYCODE_VOLUME_UP
& $adb.Source logcat -c
& $adb.Source shell input keyevent KEYCODE_VOLUME_UP
Start-Sleep -Seconds 1

$volLogs = & $adb.Source logcat -d -s SafeSignal 2>$null
$volMatch = $volLogs | Select-String -Pattern "KEYCODE_VOLUME_UP"
$sosMatchInVol = $volLogs | Select-String -Pattern "Hardware SOS"

if (-not $volMatch -or $sosMatchInVol) {
    throw "Verification failed: Volume key behaviour was altered or triggered SOS"
}
Write-Host "volume unchanged"

Write-Host "M11 PASS"
