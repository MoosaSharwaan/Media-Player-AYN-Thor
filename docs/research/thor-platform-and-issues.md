# AYN Thor: platform, firmware history and known issues

Oct 23, 2026. This follows on from [thor-dual-screen.md](thor-dual-screen.md). It covers what AYN publishes about how the Thor is built, what its firmware updates have changed, the problems other app developers have run into, and what each one means for our player.

## 1. Official documentation

**AYN publishes no developer documentation for the Thor.** I couldn't find an SDK, a dual-screen API guide, kernel source releases or ROM build instructions. AYN communicates through firmware changelogs that a staff member ("Nicole from AYN") posts to its Discord, and the community copies them to GitHub ([ChimeraGaming/AYN-OTA-Changelogs](https://github.com/ChimeraGaming/AYN-OTA-Changelogs/blob/main/Thor.md)).

What we know about how it's built comes from teardown-style community work:

| Area | What's known | Source |
| --- | --- | --- |
| Models | **Lite**: Snapdragon 865, 8 GB LPDDR4, UFS 3.1, Wi-Fi 6. **Base/Pro/Max**: Snapdragon 8 Gen 2 (SM8550), LPDDR5X, UFS 4.0, Wi-Fi 7. Max comes in 16 GB/512 GB and 16 GB/1 TB | Liliputing, Notebookcheck |
| Screens (all models) | Top: 6" AMOLED 1080 × 1920, 120 Hz, 650 nits. Bottom: 3.92" AMOLED 1080 × 1240, 60 Hz, 550 nits, touch | Liliputing |
| OS | Stock Android 13 (no upgrade announced) with AYN's "Odin" system apps: Odin launcher, `com.odin.gameassistant`, `com.odin.dualscreen.assistant`, the TCC control centre on the bottom screen, Thor Settings and Odin Settings | Wayfinder source, changelogs |
| Privileged service | `PServerBinder` runs shell commands as root for any app. It powers "Run script as root" | ES-DE Companion, Wayfinder source |
| Other systems | Official LineageOS 24 (Android 17) for Odin 2 and Thor; kernel at `LineageOS/android_kernel_ayn_qcs8550`. ROCKNIX and Thorch (Arch Linux) run mainline Linux 6.17 with Thor device-tree support from AYN | XDA, changelog |
| Battery | 6,000 mAh, USB-C 3.1 with DisplayPort output (added in firmware .293), 3.5 mm jack, microSD | Liliputing, changelog |

## 2. Firmware history: what changed that matters for a video player

| Build | Date | Relevant changes |
| --- | --- | --- |
| 1.0.0.279 | 2025-10-20 | Fixed the bottom screen lighting up unexpectedly in **top-screen-only mode** with portrait apps. Fixed wake/sleep failures when opening and closing the lid quickly. Separate bottom-screen wallpaper |
| 1.0.0.293 | 2025-11-20 | **DisplayPort output when docked.** Keyboard can be pinned to the bottom screen. Fixed the app switcher with third-party launchers. Option to hide the nav bar on the bottom screen. AYN key can wake the screen |
| 1.0.0.360 | 2026-01-13 | **Fixed: in Bilibili, sliding to change brightness changed both screens.** **Fixed washed-out colours on the top screen after leaving HDR video.** OLED burn-in and image-retention protection. Wi-Fi firmware freeze and reboot fixes. Charge limits |
| 1.0.0.372 | 2026-01-29 | **Home/Back now bind automatically to the active or focus-locked screen.** OLED Ultra Black Mode (less colour shift at low brightness). Volume slider fix for Bluetooth and wired headphones. Battery gauge fix (some users rolled back to .293 because of battery drain) |
| 1.0.0.377 | 2026-02-08 | Black and Gray themes for TCC. **System-wide audio EQ presets** |

There have been no Android version upgrades, only builds on top of Android 13. AYN changes dual-screen behaviour in point releases (Home/Back binding, the keyboard screen), so **each OTA can change how our app behaves**.

## 3. Issues other developers hit, and what each means for us

