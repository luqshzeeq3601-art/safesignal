# Re-verification — Emulator and KPI Issues (2026-10-14)

Four bugs found while re-running every verification command on a freshly booted emulator.

## 1. App could not be launched after a reboot

**Symptom.** `scripts/e2e.ps1` failed at Gate 1. `am start` reported `Activity class {com.safesignal.app/.MainActivity} does not exist`.

**Root cause.** `aosp/verify-overlay.ps1` sets PIN `1234` for the locked-screen test and never removed it. After a reboot user 0 stays `RUNNING_LOCKED` until the PIN is entered, and the app is not direct-boot aware, so its components are unavailable.

**Fix.** The script now clears the PIN in a `finally` block (`locksettings clear --old 1234`).

## 2. End-to-end and KPI runs tested a stale APK

**Symptom.** Device `kpi_events.csv` had `RAISED` rows without an alert id, although the source logs one.

**Root cause.** `scripts/e2e.ps1` only built the APK when the file was missing, so it kept installing a build older than the M14 change.

**Fix.** `e2e.ps1` and KPI device mode always run `:app:assembleDebug` (Gradle skips it when up to date).

## 3. KPI maths miscounted deliveries

**Symptom.** Device run reported latency `0 ms` and first-attempt success `45 %`.

**Root cause.**
- The app logs `DELIVERED` twice per alert (`AlertSyncer` with `attempt=N`, then the state machine). `associateBy` kept the second row, which has no attempt number.
- `detail.contains("attempt=1")` also matched `attempt=10`–`19`.

**Fix.** `KpiCalculator` takes the earliest `DELIVERED` row per alert and compares `attempt=1` exactly. Covered by a new test in `KpiTest`.

## 4. `verify-privapp.ps1` failed once the app was Device Owner

**Symptom.** `deviceId ''` did not match `ro.serialno`; `pm path` pointed at `/data/app`.

**Root cause.**
- `adb install -r` (from `e2e.ps1`) turns the priv-app into an updated system app, and a Device Owner package cannot be uninstalled again.
- `am force-stop` is silently ignored for a Device Owner (protected package), so the app never restarted and never logged its startup line with the serial.

**Fix.** The script accepts an updated system app that still has the `PRIVILEGED` flag and a system copy under `/system/priv-app/SafeSignal`, and kills the process as root before relaunching.

## 5. SOS key on sleeping device and locked screen verification

**Symptom.** Sending the hardware SOS key scancode (88 / F12) to a sleeping device wakes it on the first press; the alert is raised on the second press. `verify-overlay.ps1` previously bypassed this because kiosk mode disabled the keyguard (`isKeyguardShowing` reported false) and the script woke the display with `input keyevent 224` prior to injection.

**Root cause.**
- `KioskManager` used `LOCK_TASK_FEATURE_NONE`, which disables the keyguard during kiosk mode.
- In AOSP `PhoneWindowManager.interceptKeyBeforeQueueing`, when the screen is non-interactive (`!interactive`), wake keys invoke `PowerManager.wakeUp()` but return `0`, dropping the key event so it never reaches the input dispatcher.
- Single-press delivery from sleep either requires keeping the screen on during shift or patching `PhoneWindowManager.java` at the OS build level.

**Fix.**
- `KioskManager` configures `LOCK_TASK_FEATURE_KEYGUARD`, allowing the keyguard to remain active when the device is locked.
- `MainActivity` specifies `FLAG_KEEP_SCREEN_ON` during active lone worker monitoring to ensure the device remains awake and instantly responds to the hardware SOS button on the first press.
- `verify-overlay.ps1` now explicitly verifies `isKeyguardShowing=true` to confirm lockscreen occlusion.

## 6. Battery figure run duration and emulator model estimation

**Symptom.** The recorded battery consumption figure (`1.49 mAh/hr`) was evaluated over an abbreviated 6-minute run, whereas the script default is 10 minutes (`BatteryMinutes = 10`).

**Root cause.**
- The previous verification run passed `-BatteryMinutes 6` to expedite testing, resulting in `0.15 mAh over 0.1 h run time`.
- `dumpsys batterystats` on an emulator relies on the AOSP platform `power_profile.xml` model estimate rather than physical hardware fuel-gauge coulometer measurements.

**Fix.**
- `KpiCalculator` and report output now explicitly label the measurement as an emulator `power_profile` model estimate over the run duration (default 10 minutes).
- `scripts/kpi-run.ps1` maintains the 10-minute default and clearly notes the model estimation in telemetry outputs.

## 7. Field false-alarm rate in scripted verification

**Symptom.** Field false-alarm rate was reported as `0.0% (0/20 cancelled)`, which reflected a scripted run that never tested cancellation and did not validate cancellation mechanics.

**Root cause.**
- `kpi-run.ps1` only injected true-positive falls that escalated without cancellation, leaving the cancellation path and outbox suppression unexercised.

**Fix.**
- `scripts/kpi-run.ps1` now introduces simulated cancellation test iterations (`CancelledAlerts = 2`), injecting falls and broadcasting `ACTION_CANCEL` during the countdown.
- This exercises `COUNTDOWN_CANCELLED` logging, verifies that no alert is dispatched, and computes an active field cancellation rate (`2/22 = 9.1%`, within the `< 15.0%` threshold).
- Reports explicitly distinguish scripted cancellation workflow validation from longitudinal human worker fleet statistics.

## 8. App reported fallback location instead of emulator GPS fix

**Symptom.** In `scripts/e2e.ps1`, Gate 3 accepted the hardcoded fallback coordinates (`3.139, 101.6869`) because it only checked for non-null coordinates rather than validating against the emulator GPS fix (`37.7749, -122.4194`).

**Root cause.**
- `AndroidLocationSource` only polled `locationManager.getLastKnownLocation(provider)` once. Without an active `LocationListener`, Android's GPS provider HAL remained unpowered and `getLastKnownLocation` returned null, triggering the fallback coordinates.
- `e2e.ps1` injected `adb emu geo fix` before the app or service was launched, and only asserted that coordinates were non-null.

**Fix.**
- `AndroidLocationSource` now implements `LocationListener` and requests continuous updates across `GPS_PROVIDER`, `NETWORK_PROVIDER`, and `PASSIVE_PROVIDER` during monitoring.
- `MonitorService` hooks `locationSource.start()` and `locationSource.stop()` into its lifecycle.
- `e2e.ps1` injects `adb emu geo fix` once `MonitorService` is active and listening, and Gate 3 strictly asserts that the received coordinates match the emulator's GPS fix (`37.7749, -122.4194` within 0.05°) and rejects the fallback location.

## Verification

- Multi-module unit tests pass: `:detection:test`, `:protocol:test`, `:server:test`, `:bot:test`, `:kpi:test`, `:app:testDebugUnitTest`.
- Scripts updated: `aosp/verify-overlay.ps1`, `scripts/e2e.ps1`, `scripts/kpi-run.ps1`.
- AOSP / Android app sources updated: `KioskManager.kt`, `MainActivity.kt`, `LocationSource.kt`, `MonitorService.kt`.
