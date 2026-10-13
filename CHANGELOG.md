# Changelog

## Unreleased

### M5 — Android Toolchain & App Skeleton (2026-10-14)

- Configured Android SDK API 34 (`platforms/android-34`, `build-tools/34.0.0`, `emulator`, AOSP `x86_64` system image).
- Scaffolding `:app` module wired with Jetpack Compose, Material 3, `:detection`, and `:protocol` dependencies.
- Added `scripts/emulator-start.ps1` for automated AOSP emulator startup and `sys.boot_completed` polling.
- Implemented `MainActivity` skeleton with Compose UI (`Status: ok`).
- Verified `:app:assembleDebug`, `:app:lintDebug`, emulator boot (`sys.boot_completed = 1`), APK installation, and cold launch via adb.

### M4 — Fake Worker Bot & JVM E2E (2026-10-14)

- Implemented `:bot` CLI application (`WorkerBot`, `BotMain`) simulating worker shifts and emergency alert flows.
- Implemented network outage simulation and exponential backoff retry verification.
- Added automated JVM end-to-end integration script `scripts/e2e-jvm.ps1`.
- Verified 5/5 alerts delivered and acknowledged with retries > 0 on live Ktor server without Android hardware.

### M3 — Dispatcher Server & Console (2026-10-14)

- Implemented `:server` with Ktor Netty, serving REST endpoints `/api/alerts`, `/api/alerts/{id}/ack`, and `/health`.
- Implemented thread-safe `AlertStore` supporting idempotent duplicate alert ingestion and append-only `alerts.jsonl`.
- Implemented `server_events.csv` KPI logging tracking `RECEIVED`, `DUPLICATE`, and `ACKED` events.
- Created static single-page dispatcher console web dashboard (`/`) with auto-polling (2s interval) and one-click alert acknowledgement.
- Verified live server with unit tests and curl verification commands.

### M2 — Protocol & State Machine (2026-10-13)

- Implemented shared Kotlin serialization wire models (`AlertMessage`, `AlertRecord`, `AckRequest`, `LocationPayload`).
- Implemented pure `AlertStateMachine` covering the complete lone worker emergency lifecycle and effects.
- Implemented `RetryPolicy` for exponential backoff and retry scheduling.
- Implemented `AlertApi` with Ktor HTTP client wrapper and engine injection.
- Verified exact JSON compatibility against `Data.md` specification and tested all state transitions with unit tests.

### M1 — Detection (2026-10-13)

- Implemented pure Java `:detection` module (`AccelSample`, `DetectionEvent`, `DetectorConfig`).
- Implemented `FallDetector` with 3-phase kinematic recognition (free-fall valley, impact shock, and post-fall stillness).
- Implemented `ManDownDetector` tracking prolonged worker immobility.
- Added `scripts/gen-traces.ps1` generating 13 synthetic accelerometer traces.
- Validated 100% detection rate on `fall_*` traces and 0% false positives on `walk_*`, `sit_*`, and `drop_phone_*` traces.

### M0 — Scaffold (2026-10-13)

- Git repository, Gradle 9.8.1 wrapper, version catalog.
- Five empty JVM modules: `:detection` (Java), `:protocol`, `:server`, `:bot`, `:kpi` (Kotlin).
- Baseline documents: PRD, architecture, data, agent rules, skills, security.
- Agile backlog and Definition of Done.
