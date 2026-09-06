# NoScroll native iPhone prototype

Native SwiftUI iOS 17+ implementation of the approved screen-aware direction. **No Safari, no Android changes, and no claim of release readiness.** The reader works separately from the experimental capture/shield path.

## Implemented path

User-started ReplayKit broadcast → on-device Vision landmark comparison → consecutive-frame policy → whole-selected-app Screen Time shield → optional reading notification/manual NoScroll opening → local PDF reader.

The app provides a screenshot mapping editor, observation-only mode, individual Screen Time authorization, a one-app picker, explicit full-screen capture consent, live/stale/failed status, recovery controls, PDF import, a sample book, and saved page progress. Broadcast, shield configuration and shield action are separate embedded extensions. All application/extension identifiers derive from the same build settings.

This is a calibration-based prototype, not a pretrained Instagram detector. Before interventions, save at least one blocked and one allowed mapping. For each mapping choose three non-overlapping static UI regions on a portrait screenshot from the device. Avoid message text, account names, moving video, counts, blank regions and the status bar. Use discriminating controls; an icon shared by every tab is not enough. Dark/light mode, font scaling, device dimensions or Instagram layout changes may require new maps.

Every region must match, an allowed profile vetoes a block, ambiguous blocked matches abstain, and three consecutive matches spanning at least one second are required. Maximum sampling rate is 2 Hz. The default feature-distance threshold is 0.12 using Vision revision 2; this is **not a probability or validated accuracy figure**. Up to eight profiles are supported. Unrecognized screens are not blocked.

## Build and test on a Mac

Install Xcode with an iOS Simulator runtime and XcodeGen. From the repository root:

```sh
brew install xcodegen
bash ios/scripts/verify-macos.sh
```

That script runs the pure Swift tests and five packaging contracts, generates `ios/NoScroll.xcodeproj`, then builds the app and all embedded extensions and runs the three native Vision tests in an iPhone simulator with signing disabled. Native tests use generated patterns, not real Instagram screenshots; they check adapter plumbing only. The GitHub `iOS prototype` workflow runs the same command for relevant PR changes. Inspect the actual run result rather than assuming that adding a workflow means it passed.

To generate the project without testing:

```sh
cd ios
xcodegen generate --spec project.json
open NoScroll.xcodeproj
```

Run project generation and Xcode build commands from `ios/` so the local package resolves consistently. The verification script changes to this directory automatically, even when called from the repository root.

The generated project is intentionally ignored; `project.json` is the source of truth. No third-party runtime SDK or cloud account is required. The pure core also runs on Linux with `swift test --package-path ios`.

## Install on a physical iPhone

In `project.json`, choose bundle prefix and App Group identifiers registered to your Apple developer team; defaults are `com.noscroll.ios` and `group.com.noscroll.ios`. Regenerate the project. Set the same development team for the app and its three extensions in Xcode. Provision the shared App Group and Family Controls capability for every target that declares them. Enable Developer Mode on the test phone when Xcode requires it, select the `NoScrollIOS` scheme, and run.

**Provisioning is not pre-completed.** No team, certificates, profiles or signing secrets are included. Applying ManagedSettings from the ReplayKit extension needs specific signed-device validation; merely adding its entitlement does not prove Apple will provision or approve distribution of that combination. Request Family Controls distribution approval for the app and relevant extensions before TestFlight/App Store distribution. This prototype has not been submitted to Apple.

## First device session

1. Add blocked Reels/Home and allowed Messages/Profile/Story maps. Mapping edits stop the previous protection session.
2. Authorize Screen Time and choose **Instagram only**, confirming the selection. Changing the app selection also ends capture processing. Apple supplies an opaque token; the app cannot reverse-resolve it to verify the name.
3. Accept the full-display disclosure, choose **Prepare observation**, and use the real system broadcast picker to start NoScroll. Keep the microphone off.
4. Exercise Instagram in observation mode, then return to NoScroll to inspect the last recognized result. Check blocked and allowed layouts individually, including other apps that might look similar. Only then enable experimental blocking.
5. When shielded, use **Read instead** and tap the optional notification, or open NoScroll manually. Read/import a PDF. To return, choose **Pause blocking to navigate** and navigate to a mapped allowed screen.

## Honest boundaries and recovery

A match triggers a **whole-app shield**, not a rectangle overlay. Native Instagram navigation is inaccessible while shielded. There is no injected Back tap, Reels-tab replacement, private accessibility API, or Safari fallback. A Reel opened from a message cannot reliably be distinguished from a general Reel unless the visible landmarks differ; contextual Android shared-media containment is not reproduced.

Navigation pause clears our shield and is deliberately **unprotected until a mapped allowed screen is stably recognized**. It can remain paused indefinitely, including while scrolling. This trades enforcement for an escape from repeated blocking of the same underlying Reel. The UI discloses this; do not market it as tamper-proof or as a timed allowance.

Stopping, pausing, locking or losing capture stops new visual decisions. It does not silently clear a shield that is already latched. Open NoScroll and use **Disable & clear restrictions** to release it. That invalidates session/revision tokens so in-flight analysis cannot reapply a shield after a successfully persisted disable. Stop the system broadcast in Control Center if its recording indicator remains. If shared state cannot be written, emergency release is attempted but persistence is not confirmed; stop broadcasting and revoke NoScroll's Screen Time access in Settings as necessary. Automatic capture restart is not implemented or promised.

## Privacy and storage

Full-display capture can receive messages, passwords and other apps' pixels. It is not limited to Instagram. Frames are handled synchronously, analyzed locally, never retained beyond callbacks, never uploaded and never written as recordings. Audio buffers are ignored. Calibration screenshots remain only in memory; their originals remain in the user's Photos library. Selected-region feature vectors, labels and geometry are stored locally in the App Group. Feature vectors are still derived data and should not be described as mathematically anonymous.

Only compact session state, opaque selection tokens and fixed diagnostic codes are shared between extensions. No OCR, analytics, web requests, screenshot logs or third-party tracking SDK exists in this implementation. Imported books remain in the main app's Documents directory; extensions do not read them. Clearing/deleting app data removes local state subject to normal iOS storage behavior. Required-reason privacy manifests are included; a production privacy review remains required.

## Verification and release gates

See [DEVICE_VALIDATION.md](DEVICE_VALIDATION.md) before claiming iPhone support is validated. Local development verified 34 pure Swift tests, five packaging checks and Swift syntax parsing. That is not an Apple-SDK typecheck, physical-device test or Instagram accuracy measurement. Check CI for the native build/test result.

Not included: full Android reader feature parity (highlights, notes, OCR and quote cards), trained layout models, guaranteed messaging preservation, production artwork, App Store assets, distribution approval or an installable signed IPA.

## Primary API references

- [ReplayKit](https://developer.apple.com/documentation/replaykit)
- [System broadcast picker](https://developer.apple.com/documentation/replaykit/rpsystembroadcastpickerview)
- [Vision feature-print requests](https://developer.apple.com/documentation/vision/vngenerateimagefeatureprintrequest)
- [Managed Settings shields](https://developer.apple.com/documentation/managedsettings/shieldsettings)
- [Family Controls distribution entitlement](https://developer.apple.com/documentation/familycontrols/requesting-the-family-controls-entitlement)
- [Required-reason API declarations](https://developer.apple.com/documentation/bundleresources/app-privacy-configuration/nsprivacyaccessedapitypes/nsprivacyaccessedapitype)
- [XcodeGen project format](https://yonaskolb.github.io/XcodeGen/Docs/ProjectSpec.html)
