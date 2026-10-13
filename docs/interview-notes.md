# Interview Notes

One short entry per component: what it is, why it is built this way, and the trade-off.

## M0 — Build scaffold

**What:** A Gradle multi-module build with a version catalog (`gradle/libs.versions.toml`) and five JVM modules.

**Why:** Splitting detection, protocol, server, bot and KPI code into plain JVM modules means most of the logic is tested in seconds without an emulator. The version catalog keeps every dependency version in one file.

**Trade-off:** More build files than a single-module app, and module boundaries have to be respected (no Android imports in JVM modules).

## M1 — Fall and Man-Down Detection

**What:** A pure Java module (`:detection`) implementing `FallDetector`, `ManDownDetector`, `AccelSample`, `DetectorConfig`, and `DetectionEvent`.

**Why:** Keeping signal processing in pure Java with vector mathematics ensures deterministic testing on the JVM in milliseconds. The 3-phase fall model (free-fall valley → impact peak → post-fall immobility) eliminates false positives from walking, sitting, and dropped phones.

**Trade-off:** The 2-second post-impact stillness requirement introduces a brief, deliberate delay before raising an alert, which is necessary to confirm the worker is genuinely down and has not picked up the device.

## M2 — Protocol & Alert State Machine

**What:** Shared wire models (`AlertMessage`, `AlertRecord`, `AckRequest`), pure state machine (`AlertStateMachine`), exponential backoff (`RetryPolicy`), and HTTP client wrapper (`AlertApi`).

**Why:** Defining a pure state machine `transition(state, event) -> (newState, effects)` decouples emergency logic from Android and network I/O, making every state edge testable in milliseconds. Sharing models across `:protocol`, `:server`, `:bot`, and `:app` prevents schema drift. Client-generated `alertId` guarantees POST idempotence.

**Trade-off:** Effects cannot execute inline and must be interpreted by an outer controller, requiring explicit effect definitions.

## M3 — Dispatcher Server & Web Console

**What:** Ktor Netty HTTP server (`:server`), thread-safe `AlertStore` with append-only JSONL persistence, `server_events.csv` KPI logger, and a static web console.

**Why:** Sharing data models directly with `:protocol` eliminates serialization drift. The client-generated `alertId` makes retried POSTs idempotent (returning HTTP 200 on duplicate vs 201 on new). A zero-dependency vanilla JS console with 2-second polling delivers immediate dispatcher visibility and acknowledgement without complex WebSocket infrastructure.

**Trade-off:** 2-second HTTP polling incurs periodic requests rather than push events, but operates reliably across constrained networks without stateful socket maintenance.

## M4 — Fake Worker Bot & JVM End-to-End

**What:** JVM fake worker (`:bot`) CLI simulating emergency raises, backoff retries, and ack polling, paired with `scripts/e2e-jvm.ps1`.

**Why:** Proves that alerts flow from worker to server and through acknowledgement with zero Android dependencies. Validates network retry survival during transient outages and verifies end-to-end integration in seconds on standard CI machines.

**Trade-off:** Simulates radio link failures through HTTP failure responses and backoff delays rather than actual cellular modem disconnection.

## M5 — Android Toolchain & App Skeleton

**What:** Android SDK API 34 setup, `:app` module scaffolding with Compose, and an automated AOSP API 34 emulator startup script (`scripts/emulator-start.ps1`).

**Why:** Establishes the real Android runtime and device verification pipeline without breaking JVM test speed. An AOSP image (without Google Play Services) mirrors target ruggedized enterprise devices and boots quickly.

**Trade-off:** Android SDK compilation and emulator booting add toolchain weight compared to pure JVM modules, but are essential for sensor listeners, foreground services, and privileged system app testing in subsequent milestones.

## M6 — Data Layer & Reliable Outbox

**What:** Room database (`SafeSignalDatabase`, `AlertDao`, `AlertEntity`), repository (`AlertRepository`), reliable outbox syncer (`AlertSyncer`), and telemetry logging (`FileKpiLogger`).

**Why:** Emergency alerts must survive process death and poor radio coverage. The Room outbox stores alerts with `status = PENDING`. `AlertSyncer` applies exponential backoff retry and polls for dispatcher acknowledgement. Using fake clocks and Ktor's `MockEngine` allows full retry and outage verification on the JVM in milliseconds, while `AlertDaoTest` instrumented tests validate SQLite queries on the live AOSP emulator.

**Trade-off:** Running the retry loop in the service process instead of WorkManager minimizes latency and simplifies testing with simulated time, but requires the outbox to be re-scanned when the process or service restarts.

## M7 — Foreground Monitoring Service & Fall Detection

**What:** A foreground Android service (`MonitorService`, type `location`), partial wake lock, accelerometer listener (`SensorSource`), pure state machine orchestrator (`AlertController`), and kinematic fall engine (`FallDetector`).

**Why:** Background sensors on modern Android are throttled or killed unless hosted in a foreground service with a partial wake lock. Decoupling sensor ingestion via `SensorSource` and business transitions into pure `AlertStateMachine` ensures the signal processing remains testable without device hardware, while `FallInjectionReceiver` and `scripts/inject-fall.ps1` enable automated live verification on the AOSP emulator.

**Trade-off:** Android 14+ enforces strict foreground service type policies and requires runtime permissions (`FOREGROUND_SERVICE_LOCATION`, `ACCESS_FINE_LOCATION`) and an eligible app state before starting location-typed services.

## M8 — Compose UI, False Alarm Cancel, SOS Button & History

**What:** Jetpack Compose user interface (`StatusScreen`, `HistoryScreen`, `StatusViewModel`, `HistoryViewModel`, tab navigation in `MainActivity`) and automated instrumented UI tests (`SafeSignalUiTest`).

**Why:** Gives lone workers clear visual indicators of protection state (Off Duty, Monitoring Active, Countdown, Dispatching, Delivered, Acknowledged). The countdown screen features a prominent, unmissable "I'm OK — Cancel Alarm" button to prevent false alarms from burdening central dispatch, while the SOS button provides immediate emergency escalation. The History tab provides an immutable on-device audit trail of all alerts and acknowledgements.

**Trade-off:** Compose UI test automation on emulators requires test synchronization rules and explicit state waiting (`waitUntil`), but ensures high-fidelity regression protection against visual state bugs and lifecycle flaws.

## M9 — Dispatcher Acknowledgement & Emulator End-to-End

**What:** Full-loop integration test script (`scripts/e2e.ps1`) executing across 4 validation gates: foreground monitoring service startup, synthetic fall injection, dispatcher server ingestion with valid GPS location and battery level, and dispatcher acknowledgement observed by the Android worker beacon.

**Why:** Proves that the complete distributed system functions as a cohesive unit on actual Android runtime rather than isolated mocks. Uncovered and eliminated subtle race conditions between asynchronous Room outbox persistence and HTTP network dispatching.

**Trade-off:** End-to-end emulator testing involves multiple moving parts (adb process control, reverse port forwarding, Ktor server process lifecycles, and asynchronous polling), requiring robust timeout management and clean process tree teardown in PowerShell.




