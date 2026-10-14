# M9 — AlertSyncer Room Outbox Race Condition

## Symptom
During early end-to-end integration testing on the emulator, synthetic falls raised an alert and transitioned to `Sending`, but `AlertSyncer` failed to find the alert in the Room outbox database table (`alerts`), reporting `0 pending alerts` and preventing the alert from being transmitted to the dispatcher server.

## Root Cause
In `AlertController`, transitions into `Sending` trigger two distinct effects in sequence:
1. `PersistAlert(alert)` — writes the initial `PENDING` alert entity to SQLite via `AlertDao.insert(entity)`.
2. `SendAlert(alertId)` — requests `AlertSyncer.drainOutbox()` to drain all pending alerts and POST them over HTTP.

However, `PersistAlert` was executed asynchronously inside a background coroutine (`coroutineScope.launch`) without coordination. Because SQLite write transactions require disk I/O, `SendAlert` executed on a separate thread and immediately called `alertDao.getPendingAlerts()` before the SQLite insert transaction had committed. As a result, the outbox was momentarily empty when queried.

## Fix
The race condition was eliminated using a two-tier approach:
1. **Coroutine Coordination**: In `AlertController`, the job returned by the persistence dispatch is tracked, ensuring subsequent delivery actions await transaction completion when chained.
2. **Resilient Outbox Drainage**: In `AlertSyncer.drainOutbox()`, if a specific `alertId` is actively requested but not immediately returned by `getPendingAlerts()`, a short backoff retry (50 ms up to 3 attempts) polls the DAO before concluding the outbox is truly empty.

## Verification
- Verified by instrumented tests in `app/src/androidTest/kotlin/com/safesignal/app/AlertDaoTest.kt`.
- Verified live on the AOSP API 34 emulator via `scripts/e2e.ps1`, consistently achieving `E2E PASS (4/4)` across cold and warm service runs.
