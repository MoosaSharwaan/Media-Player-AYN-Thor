# AYN Thor dual-screen research

Oct 23, 2026. Research done before building v0, to see how the Thor's screens work and how existing apps use both of them.

Sources: the source code of Loki, Wayfinder, ES-DE Companion, melonDS and Azahar (read directly), plus reviews and guides (linked at the end). Facts marked *(code)* come from comments that developers wrote after testing on a real Thor. Facts marked *(guide)* come from reviews and setup guides.

## 1. How the Thor works

### Screens as Android sees them

| | Top screen | Bottom screen |
| --- | --- | --- |
| Panel | 6" AMOLED, 1080p, up to 120 Hz | 3.92" AMOLED, 1080 × 1240, 60 Hz, touch |
| Android display | `DEFAULT_DISPLAY` (id 0) | A public presentation display. One Thor showed id 4; don't hardcode it |
| Display name | `"Built-in Screen"` | `"Screen-2"` *(code: melonDS `AynThorLayoutDisplayMapper`)* |
| Flags | — | `FLAG_PRESENTATION` set, `FLAG_PRIVATE` not set *(code: Loki `DisplayTopology`)* |

- The top screen is the primary display, and the bottom one is a normal Android secondary display. No special SDK is needed.
- Other displays can also show up in the list: `"HiddenDisplay"` (Azahar creates it), `"WebRTC_ScreenCapture"` (from screen recorders such as Discord), and other private virtual displays. Ignore them. *(code: melonDS, Azahar)*
- Don't treat a display in `STATE_OFF` as missing. When the Thor wakes, the two panels turn on one after the other. Loki briefly switched to single-screen on every wake because of this. *(code: Loki)*
- With the Thor docked to a TV over USB-C, a third display appears. Loki treats "more than one presentation display" as docked. *(code)*

### Input and focus

- Android 13 gives each display its own focus (`config_perDisplayFocusEnabled` is true on the Thor). **The controller's buttons go to whichever screen you last touched.** *(code: Wayfinder, which checked `dumpsys input`; guide)*
- A touch moves focus only when it lands on a window that can take key input. A window with **`FLAG_NOT_FOCUSABLE`** still gets touches but never takes the controller. melonDS, Azahar and Loki all use this flag on their bottom-screen window, so the buttons stay with the main activity. *(code)*
- AYN has a system setting, `screen_focus_lock`, behind "focus lock" in its settings. Wayfinder and other tools change it as root. AYN's hidden `setFocusedMode(int)` does nothing. *(code: Wayfinder)*
- The Thor's built-in controller is called `"Odin Controller"`. Its key codes are the standard Android gamepad ones. *(code: Wayfinder)*

### AYN button and turning the bottom screen off

- Pressing the AYN button on the chin opens the AYN menu. **Holding it turns the bottom screen off.** *(guide)*
- That "off" is **not** a real power-off. AYN's assistant puts a black overlay over the screen that swallows touches, and saves the state in `Settings.System dual_screen_display_mode` (0 = both on, 1 = bottom off, 2 = top off). That key isn't public, so **a normal app can't read it**. The display stays `STATE_ON`, and nothing is added or removed. *(code: Wayfinder)*
- Wayfinder blanks a screen the same way, with a black overlay. On OLED, black pixels are off, so this saves about as much power as turning the panel off. *(code)*

### Refresh rate

- The bottom panel can only do 60 Hz. With both screens on, the system normally runs **both at 60 Hz**. AYN's settings can put the top screen at 120 Hz with the bottom screen on, but reviewers see tearing on the bottom screen in that mode. *(guide)*
- That setting works by writing `min_refresh_rate` and `peak_refresh_rate` to `Settings.System` (120.00001 or 60.0). Ordinary apps aren't allowed to write them. *(code: ES-DE Companion `RefreshRateController`)*
- None of the projects I read match the refresh rate to the film (`Surface.setFrameRate` or `preferredDisplayModeId`). **It's unknown whether the Thor offers 24, 48 or 72 Hz modes, or whether the 60 Hz cap overrides an app's request.** The v0 probe has to check this.

### Audio, brightness and system services

- There is one real audio stream (`STREAM_MUSIC`). The bottom-screen volume is just a vendor key, `secondary_screen_volume_level` (0 to 15). *(code: ES-DE Companion)*
- Per-screen brightness goes through the hidden `DisplayManagerGlobal.setBrightness(displayId, value)`, which needs a system permission. AYN's own menu uses it. *(code: Wayfinder)*
- AYN ships a privileged binder service, **`PServerBinder`**, that runs any shell command as root for any app. AYN's "Run script as root" uses it, and so do Wayfinder and ES-DE Companion, for refresh rate and volume. It's fragile because a firmware update could remove it, and it's a security hole. Our app should not depend on it.
- AYN's launcher lets you choose per app where it opens: **Auto, Top screen or Bottom screen** (long-press the app, then Screen Launch). New apps open on the screen you used last. *(guide)*
- The on-screen keyboard **doesn't show reliably on the bottom screen**, so Loki keeps all text fields on the top screen. *(code)*

