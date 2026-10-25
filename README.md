# Exo Player

*A media player for the AYN Thor*

A personal Android video player built for the **AYN Thor Max**'s two screens. It plays movies and series from the SD card, fetches posters and plots, remembers where you stopped, plays the next episode for you, and uses less battery than VLC.

- **Top screen:** the film, full-screen and unobstructed.
- **Bottom screen:** episode list, seek bar, audio and subtitle pickers. Touch them freely; the controller stays on the film.
- **One screen is fine:** with the bottom screen off, it works like a normal phone player.

> Personal app, installed as an APK; not for the Play Store. Online only when you ask: Fetch info and Find subtitles are the only things that connect.

## Status

**v1 is built.** Every v1 feature is in the code and its tests pass on the PC, but nothing has run on a Thor yet, and the online services (TMDB, fanart.tv, AniList, OpenSubtitles) were tested against stand-in servers because the build machine can't reach them. What's left for v1 is the two gates, which need the Thor: Gate 1 (both screens, buttons and touch on the device) and Gate 2 (battery against VLC, and 95 % of your 100 file names matched).

| Stage | What it adds | Status |
| --- | --- | --- |
| 1. Shell | Both screens, controller input, B steps back, the diagnostics page (Select + Start) with the v0 measurements | Done |
| 2. Library | Folder picker, filename reading (movies, SxxEyy, anime), database | Done |
| 3. Browsing | The five tabs on L2 / R2, poster cards, art on the top screen | Done |
| 4. Player | Resume where you stopped, Pause, Play from start, Mark watched, Audio, Subtitles with ±50 ms timing, ‹ Prev / Next ›, Up next, ✎ and ⏻ | Done |
| 5. Series and anime | Show details, season cards, episode cards (3 across, ✓ watched, progress, missing greyed), continue straight into the episode, one-season shows skip the season cards | Done |
| Settings | API keys and the OpenSubtitles login (typed or pasted, stored encrypted), video folder, About. Start or ⚙ on Browsing | Done |
| 6. Online, on request | Fetch info from TMDB (fanart.tv and AniList as fallbacks), only what's missing; Fix match; Find subtitles from OpenSubtitles, saved next to the video | Done |
| 7. Player extras | Playback settings on Start (speed, picture, audio delay, subtitle look, brightness), volume bar with boost, Mark unwatched, scrubbing, headset buttons, pause when leaving | Done |
| Library extras | Search, sort and filter, folder browser | Done |
| Gates 1 and 2 | Measure and test on the Thor; battery against VLC; your 100 file names | Needs the Thor |

| Version | What it adds | Status |
| --- | --- | --- |
| v0 | Two-screen test app that measures the Thor; layout mockups | Done (diagnostics page in the app) |
| v1 | Library scan, artwork, resume, Continue watching, auto-play next, local subtitles, subtitle search | Built; gates need the Thor |
| v2 | Backup, launch from frontends, TV output | Planned |
| v3 | Skip intro, Trakt sync, themes, picture-in-picture | Planned |

## Building

Needs JDK 17+ and the Android SDK (platform 36). Put `sdk.dir=…` in `local.properties`, then:

```
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # unit tests, plus screenshots of both screens in app/build/screenshots
```

## Documents

| Document | What's in it |
| --- | --- |
| [docs/PLAN.md](docs/PLAN.md) | The plan: goals, how the Thor works, two-screen design, battery rules, architecture, data model, phases, testing, risks |
| [docs/FEATURES.md](docs/FEATURES.md) | Every feature with its version and acceptance test |
| [docs/mockups/plan-and-screens.html](docs/mockups/plan-and-screens.html) | Mockups of both screens in every state, the plan by category, and the draft display and layout rules ([live page](https://claude.ai/artifact/JKHKE3bY5DPKwPxaqQdjhr)) |
| [docs/research/thor-dual-screen.md](docs/research/thor-dual-screen.md) | How the Thor's two screens work, how frontends launch apps, and how melonDS, Azahar, Loki, Wayfinder and ES-DE Companion drive both screens |
| [docs/research/thor-platform-and-issues.md](docs/research/thor-platform-and-issues.md) | What AYN publishes, firmware history, and the issues other Thor developers hit, with what each means for us |

## Key decisions so far

- **Kotlin + Jetpack Compose**, **Media3 ExoPlayer** with the FFmpeg audio add-on (DTS, TrueHD, AC3), **Room**, **TMDB** for artwork.
- **Two screens with Android's `Presentation` API:** the activity and video on the top screen, a non-focusable Compose window on the bottom screen. The same approach is used by melonDS, Azahar and Loki on the Thor.
- **Video in a `SurfaceView`** so the display hardware draws it at the lowest power.
- **Public Android APIs only:** no root and no AYN privileged service.

## Building

Not yet. When v0 lands: open the project in Android Studio (latest stable) on your PC, connect the Thor (or a phone) with USB debugging on, and press Run. Without a Thor, use **Developer options › Simulate secondary displays** on a phone or emulator to get a second screen.

## Credits

Artwork and metadata will come from [TMDB](https://www.themoviedb.org/). This product uses the TMDB API but is not endorsed or certified by TMDB.
