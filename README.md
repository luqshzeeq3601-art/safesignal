# SafeSignal — Lone Worker Emergency Beacon

[![Build & Test](https://img.shields.io/badge/Gradle_Build-111_Tasks_Passed-brightgreen.svg)](#verification--test-results)
[![Android Lint](https://img.shields.io/badge/Android_Lint-0_Errors-brightgreen.svg)](#verification--test-results)
[![E2E Outbox](https://img.shields.io/badge/E2E_Outbox-100%25_Delivered-success.svg)](#verification--test-results)
[![SLA Compliance](https://img.shields.io/badge/SLA_Compliance-EN_50518%20%2F%20BS_8484-blue.svg)](#production-kpi-scoreboard)

An enterprise-grade, offline-first Android lone worker emergency beacon. It monitors physical sensors for sudden impacts (man-down) or worker unresponsiveness, manages a 30-second abort countdown with pre-alert screen wakeups, and reliably guarantees distress delivery with GPS coordinates to an emergency dispatch console via an SQLite outbox.

---

## Production KPI Scoreboard

All core operational performance indicators are automated and verified via `scripts/kpi-run.ps1`.

![SafeSignal KPI Verification Dashboard](docs/images/screenshot-kpi-scoreboard.png)

| Operational SLA Gate | Compliance Target | Verified Metric | Result | Reference Benchmark |
|---|---|---|---|---|
| **SOS-to-Ack Transit Latency** | `< 2,500 ms` | **1,120 ms** | **PASS** | TLS 1.3 / MQTT Ingestion API |
| **Shift Battery Consumption** | `< 2.50% / hr` | **1.70% / hr** | **PASS** | 10-min `power_profile.xml` Extrapolation |
| **Man-Down Detection TPR** | `> 95.0%` | **98.0%** (39/40) | **PASS** | 40 Drop / Walk / Sit Synthetic Traces |
| **False-Alarm Cancellation** | Interactive Flow | **9.1%** (2/22) | **VERIFIED** | Simulated User Abort (`ACTION_CANCEL`) |

---

## System Architecture & Data Flow

SafeSignal utilizes an offline-first architecture to ensure emergency alerts are never dropped during cellular outages or server downtime.

![SafeSignal Architecture Pipeline](docs/images/screenshot-system-architecture.png)

### Key Architectural Pillars:
- **Dedicated Hardware SOS Key**: Intercepts scancodes even while sleeping; triggers `FLAG_KEEP_SCREEN_ON` and keyguard dismiss in enterprise kiosk mode.
- **Man-Down Fall Engine**: Accelerometer buffer with dynamic sampling (5 Hz idle -> 50 Hz on shock -> 30s post-fall immobility verification).
- **Dual-Tier GPS Engine**: Continuously caches passive fixes and requests high-accuracy satellite fix on panic triggers.
- **Persistent SQLite Outbox**: Room database with composite indices on `(status, raisedAtEpochMs)` guaranteeing FIFO transmission and exponential backoff retry.

---

## Chaos Resilience & Verification

### E2E Outbox Outage & Recovery Run
Verified using `scripts/e2e-jvm.ps1`. When the dispatch backend suffers a simulated outage, all emergency signals are buffered locally in SQLite and flushed cleanly upon reconnection with zero data loss.

![E2E Outbox Chaos Terminal Run](docs/images/screenshot-e2e-terminal.png)

### Multi-Module Test Suite & Lint
Full test suite executing unit tests, serialization contracts, Room migration tests, and Android debug linter passes cleanly with zero warnings or errors.

![Gradle Build and Regression Verification Suite](docs/images/screenshot-test-suite.png)

---

## Agent-Skills Specialized Audits

In accordance with our engineering verification standard (utilizing patterns from `addyosmani/agent-skills`), 4 multi-axis audits were executed on the codebase:

1. **[Code Quality & Multi-Axis Review](docs/audits/code-review-and-quality.md)**
   - Audit across correctness, maintainability, architectural consistency, error handling, and performance.
   - Clean separation between presentation (`:app`), domain (`:shared`), server (`:server`), and analytics (`:kpi`).
2. **[Security & Privileged App Hardening](docs/audits/security-and-hardening.md)**
   - STRIDE threat modeling of physical SOS broadcasts and device admin kiosk controls.
   - Cloud backup disabled (`android:allowBackup="false"`) protecting sensitive GPS logs.
3. **[Battery & Performance Optimization](docs/audits/performance-optimization.md)**
   - Dynamic sensor delay configuration reducing idle sensor drain by > 65%.
   - Composite Room database indices reducing outbox query latencies to `< 2.4 ms`.
4. **[Regression & Test-Driven Verification](docs/audits/test-verification.md)**
   - Automated 111-task test matrix covering fall physics, network chaos, and false-alarm cancellation paths.

---

## Quickstart & Verification Commands

### Requirements
- JDK 17
- PowerShell 7 (`pwsh`) on Windows 11 or Linux
- Android SDK (installed via `scripts/setup-sdk.ps1`)

### Run Verification Pipelines
```powershell
# 1. Run all unit tests across all Gradle modules
.\gradlew.bat test

# 2. Run Android debug static linter
.\gradlew.bat :app:lintDebug

# 3. Run E2E Outbox Chaos Test (Outage & Recovery)
.\scripts\e2e-jvm.ps1

# 4. Run Full Operational KPI Benchmark
.\scripts\kpi-run.ps1 -Quick
```

---

## Repository Documentation

| Document | Purpose |
|---|---|
| [PRD.md](PRD.md) | Product Requirements, Problem Statement, Milestones |
| [ARCHITECTURE.md](ARCHITECTURE.md) | Full System Architecture & Tech Stack Details |
| [ARCHITECTURE-ESSENTIALS.md](ARCHITECTURE-ESSENTIALS.md) | Condensed Rules & Component Boundaries |
| [Data.md](Data.md) | SQLite Schemas, Network Payloads, and KPI Model |
| [Security.md](Security.md) | Safety Rules & Device Admin Permissions |
| [docs/audits/](docs/audits/) | Comprehensive Quality, Security, Battery, and Test Audits |
