# Architecture Essentials

The rules to keep in mind on every change. Detail is in `ARCHITECTURE.md`.

1. **Seven modules.** `:detection` (Java), `:protocol`, `:server`, `:bot`, `:kpi` (Kotlin JVM), `:app` (Android), `:overlay` (RRO, XML only).

2. **No Android imports** in `:detection`, `:protocol`, `:server`, `:bot`, `:kpi`.

3. **`:detection` is Java only.** No Kotlin, no coroutines.

4. **State changes go through `AlertStateMachine.transition`.** It is pure: no I/O, no clock, no randomness. Side effects are returned as `effects` and run by `AlertController`.

5. **MVVM.** Composables read one `StateFlow<UiState>` from a ViewModel and send events up. ViewModels never touch Room, HTTP or sensors directly; they call a repository or `AlertController`.

6. **Every data source is an interface** (`SensorSource`, `LocationSource`, `BatterySource`) bound by Hilt, with a fake for tests.

7. **`alertId` is a client-generated UUID.** The server treats a repeated `alertId` as a duplicate (200, not 201).

8. **Message shapes live only in `:protocol`** and are shared by app, server and bot. A breaking change bumps `protocolVersion`.

9. **Versions live only in `gradle/libs.versions.toml`.**

10. **Scripts are PowerShell 7** and exit non-zero on failure.

11. **A milestone is done** when its verification command passes, `.\gradlew.bat check` passes, and output is saved in `docs/evidence/`.
