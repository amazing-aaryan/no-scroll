# Blocker destination feature — implementation decisions

## [2026-09-05] Keep redirection separate from reading and blocking policy
**Decision:** Add a persistent, device-local destination choice, defaulting to the existing reader; allow other launchable apps without requiring a PDF. Both clickable overlays use the same launch path. Missing or inaccessible apps open the chooser instead of silently forcing reading.
**Why:** The user requested this feature on a new branch, and existing reader/navigation and Instagram safety exceptions must remain unchanged.
**Impact:** New remote branch `feature/choose-blocker-destination` starts at `6038d17fd5909642c67d824347444f654afb9762`. No changes to master, signing, version, app identity or Instagram detection. This sandbox cannot clone GitHub or download Android dependencies directly; GitHub connector reads/writes and repository CI will be used. Local verification covers the pure Kotlin behavior, not a device run.
