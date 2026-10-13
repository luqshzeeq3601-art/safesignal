# Interview Notes

One short entry per component: what it is, why it is built this way, and the trade-off.

## M0 — Build scaffold

**What:** A Gradle multi-module build with a version catalog (`gradle/libs.versions.toml`) and five JVM modules.

**Why:** Splitting detection, protocol, server, bot and KPI code into plain JVM modules means most of the logic is tested in seconds without an emulator. The version catalog keeps every dependency version in one file.

**Trade-off:** More build files than a single-module app, and module boundaries have to be respected (no Android imports in JVM modules).
