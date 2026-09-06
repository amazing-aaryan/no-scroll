# Native iPhone screen-aware NoScroll prototype

Approved direction: the user's September 5 request to implement native screen capture, local screen recognition, conditional blocking and reading; no Safari.

## Scope
An isolated SwiftUI iOS 17+ app, ReplayKit Broadcast Upload Extension, Screen Time shield configuration/action extensions, local PDF import/reading with saved progress, and pure Swift policy tests. Android is unchanged. No cloud inference, third-party tracking, private APIs, automated touches, fake accessibility access, beta-only API dependency, or full reader parity claim.

## Observation
The user explicitly accepts a full-screen capture disclosure and starts Apple's broadcast picker. Audio is discarded. A screenshot chosen by the user is held only in memory during calibration. The user marks three nonoverlapping static interface landmarks per profile; only secure-coded Vision feature-print vectors, normalized rectangles, the user-provided profile label and aspect ratio are persisted. Live frames never leave the extension and are not written to disk. Process at most two frames per second, synchronously, without retaining ReplayKit sample buffers after callbacks return. Expose stopped, paused, failed and stale-capture states honestly. No foreground application identity is inferred from ReplayKit's first-app annotation.

## Recognition
Three matched landmarks and matching portrait aspect ratio are required for a blocked profile. Allowed profiles veto blocked matches; ambiguity and unsupported orientations abstain. Require at least one blocked and one allowed mapping before enabling interventions. Default mode is observation. Vision revision 2 distances are not confidence percentages; thresholds start strict and require physical-device validation. Match over consecutive distinct frames and at least one second before intervention. A profile change invalidates current recognition evidence.

## Enforcement and recovery
The user authorizes individual Family Controls and selects exactly one application, explicitly confirming it is Instagram. Opaque application tokens cannot be reverse-resolved. No categories, domains or all-app shield is used. Only the `noscroll.visual` named ManagedSettingsStore is changed. Cross-process mutations use a file lock and revalidate current policy before applying a shield. A shield remains latched even though its own screen is no longer recognized as Instagram. Observing an unknown frame never automatically clears a shield.

Primary shield action requests the reader, posts an optional local notification and returns `.close`. Do not claim one-tap host-app launching on unsupported/unverified SDKs. Secondary action explicitly pauses blocking for navigation, clears only our shield, and re-arms only after stable recognition of a mapped allowed screen. This recovery mode may stay paused indefinitely; disclose the bypass rather than promise a timer that depends on a live process. User-facing stop clears restrictions and invalidates in-flight decisions. A capture stop does not silently clear an already-latched restriction; recovery is available in NoScroll.

## Data and concurrency
App Group configuration contains token data, permission/consent/mode/revision and compact capture health. Profile feature prints are in a separate atomic file. Cross-process transitions are serialized by an advisory file lock. Capture workers carry session IDs and configuration revisions; results from older sessions/revisions cannot change restrictions. Corrupt data, absent entitlements, revoked authorization and write failures must not report protection as active.

## Gates
Pure Swift tests verify map validation, classification, abstention, temporal stability, recovery, stale session handling and state persistence. Native adapter tests use generated patterns and prove feature-print plumbing only. macOS CI generates and builds all targets with signing disabled. Physical tests cover actual Instagram layouts, false positives in other apps, notifications, capture lock/stop/crash, authorization revocation, memory/battery and shield recovery. Entitlements/signing/distribution approval and physical-device gates remain required for release.
