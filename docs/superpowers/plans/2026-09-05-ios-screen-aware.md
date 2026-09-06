# Native screen-aware iOS implementation plan

> **For agentic workers:** Use superpowers:executing-plans to implement this approved direction in this session, task by task.

**Goal:** A native, locally analyzed, user-consented screen-aware NoScroll prototype with no Safari.
**Architecture:** Apple adapters wrap a pure Swift classifier/state machine. A locked App Group state store serializes UI, capture and shield actions; calibration stores feature-print descriptors rather than screenshots.
**Tech Stack:** Swift 5.9+, SwiftUI, ReplayKit, Vision revision 2, FamilyControls, ManagedSettings, PDFKit, XCTest, XcodeGen.
**Spec:** `docs/superpowers/specs/2026-09-05-ios-screen-aware-design.md`

## Global constraints
- iOS 17+; no beta-only APIs; Android unchanged.
- Full-display capture is explicit and revocable. Discard audio; no pixel/text/network logging.
- App-level shield after a selective visual trigger, never an Android-equivalent overlay claim.
- Observation is the default; interventions require one app token and both positive and allowed maps.
- Unknown/ambiguous screens abstain. A latched shield never clears just because its screen is unknown.
- Navigation recovery is explicitly unprotected until an allowed screen is stably recognized.

### Task 1: Pure policy, mapping, and storage
Files: `ios/Core/Package.swift`, `ios/Core/Sources/NoScrollCore/*.swift`, `ios/Core/Tests/NoScrollCoreTests/*.swift`.
Interfaces: `ScreenProfile`, `ProfileDistances`, `LandmarkClassifier.classify`, `ProtectionEngine.observe`, `ControlState`, `LockedStateStore.withState`.
- [x] Write failing XCTest cases for safe-profile veto, partial landmark matches, geometry, temporal evidence, late results, recovery and filesystem concurrency.
- [x] Run `swift test --package-path ios/Core`; record expected missing-feature failures.
- [x] Implement those interfaces with no Apple-framework dependency, then run the complete suite.
- [x] Commit verified core and tests.

### Task 2: Native capture, maps and restriction adapters
Files: `ios/Shared/*.swift`, `ios/Broadcast/SampleHandler.swift`, `ios/ShieldConfiguration/*.swift`, `ios/ShieldAction/*.swift`, `ios/NativeTests/*.swift`.
Interfaces: `VisionMatcher.makeProfile`, `VisionMatcher.distances`, `ProtectionCoordinator`, `SampleHandler`.
- [x] Add native adapter tests for deterministic self-matching and schema round trips, and configuration contract tests before adapters.
- [x] Implement calibration descriptors, bounded synchronous frame analysis, atomic policy revalidation and named-store shielding.
- [x] Implement consent/session lifecycle, latched blocks, explicit navigation recovery, and notification/manual reader handoff.
- [x] Run core/contract tests.
- [ ] Verify native tests in macOS CI and complete signed-device gates.

### Task 3: Native user workflow and build integration
Files: `ios/App/*.swift`, `ios/project.json`, `ios/Configuration/*`, `ios/scripts/*`, `.github/workflows/ios-ci.yml`.
- [x] Implement onboarding, single-app selection, screenshot landmark mapping, visible capture status, broadcast picker, disable/recovery controls, PDF import and saved page.
- [x] Generate target manifests and privacy/entitlement files. Do not invent a signing team.
- [x] Add CI with least-privilege token, core tests, project-contract checks and unsigned native build/test. Do not deploy.
- [x] Validate every plist/YAML, run Swift tests, inspect changed-file scope and record unavailable device gates.

### Task 4: Documentation and review
Files: `ios/README.md`, `ios/DEVICE_VALIDATION.md`, `ios/reasoning.md`, root iOS entrypoint documentation.
- [x] Document setup, native-only UX, calibration, capture privacy, stopped-state behavior, optional notification handoff and explicitly unprotected recovery.
- [x] Review for stale-frame races, write failures, consent bypasses, memory retention and claims exceeding evidence.
- [x] Run fresh local verification.
- [x] Publish draft PR #4 on `feature/ios-screen-aware-prototype` without merging.
- [ ] Confirm native CI after fixing the package-path regression; report exact results and remaining Apple/device gates.