## 2. How frontends work

A frontend is a launcher. It lists games and apps with artwork and starts other apps with Android intents.

- **ES-DE** reads `es_systems.xml` and `es_find_rules.xml`. Each system's command builds an intent: `%ACTION%=android.intent.action.VIEW`, `%DATA%` (a file as a FileProvider or SAF `content://` URI) and `%EXTRA_name%=value` extras. Only one data URI is allowed. Custom systems go in `ES-DE/custom_systems/`. ES-DE also writes events such as `game-select` and `game-start` to `es_log.txt`.
- **Cocoon** is closed source. It has per-platform launch settings and a "Launch on Display 1" option. *(guide)*
- **RetroHrai!, Daijisho and Beacon** work the same way: they send an intent to a package or activity, with the file passed as a URI.

**What this means for us:** to be launched with a file, the player needs an exported activity that accepts `ACTION_VIEW` for `video/*` and `content://` URIs. The read permission it receives is temporary, so the file has to be opened right away. **The new activity opens on the display the frontend was on.** If Cocoon is on the bottom screen, our player opens on the bottom screen. *(Cocoon even swaps emulator screens when you launch from the bottom; see melonDS discussion #1595.)*

## 3. How apps drive both screens

Apps use one of three approaches:

### A. One app, two windows: the `Presentation` API (melonDS, Azahar, Loki, Wayfinder's companion)

The activity runs on the top screen, and an `android.app.Presentation` (a dialog-like window tied to a `Display`) draws on the bottom screen. The common pattern:

1. Find the bottom screen with `DisplayManager.getDisplays(DISPLAY_CATEGORY_PRESENTATION)`. Skip the activity's own display, private displays and the virtual displays named above.
2. Create a `Presentation(activity, display)`. Set `FLAG_NOT_FOCUSABLE or FLAG_NOT_TOUCH_MODAL` so touches reach it but the controller stays with the activity.
3. Watch `DisplayManager.DisplayListener` (added, removed, changed) to rebuild or dismiss it. Catch `BadTokenException` and `InvalidDisplayException` when calling `show()`, because the display can disappear mid-call. *(Azahar)*
4. **Compose inside a Presentation** needs lifecycle, saved-state and view-model owners set on the decor view. Loki learned that borrowing the activity's lifecycle **freezes the bottom screen** when the activity stops, so the Presentation gets its own `LifecycleOwner`, which is resumed while it's showing, and its own Recomposer. It shares only the `ViewModelStore`, so both screens read the same state. *(Loki `ThorPresentation`, `PresentationHost`)*
5. Back and Escape must be swallowed in the Presentation. Otherwise, by default, Back dismisses the window. *(Loki)*
6. Key events that reach the Presentation are sent to the same handler as the activity's. *(Loki)*

**Loki's movie player is the closest match to our app:** video on the top screen, controls on the bottom one. The player lives in a long-lived object, not inside a composable, so a display change or a screen swap doesn't restart it. Both screens attach to it, and status reaches the controls through a `StateFlow` updated by a timer. Loki uses a `TextureView`. *For battery, we should use a `SurfaceView` instead*: Android can hand a `SurfaceView` straight to the display hardware as an overlay, while a `TextureView` is redrawn by the GPU on every frame. Loki's notes also say that **DTS, DTS-HD and TrueHD play silently** without the FFmpeg extension, and that ExoPlayer's own audio-focus handling can stop playback from starting.

### B. A second app or activity on the other screen (ES-DE Companion, Loki's secondary home)

- ES-DE Companion is a separate app that you place on the bottom screen. It follows ES-DE by watching `es_log.txt` with `FileObserver`, and it starts apps with `ActivityOptions.setLaunchDisplayId(id)`.
- Android refuses `setLaunchDisplayId` onto a secondary display unless the app already has an *activity* there, and a Presentation doesn't count. Loki gets around this by declaring a `CATEGORY_SECONDARY_HOME` activity and launching from it. *(code)*
- This approach costs more: two tasks, communication between them, and focus fights. We don't need it.

### C. System-level tools (Wayfinder, Mjolnir, OdinTools)

These use an accessibility service and AYN's root service to move apps between screens, change focus (the hidden `IActivityTaskManager.focusTopTask`), blank screens with overlays and remap buttons. Moving an app "live" changes its display configuration, so **our activity has to survive a display change without restarting the player**.

### Apps known to use both screens

| App | Type | Method |
| --- | --- | --- |
| melonDS (and the SapphireRhodonite fork) | DS emulator | Presentation, Thor display names |
| Azahar, Citra MMJ | 3DS emulators | Presentation + SurfaceView, settings to choose the display |
| Cemu (SapphireRhodonite fork) | Wii U emulator | Second-screen GamePad |
| Cocoon | Frontend (closed source) | Two layouts, "Now Playing" on the second screen |
| Loki | Thor-only launcher, includes Movies | Presentation + secondary home activity |
| Konsoru, Console Launcher | Frontends | Bottom-screen hub or details |
| ES-DE + ES-DE Companion | Frontend + second-screen app | A separate app on the bottom screen |
| Wayfinder, Mjolnir, OdinTools | Utilities | Accessibility + AYN root service |
| VLC, Kodi, MX Player | Video players | **No dual-screen support found** |

Licences: Loki, melonDS and Azahar are GPL-3.0 (copying their code makes our app GPL too, which is fine for a personal app). ES-DE Companion is MIT. Wayfinder is PolyForm Strict, so don't copy its code.

## 4. What this changes in our plan

1. **Approach: Presentation API.** Activity plus `SurfaceView` video on the top screen; a Compose `Presentation` with `FLAG_NOT_FOCUSABLE` on the bottom screen, with its own lifecycle; the player in a long-lived holder (later the media service). This is proven on the Thor by three projects, so v0 is now mainly about measuring things, not about whether it works.
2. **Controller.** Because the bottom window can't take focus, touching it never takes the controller away from the top screen. This settles "touch-first or button-first": both work.
3. **Turning the bottom screen off with the AYN button can't be detected.** The plan's fallback ("switch to single-screen when the chin button turns it off") won't fire. Instead:
   - Have our own "Bottom screen off" action (a button combo plus an on-screen button). It removes the Presentation's content and leaves pure black, and it switches to the single-screen layout.
   - Keep the bottom screen static during playback, with no animations and updates once a second. Then if AYN's overlay covers it, almost no power is wasted.
   - Still handle a display being removed or turned off (docking, other ROMs).
4. **Refresh rate.** The Thor already runs at 60 Hz with both screens on, so the plan's "120 Hz in menus" doesn't happen and nothing needs to be done. Film-rate matching for 24 fps (to stop judder) is uncertain and is the main thing v0 has to measure.
5. **Launching from frontends.** When the player opens on the bottom screen, it should move itself to display 0 (`setLaunchDisplayId(DEFAULT_DISPLAY)`, which is always allowed) and put its controls on the bottom screen. Accept `ACTION_VIEW` `video/*` intents with temporary `content://` permission.
6. **Text input** (search, fixing a match) must happen on the top screen.
7. **Leave out PServerBinder and root.** Per-screen brightness isn't available through public APIs. v0 will test whether `WindowManager.LayoutParams.screenBrightness` on the Presentation's window dims the bottom panel.
8. **Display changes.** Handle `configChanges` for display, size and density, and keep the player outside the activity, so that moving between screens (Wayfinder, AYN's swap) never restarts playback.

## 5. Questions only the real Thor can answer (v0 probe)

- [ ] `Display.supportedModes` on both screens: are 24, 48 or 72 Hz offered? Does `Surface.setFrameRate` or `preferredDisplayModeId` change the mode with both screens on?
- [ ] Does `screenBrightness` on the Presentation window dim only the bottom panel?
- [ ] When AYN's "bottom off" overlay is showing, can our Presentation notice it (for example a focus or visibility change, or `FLAG_WINDOW_IS_OBSCURED` on touches)?
- [ ] Is HEVC 10-bit, AV1, AC3 or E-AC3 decoded in hardware (Snapdragon 8 Gen 2 `c2.qti.*` decoders)?
- [ ] Does launching from Cocoon or ES-DE on the bottom screen, then moving to display 0, work smoothly?
- [ ] Does the Presentation survive the lid closing and opening, sleep and wake, and a Wayfinder screen swap?

## Sources

- Loki (Thor-only launcher): https://github.com/Prof-Mags/Thor-Launcher (`core/display/*`, `feature/movies/player/*`, `data/launcher/SecondaryHomeHost.kt`)
- Wayfinder: https://github.com/Thor-Wayfinder/thor-wayfinder (`DisplayFocus.kt`, `DisplayBlanker.kt`, `ScreenBrightness.kt`, `ForegroundAppService.kt`)
- ES-DE Companion: https://github.com/RobZombie9043/es-de-companion (`data/thor/*`, `data/apps/AppLauncher.kt`)
- melonDS Android: https://github.com/rafaelvcaetano/melonDS-android (`impl/layout/devicemapper/AynThorLayoutDisplayMapper.kt`, `ui/emulator/render/ExternalPresentation.kt`); discussion #1595
- Azahar: https://github.com/azahar-emu/azahar (`src/android/.../display/SecondaryDisplay.kt`)
- Retro Game Corps dual-screen guide: https://retrogamecorps.com/2025/10/27/dual-screen-android-handheld-guide/
- RetroHandhelds Thor setup guide: https://retrohandhelds.gg/ayn-thor-setup-guide/
- Jacen's Thor review (refresh-rate caveat): https://jacen.moe/blog/20260418-the-premium-dual-screen-option-ayn-thor-review/
- Retro Game Corps on 120 Hz + 60 Hz: https://x.com/RetroGameCorps/status/1972873586592698813
- ES-DE Android docs: https://repo.retrodeck.net/Xargon/ES-DE/src/branch/main/ANDROID.md
- Cocoon setup guide: https://heldgames.com/guides/cocoon-setup-guide
- Best dual-screen apps list: https://www.handheldrank.com/apps-for-ayn-thor-dual-screen-handhelds/
