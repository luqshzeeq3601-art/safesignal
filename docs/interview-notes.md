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
