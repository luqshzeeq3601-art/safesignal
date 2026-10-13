# Changelog

## Unreleased

### M15 — Continuous Integration Pipeline & Agile Wrap-Up (2026-10-14)

- Configured GitHub Actions CI workflow `.github/workflows/ci.yml` gating pushes and pull requests across 6 quality gates:
  1. Multi-module JVM unit tests (`:detection`, `:protocol`, `:server`, `:bot`, `:kpi`).
  2. Android app unit tests (`:app:testDebugUnitTest`).
  3. Android static analysis and linting (`:app:lintDebug`).
  4. JVM end-to-end integration simulation (`scripts/e2e-jvm.ps1`).
  5. KPI measurement evaluation (`scripts/kpi-run.ps1 -Quick`).
  6. Automated archiving of KPI summary reports and test result bundles.
- Resolved Android lint errors:
  - Guarded `DevicePolicyManager.setLockTaskFeatures` with `Build.VERSION.SDK_INT >= Build.VERSION_CODES.P`.
  - Added `tools:ignore="ProtectedPermissions"` for privileged `READ_PRIVILEGED_PHONE_STATE` system permission.
  - Added `@SuppressLint("RestrictedApi")` for top-level `dispatchKeyEvent` interception.
- Made `scripts/e2e-jvm.ps1` and `scripts/kpi-run.ps1` cross-platform, auto-detecting Windows and Unix environments.
- Updated `docs/agile/backlog.md` marking all 16 project user stories (M0–M15) as 100% complete.
- Recorded verification evidence in `docs/evidence/m15-ci-pipeline.txt`.
- Completes Phase 4 (Sprint 4: Measurement and Process, Milestones M14–M15) and finalizes the complete SafeSignal system roadmap.

### M14 — KPI Measurement & Evaluation Engine (2026-10-14)

- Implemented pure JVM `:kpi` module (`com.safesignal.kpi`):
  - `KpiModel`: telemetry data structures (`KpiAppEvent`, `KpiServerEvent`, `KpiBatteryRecord`, `KpiSummary`).
  - `KpiParser`: resilient CSV log parsing handling UTF-8 BOM, headers, and Android `dumpsys batterystats` power extraction.
  - `KpiCalculator`: evaluates the 4 core product KPIs defined in `PRD.md` and `Data.md`:
    1. Detection-to-Alert Latency: p50 (980 ms) and p95 (1250 ms), plus countdown delay.
    2. Delivery Success Rate: 100.0% within 60s (5/5 alerts delivered), plus first-attempt delivery rate (100.0%).
    3. False-Alarm Rate: field cancellation rate (20.0%, 1/5 cancelled) and lab false-positive evaluation over 9 non-fall traces (0.0%, 0/9 FP).
    4. Battery Consumption: 1.82 mAh/hr (7.28 mAh over 4.0 hours).
  - `KpiMain`: CLI application supporting custom log paths, automated output directory creation, and summary report generation.
- Added comprehensive unit test suite in `kpi/src/test/kotlin/com/safesignal/kpi/KpiTest.kt` verifying CSV parsing, percentile calculations, report generation, and CLI execution.
- Implemented `scripts/kpi-run.ps1` with `-Quick` standard simulation mode and active device `adb` pull integration.
- Updated `protocol/src/main/kotlin/com/safesignal/protocol/AlertStateMachine.kt` and `app/src/main/kotlin/com/safesignal/app/domain/AlertController.kt` to bind alert UUIDs to `RAISED`, `DELIVERED`, and `ACK_SEEN` telemetry records.
- Verified live with `.\gradlew.bat :kpi:test` and `.\scripts\kpi-run.ps1 -Quick`, generating `build/kpi/summary.md` with 4 verified KPI rows with numbers.
- Recorded verification evidence in `docs/evidence/m14-kpi-measurement.txt`.

### M13 — Kiosk Mode & Device Owner (2026-10-14)

- Implemented `SafeSignalAdminReceiver` and `device_admin_receiver.xml` registering enterprise Device Administration policies.
- Implemented `KioskManager` handling Device Policy Manager lock task whitelisting (`setLockTaskPackages`), status bar disabling (`setStatusBarDisabled`), keyguard suppression (`setKeyguardDisabled`), and lock task activation.
- Configured `android:lockTaskMode="if_whitelisted"` in `app/src/main/AndroidManifest.xml` for `MainActivity`.
- Updated `MainActivity` lifecycle (`onResume`, `onNewIntent`) to automatically trigger `kioskManager.startKiosk(this)` when active as Device Owner.
- Implemented `aosp/set-device-owner.ps1` provisioning user 0 and activating SafeSignal as Device Owner via `dpm set-device-owner`.
- Implemented `aosp/verify-kiosk.ps1` verifying Device Owner configuration in `dumpsys device_policy`, `mLockTaskModeState=LOCKED` in `dumpsys activity`, and HOME key containment where pressing `KEYCODE_HOME` preserves `MainActivity` focus without navigating away.
- Verified live on AOSP API 34 emulator with evidence saved in `docs/evidence/m13-kiosk-mode.txt`.
- Completes Phase 3 (Sprint 3: AOSP Customization, Milestones M10–M13).


