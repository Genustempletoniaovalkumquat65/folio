# Folio

I wanted the iPhone Duo on my Galaxy Z Fold, so I'm building it.

Folio is an Android launcher that takes Apple's foldable iPhone design and makes it work on Android. The status bar,
Dynamic Island and dock live in a Side Bar on the right edge. Home stays the same whether the phone's folded or open;
unfolding just gives you more room. It works on the cover screen, the inner screen, either rotation, half folded,
split-screen, and it scales up on tablets and bigger screens too. No root needed.

<p align="center">
  <img src="docs/images/folio-cover-home.png" width="300" alt="Folio on a Galaxy Z Fold cover screen: widgets, a four-column app grid, and the Side Bar with the time, battery and dock on the right">
</p>

> Still in development (0.x). I use it every day on my Fold8, and the layout is tested on 20 screen sizes, from small
> phones to desktop windows. What changed: [CHANGELOG.md](CHANGELOG.md).

## What it does

**Adapts to the screen, doesn't just stretch**
- Lays out by how much room there is, not what device it is: phones, flips, foldables, split-screen, tablets.
- Open the Fold and you get two Home pages side by side. Portrait unfolded gets one centered page with a dock bar. The cover in landscape gets two columns.
- Knows where the fold is: when the phone is half folded, sheets, menus and Home rows move off the crease.
- Bigger screens draw everything bigger, like iPad, instead of a tiny phone layout in the middle.

**The iOS stuff**
- Side Bar with the status bar, a Dynamic Island that wraps the camera, and the dock.
- Notification Center, Control Center, Spotlight, Today View, Smart Stacks and the widget gallery.
- Jiggle mode, folders, Icon Stacks, per-page icon size and labels, and App Library search.
- Focus modes with schedules and the Home pages you want to see.
- Download rings on updating apps, blue dots on new ones, and Add to Home Screen for shortcuts and websites.
- Settings built like iOS: menus with checkmarks, segmented controls, switches, sheets and alerts. What's New shows up after updates.

**Tweaks (all off until you turn them on)**
- Ideas from jailbreak tweaks I liked (Velox, Activator, Velvet, Axon, ColorFlow, Harbor), rebuilt from scratch.
- Turn a tweak on for just the cover or just the inner screen, save and share themes, and Safe Mode if something crashes.

**Private**
- No accounts, no ads, no analytics. Nothing leaves your phone unless you share a crash report yourself.
  Every permission is optional and explained where it's used. More in [PRIVACY.md](PRIVACY.md).

## What you need

- Android 12 or newer.
- Made on a Galaxy Z Fold, but it's meant to work on any Android phone, flip, tablet or Chromebook. I haven't tried a
  real tablet yet, so let me know how it goes.

## Build it

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:assembleFast
adb install -r app/build/outputs/apk/fast/app-fast.apk
```

`assembleFast` is the optimized build I use day to day. `:app:assembleDebug` works too (APK in
`app/build/outputs/apk/debug/`). After installing, press Home and pick Folio, or open Folio Settings and tap
**Set as home app**.

Tests: `./gradlew :app:testFastUnitTest`

## Where things are

- `app/src/main/java/com/mccal/folio/`: the launcher (Kotlin, Jetpack Compose, Jetpack WindowManager)
  - `LayoutModel.kt`: screen sizes, Home layout and big-screen scaling
  - `LauncherScreen.kt`, `HomeWorkspace.kt`: Home, pages and editing
  - `StatusRail.kt`, `CutoutIsland.kt`: the Side Bar status bar and Dynamic Island
  - `CustomizationSheet.kt`, `IosControls.kt`, `FolioSheet.kt`: Settings and the iOS controls
- `app/src/test/`: unit tests, including the screen size tests
- `docs/`: [user guide](docs/user-guide.md), [troubleshooting](docs/troubleshooting.md), [roadmap](docs/plan.md),
  [architecture](docs/architecture.md), [research notes](docs/research-notes.md)

## Contributing

Ideas and fixes are welcome. Read [CONTRIBUTING.md](CONTRIBUTING.md) first. No GPL code and nothing that needs root.

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
