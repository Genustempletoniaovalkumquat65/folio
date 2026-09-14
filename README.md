# Folio

**An iOS-style Home Screen for Android, built around Apple's "iPhone Duo" design for foldables.**

Folio puts status, the Dynamic Island and the dock in a Side Bar along the right edge. It shows one continuous
interface on a foldable's cover and inner screens, in portrait or landscape, and it scales up for tablets and
desktop windows. It's a normal Android launcher with no root.

<p align="center">
  <img src="docs/images/folio-cover-home.png" width="300" alt="Folio Home on a Galaxy Z Fold cover screen: widgets, a four-column app grid, and the Side Bar with the clock, battery and dock on the right">
</p>

> Status: in development (0.x). Built and tested daily on a Galaxy Z Fold8; layouts are unit-tested on 20 window sizes,
> from a 320 dp phone to a 2560 dp desktop window. See [CHANGELOG.md](CHANGELOG.md).

## Features

**Home that adapts, not stretches**
- Layouts follow size classes, never device checks: phones, flip covers, foldables, split-screen, tablets and desktop windows.
- Unfolded, you get two Home pages side by side. Unfolded portrait gets one centered page with a dock bar. Landscape covers get two columns.
- Hinge-aware: when the phone is half folded, sheets, alerts, menus and Home rows move off the fold.
- On big screens everything scales up in proportion, like iPad.

**iOS-inspired pieces**
- Side Bar: status capsule, a Dynamic Island that wraps a side camera, and the dock.
- Notification Center, Control Center, Spotlight with suggestions, the Today View, Smart Stacks and the widget gallery.
- Jiggle-mode editing, folders, Icon Stacks, per-page icon size and labels, and App Library-style search.
- Focus modes with schedules, Home pages to show, and Do Not Disturb rules.
- App download progress rings, blue dots on new apps, and Add to Home Screen for shortcuts and widgets.
- Settings built from iOS controls: menu rows, segmented controls, switches, form sheets and alerts. What's New appears after updates.

**Tweaks, off by default**
- Ideas from classic jailbreak tweaks (Velox panels, Activator gestures, tinted notifications and more), re-created from scratch.
- Per-screen overrides, themes you can save and share, Safe Mode after repeated crashes, and local-only crash reports.

**Private by design**
- No accounts, no analytics. Every permission is optional and explained where it's used. See [PRIVACY.md](PRIVACY.md).

## Requirements

- Android 12 (API 31) or newer. Target SDK 36.
- Best on foldables (Galaxy Z Fold / Flip, Pixel Fold), and designed to work on any phone, tablet or Chromebook. Tablets are unit-tested but not yet checked on a device or emulator.

## Build and install

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

For a fast, optimized build to use day to day, use `:app:assembleFast` (the APK is in `app/build/outputs/apk/fast/`).
Then press Home and pick Folio, or choose **Set as home app** in Folio's settings.

Run the tests with `./gradlew :app:testFastUnitTest`.

## Project layout

- `app/src/main/java/com/mccal/folio/`: the launcher (Kotlin + Jetpack Compose + Jetpack WindowManager).
  - `LayoutModel.kt`: size classes, Home geometry and big-screen scaling.
  - `LauncherScreen.kt`, `HomeWorkspace.kt`: Home, pages and editing.
  - `StatusRail.kt`, `CutoutIsland.kt`: the Side Bar status capsule and Dynamic Island.
  - `CustomizationSheet.kt`, `IosControls.kt`, `FolioSheet.kt`: Settings and iOS-style controls.
- `app/src/test/`: unit tests, including the screen-size matrix.
- `docs/`: [plan and roadmap](docs/plan.md), [architecture](docs/architecture.md), [user guide](docs/user-guide.md),
  [troubleshooting](docs/troubleshooting.md), and [research notes](docs/research-notes.md).

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). Avoid GPL code and root-only features.

## Credits
- **[DuoLauncher](https://github.com/jakesgoodapps/DuoLauncher)** by [jakesgoodapps](https://github.com/jakesgoodapps) and the Duo Launcher contributors (MIT): Folio's starting codebase — the iPhone Duo-style right-side dock and layouts for both Fold screens, paired Home pages and the unfolded workspace, Android widgets, folders, work profiles, wallpapers with sunrise/sunset switching, and Google search/Discover.
- **[iphone-duo](https://github.com/chuspeeism/iphone-duo)** by chuspeeism (MIT): the blur/darkening curves and hinge-angle model that Folio's fold shader follows.
- **u/moomanjohnny** on r/GalaxyFold: the Galaxy Z Fold 8 proof of concept that showed screenshots + shaders + hinge sensors can recreate the iPhone Duo unfold; inspired Folio's screenshot-morph fold style. No code was released or used.
- **[QuickLaunch](https://github.com/AhmedTheGeek/QuickLaunch)** by AhmedTheGeek (GPL-3.0): ideas for Spotlight — requesting the keyboard after the first frame, frecency ranking with a 7-day half-life, and drag-to-split-screen. Re-implemented independently; no QuickLaunch code is included.
- **iOS jailbreak tweaks** (ideas only, re-created from scratch; no code): Velox by Phillip Tennen (app panels), Activator by Ryan Petrich (gesture and event actions), Axon by Nepeta (notification app row), Velvet by NoisyFlake & HiMyNameisUbik (tinted notifications), ColorFlow by David Goldman (album-art colors), Harbor by Evan Swick (dock magnification). Authors as credited by iDownloadBlog.

Folio started from [DuoLauncher](https://github.com/jakesgoodapps/DuoLauncher)
(commit `f1bc0f1`, 10 Sep 2026), © 2026 Duo Launcher contributors, used under the MIT License (see `LICENSE`).
Third-party dependency licenses are listed in `THIRD_PARTY_NOTICES.md`.
Apple, iPhone, Google, Android and Samsung are trademarks of their owners; Folio is not affiliated with them.