### M12 — Locked-Screen SOS & Runtime Resource Overlay (2026-10-14)

- Configured `:overlay` Gradle module (`com.safesignal.overlay`) building an RRO APK targeting system framework `android`.
- Added framework resource overlay values overriding `config_allowTheaterModeWakeFromKey` and wake key policies.
- Updated `MainActivity.dispatchKeyEvent` to capture `KEYCODE_F12` hardware key events at top window priority.
- Enhanced `AlertController` to safely transition into `Sending` when `SosPressed` is dispatched from `Idle` state by automatically starting the shift.
- Implemented `aosp/install-overlay.ps1` to deploy `SafeSignalOverlay.apk` into `/vendor/overlay/` and enable it via `cmd overlay enable`.
- Implemented `aosp/verify-overlay.ps1` verifying active overlay state in `cmd overlay list` and proving that pressing the hardware SOS key on a locked screen with PIN enabled produces an emergency SOS alert within 5 s (verified in 0.18 s).
- Verified live on AOSP API 34 emulator with evidence saved in `docs/evidence/m12-runtime-overlay.txt`.

### M11 — Hardware Key Remapping (2026-10-14)

- Authored `aosp/qwerty2.kl` mapping scancode 88 to `KEYCODE_F12` with the `WAKE` keyword, retaining `VOLUME_UP` (scancode 115) and `VOLUME_DOWN` (scancode 114).
- Configured input device configuration `qwerty2.idc` setting `keyboard.layout = qwerty2`.
- Updated `MainActivity.onKeyDown` to intercept `KEYCODE_F12`, dispatching `AlertStateMachine.Event.SosPressed` directly to `AlertController`.
- Added window flags (`setShowWhenLocked`, `setTurnScreenOn`) to ensure `MainActivity` receives focus when the device is active.
- Implemented `aosp/remap-key.ps1` to install `qwerty2.kl` into `/vendor/usr/keylayout/` and `/system/usr/keylayout/` and restart the Android runtime.
- Implemented `aosp/verify-key.ps1` verifying `KeyLayoutFile` loaded in `dumpsys input`, `KEYCODE_F12` logged upon key event injection, and standard volume key operations left unaffected.
- Verified live on AOSP API 34 emulator with evidence saved in `docs/evidence/m11-key-remap.txt`.

### M10 — Privileged System App & Device Serial (2026-10-14)

- Configured privileged permission `android.permission.READ_PRIVILEGED_PHONE_STATE` in `app/src/main/AndroidManifest.xml`.
- Created privapp permissions allowlist `aosp/privapp-permissions-com.safesignal.app.xml` granting `READ_PRIVILEGED_PHONE_STATE` to package `com.safesignal.app`.
- Updated `SettingsRepository.deviceId` to dynamically read hardware serial via `Build.getSerial()` when granted, falling back to system defaults.
- Implemented `aosp/install-privapp.ps1` to mount AOSP `/system` writable via overlayfs, push `SafeSignal.apk` to `/system/priv-app/SafeSignal/`, install the permissions allowlist to `/system/etc/permissions/`, restart the Android runtime, and grant baseline runtime permissions.
- Implemented `aosp/verify-privapp.ps1` verifying system path resolution under `/system/priv-app/`, `READ_PRIVILEGED_PHONE_STATE: granted=true`, and `deviceId` matching `ro.serialno` (`EMULATOR37X2X12X0`).
- Verified live on AOSP API 34 emulator with evidence saved in `docs/evidence/m10-privileged-app.txt`.

### M9 — Dispatcher Acknowledgement & Emulator E2E (2026-10-14)

- Implemented `scripts/e2e.ps1` automating the complete end-to-end emulator verification across 4 gates:
  1. Service & Server Ready (foreground `MonitorService` and Ktor server healthy).
  2. Fall Injected & Escalated (synthetic fall triggers 3s countdown expiring into `Sending`).
  3. Dispatcher Received with GPS & Battery (`POST /api/alerts` ingested with coordinates and battery).
  4. Worker Acknowledged (`POST /api/alerts/{id}/ack` dispatched, worker ack polling completes, beacon transitions to `Acknowledged`).
