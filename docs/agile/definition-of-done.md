# Definition of Done

A story is done when all of these are true:

1. Its verification command gives the expected result in `backlog.md`.
2. `.\gradlew.bat check` passes.
3. Script or test output is saved in `docs/evidence/`.
4. `docs/interview-notes.md` has a short entry for each new component: what, why, trade-off.
5. `CHANGELOG.md` is updated.
6. Work is committed on the story's branch with a Conventional Commit message.
7. Any real bug found on the way has a write-up in `docs/debugging/`.
