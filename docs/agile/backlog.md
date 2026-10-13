# Product Backlog

One story per milestone, in build order. The verification command is the acceptance test. Commands run from the project root in PowerShell.

Status: ✅ done · ⏳ next · ⬜ to do

| # | Status | User story | Verification command | Expected result |
|---|---|---|---|---|
| M0 | ✅ | As a developer, I want a scaffolded multi-module build and baseline docs, so every later story has a place to land | `.\gradlew.bat projects` | `BUILD SUCCESSFUL`; lists `:detection :protocol :server :bot :kpi` |
| M1 | ✅ | As a lone worker, I want falls and man-down detected from the accelerometer, so help is called when I cannot call | `.\gradlew.bat :detection:test` | All pass; every `fall_*` trace detected, 0 detections on `walk_* / sit_* / drop_phone_*` |
| M2 | ✅ | As a developer, I want one shared message format and a pure alert state machine, so app, server and bot agree | `.\gradlew.bat :protocol:test` | All pass; JSON output matches the sample in `Data.md` |
| M3 | ✅ | As a dispatcher, I want a console that lists alerts and lets me acknowledge them | `.\gradlew.bat :server:test`, then `.\gradlew.bat :server:run` and `curl.exe -s http://localhost:8080/health` | Tests pass; curl prints `OK`; console lists a POSTed alert |
| M4 | ✅ | As a tester, I want a fake worker and a JVM end-to-end script, so delivery and retry are proven without a device | `.\scripts\e2e-jvm.ps1` | `5/5 delivered, 5/5 acknowledged, retries>0`; exit code 0 |
| M5 | ✅ | As a developer, I want the Android toolchain and an app skeleton running on an AOSP emulator | `.\gradlew.bat :app:assembleDebug :app:lintDebug`, `.\scripts\emulator-start.ps1`, `adb shell am start -W com.safesignal.app/.MainActivity` | `BUILD SUCCESSFUL`; `sys.boot_completed` = 1; `Status: ok` |
| M6 | ✅ | As a lone worker, I want alerts stored on the device and retried, so a bad network does not lose one | `.\gradlew.bat :app:testDebugUnitTest` and the `AlertDaoTest` instrumented run | Unit tests pass (fake clock + `MockEngine`); DAO tests pass on emulator |
| M7 | ⏳ | As a lone worker, I want monitoring to keep running in the background during my shift | `.\scripts\inject-fall.ps1` then `adb logcat -d -s SafeSignal` | `Monitoring -> Countdown(FALL)` then `-> Sending`; service `isForeground=true` |
| M8 | ⬜ | As a lone worker, I want to see status, cancel a false alarm, press SOS and view history | `.\gradlew.bat :app:connectedDebugAndroidTest` | All pass |
| M9 | ⬜ | As a dispatcher, I want an alert with location and battery, and the worker to see my acknowledgement | `.\scripts\e2e.ps1` | `E2E PASS (4/4)` |
| M10 | ⬜ | As a device integrator, I want the app installed as a privileged system app, so it can read the device serial | `.\aosp\install-privapp.ps1; .\aosp\verify-privapp.ps1` | Path under `/system/priv-app/`; permission `granted=true`; `deviceId` = `ro.serialno` |
| M11 | ⬜ | As a lone worker, I want a hardware key to act as an SOS button | `.\aosp\remap-key.ps1; .\aosp\verify-key.ps1` | New KeyLayoutFile loaded; key logs `KEYCODE_F12`; volume unchanged |
| M12 | ⬜ | As a lone worker, I want the SOS key to work when the device is locked | `.\aosp\install-overlay.ps1; .\aosp\verify-overlay.ps1` | Overlay enabled; locked-screen key press produces an `SOS` alert within 5 s |
| M13 | ⬜ | As an employer, I want the device locked to SafeSignal (kiosk) | `.\aosp\set-device-owner.ps1; .\aosp\verify-kiosk.ps1` | App is Device Owner; `mLockTaskModeState=LOCKED`; HOME stays in `MainActivity` |
| M14 | ⬜ | As a product owner, I want four KPIs measured by a script | `.\gradlew.bat :kpi:test` and `.\scripts\kpi-run.ps1 -Quick` | Tests pass; `build\kpi\summary.md` has 4 KPI rows with numbers |
| M15 | ⬜ | As a team, we want CI to gate every change | `gh run list --limit 1` | Latest run `completed  success` |

## Sprint grouping

| Sprint | Stories | Goal |
|---|---|---|
| 1 | M0–M4 | Alerts flow bot → server → ack on the JVM |
| 2 | M5–M9 | Injected fall on the emulator becomes an acknowledged alert |
| 3 | M10–M13 | AOSP customization verified by scripts |
| 4 | M14–M15 | KPIs and CI |
