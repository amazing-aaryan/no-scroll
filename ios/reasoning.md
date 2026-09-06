# iOS implementation decisions

## [2026-09-05 21:20 America/Chicago] Native screen-aware prototype
**Decision:** Implement user-approved native capture, local landmark recognition, conditional app shielding, explicit recovery, and a minimal local PDF reader under `ios/`. No Safari and no Android source or release changes.
**Why:** Prove observation/intervention/recovery before a full feature-parity reader port. Use ReplayKit for the first compatibility prototype rather than a beta-only ScreenCaptureKit dependency. Real-device calibration avoids pretending synthetic screenshots prove Instagram accuracy.
**Impact:** This is an experimental, consent-based prototype, not a release-ready iPhone product. Swift logic can be verified on Linux; signed Apple framework behavior needs macOS CI and physical iPhone evidence. Screen Time shields the whole selected app after a selective trigger. Never call ReplayKit's first-application annotation a live foreground-app detector.

## [2026-09-05 21:20 America/Chicago] Isolation and distribution
**Decision:** Work in a new local staging repository and publish a tree based on remote master `6038d17fd5909642c67d824347444f654afb9762` to a separate feature branch.
**Why:** Direct git cloning is blocked by this runtime's DNS, while the connected GitHub read/write API is available.
**Impact:** Do not claim local Android tests or a full local checkout. Preserve all remote base-tree entries, and never merge or modify master as part of this session.

## [2026-09-05 21:38 America/Chicago] Review and verification boundary
**Decision:** Keep recovery explicitly unprotected until an allowed map matches; provide emergency clear and honest unreadable/stale-status messages. Rollback clears a shield only after an effect was actually changed, not after failed preconditions.
**Why:** Avoid silent protection claims, immediate re-block loops, stale-frame races and accidental removal of an existing restriction on validation failure.
**Impact:** Local verification passed 34 core tests, four packaging contracts and native Swift syntax parsing. Apple-SDK compilation, actual Vision runtime tests, entitlement behavior and physical Instagram testing require their own evidence. No independent reviewer or graphify executable was available in this runtime; neither review nor graph refresh is claimed.

## [2026-09-05 21:50 America/Chicago] Native CI package-path regression
**Decision:** Change the verification script into the iOS directory before project generation and `xcodebuild`, and add an orchestration regression test that launches it from an unrelated directory. Update the manual generation instructions accordingly.
**Why:** macOS CI passed all 34 core tests and four initial packaging checks, then package resolution searched the repository root for `Package.swift` rather than `ios/`. The new orchestration test failed against the prior script and now checks both generator and build working directories.
**Impact:** The original native CI run failed before Apple-framework compilation. A new run is required; neither native compilation nor Vision runtime success follows from these local checks.
