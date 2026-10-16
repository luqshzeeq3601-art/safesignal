# Security & Privileged App Hardening Audit

**Project:** SafeSignal Lone Worker Emergency Beacon  
**Date:** October 2026 (claims re-checked against the code on 2026-10-16)  
**Scope:** Android app (`:app`), Device Admin / kiosk mode, alert transport to `:server`  
**Methodology:** STRIDE threat modeling against `AndroidManifest.xml`, `KioskManager.kt`, `MainActivity.kt` and `ServerRoutes.kt`.

> **Correction (2026-10-16).** An earlier version of this audit described MQTT, TLS 1.3, a `:shared` module, a `KioskKeyReceiver` and unexported components. None of these exist in the code. This version describes what the code does, and lists the gaps as open findings.

---

## 1. Executive Summary

| Security Domain | Status | Finding |
|---|---|---|
| **Data protection at rest** | PASS | `android:allowBackup="false"`; alerts live only in the app's private Room database. |
| **Device admin & kiosk** | PASS | App is Device Owner; lock-task mode with `LOCK_TASK_FEATURE_KEYGUARD` so the lock screen still works. |
| **Exported components** | FIXED | `MonitorService` and `SosKeyReceiver` are `exported="false"`. `FallInjectionReceiver` exists only in debug builds and requires `android.permission.DUMP`. |
| **Network transport** | **OPEN** | Plain HTTP (`usesCleartextTraffic="true"`), no TLS, no authentication. Acceptable for the emulator demo only. |
| **Server input validation** | PARTIAL | Rejects unparseable JSON and blank `alertId` / `workerId`. No range checks on coordinates or battery. |

---

## 2. Threat Model (STRIDE Assessment)

| Threat | Risk | Scenario | Current mitigation | Status |
|---|---|---|---|---|
| **Spoofing (S)** | High | Any installed app raises a fake alert or fall. | `SosKeyReceiver` and `MonitorService` are not exported. `FallInjectionReceiver` (`INJECT_FALL`, `START_MONITORING`) is debug-only and requires `android.permission.DUMP` (`signature\|privileged\|development`), which the adb shell holds and third-party apps cannot obtain. | FIXED |
| **Tampering (T)** | High | Another app stops monitoring or cancels a countdown. | Kiosk mode stops the worker leaving the app, and `MonitorService` is `exported="false"`, so `STOP_MONITORING`, `CANCEL` and `RESET` are only accepted from inside the app. | FIXED |
| **Repudiation (R)** | Med | Dispute over whether an alert was sent or seen. | Room rows keep `raisedAtEpochMs`, `sentAtEpochMs`, `acknowledgedAtEpochMs` and `acknowledgedBy`; the server appends to `alerts.jsonl`. | PASS |
| **Information disclosure (I)** | High | Worker location leaks through backup or the network. | Backup disabled. Location travels in cleartext HTTP. | PARTIAL |
| **Denial of service (D)** | High | Network outage stops an alert. | Room outbox; `AlertSyncer` retries with exponential backoff (1 s to 15 s, 10 attempts). Proven with a real server outage in `scripts/e2e-jvm.ps1`. | PASS |
| **Elevation of privilege (E)** | Med | External apps drive the service. | No component that drives alerts is reachable without `DUMP`; the only such entry point is absent from release builds. | FIXED |

---

## 3. Detailed Component Audits

### 3.1 Android Manifest & Component Exports
- **`android:allowBackup="false"`**: set, so `adb backup` and cloud backup cannot copy alert history.
- **`SafeSignalAdminReceiver`**: exported, protected by `android.permission.BIND_DEVICE_ADMIN`. Correct.
- **`MonitorService`**: `exported="false"` (fixed 2026-10-16). It handles `START_MONITORING`, `STOP_MONITORING`, `INJECT_FALL`, `SOS`, `CANCEL` and `RESET`, now only from explicit intents inside the app (`StatusViewModel`, `FallInjectionReceiver`).
- **`SosKeyReceiver`**: `exported="false"`, no intent filter (fixed 2026-10-16). Nothing outside the app sent `SOS_KEY`; the hardware SOS key (scancode 88 → `KEYCODE_F12` via `aosp/qwerty2.kl`) is handled in `MainActivity.dispatchKeyEvent`, so the remapped key path is unaffected.
- **`FallInjectionReceiver`**: moved to `app/src/debug/` (fixed 2026-10-16), so release builds do not contain it. In debug builds it is exported with `android:permission="android.permission.DUMP"`, which `adb shell` (uid 2000) holds and third-party apps cannot be granted. It accepts `INJECT_FALL` and `START_MONITORING` and forwards them to `MonitorService`. `scripts/e2e.ps1`, `scripts/inject-fall.ps1` and `scripts/kpi-run.ps1` now start monitoring with `am broadcast -a com.safesignal.app.START_MONITORING` instead of `am start-foreground-service`.
- **Evidence:** `docs/evidence/exported-components-negative-checks-2026-10-16.txt` (adb shell gets `Requires permission not exported` for `STOP_MONITORING` / `RESET`, no receiver resolves `SOS_KEY`, `DUMP` is `signature|privileged|development`), `docs/evidence/exported-components-e2e-2026-10-16.txt` (E2E PASS 4/4 through the debug receiver, adb as uid 2000), `docs/evidence/exported-components-verify-key-2026-10-16.txt` (M11 PASS: remapped F12 still raises SOS) and `docs/evidence/exported-components-gradle-2026-10-16.txt` (`test`, `:app:lintDebug`).

### 3.2 Device Admin & Kiosk Lockdown (`KioskManager.kt`)
- Lock-task features as configured:
  ```kotlin
  dpm.setLockTaskFeatures(adminComponent, DevicePolicyManager.LOCK_TASK_FEATURE_KEYGUARD)
  ```
  Only the keyguard is enabled; home, overview and notifications stay blocked while locked.
- **Screen wake for the SOS key**: `MainActivity` calls `setShowWhenLocked(true)` and `setTurnScreenOn(true)`, and adds `FLAG_KEEP_SCREEN_ON` during a shift, so the first hardware key press raises the alert (see `docs/debugging/reverification-emulator-issues.md`, section 5).

### 3.3 Storage & Transport
- **Room database**: private app storage, indexed on `status` and `raisedAtEpochMs` (two single-column indexes).
- **Transport**: Ktor HTTP over cleartext to `http://10.0.2.2:8080` on the emulator. No TLS, no certificate pinning, no client authentication. The dispatcher `POST /api/alerts/{id}/ack` endpoint is also unauthenticated.
- **Recommended fix before any real deployment:** HTTPS with a network security config that removes cleartext, plus a per-device credential on alert and ack requests.
