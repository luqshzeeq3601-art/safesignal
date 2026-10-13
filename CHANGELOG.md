# Changelog

## Unreleased

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
