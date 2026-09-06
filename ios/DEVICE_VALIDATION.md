# Physical iPhone acceptance gate

**Status: NOT RUN. Do not mark these gates passed from unit tests, source inspection, simulator success, or a workflow being present.** Use a dedicated test account and redact any evidence before sharing it. Record commit, iPhone model, iOS version, Instagram version, theme, text size, exact maps and signing/provisioning setup.

## Required cases

| Gate | Procedure and expected result |
|---|---|
| Signed capture | Install all extensions with the same App Group; start via Apple's picker. Verify callbacks on the real Instagram app. No app-identity claim from ReplayKit's first-app annotation. |
| Consent | Fresh install, denied permission, consent withdrawn and old-session callbacks cannot enable recognition or restrictions. |
| Positive classification | In observe mode run at least 30 separate entries each into mapped Home and Reels screens. Record matches, missed cases and time to recognition. No assumed accuracy target is already achieved. |
| Negative classification | Exercise Messages, profiles, Stories, Search, notifications, Control Center, keyboard and non-Instagram apps. Record every false block/match, including app screens displaying an Instagram screenshot. |
| Dynamic content | Change video, post, account, like counts, theme and text size. Static landmarks should remain discriminating or abstain; redo maps when needed. |
| Actual shield | Enable blocking, enter mapped Reels, verify all three landmarks plus temporal evidence precede an application-level shield. Confirm the ReplayKit extension is actually permitted to set it on this signed device. |
| Latch | A shield's own screen, black frames, unknown frames or another foreground app must not auto-clear it. |
| Reader handoff | Test notification accepted/denied, Focus enabled, host app terminated and normal foregrounding. Manual opening must remain usable. Record any notification-delivery restriction from the shield extension. |
| Recovery | Pause to navigate, expose the same Reel, verify no immediate re-block. Visit a mapped allowed screen, verify re-arm; then enter Reels again. A pause that remains unprotected must be clearly visible. |
| Disable race | Repeatedly disable during active analysis and change maps/app selection. No late decision may recreate a shield after successful disable. |
| Lifecycle | Lock/unlock, stop broadcast, pause/resume, force-quit host, terminate extension, restart phone, revoke Screen Time. UI must never claim live protection beyond heartbeat freshness; existing shields have a working explicit release. |
| Corruption | Missing/invalid profiles, missing App Group, malformed token state and write failures do not silently authorize blocking. Emergency release and Settings revocation remain available. |
| Performance | Run 30 minutes with several maps; record extension peak resident memory, CPU, processing latency, thermal state and battery impact. No jetsam termination or processing backlog. Frames exceeding the stale-result deadline must not act. |
| Reader | Import a normal and large PDF, reject locked/invalid/over-limit input, restore page after termination, preserve imported files after access to the source is removed. |

## Stop conditions

Do not ship if ordinary Messages navigation causes false restrictions, the extension cannot legally/properly apply shields, recovery traps the user, capture status is misleading, or memory/capture restart friction makes the experience unacceptable. Do not replace a failing native gate with Safari or a private-API workaround without a new product decision.

## Release beyond the prototype

Require a passing integrated native build, independent code/security review, completed signed-device evidence, Apple distribution entitlement approval, accessible calibration interaction, production icons/assets and privacy/distribution review. The calibration UI currently relies on visual drag selection and needs an accessible alternative before a general release. Keep Android's release process independent.
