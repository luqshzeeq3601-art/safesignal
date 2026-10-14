# Security & Privileged App Hardening Audit

**Project:** SafeSignal Lone Worker Emergency Beacon  
**Date:** October 2026  
**Scope:** Android App (`:app`), Shared Library (`:shared`), Device Admin / Kiosk mode, Alert Ingestion & MQTT Outbox  
**Methodology:** STRIDE threat modeling, OWASP Mobile Security Testing Guide (MSTG), Android Enterprise Security Guidelines.

---

## 1. Executive Summary

| Security Domain | Status | Key Mitigations & Findings |
|---|---|---|
| **Data Protection at Rest** | PASS | Room DB hardened; Android cloud backup disabled (`android:allowBackup="false"`). |
| **Privilege & Device Admin** | PASS | Device Admin policies locked to single owner package; Keyguard & screen wakeup preserved in Kiosk mode. |
| **Network & Outbox Transport** | PASS | Strict TLS verification; exponential backoff retry; persistent disk FIFO queue. |
| **Input & Intent Validation** | PASS | Explicit broadcast targets; exported components audited with strict permissions. |
| **Tamper & Disabling Defense** | PASS | Lock Task Mode (Kiosk) prevents task dismissal or forced uninstallation in field duty. |

---

## 2. Threat Model (STRIDE Assessment)

| Threat | Risk | Mitigation in SafeSignal | Status |
|---|---|---|---|
| **Spoofing (S)** | High | Attackers injecting fake GPS or alert events via broadcasts. | Service accepts broadcasts only with internal action filters and validates source context. Sensor processing runs strictly in-process. | PASS |
| **Tampering (T)** | High | Worker or malicious party killing the safety background service or clearing app data. | Kiosk / Device Owner enforcement (`LOCK_TASK_FEATURE_HOME`, `LOCK_TASK_FEATURE_KEYGUARD`). Service runs as foreground service with sticky notification. | PASS |
| **Repudiation (R)** | Med | Worker claiming alert was not sent, or dispatcher claiming alert was never received. | Room outbox maintains persistent timestamps (`raisedAtEpochMs`), UUIDs, and transit statuses (`QUEUED`, `ACKNOWLEDGED`, `CANCELLED`). | PASS |
| **Information Disclosure (I)** | High | Sensitive lone worker GPS coordinates leaking through Android cloud backups or shared storage. | Explicitly disabled `android:allowBackup="false"` in `AndroidManifest.xml`. Coordinates stored exclusively in private SQLite sandbox. | PASS |
| **Denial of Service (D)** | High | Cellular/WiFi dropouts preventing transmission of emergency signals. | Offline-first SQLite queue. Automatic retry daemon with jittered exponential backoff until ACK is received. | PASS |
| **Elevation of Privilege (E)** | Med | External apps abusing SafeSignal intents or services. | Zero unauthenticated exported services or receivers. Explicit permission boundaries declared. | PASS |

---

## 3. Detailed Component Audits

### 3.1 Android Manifest & Component Exports
- **`android:allowBackup="false"`**: Verified set to prevent ADB / cloud extraction of worker location histories and emergency contacts.
- **Service Declarations**: `MonitorService` is declared with explicit intent filters and is not exported to external applications without signature-level permissions.
- **Receiver Boundaries**: Hardware SOS broadcast receiver (`KioskKeyReceiver`) responds strictly to configured enterprise key events.

### 3.2 Device Admin & Kiosk Lockdown (`KioskManager.kt`)
- **Keyguard Interaction**: Hardened kiosk configuration using:
  ```kotlin
  flags = DevicePolicyManager.LOCK_TASK_FEATURE_HOME or
          DevicePolicyManager.LOCK_TASK_FEATURE_KEYGUARD or
          DevicePolicyManager.LOCK_TASK_FEATURE_OVERVIEW
  ```
- **Screen Wakeup on Emergency**: Verified `FLAG_KEEP_SCREEN_ON` and `FLAG_DISMISS_KEYGUARD` invocation during SOS triggers, resolving the 2-press sleep injection limitation.

### 3.3 Storage & Transport Hardening
- **Room Database**: Indexed `status` and `raisedAtEpochMs` to prevent full-table scan resource exhaustion during large historical audits.
- **Transmission Security**: MQTT / HTTP endpoints enforce TLS 1.3 with certificate verification in production profiles.
