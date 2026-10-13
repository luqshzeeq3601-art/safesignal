# Starts an AOSP API 34 emulator and polls adb until boot completes.
# Usage: .\scripts\emulator-start.ps1 [-AvdName <string>] [-TimeoutSec <int>] [-Headless]

param(
    [string]$AvdName = "aosp_api34",
    [int]$TimeoutSec = 180,
    [switch]$Headless
)

$ErrorActionPreference = "Stop"

# Ensure Android SDK binaries are in PATH
$androidSdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { "$HOME\AppData\Local\Android\Sdk" }
$platformTools = Join-Path $androidSdk "platform-tools"
$emulatorDir = Join-Path $androidSdk "emulator"

if (Test-Path $platformTools) {
    if ($env:Path -notlike "*$platformTools*") {
        $env:Path = "$platformTools;$env:Path"
    }
}
if (Test-Path $emulatorDir) {
    if ($env:Path -notlike "*$emulatorDir*") {
        $env:Path = "$emulatorDir;$env:Path"
    }
}

$adb = Get-Command adb -ErrorAction SilentlyContinue
$emulator = Get-Command emulator -ErrorAction SilentlyContinue

if (-not $adb -or -not $emulator) {
    throw "adb or emulator executable not found in PATH or Android SDK directory ($androidSdk)"
}

Write-Host "Ensuring AVD '$AvdName' is configured..."
$avdCheck = & $emulator.Source -list-avds
if ($avdCheck -notcontains $AvdName) {
    Write-Host "AVD '$AvdName' not found. Creating via .\scripts\create-avd.ps1..."
    & "$PSScriptRoot\create-avd.ps1"
}

# Check if an emulator is already booted
Write-Host "Checking for existing emulator instances..."
$devices = & $adb.Source devices | Where-Object { $_ -match "emulator-\d+\s+device" }
if ($devices) {
    $bootCompleted = & $adb.Source shell getprop sys.boot_completed 2>$null
    if ($bootCompleted -and $bootCompleted.Trim() -eq "1") {
        Write-Host "Emulator is already running and booted (sys.boot_completed = 1)."
        exit 0
    }
}

Write-Host "Starting emulator '$AvdName'..."
$emulatorArgs = @("-avd", $AvdName, "-no-snapshot-load", "-gpu", "auto")
if ($Headless) {
    $emulatorArgs += @("-no-window", "-no-audio")
}

$proc = Start-Process -FilePath $emulator.Source -ArgumentList $emulatorArgs -PassThru

Write-Host "Waiting for device to connect via adb..."
& $adb.Source wait-for-device

Write-Host "Waiting for Android boot completion (sys.boot_completed = 1)..."
$startTime = [System.DateTime]::UtcNow
$booted = $false

while (([System.DateTime]::UtcNow - $startTime).TotalSeconds -lt $TimeoutSec) {
    Start-Sleep -Seconds 3
    $status = & $adb.Source shell getprop sys.boot_completed 2>$null
    if ($status -and $status.Trim() -eq "1") {
        $booted = $true
        break
    }
    Write-Host "Still booting... ($([int]([System.DateTime]::UtcNow - $startTime).TotalSeconds)s elapsed)"
}

if (-not $booted) {
    throw "Timed out after $TimeoutSec seconds waiting for emulator to boot."
}

Write-Host "Emulator booted successfully! sys.boot_completed = 1"
