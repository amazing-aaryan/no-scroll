# NoScroll Beta and Release Guide

This document is the source of truth for distributing NoScroll outside local development and for shipping updates after the first release.

## Current release state — 2026-09-01

Repository configuration:

- Package/application ID: `com.noscroll`
- `minSdk`: 28 (Android 9)
- `compileSdk`: 36
- `targetSdk`: 36 (Android 16)
- `versionCode`: 1
- `versionName`: 1.0
- Declared Gradle distribution: 8.11.1
- No release `signingConfig` is committed to the repository.
- Windows Gradle wrapper launcher (`gradlew.bat`), wrapper JAR, and wrapper properties are present; the Unix `gradlew` launcher is currently missing.

### Play beta status

The repository-level target-API discrepancy has been resolved: NoScroll now targets Android 16 / API 36, which meets Google's requirement for new apps and app updates submitted from August 31, 2026 onward.

This does **not** mean the app is ready to upload to Google Play yet. Before the first Play upload:

1. Run the full build/test suite and validate Android 16 behavior changes on device, especially edge-to-edge/predictive-back behavior plus NoScroll's accessibility, overlay, foreground-service, notification, file, sharing, PDF, and intent flows.
2. Confirm whether `com.noscroll` has already been created/registered in the team's Play Console. The repository cannot prove Play Console registration state.
3. If it has not been created, create the app in Play Console using the final package ID `com.noscroll`.
4. Configure Play App Signing and create a secure upload key/keystore.
5. Configure release signing locally or in CI. Do not commit the keystore, passwords, service-account credentials, or other signing secrets.
6. Build and upload a signed Android App Bundle (`.aab`) to an Internal testing track first.
7. Complete Play Console requirements that apply to the account/app, including store listing, app content/declarations, privacy/data-safety information, tester access, and any required review steps.

## Do we need Google Play before we can update the app?

No. There are two separate update paths.

### Local / sideloaded updates

A sideloaded build can update an already installed build only when Android considers it the same app. At minimum, the package ID and signing identity must be compatible and the new build must have an acceptable version.

For development on Windows, a debug APK can normally replace an existing debug APK signed by the same debug key:

```powershell
.\gradlew.bat assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

On Linux/macOS, the Unix `gradlew` launcher is currently missing. Until it is restored, use an installed Gradle 8.11.1, which matches the wrapper configuration and CI.

A debug APK generated on another computer may use a different debug signing key. In that case Android may reject the in-place update and require uninstall/reinstall.

### Google Play updates

Google Play registration/setup is required before **Google Play** can distribute the first beta or any later Play update. After that one-time setup, normal updates do not require registering a new app.

For every subsequent Play release:

1. Keep `applicationId "com.noscroll"` unchanged.
2. Keep the app's Play signing identity unchanged.
3. Increment `versionCode` above every previously uploaded build.
4. Update `versionName` when appropriate for humans.
5. Build a signed release AAB with the registered upload key.
6. Upload it to the desired testing/production track.
7. Review release notes and roll out the release.

## Debug-to-Play transition

Do not assume a locally installed debug build will update in place to the Play build. Debug and Play distributions normally use different signing identities. Testers moving from a debug build to the first Play-distributed build may need to:

1. Uninstall the debug build.
2. Install NoScroll from the Play testing link.
3. Re-enable the Accessibility Service.
4. Re-grant Display Over Other Apps and any other runtime/special permissions.
5. Re-import local-only content if uninstalling removed app-private data.

Plan the first external beta with this migration cost in mind.

## Versioning rules

- Never reuse a `versionCode` that has been uploaded to Play.
- Bump `versionCode` for every distributed Play build, even if `versionName` stays the same.
- Do not change the package ID after establishing the Play app unless intentionally creating a separate app.
- Do not rotate or replace the app signing identity casually. Follow Play App Signing procedures for supported key upgrades or upload-key resets.

Suggested early beta sequence:

| Channel | Example versionCode | Purpose |
|---|---:|---|
| Local debug | development-only | Fast device testing |
| Play Internal | 2 | First signed Play beta |
| Play Internal/Closed | 3+ | Regression fixes and broader testers |
| Production | later | Only after beta gates pass |

The exact next `versionCode` must be checked against Play Console before upload. The table assumes `1` has not already been uploaded externally.

## Build and verification gates

The Windows wrapper and wrapper JAR are present and declare Gradle 8.11.1. CI provisions Gradle 8.11.1 explicitly because the Unix `gradlew` launcher is missing.

Before distributing any beta candidate on Windows, run at least:

```powershell
.\gradlew.bat clean
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebug
```

After release signing is configured locally/CI, also build the release bundle:

```powershell
.\gradlew.bat bundleRelease
```

On Linux/macOS use the equivalent `gradle` commands with Gradle 8.11.1 until the Unix wrapper launcher is restored.

A successful build is not enough. Manually regression-test the beta candidate on a physical device, including:

- First-run permission/setup flow.
- Accessibility service enabled, disabled, and re-enabled.
- Overlay permission enabled, denied, and restored.
- Instagram Home/Reels blocking and allowed surfaces.
- Story-viewer overlay suppression.
- Opening NoScroll directly and from the Instagram blocker/entry point.
- PDF import from file picker and share sheet.
- PDF open/read/page progress and relaunch restoration.
- Text selection, highlight, note, and delete/edit flows.
- OCR fallback on a scanned page.
- Quote-card creation and each supported share path.
- App restart, device reboot, and service recovery.
- Predictive-back behavior on Android 16.
- Edge-to-edge/inset handling on Android 16.
- Upgrade from the immediately previous beta through the same distribution channel.

For a Play candidate, also test the actual Play-installed artifact through Internal testing rather than relying only on a locally installed APK.

## Release checklist

Current repository-level SDK gate:

- [x] `targetSdk` is API 36, meeting the current Google Play submission requirement as of 2026-09-01.

A candidate is ready to promote only when all of the following are true:

- [ ] Re-check that `targetSdk` still meets the current Google Play submission requirement at release time.
- [ ] Package ID is still `com.noscroll`.
- [ ] `versionCode` is unique and greater than every prior Play upload.
- [ ] Release signing is configured outside source control.
- [ ] `bundleRelease` succeeds.
- [ ] Unit tests, lint, and debug build pass.
- [ ] Core blocker and reader regression tests pass on physical hardware.
- [ ] Android 16 predictive-back and edge-to-edge behavior is verified.
- [ ] The Play Internal build installs and launches successfully.
- [ ] Upgrade testing from the previous beta passes where an in-place upgrade is expected.
- [ ] Accessibility/overlay permission behavior is rechecked on the target Android versions.
- [ ] Store listing, policy declarations, privacy/data-safety details, and tester configuration are complete.

## Security rules

Never commit:

- `.jks` / `.keystore` files
- keystore passwords
- key aliases/passwords in plaintext
- Play service-account credentials
- API secrets

Keep release credentials in an approved local secret store or CI secret manager. Only non-secret signing metadata or setup instructions belong in Git.

## External references

- Google Play target API requirement: https://developer.android.com/google/play/requirements/target-sdk
- Android 16 target-specific behavior changes: https://developer.android.com/about/versions/16/behavior-changes-16
- Android app signing / Play App Signing: https://developer.android.com/studio/publish/app-signing
- Preparing an Android app for release: https://developer.android.com/studio/publish/preparing
