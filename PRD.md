# SafeSignal — Product Requirements

## Problem Statement

People who work alone (security guards, utility technicians, warehouse night staff) can fall or become unresponsive with nobody nearby to notice. A phone they already carry can detect this and call for help, but only if the alert is raised automatically, survives a bad network, and reaches a dispatcher who confirms it.

Pain points:

- A hurt worker may be unable to unlock a phone and dial.
- A single failed network request must not lose an emergency alert.
- False alarms waste dispatcher time, so the worker needs a chance to cancel.

## Project Objectives

| Objective | Success metric |
|---|---|
| Detect falls and "man-down" from the accelerometer | Every labelled `fall_*` trace detected; 0 detections on `walk_*`, `sit_*`, `drop_phone_*` |
| Deliver alerts reliably | ≥ 95 % of raised alerts delivered within 60 s, including a server outage scenario |
| Close the loop with the dispatcher | Acknowledgement visible in the app after the dispatcher presses Ack |
| Work as a dedicated device | Hardware SOS key works when locked; kiosk mode holds the app in front |
| Measure it | Four KPIs produced by a script: latency, delivery rate, false-alarm rate, battery per hour |

This is a portfolio project for an entry-level Android Software Engineer role. A second objective is that every part is simple enough to explain in an interview.

## Target Audience

| Persona | Use case |
|---|---|
| Lone worker | Starts a shift, carries the phone, cancels a false alarm or presses SOS |
| Dispatcher | Watches the web console, acknowledges alerts |
| Device integrator | Applies the AOSP customizations (privileged app, key remap, overlay, device owner) |
| Interviewer / reviewer | Reads the code, runs the verification commands |

## Tech Stack

| Choice | Rationale |
|---|---|
| Kotlin + Jetpack Compose, MVVM, Hilt | Current Android standard; clear layer boundaries |
| Java for `:detection` | Pure maths reads naturally in Java; shows Java/Kotlin interop |
| Room 2.8.5 | Local history and outbox; widely documented |
| Ktor 3.6.0 (server and client) | One HTTP library on both sides, same serialization models |
| Platform `LocationManager` | AOSP emulator image has no Google Play services |
| Polling for acknowledgement | No FCM on AOSP; easy to explain |
| JUnit 4 + MockK | Compose UI tests need JUnit 4; one framework everywhere |
| PowerShell 7 scripts | Run on Windows 11 and on GitHub Ubuntu runners |
| AOSP API 34 emulator image | Allows `adb root` and remount for system customization |

Exact versions live in `gradle/libs.versions.toml`.

## Execution Plan

MVP = M0–M9 (detection → alert → delivery → acknowledgement on an emulator). M10–M13 add AOSP customization, M14 KPIs, M15 CI.

| Phase | Milestones | Outcome |
|---|---|---|
| 1. Core logic (JVM only) | M0 scaffold, M1 detection, M2 protocol, M3 server, M4 bot + JVM end-to-end | Alerts flow bot → server → ack with no Android |
| 2. Android app | M5 toolchain + skeleton, M6 data layer, M7 service, M8 UI, M9 emulator end-to-end | Injected fall produces an acknowledged alert |
| 3. AOSP customization | M10 privileged app, M11 key remap, M12 overlay, M13 kiosk | Dedicated-device behaviour, verified by scripts |
| 4. Measurement and process | M14 KPIs, M15 CI + Agile wrap-up | KPI summary and green CI |

Each milestone is one user story in `docs/agile/backlog.md` with a verification command as its acceptance test.

### Out of scope

- Server authentication (localhost demo; see `Security.md`).
- Real fall datasets (traces are synthetic and labelled as such).
- Push notifications, multi-tenant server, iOS.
