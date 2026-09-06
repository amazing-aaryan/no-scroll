# Native iPhone development

NoScroll has a separate **experimental native iOS 17+ screen-aware prototype** under [`ios/`](ios/README.md). It uses user-started ReplayKit capture, on-device landmark comparison, conditional whole-app Screen Time shielding and a local PDF reader. It does not use Safari and does not change the Android implementation.

Start with the [build/setup guide](ios/README.md), [physical-device acceptance gate](ios/DEVICE_VALIDATION.md), and [approved design](docs/superpowers/specs/2026-09-05-ios-screen-aware-design.md). An implemented prototype, passing unit tests and successful simulator builds are not evidence of reliable Instagram blocking on a physical iPhone. Apple signing/provisioning, actual-device classification, capture lifecycle, shield application and recovery remain explicit release gates.

Android documentation and release instructions remain in `README.md`, `PRODUCT.md` and `RELEASE.md`. This prototype is not an Android release or a full iOS port of all reader features.