| Issue | What happened | What it means for us |
| --- | --- | --- |
| **Bottom screen only works when the app is the home app** (Argosy launcher #122) | Argosy dropped its earlier method as "not reliable" and now draws on the bottom screen only when set as the home app | Argosy runs the bottom screen as a separate activity. melonDS, Azahar and Loki's player use a **`Presentation`** owned by the top-screen activity, which needs no home role. **Use a Presentation, not a second activity.** |
| **Bottom screen stuck after exit** (iiSU #265) | The bottom screen stayed on the app after it closed, until a reboot. Closed without a fix | Always `dismiss()` the Presentation in `onStop`/`onDestroy` and when playback ends, and catch exceptions. Never leave a window on the bottom screen after we close |
| **Top screen isn't "presentable"** (Azahar #1437) | An app launched on the bottom screen can't put a Presentation on the top screen; AYN doesn't mark display 0 as a presentation display | If a frontend opens us on the bottom screen, **move the activity to display 0** before setting up both screens |
| **External display takes over** (Azahar #1437) | When docked to a TV, a third display appears and apps pick it instead of the bottom screen | Choose the bottom screen by name or flags, never "the first presentation display". When docked (v2 TV output), decide on purpose: video on the TV, controls on the bottom |
| **Overlays on the wrong screen** (Wayfinder #34) | Wayfinder's quick panel appeared over the game on the top screen and interfered with the graphics driver | Other tools (Wayfinder, AYN's game assistant, TCC) may draw over our screens. Never assume our windows are the only ones, and handle losing focus without pausing |
| **Dual screen breaks on LineageOS 24** (WatermelonDS #246) | On Android 17 the app only saw the display it launched on | Custom ROMs change display behaviour. The single-screen layout has to work everywhere. We support stock Android 13 first |
| **Brightness changed both screens** (fixed in .360) | A window's brightness override (Bilibili) dimmed both panels | **Window `screenBrightness` on the Thor may not be per-screen.** Don't use it to dim the bottom screen until v0 tests it on current firmware. Dimming by drawing black is safe |
| **Washed-out colours after HDR video** (fixed in .360) | Leaving HDR playback left the top screen's colours wrong | Test HDR10 files, and the return to the library after them, on every OTA |
| **Battery drain bugs** (.372 era) | A battery gauge bug and standby drain; some rolled back | Do battery comparisons only on a stable firmware, with VLC on the **same** build, and write the build number on every test |
| **Home/Back follow the active screen** (.372) | Home and Back now act on the last-touched or focus-locked screen | Touching the bottom screen (our controls) must not make Back act on the bottom screen. Our bottom window can't take focus, so Back stays with the activity, but this needs testing on .372+ |
| **System EQ** (.377) | AYN applies its own speaker EQ system-wide | Our "night mode" and volume boost sit on top of AYN's EQ. Don't add a second EQ in v1 |

## 4. Risks added to the plan

1. **Firmware updates change behaviour.** Keep a test checklist per OTA (see plan). Write the firmware build into every bug report and battery test.
2. **No official API for screen state.** The AYN-button "bottom off" isn't visible to apps (it's an overlay). Per-screen brightness, refresh rate and volume are private settings, reachable only through the root service. We stay on public APIs and accept those limits.
3. **The Lite model is a different chip.** The Snapdragon 865 has no hardware AV1 decoding. The app should notice software decoding and show a notice, as the plan already says. The Max is the target.
4. **Third-party overlays and assistants** (TCC, game assistant, Wayfinder) share the screens with us.

## Sources

- AYN OTA changelogs (community copy of AYN's posts): https://github.com/ChimeraGaming/AYN-OTA-Changelogs/blob/main/Thor.md
- Argosy launcher issue #122: https://github.com/rommapp/argosy-launcher/issues/122
- iiSU issue #265: https://github.com/iisu-network/iiSU/issues/265
- Azahar issue #1437: https://github.com/azahar-emu/azahar/issues/1437
- WatermelonDS issue #246: https://github.com/SapphireRhodonite/WatermelonDS/issues/246
- Wayfinder issue #34: https://github.com/Thor-Wayfinder/thor-wayfinder/issues/34
- LineageOS 24 for Odin 2 / Thor (XDA): https://xdaforums.com/t/official-lineageos-24-for-the-ayn-odin-2-odin-2-mini-odin-2-portal-thor-retroid-pocket-6-nova.4802565/
- Thorch (Arch Linux for Thor): https://github.com/thorch-os/thorch
- Liliputing specs: https://liliputing.com/ayn-thor-is-dual-screen-android-handheld-game-console-with-oled-displays-and-qualcomm-snapdragon-inside/
- Notebookcheck on Max 512 GB variant: https://www.notebookcheck.it/AYN-Thor-Max-16GB-1TB-ottiene-un-aumento-di-prezzo-annunciata-la-nuova-variante-da-16GB-di-RAM-512GB.1276917.0.html
- Steam Deck HQ on .372: https://steamdeckhq.com/news/ayn-thor-gets-oled-ultra-black-mode-fixes-bugs/
- Notebookcheck on .293 update: https://www.notebookcheck.net/AYN-Thor-gets-a-price-increase-alongside-a-software-update-to-help-with-overheating-and-battery-life.1203338.0.html
