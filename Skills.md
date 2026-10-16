# Skills — Authorized Tools

Commands and endpoints an agent may use in this project. Anything not listed needs the owner's approval.

## Shell (PowerShell 7)

| Tool | Allowed use |
|---|---|
| `.\gradlew.bat` | `projects`, `check`, `test`, `:server:run`, `assembleDebug`, `lintDebug`, `connectedDebugAndroidTest` |
| `git` | status, diff, branch, add, commit, log. **Push needs approval** |
| `gh` | `run list`, `run view`, `pr create` after the repo exists. **Repo creation needs approval** |
| `adb` (from M5) | `devices`, `shell`, `logcat`, `install`, `push`, `root`, `remount`, `reboot`, `emu …` — emulator `safesignal_api34` only |
| `emulator`, `avdmanager`, `sdkmanager` (from M5) | via `scripts/setup-sdk.ps1` and `scripts/emulator-*.ps1`. **SDK download needs approval** |
| `curl.exe` | requests to `http://localhost:8080` only |
| `scripts/*.ps1`, `aosp/*.ps1` | project scripts |
| `python scripts/render-docs.py` | redraw the README charts in `docs/images/` |

## Local HTTP endpoints (`:server`, `http://localhost:8080`)

| Method | Path |
|---|---|
| POST | `/api/alerts` |
| GET | `/api/alerts`, `/api/alerts/{id}` |
| POST | `/api/alerts/{id}/ack` |
| GET | `/health`, `/` |

From the emulator the host is `http://10.0.2.2:8080`.

## External hosts (read-only, dependency resolution)

`services.gradle.org`, `repo1.maven.org`, `dl.google.com`, `plugins.gradle.org`.

## Not allowed

- `sudo`, system-level package installs, changing Windows features.
- `adb` against a physical device.
- Deleting anything outside the project folder or the `safesignal_api34` AVD.
