# Battery & Performance Optimization Audit

**Project:** SafeSignal Lone Worker Emergency Beacon  
**Date:** October 2026 (claims re-checked against the code and evidence on 2026-10-16)  
**Scope:** Background service lifecycle, sensor sampling, location, wake lock, Room indexing, battery measurement.  
**Reference tooling:** `dumpsys batterystats` on the `safesignal_api34` emulator.

> **Correction (2026-10-16).** An earlier version described 5 Hz idle sampling switching to 50 Hz on impact, a battery saving of over 65%, a 1.70%/hour drain, 1,120 ms latency, sub-2.4 ms Room queries and physical-device tests on an XCover6 Pro / Zebra TC58. None of these appear in the code or in `docs/evidence/`. This version reports only what was measured.

---

## 1. Executive Summary

Measured in the device-mode KPI run, 2026-10-14 (`docs/evidence/reverification-2026-10-14.txt`).

| Metric | Target | Measured | Status | Notes |
|---|---|---|---|---|
| **Battery use** | < 5.0 mAh/h | **1.49 mAh/h** | PASS | Emulator `power_profile.xml` model estimate over a 10-minute run, not a hardware fuel-gauge reading. |
| **Raise-to-delivery latency** | p50 < 5 s, p95 < 15 s | **p50 539 ms, p95 548 ms** | PASS | 20 alerts. The countdown is excluded; it was 3 s in this run (default 15 s). |
| **Room query latency** | — | not measured | — | Indexes exist (section 3.1); no timing was recorded. |
| **GPS fix in alert** | Real fix, not fallback | **Verified** | PASS | `scripts/e2e.ps1` Gate 3 checks the alert carries the emulator fix `37.7749, -122.4194`. |

---

## 2. Sensor & Location Sampling

### 2.1 Accelerometer (fall and man-down)
- **Fixed rate:** `SensorSource` registers with `SENSOR_DELAY_GAME` (about 50 Hz) for the whole shift and unregisters when monitoring stops. There is no adaptive low-rate idle mode.
- **Opportunity (not implemented):** a lower idle rate, or the hardware significant-motion sensor, could cut idle drain. Any change must be re-checked against the 13 labelled traces, which are recorded at 50 Hz.

### 2.2 Location (`LocationSource.kt`)
- During monitoring, `AndroidLocationSource` requests updates from the GPS, network and passive providers (minimum interval 1 s, minimum distance 0 m), because the AOSP image has no Play services.
- When an alert is built, the newest of the cached fix and each provider's last-known location is used.
- **Finding:** if no provider has a fix, the alert carries hard-coded coordinates (`3.1390, 101.6869`, accuracy 15 m) timestamped "now". A dispatcher cannot tell this from a real fix. Sending `location = null`, as `Data.md` describes, would be safer.
- **Opportunity:** a 1 s GPS interval for a whole shift is costly on hardware; a longer interval while monitoring and a fresh request on alert would save power.

---

## 3. Database & Wake Lock

### 3.1 Room indexing
`AlertEntity.kt` declares two single-column indexes:
```kotlin
@Entity(
    tableName = "alerts",
    indices = [
        Index(value = ["status"]),
        Index(value = ["raisedAtEpochMs"])
    ]
)
```
The outbox query (`status = 'PENDING'`) uses the `status` index instead of a full scan. History is small (one row per alert), so no timing benchmark was run.

### 3.2 Wake lock
- `MonitorService` acquires one `PARTIAL_WAKE_LOCK` when the shift starts, with a 12-hour safety timeout, and releases it when the shift stops.
- The lock is held for the whole shift, not only during the countdown or retries. That keeps sensors and retries running with the screen off, and is the main battery cost to measure on real hardware.

---

## 4. Battery Measurement Note
- All battery figures come from the emulator and the AOSP `power_profile.xml` model. No physical device has been measured.
- `scripts/kpi-run.ps1` (device mode) runs a timed 10-minute unplugged session and reads `dumpsys batterystats`. `-Quick` mode reports battery as not measured.
