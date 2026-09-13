# Folio

An Android launcher for foldables (Galaxy Z Fold / Pixel Fold) inspired by Apple's iPhone Duo:
right-side dock, Duo-style unfold animation, corner status cluster, and more.

- Package: `com.mccal.folio`
- Min SDK 31 · Target/compile SDK 36 · Kotlin + Jetpack Compose + Jetpack WindowManager

## Build

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/`. Install on a connected phone with:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Docs
- `docs/research.html`: research report (open in a browser)
- `docs/research-notes.md`: full research notes and phased build plan
- `docs/architecture.md`, `docs/user-guide.md`, `docs/troubleshooting.md`: inherited from DuoLauncher
- `docs/UPSTREAM-DUOLAUNCHER-README.md`: the original DuoLauncher README

## Build order
0. Setup guide: Wallet swipe off, double-press Wallet, right-edge back fix, side key to assistant
1. Core: right dock + left-hand mirror, status cluster, full-page widgets + stacks, App Library + hidden apps
2. Search box (plain Google / DuckDuckGo / AI apps) + top-edge swipe zones
3. Unfold animation
4. Vertical island + AI notification summaries, optional assistant role
5. Optional: dock/island over all apps (sideload only)

## Credits
- **[DuoLauncher](https://github.com/jakesgoodapps/DuoLauncher)** by the Duo Launcher contributors (MIT): Folio's starting codebase.
- **[iphone-duo](https://github.com/chuspeeism/iphone-duo)** by chuspeeism (MIT): the blur/darkening curves and hinge-angle model that Folio's fold shader follows.
- **u/moomanjohnny** on r/GalaxyFold: the Galaxy Z Fold 8 proof of concept that showed screenshots + shaders + hinge sensors can recreate the iPhone Duo unfold; inspired Folio's screenshot-morph fold style. No code was released or used.
- **[QuickLaunch](https://github.com/AhmedTheGeek/QuickLaunch)** by AhmedTheGeek (GPL-3.0): ideas for Spotlight — requesting the keyboard after the first frame, frecency ranking with a 7-day half-life, and drag-to-split-screen. Re-implemented independently; no QuickLaunch code is included.

Folio started from [DuoLauncher](https://github.com/jakesgoodapps/DuoLauncher)
(commit `f1bc0f1`, 10 Sep 2026), © 2026 Duo Launcher contributors, used under the MIT License (see `LICENSE`).
Third-party dependency licenses are listed in `THIRD_PARTY_NOTICES.md`.
Apple, iPhone, Google, Android and Samsung are trademarks of their owners; Folio is not affiliated with them.