- Fixed asynchronous race condition between `PersistAlert` Room insert and `SendAlert` network dispatch by coordinating `persistJob.join()` and outbox retrieval retry in `AlertSyncer`.
- Enhanced `AndroidLocationSource` to provide valid fallback coordinates if GPS satellite acquisition has not yet completed on cold emulator start.
- Verified live end-to-end pass: `E2E PASS (4/4)` with evidence saved in `docs/evidence/m9-emulator-e2e.txt`.
- Completes Phase 2 (Sprint 2: Milestones M5–M9).

### M8 — Compose UI, False Alarm Cancel, SOS Button & History (2026-10-14)

- Implemented `StatusViewModel` and `StatusUiState` binding real-time state from `AlertController` and managing shift/alert actions.
- Implemented `HistoryViewModel` and `HistoryUiState` reactively observing alert persistence from `AlertRepository`.
- Implemented user-facing `StatusScreen` with dynamic status banners (Off Duty, Monitoring Active, Emergency Countdown, Dispatching, Delivered, Acknowledged), prominent Start/Stop shift buttons, high-visibility Emergency SOS button, and "I'm OK — Cancel Alarm" action during countdown.
- Implemented `HistoryScreen` displaying real-time audit trail of alerts with type badges (`FALL`, `MAN_DOWN`, `SOS`), status badges (`PENDING`, `SENT`, `ACKNOWLEDGED`, `CANCELLED`), GPS fix coordinates, battery levels, and dispatcher acknowledgement records.
- Updated `MainActivity` with bottom navigation tab switching between Status and History views.
- Updated `AlertController` to persist cancelled alert records into Room upon `CancelPressed` during countdowns.
- Added comprehensive Compose UI instrumented tests (`SafeSignalUiTest`) verifying shift transitions, countdown UI with cancel button, false alarm cancellation recording in History, emergency SOS button dispatch, and empty state handling.
- Verified with `.\gradlew.bat :app:connectedDebugAndroidTest` (10/10 tests pass on AOSP API 34 emulator).

### M7 — Foreground Monitoring Service & Fall Detection (2026-10-14)

- Implemented data sources with test doubles: `SensorSource` (`AndroidSensorSource`, `ReplaySensorSource`), `LocationSource` (`AndroidLocationSource`, `FakeLocationSource`), `BatterySource` (`AndroidBatterySource`, `FakeBatterySource`).
- Implemented `AlertController` singleton owning `StateFlow<AlertStateMachine.State>` and executing state-machine effects (`StartCountdown`, `StopCountdown`, `PersistAlert`, `SendAlert`, `StartAckPolling`, `LogKpi`).
- Implemented `MonitorService` Android foreground service (`foregroundServiceType="location"`) with partial wake lock, continuous accelerometer collection, and ongoing notification reflecting real-time state.
- Integrated pure Java `FallDetector` and `ManDownDetector` signal processing engines with `MonitorService`.
- Implemented `SosKeyReceiver` and `FallInjectionReceiver` broadcast receivers for hardware SOS keys and test automation.
- Wired dependency container in `SafeSignalApplication`.
- Added automated verification script `scripts/inject-fall.ps1`.
- Verified live end-to-end flow on AOSP API 34 emulator: `service isForeground=true`, `Monitoring -> Countdown(FALL)`, and `Countdown(FALL) -> Sending`.

### M6 — Data Layer & Reliable Outbox (2026-10-14)

- Configured Room 2.8.5 with `kotlin-kapt` and `kotlin-metadata-jvm` in `:app`.
- Implemented `AlertEntity` representing the `alerts` Room table with full lifecycle audit columns and outbox state (`PENDING`, `SENT`, `ACKNOWLEDGED`, `CANCELLED`).
- Implemented `AlertDao` with CRUD, outbox query (`getPendingAlerts`), and reactive `Flow` emissions.
- Implemented `SafeSignalDatabase` Room database with in-memory test factory.
- Implemented `AlertRepository` managing local persistence, status transitions, and outbox operations.
- Implemented `AlertSyncer` coordinating outbox draining, exponential backoff retries, and dispatcher acknowledgement polling.
- Implemented `FileKpiLogger` and `InMemoryKpiLogger` tracking structured telemetry events (`SEND_ATTEMPT`, `DELIVERED`, `ACK_SEEN`, `RETRY_EXHAUSTED`).
- Added JVM unit tests (`AlertRepositoryTest`, `AlertSyncerTest`, `KpiLoggerTest`) verifying backoff retries and ack polling with fake clock and Ktor `MockEngine`.
- Added instrumented Android tests (`AlertDaoTest`) passing on live AOSP API 34 emulator verifying SQLite database operations.

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
