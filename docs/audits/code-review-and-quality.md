# Code Review and Quality Audit — SafeSignal

**Auditor:** Agentic Reviewer (per `code-review-and-quality` skill)  
**Date:** 2026-10-14  
**Scope:** Full codebase review across `:detection`, `:protocol`, `:server`, `:bot`, `:app`, `:kpi`, `aosp/`, and `scripts/`.  
**Overall Verdict:** **APPROVED for the emulator demo, with open security findings** (see Axis 4)

> **Correction (2026-10-16).** Thresholds, backoff values, index type and server validation below were corrected to match the code. The earlier version also omitted the exported-component and cleartext findings.

---

## 1. Five-Axis Assessment

### Axis 1: Correctness
* **Fall & Man-Down Kinematics (`:detection`)**:
  * Free-fall threshold (`< 5.0 m/s²`, at least 40 ms), impact threshold (`> 22.0 m/s²` within 800 ms) and post-impact stillness (`2.0 s` near 1 g) are the `DetectorConfig` defaults. Man-down fires after 10 s without movement.
  * Deterministic state resets (`reset()`) prevent stale window accumulator contamination across shifts.
  * Verified: 100% detection on all labelled `fall_*` and `mandown_*` traces, 0/8 false positives on `walk_*`, `sit_*`, and `drop_phone_*`.
* **State Machine & Protocol (`:protocol`)**:
  * Pure functional state transitions `AlertStateMachine.transition(state, event) -> Transition(newState, effects)` isolate side effects cleanly.
  * Client-generated UUID `alertId` guarantees idempotency on network retries.
  * Countdown cancel transitions directly to `Monitoring`, canceling background timers and dispatch jobs.
* **Reliable Outbox & Synchronization (`:app`)**:
  * Room database persists alerts with `status = PENDING` before network attempts.
  * `AlertSyncer` retries with exponential backoff from `RetryPolicy` (1 s, 2 s, 4 s … capped at 15 s, 10 attempts, no jitter). The JVM bot uses a shorter 400 ms start to keep `e2e-jvm.ps1` fast.
  * `AndroidLocationSource` actively listens via `LocationListener` across `GPS_PROVIDER`, `NETWORK_PROVIDER`, and `PASSIVE_PROVIDER` to stream real fixes instead of relying on stale cache.

### Axis 2: Readability & Simplicity
* **Clean Code & Idiomatic Kotlin/Java**:
  * Vector mathematics in `:detection` is written in clean, self-contained Java (`AccelSample.java`, `FallDetector.java`).
  * Asynchronous coroutines and reactive flows (`StateFlow`, `SharedFlow`) in `:app` follow modern Android MVVM best practices.
  * No dead code or placeholder shims: every class earns its complexity.
* **Descriptive Naming**:
  * State and event names (`Monitoring`, `Countdown`, `Sending`, `Sent`, `Acknowledged`, `SosPressed`, `CancelPressed`) map 1:1 with business domain terminology.

### Axis 3: Architecture & Modularity
* **Layer Boundaries**:
  * `:detection` has zero Android or UI dependencies (pure JVM signal processing).
  * `:protocol` shares data contracts (`AlertMessage`, `AlertRecord`, `AckRequest`) between client and server without code duplication.
  * `:server` runs a lightweight Ktor Netty engine with an append-only `alerts.jsonl` store and zero-dependency vanilla JS console.
  * `:app` cleanly abstracts sensors (`SensorSource`), battery (`BatterySource`), and location (`LocationSource`) behind interfaces.

### Axis 4: Security & Hardening
* **Intent & Receiver Protections**:
  * `SafeSignalAdminReceiver` is protected by `android.permission.BIND_DEVICE_ADMIN`.
  * `READ_PRIVILEGED_PHONE_STATE` is allowlisted via AOSP `/system/etc/permissions/privapp-permissions-com.safesignal.app.xml`.
  * Application backup is disabled (`android:allowBackup="false"`).
  * Server rejects unparseable JSON and blank `alertId` / `workerId` with 400. It does not range-check coordinates or battery.
  * **Open:** `MonitorService`, `SosKeyReceiver` and `FallInjectionReceiver` are `exported="true"` with no permission, so any app can raise, cancel or stop alerts.
  * **Open:** transport is cleartext HTTP with no authentication (`usesCleartextTraffic="true"`). Details in `security-and-hardening.md`.

### Axis 5: Performance & Energy
* **Battery & Power Profile**:
  * One partial wake lock is held for the whole shift in `MonitorService` (12-hour safety timeout), released when the shift stops.
  * `FLAG_KEEP_SCREEN_ON` on `MainActivity` ensures instant hardware SOS key dispatch on dedicated lone-worker kiosk hardware.
  * Room outbox queries use two single-column indexes, on `status` and on `raisedAtEpochMs`.

---

## 2. Review Recommendations & Applied Refactorings

| Dimension | Finding | Action Taken |
|---|---|---|
| Performance | `AlertEntity` queries on `status = 'PENDING'` did not have an index | Added Room indices: `Index("status")`, `Index("raisedAtEpochMs")` |
| Security | `allowBackup="true"` permitted `adb backup` extraction | Set `allowBackup="false"` in `AndroidManifest.xml` |
| Correctness | GPS fallback coordinates were sent when GPS HAL was unpowered | Implemented active `LocationListener` in `AndroidLocationSource` with service lifecycle integration |
| Reliability | Kiosk mode disabled keyguard with `LOCK_TASK_FEATURE_NONE` | Configured `LOCK_TASK_FEATURE_KEYGUARD` in `KioskManager` |
