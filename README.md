# Exo Player

*A media player for the AYN Thor*

A personal Android video player built for the **AYN Thor Max**'s two screens. It plays movies and series from the SD card, fetches posters and plots, remembers where you stopped, plays the next episode for you, and uses less battery than VLC.

- **Top screen:** the film, full-screen and unobstructed.
- **Bottom screen:** episode list, seek bar, audio and subtitle pickers. Touch them freely; the controller stays on the film.
- **One screen is fine:** with the bottom screen off, it works like a normal phone player.

> Personal app, installed as an APK; not for the Play Store. Online only when you ask: Fetch info and Find subtitles are the only things that connect.

## Status

**Planning done; v0 (the two-screen test app) is next.** There is no code yet.

| Version | What it adds | Status |
| --- | --- | --- |
| v0 | Two-screen test app that measures the Thor; layout mockups | Next |
| v1 | Library scan, artwork, resume, Continue watching, auto-play next, local subtitles, subtitle search | Planned |
| v2 | Backup, launch from frontends, TV output | Planned |
| v3 | Skip intro, Trakt sync, themes, picture-in-picture | Planned |

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
