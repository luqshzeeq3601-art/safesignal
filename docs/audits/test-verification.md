# Regression & Test-Driven Verification Report

**Project:** SafeSignal Lone Worker Emergency Beacon  
**Date:** October 2026 (claims re-checked against the code and evidence on 2026-10-16)  
**Scope:** JVM and Android unit tests, instrumented tests, JVM outage simulation, KPI pipeline, emulator end-to-end gates

> **Correction (2026-10-16).** An earlier version cited "111 Gradle tasks", "10 lint tasks", "9 retries", a `:shared` module, test classes that do not exist (`AlertPayloadTest`, `OutboxQueueTest`, `FallDetectionAlgorithmTest`), `QUEUED` / `TRANSMITTING` statuses and a 30-second stillness window. This version counts `@Test` methods in the source and quotes the saved evidence.

---

## 1. Executive Summary

| Suite | Command | Size | Result | Evidence |
|---|---|---|---|---|
| **JVM & Android unit tests** | `.\gradlew.bat test` | 78 tests in 6 modules | PASS | CI, `docs/evidence/m1`–`m8` |
| **Instrumented tests** | `.\gradlew.bat :app:connectedDebugAndroidTest` | 10 tests (emulator, not in CI) | PASS | `docs/evidence/m6-data-layer.txt`, `m8-ui-and-history.txt` |
| **Android lint** | `.\gradlew.bat :app:lintDebug` | — | PASS | CI, `docs/evidence/m15-ci-pipeline.txt` |
| **Server outage (JVM)** | `scripts/e2e-jvm.ps1` | 5 alerts | PASS: 5/5 delivered, 5/5 acknowledged, 5 retries | `docs/evidence/reverification-2026-10-14.txt` |
| **KPI pipeline (device mode)** | `scripts/kpi-run.ps1` | 4 KPIs, 22 alerts | PASS 4/4 | `docs/evidence/reverification-2026-10-14.txt` |

---

## 2. Test Execution Breakdown

### 2.1 Unit tests (`.\gradlew.bat test`)

| Module | Tests | Test classes | What they cover |
|---|---|---|---|
| `:detection` | 18 | `AccelSampleTest`, `DetectorConfigTest`, `FallDetectorTest`, `ManDownDetectorTest` | All 13 labelled traces; free fall without impact, impact without free fall, one-sample glitches, movement during the 2 s stillness window. |
| `:protocol` | 24 | `AlertStateMachineTest`, `RetryPolicyTest`, `AlertMessageJsonTest`, `AlertApiTest` | Every state-machine transition, backoff (1 s to 15 s, 10 attempts), JSON round-trip, HTTP client against a mock engine. |
| `:server` | 11 | `AlertStoreTest`, `ServerApiTest` | 201 new / 200 duplicate `alertId` / 400 invalid, acknowledgement, `alerts.jsonl` persistence. |
| `:bot` | 3 | `WorkerBotTest` | Send, retry and ack polling. |
| `:kpi` | 5 | `KpiTest` | CSV parsing and KPI maths (first `DELIVERED` per alert, exact `attempt=1`). |
| `:app` | 17 | `AlertControllerTest`, `AlertRepositoryTest`, `AlertSyncerTest`, `KpiLoggerTest` | Effects run by the controller, outbox status changes (`PENDING` → `SENT` → `ACKNOWLEDGED`, or `CANCELLED`), retry loop with a fake clock. |

Instrumented (`:app:connectedDebugAndroidTest`, 10 tests): `AlertDaoTest` (Room queries on a real SQLite database) and `SafeSignalUiTest` (Compose status and history screens).

### 2.2 Server outage simulation (`scripts/e2e-jvm.ps1`)
1. Start the Ktor server and wait for `/health`.
2. Stop the server.
3. Start the bot. Its first alert fails against the stopped server.
4. Restart the server while the bot is retrying.
5. **Result (2026-10-14):** the first alert was delivered on attempt 6 after 5 refused attempts; the other 4 were delivered on the first attempt. 5/5 delivered, 5/5 acknowledged.

The bot drives the real `AlertStateMachine` and `RetryPolicy` (with a shorter 400 ms initial backoff) against the real server. It does not use the app's Room outbox; the outbox retry path is covered by `AlertSyncerTest` and by the emulator run below.

### 2.3 False-alarm cancellation (`scripts/kpi-run.ps1`)
- The device-mode run injects 2 falls and sends `com.safesignal.app.CANCEL` during the countdown.
- **Result:** 2 of 22 detections cancelled (9.1%, target < 15%), and no alert was sent for either. This proves the cancel path works; it is not a measure of real worker behaviour.

### 2.4 GPS gate (`scripts/e2e.ps1`)
- Gate 3 injects an emulator fix after `MonitorService` starts and requires the alert to carry `37.7749, -122.4194` (within 0.05°).
- It fails if the alert carries the hard-coded fallback `3.139, 101.6869`.
