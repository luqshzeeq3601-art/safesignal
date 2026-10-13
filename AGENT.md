# Agent Instructions

Operating rules for an AI coding agent working in this repository. Read `ARCHITECTURE-ESSENTIALS.md` first.

## Workflow

1. Work one milestone (one user story in `docs/agile/backlog.md`) at a time, in order.
2. One git branch per story: `story/m<N>-<slug>`.
3. Write the tests named in the story's acceptance criteria along with the code.
4. Close a story only when the Definition of Done (`docs/agile/definition-of-done.md`) is met.
5. Stop and ask before: downloading the Android SDK, creating or pushing to a GitHub repo, anything that changes Windows features.

## Commands to run

| When | Command |
|---|---|
| After any change | `.\gradlew.bat check` |
| One module | `.\gradlew.bat :<module>:test` |
| Module list | `.\gradlew.bat projects` |
| JVM end-to-end (from M4) | `.\scripts\e2e-jvm.ps1` |
| Emulator tests (from M6) | `.\gradlew.bat :app:connectedDebugAndroidTest` |
| Emulator end-to-end (from M9) | `.\scripts\e2e.ps1` |

A command that fails to start is not a pass.

## Coding standards

- Kotlin official style; Java 17 for `:detection`.
- No Android imports in JVM modules.
- Keep classes small and explainable; prefer the standard library over a new dependency.
- New dependency → add it to `gradle/libs.versions.toml`, never inline a version.
- Comments explain why, not what.

## Error handling

- Network and I/O failures become values (`Result` / sealed class) at the repository boundary; ViewModels never see raw exceptions.
- Scripts use `$ErrorActionPreference = 'Stop'` and exit non-zero on failure.
- A real bug found during the build gets a write-up in `docs/debugging/case-NN-<slug>.md` (symptom, evidence, root cause, fix). Never invent one.

## Commits

- Conventional Commits: `feat(detection): …`, `test(protocol): …`, `docs: …`, `chore: …`.
- Reference the story: `(M3)` in the subject.
- Update `CHANGELOG.md` when a milestone closes.
- Never commit secrets, keystores, `local.properties` or build output.
