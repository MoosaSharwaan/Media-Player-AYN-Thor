# Thor Media Player: plan and system specs

Oct 23, 2026 · @MSharwaan

Updated the same day with the findings from [research/thor-dual-screen.md](research/thor-dual-screen.md) and [research/thor-platform-and-issues.md](research/thor-platform-and-issues.md). Changes from the first draft are marked **(research)**.

## Overview

Thor Media Player (working name) is a personal Android video player for the AYN Thor Max. It plays movies and series from the SD card, fetches their artwork, tracks what you watch, finds subtitles, and uses both screens. It must use no more battery than VLC playing the same file.

It fills a gap the current options leave. Kodi has a great library but uses one screen. Cocoon can list videos but has no artwork scraper for them, and VLC closes when an episode ends, so nothing plays the next one. **(research)** VLC, Kodi and MX Player have no dual-screen support. The only Thor app with video on top and controls below is Loki's Movies section, which streams online sources and doesn't do a local library.

**Goals**

- Play every common file on the SD card smoothly, with hardware decoding.
- Identify movies and episodes from their filenames and show posters, backdrops and plots automatically.
- Video on the top screen; episodes, controls and subtitles on the bottom screen.
- Resume, watched status, auto-play next episode and a Continue watching row.
- Watching stats kept on the device.
- Battery use at or below VLC's on the same file.
- Fully usable with the Thor's buttons, without touching the screen.

The full feature list, with versions, is in [FEATURES.md](FEATURES.md).

**Design principles**

1. **Offline first.** Everything works without internet once artwork is fetched.
2. **Battery first.** No feature may run work in the background during playback.
3. **Controller first.** Every screen works with the D-pad and face buttons.
4. **One screen is fine.** With the bottom screen off, the app still works fully.
5. **Public APIs only (research).** No root and no AYN privileged service, so firmware updates break as little as possible.

## How the Thor works (research)

| | Top screen | Bottom screen |
| --- | --- | --- |
| Panel | 6" AMOLED 1080 × 1920, up to 120 Hz | 3.92" AMOLED 1080 × 1240, 60 Hz, touch |
| Android display | `DEFAULT_DISPLAY` (0), named `"Built-in Screen"` | Public presentation display, named `"Screen-2"` (id seen as 4; never hardcoded) |

- **Controller focus follows the screen you touched last.** A window flagged `FLAG_NOT_FOCUSABLE` gets touches but never takes the controller.
- **Both screens run at 60 Hz** while both are on. 120 Hz on the top screen is an AYN setting that causes tearing on the bottom screen.
- **Holding the AYN button "turns off" the bottom screen with a black overlay.** The display stays on, and apps can't detect it.
- **The on-screen keyboard isn't reliable on the bottom screen.**
- **An app launched on the bottom screen can't put a window on the top screen**, because AYN doesn't mark display 0 as a presentation display.
- **AYN publishes no developer docs.** Behaviour changes in firmware point releases, for example .372 bound Home and Back to the active screen.

## Dual-screen design (to be designed)

The screen layouts aren't decided yet. They get designed as mockups in v0, compared, and chosen before any screen is built.

**What is fixed** (hardware and requirements, not layout)

- Top screen 6" 1080p; bottom screen 3.92" touch; both at 60 Hz with both on.
- Video always plays full-screen on whichever screen shows it.
- Every screen works with buttons alone, and with the bottom screen off.
- Layouts follow the battery rules: true black, a quiet bottom screen during playback.
- **(research)** Touching the bottom screen never takes the controller away from the top screen, so the bottom screen can be touch-first and button-first at the same time.
- **(research)** Text entry (search, fixing a match) happens on the top screen.

**Decisions so far** (also shown on the [mockup page](mockups/plan-and-screens.html))

| # | Decision | Date |
| --- | --- | --- |
| D1 | **The top screen is display only.** It never takes input, focus or touch; everything you do happens on the bottom screen (rule R11). Open: single-screen mode, and where typing goes (R10 now conflicts) | Oct 23, 2026 |
| D2 | **Browsing, bottom screen:** five tabs: Continue watching, Movies, Series, Anime, Others. No library counts line. Nothing under the tabs until it's designed | Oct 23, 2026 |
| D3 | **L2 / R2 move between the browsing tabs**; tapping a tab works too | Oct 23, 2026 |
| D4 | **Browsing, top screen:** a poster mix of everything in the current tab, with the tab's name and count | Oct 23, 2026 |
| D5 | **The tab row stays on one line** and runs past both edges of the bottom screen; the selected tab sits in the centre, and L2 / R2 go round in a full circle (Others → R2 → Continue watching) | Oct 23, 2026 |
| D6 | **Browsing, cards:** under the tabs, cards for every title in the selected tab. Moving through them makes the top screen switch from the poster mix to the focused title's poster, details and plot (and where you stopped, in Continue watching) | Oct 23, 2026 |
| D7 | **Artwork comes from proper sources through their APIs, at the right size.** Wide 16:9 backdrops for the top screen (never a crop of a poster), posters sized for the bottom-screen cards. The mockup's poster crops are a stand-in only. See *Artwork sources and sizes* below | Oct 23, 2026 |
| D8 | **Opening a movie plays it straight away**, from where you stopped. One Movie screen covers playing and paused. Top screen: the film only while playing; the title's art and details while paused; never a progress bar or controls over the picture. Bottom screen, in order: progress bar, Resume / Pause, Play from start and Mark watched (same style), Audio and Subtitles (choose a track, or Find subtitles when the file has none), subtitle timing −50 / +50 ms, and a small ⏻ button in the bottom-right corner that turns the bottom screen off and stays visible while it is off. Fix match is not on this screen. L2 / R2 skip back / forward 10 s | Oct 23, 2026 |
| D9 | **Subtitle search moves to v1**, through the OpenSubtitles API (by file hash first, then title). The Movie screen's Subtitles block offers it when the file has none | Oct 23, 2026 |
| D10 | **When the bottom screen is off, only the ⏻ button turns it back on.** Touching elsewhere on the black screen does nothing | Oct 23, 2026 |
| D11 | **Fix match is a small ✎ button next to ⏻** in the bottom-right corner of the Movie screen, not a full-width field. It hides while the bottom screen is off | Oct 23, 2026 |

**Questions the design must answer**

- Does the video always play on the top screen, or can you choose?
- What does the bottom screen show while browsing, and while playing?
- Poster row, grid or list for browsing, and how many titles on screen?
- In single-screen mode, where do the menus and episode list go?
- What do the buttons do on each screen?

**Starting ideas from the first mockup** (one option to compare, not a decision)

| State | Top screen (6", 1080p) | Bottom screen (3.92", touch) |
| --- | --- | --- |
| Browsing | Poster row you scroll; the selected title large, with backdrop, year, genre, plot | Filters and search: Movies, Series, Continue watching, Unwatched, Recently added |
| Movie selected | Backdrop, logo, plot, cast, runtime | Play / Resume button, subtitle and audio choice, mark watched |
| Series selected | Show backdrop, logo, plot | Season buttons, then the episode list: available ones playable, missing ones greyed; current one highlighted |
| Playing | Full-screen video only | Episode list stays; seek bar, play/pause, skip, audio and subtitle pickers, delay controls. Dims after 10 s untouched |
| Episode ending | Video keeps playing the credits | Up next card with a 10 s countdown and Cancel |
| Stats | Charts: time watched per week, top shows, genres | Period picker and history list |
| Bottom screen off | Normal single-screen player: menus appear over the video, like any phone player | Pure black |

**Button mapping (first idea, not decided)**

- D-pad moves on the top screen while browsing, and on the bottom screen while playing.
- A plays or selects, B goes back, X opens subtitles and audio, Y marks watched.
- L1 and R1 jump to the previous and next episode; L2 and R2 skip 10 s back and forward.
- **(research)** One combo turns the bottom screen off and on (our own, because AYN's can't be detected).

**Fallback (changed by research)**

The app watches the displays when it starts and whenever they change (`DisplayManager.DisplayListener`). If the bottom screen is removed, it switches to the single-screen layout without stopping playback. A display that is merely *off* (during wake-up) doesn't count as removed.

Turning the bottom screen off by holding the AYN button **can't be detected**: AYN draws an overlay and the display stays on. So:

- The app has its own **Bottom screen off** action. It blanks our window to pure black (OLED pixels off) and switches to the single-screen layout.
- During playback the bottom screen stays static (no animations, redraws once a second), so little is wasted if AYN's overlay covers it.

If a frontend opens the app on the bottom screen, the app moves itself to display 0 first.

## Battery and performance budget

The target: an hour of 1080p playback uses no more battery than VLC playing the same file on the same settings. Every rule below exists to hit that.

**Rules while a video plays**

1. **Hardware decoding by default.** The video chip decodes H.264, HEVC, VP9 and AV1 at a fraction of the power software decoding needs. Software is used only for a file the chip can't decode, and the app shows a small notice when that happens.
2. **Video drawn by the display hardware (research).** Video goes into a `SurfaceView`, which the display hardware composes directly. A `TextureView` (what Loki uses) costs a GPU copy every frame.
3. **Never raise the refresh rate (research).** The Thor runs at 60 Hz with both screens on. The app never asks for 120 Hz. It asks for the film's own frame rate (24, 25, 30 or 60) only if v0 shows the Thor offers those modes; that also stops 24 fps judder.
4. **True black interface.** The Thor's screens are AMOLED, where black pixels are switched off. All backgrounds are pure black.
5. **Quiet bottom screen.** It dims after 10 s untouched and wakes on a touch or a button. An option turns it fully black during playback. **(research)** Dimming is done by drawing black over the content, because a window brightness override changed *both* screens on older firmware; v0 tests whether it's per-screen now.
6. **Slow updates on the bottom screen.** The seek bar and clock redraw once a second, not every frame.
7. **No background work during playback.** Scanning, artwork downloads and subtitle searches pause until playback stops.
8. **Few writes.** Position and stats are saved on pause, stop, episode change and once a minute, not continuously.
9. **Standard performance mode is enough.** The app never needs the Thor's High mode, and never asks for it.

**Rules when not playing**

1. **No always-running service.** Nothing runs in the background except a short job after you add files.
2. **Artwork downloads are batched** and only run on Wi-Fi. An option limits them to when the Thor is charging.
3. **Images decoded at display size.** Posters are stored at the size shown, not full resolution, so scrolling stays smooth and light.
4. **Rescans are cheap.** Only folders whose contents changed are read again.

| Activity | Budget |
| --- | --- |
| 1 h of 1080p HEVC playback, brightness 50%, Wi-Fi off | No more than VLC on the same file (measured, ±3%) |
| App idle in the background | No wake-ups, no network |
| First scan of 200 files, artwork included | Under 5 min on Wi-Fi |
| Rescan with nothing new | Under 5 s |
| Memory during playback | Under 300 MB |
| Start to first frame | Under 1.5 s from a local file |

## System architecture and tech stack

The app is split so playback depends only on local files and the database. Anything that needs the internet runs separately and only adds to the database.

```
            Top screen (display 0)                    Bottom screen ("Screen-2")
   ┌───────────────────────────────────┐    ┌───────────────────────────────────┐
   │ MainActivity (Compose)            │    │ BottomScreen Presentation         │
   │  • browsing UI / SurfaceView video│    │  (Compose, own lifecycle,         │
   │  • receives all controller keys   │    │   FLAG_NOT_FOCUSABLE: touch only) │
   └───────────────┬───────────────────┘    └───────────────┬───────────────────┘
                   │      shared ViewModels / StateFlows      │
                   └───────────────┬──────────────────────────┘
                     ┌─────────────┴──────────────┐
                     │ Playback service (Media3   │  ← the only part running
                     │ session, ExoPlayer, FFmpeg │    during playback
                     │ audio)                     │
                     └─────────────┬──────────────┘
         ┌─────────────────────────┼───────────────────────────┐
   ┌─────┴──────┐        ┌─────────┴─────────┐        ┌────────┴─────────┐
   │ Room DB    │        │ Library scanner   │        │ Online workers    │
   │ DataStore  │        │ (SAF folder, file │        │ TMDB, OpenSubs    │
   │            │        │  name parser)     │        │ (WorkManager)     │
   └────────────┘        └───────────────────┘        └──────────────────┘
```

**Two-screen rules (research):**

- The activity runs on the top screen; the bottom screen is an `android.app.Presentation` on the display found through `DISPLAY_CATEGORY_PRESENTATION`. It skips private, virtual and "HiddenDisplay"/"WebRTC_ScreenCapture" displays, and picks the bottom panel over a docked TV.
- The Presentation window is `FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCH_MODAL`, has its **own** `LifecycleOwner` (resumed while showing) and shares only the `ViewModelStore`. Borrowing the activity's lifecycle freezes it (a lesson from Loki). It swallows Back and Escape, and forwards keys to the same handler as the activity.
- The player lives in the playback service, never in a composable, so a display change, a screen swap or the activity being recreated never restarts the film.
- The activity declares `configChanges` for screen size, density and layout, so moving between displays doesn't recreate it.
- `show()` is wrapped against `BadTokenException` and `InvalidDisplayException`; the Presentation is always dismissed when the app leaves, so the bottom screen never gets stuck.

| Area | Choice | Why |
| --- | --- | --- |
| Language and UI | Kotlin, Jetpack Compose | Android's current standard; good controller focus handling |
| Second screen | `Presentation` API on the bottom display **(research: confirmed)** | Used by melonDS, Azahar and Loki on the Thor; needs no home-app role (Argosy's separate-activity approach does) |
| Video surface | `SurfaceView` **(research)** | The display hardware composes it directly: lowest power |
| Playback engine | Media3 ExoPlayer + FFmpeg audio add-on (recommended); libVLC as the alternative | Hardware decoding with low power; FFmpeg adds DTS, TrueHD and AC3 (without it they play silently) |
| Media controls | Media3 media session in a foreground service | Notification and headset controls; Android won't kill playback |
| Database | Room (SQLite) | Fast, offline, simple to back up |
| Settings | DataStore | Small settings without touching the database |
| Images | Coil, decoded at display size, disk cache | Smooth scrolling, low memory |
| Network | OkHttp with response cache | TMDB and OpenSubtitles calls, cached so they're not repeated |
| Background work | WorkManager with Wi-Fi and charging rules | Runs only when allowed; Android batches it to save battery |
| Filename parsing | Kotlin rules ported from the Movie Scraper | guessit is Python and can't run on Android |
| Subtitles | Media3's subtitle renderer; a libass-based renderer in v2 | srt, vtt and basic ASS in v1; full ASS styling later |
| Artwork and info | TMDB API first; fanart.tv and AniList as fallbacks (see below) | Free keys; proper 16:9 backdrops, posters, title logos, episode stills |
| Subtitle search | OpenSubtitles API | Search by file hash finds the exact matching file |
| Stats charts | A Compose charts library (e.g. Vico) | Draws on the device, no internet |
| Opening from frontends | `ACTION_VIEW` for `video/*` with a temporary `content://` grant | How ES-DE (`%ACTION%`/`%DATA%`), Cocoon, Daijisho and RetroHrai! launch apps |

## Artwork sources and sizes (decision D7)

Every image is fetched through an API, in the size its screen needs, and stored on the device. Nothing is cropped from a different kind of image.

| Where it shows | Image type | Source, in order | Size fetched | Notes |
| --- | --- | --- | --- | --- |
| Top screen: focused title, Movie/Series selected | **Backdrop**, 16:9 | TMDB backdrops → fanart.tv backgrounds → AniList banner (anime) | TMDB `w1280` (`original` when docked to a TV) | Prefer **textless** backdrops (`include_image_language=null`), because our own title sits over them. Highest-rated by TMDB vote |
| Top screen: title over the art | **Logo** (transparent PNG) | TMDB logos → fanart.tv clearlogo | `w500` | Language: the app's language, then English. Falls back to the title in text |
| Bottom screen: cards | **Poster**, 2:3 | TMDB posters → AniList `coverImage.extraLarge` (anime) → fanart.tv | TMDB `w342` (a card is ≈ 260 px wide on the 1240 px panel) | Decoded at card size (battery rule) |
| Top screen: poster mix | Poster, 2:3 | Same as cards | `w342` | Reuses the card images; no extra downloads |
| Series: episode list, Up next | **Still**, 16:9 | TMDB episode stills | `w300` (list), `w780` (Up next) | Missing stills fall back to the show's backdrop |
| Others tab (unmatched videos) | Frame from the video | Extracted on the device | 960 × 540 | No network |

Rules:

- **Right size, once.** Download each image in the size above, resize to the exact display size, and store it as WebP. About 0.5 MB per title, as budgeted.
- **Wide art only on the wide screen.** If no 16:9 backdrop exists from any source, show the blurred-poster fallback, never a stretched or cropped poster.
- **Anime** is matched on TMDB first; AniList fills gaps (its banners and covers are often better for anime). Both IDs are kept on the title.
- **Credits:** TMDB in the About screen (required); fanart.tv and AniList as their terms require.
- **Keys:** TMDB (free), fanart.tv personal API key (free); AniList needs no key.
- **When:** only on Wi-Fi, batched, never during playback (battery rules).

## Data model

Everything lives in one small database on the device, so the library, progress and stats work offline. Stats are worked out from watch sessions, never stored as running totals, so they can't drift.

| Table | Key fields | Notes |
| --- | --- | --- |
| media\_file | id, path, size, modified, duration, video codec, resolution, audio tracks, subtitle tracks | One row per file on the card; size + modified time detect changes |
| movie | tmdb\_id, title, year, plot, runtime, rating, genres, director | Linked to its file |
| show | tmdb\_id, name, first aired, plot, genres, network, status | One per series |
| season | show id, number, name, episode count | From TMDB, so missing episodes can be listed |
| episode | show id, season, number, title, air date, plot, still | Linked to a file when one exists; none = missing |
| artwork | owner, type (poster, backdrop, logo, still, frame), source (TMDB, fanart.tv, AniList, device), source id, language, width × height, local path | Images stored on the device after download, already at display size |
| match\_override | file path, type, tmdb\_id, season, episode | Your manual fixes; applied before automatic matching |
| playback\_state | file id, position, watched, times finished, last played, chosen audio and subtitle track, delays | One row per file |
| watch\_session | file id, started, ended, seconds watched | One row each time you watch; all stats come from these |
| subtitle\_file | file id, path, language, source (local, embedded, downloaded) | Downloaded files are saved next to the video |
| intro\_marker | show id, season, start, end | For Skip intro (v3) |

Settings (theme, bottom screen behaviour, subtitle style, Wi-Fi-only downloads) are kept separately in DataStore, not the database.

## Device and system requirements

The app targets the Thor Max first and runs on any Android 10+ phone, with the dual-screen layout only where a second display exists.

**Target device: AYN Thor Max**

| Part | Spec | What it means for the app |
| --- | --- | --- |
| Chip | Snapdragon 8 Gen 2 | Hardware decoding for H.264, HEVC 10-bit, VP9 and AV1 (the Thor **Lite**'s Snapdragon 865 has no AV1 hardware) |
| Top screen | 6" AMOLED, 1080 × 1920, 120 Hz (60 Hz with both screens on) | Video and browsing; true black saves power |
| Bottom screen | 3.92" AMOLED 1080 × 1240, 60 Hz, touch | Controls and episode list |
| RAM | 16 GB | No memory pressure; keep under 300 MB anyway |
| Storage | 512 GB internal + microSD | Videos on the SD card; artwork on internal |
| Battery | 6,000 mAh | The budget in the battery section |
| Android | 13 with AYN's Odin system apps; firmware 1.0.0.377 is the latest known | Sets the features the app can rely on; retest after every OTA |
| Video out | USB-C DisplayPort (since firmware .293) | TV output in v2 |

**App requirements**

| Item | Value |
| --- | --- |
| Minimum Android | 10 (API 29) |
| Target Android | 14 (API 34) |
| APK size | Under 40 MB |
| Artwork cache | About 0.5 MB per title; 250 MB for 500 titles; size cap in settings |
| Database | Under 50 MB for thousands of files |

**Permissions**

- **Folder access** through Android's folder picker for the SD card video folder only. No broad storage permission.
- **Internet** and network state, for artwork and subtitle search only.
- **Foreground media playback service** and **notifications**, so playback controls show in the notification shade.
- **Keep screen on** during playback only.

**Accounts and keys (all free)**

- TMDB API key, for artwork and info. The app must credit TMDB in its About screen.
- OpenSubtitles account and API key, for subtitle search (v1). The free tier has a daily download limit.
- Trakt account, optional (v3).

**Build machine**

- Your Windows PC with Android Studio (latest stable), which includes the Java toolkit.
- 16 GB RAM recommended for the Android emulator.
- USB-C cable and USB debugging turned on, on the phone and later the Thor.

## Build phases and milestones

The work runs in four versions. Each one ends at a gate, and the next starts only when the gate passes.

| Version | Contents | Gate to pass |
| --- | --- | --- |
| **v0: two-screen test app + mockups** | A small probe app: video on the top screen, controls on the bottom screen, display and decoder report, refresh-mode test, bottom-screen dimming test, launch-from-bottom test. Layout mockups for the questions above | **Gate 1:** both screens work on the Thor with buttons and touch; the probe questions below are answered; a layout is chosen |
| **v1: usable player** | Library scan, filename matching, artwork, series, resume and watched, Continue watching, auto-play next, local subtitles, subtitle search (OpenSubtitles), all v1 baseline rows | **Gate 2:** battery test passes against VLC; 95% of the filename test set matches |
| **v2: comfort and stats** | Stats and export, backup, launch from frontends, sleep timer, background audio, night mode, TV output, full ASS | **Gate 3:** no battery regression; stats match a hand count |
| **v3: extras** | Skip intro, Trakt, themes, picture-in-picture | — |

v0 comes first because the two-screen behaviour on the Thor is the biggest unknown. **(research)** Other Thor apps already prove the approach, so v0 now mostly *measures* things. If the gate after v1 fails on battery, it gets fixed before any v2 feature is added. Each build round ends with a short list of things for you to try on the device.

**Questions v0 must answer on the real Thor (research)**

- [ ] Which display modes do both screens offer? Does asking for the film's frame rate (`Surface.setFrameRate`, `preferredDisplayModeId`) change the mode with both screens on?
- [ ] Does a brightness override on the bottom-screen window dim only the bottom panel on firmware .377?
- [ ] Can our window notice AYN's "bottom off" overlay at all?
- [ ] Which codecs are hardware-decoded (HEVC 10-bit, AV1, AC3, E-AC3)?
- [ ] Does launching from Cocoon or ES-DE on the bottom screen, then moving to display 0, work smoothly?
- [ ] Does the bottom-screen window survive lid close and open, sleep and wake, an AYN screen swap and a Wayfinder move?
- [ ] With Home and Back bound to the active screen (.372+), does touching our bottom controls change where Back goes?

## Testing plan

Most of the app can be tested before the Thor arrives. Only final dual-screen tuning and the battery check need the real device.

| Stage | Where | What gets tested |
| --- | --- | --- |
| 1. Unit tests | On the PC, no device | Filename parser against 100 real filenames; stats maths; match overrides |
| 2. Emulator | Android Studio emulator | Library, artwork, playback, resume, auto-next |
| 3. Simulated second screen | Emulator or phone with Developer options › Simulate secondary displays (1080 × 1240 is closest) | Top and bottom screen layouts, switching to single screen |
| 4. Your phone | Real hardware, one screen | Hardware decoding, subtitles, controller over Bluetooth |
| 5. Thor Max | The real device | Both screens, buttons, AYN button, bottom screen off, battery budget |

**Test file set** (your own files, kept in one test folder)

- One each of: H.264 mkv, HEVC 10-bit mkv, AV1, mp4 with AAC, a file with DTS audio, one with AC3, **one HDR10 file (research)**.
- A file with embedded ASS subtitles, one with a separate .srt, one with two audio languages.
- Three loose episodes of one show with messy names, and one movie with a vague name, to test matching.

**Battery test, run on the Thor**

1. Write down the firmware build. Charge to 100%, brightness 50%, Wi-Fi off, Performance mode Standard.
2. Play the same 1-hour 1080p HEVC file in VLC; note the battery drop.
3. Charge again, repeat in the app with the bottom screen dimmed.
4. Pass: the app's drop is no more than VLC's, within 3%. Fail: profile the app before adding features.

**After every Thor OTA (research):** both screens show, buttons go to the top screen after touching the bottom screen, Back works, bottom screen off works, HDR playback and returning from it, a 10-minute battery spot check.

## Risks, limits and open decisions

| Risk | Impact | Plan |
| --- | --- | --- |
| The Thor's bottom screen may not behave like a standard second display | Dual-screen layout fails | **(research: largely retired)** melonDS, Azahar and Loki use it as a standard presentation display. v0 confirms it |
| AYN's "bottom screen off" can't be detected **(research)** | Our window keeps drawing under AYN's overlay | Our own Bottom screen off action; static bottom screen during playback |
| No film-rate refresh modes **(research)** | 24 fps films judder at 60 Hz | Measured in v0; if absent, accept it (same as VLC) |
| Window brightness may affect both screens **(research)** | Dimming the bottom screen dims the film | Dim by drawing black until v0 proves per-screen brightness |
| Launched on the bottom screen by a frontend **(research)** | Can't put a window on the top screen from there | Move to display 0 at start |
| Docked TV becomes a third display **(research)** | Controls land on the TV | Choose the bottom panel by name or flags; TV handling in v2 |
| Some files need software decoding (rare codecs, very high bitrates) | Higher battery use | Show a notice; keep hardware decoding the default |
| DTS, TrueHD play silently without FFmpeg **(research)** | No sound with no error | FFmpeg audio add-on; an "unsupported audio" notice |
| ASS subtitle styling is limited in the default engine | Anime-style subtitles look plain | Basic styling in v1; add a full ASS renderer in v2 |
| Filename parsing misses odd names | Wrong or missing artwork | Manual fix screen and overrides from v1 |
| OpenSubtitles free-tier daily limit | Few downloads per day | Search by file hash for the right file first time; local files always work |
| I can't compile or test on the device here | Slower rounds | You build in Android Studio; each round ends with a short test list |
| AYN firmware updates change dual-screen behaviour | Layout breaks after an update | Single-screen fallback always works; the after-OTA checklist above |
| Custom ROMs (LineageOS 24) behave differently **(research)** | Dual screen may not work | Stock firmware is the target; single-screen layout works everywhere |
| Other overlays (TCC, game assistant, Wayfinder) share the screens **(research)** | Focus or drawing surprises | Don't assume our windows are alone; never pause on losing focus |

**Open decisions**

- [ ] App name (Thor Media Player is a placeholder).
- [ ] Playback engine: Media3 ExoPlayer with the FFmpeg audio add-on (recommended: lower battery, better Android integration), or libVLC (plays more odd files, heavier).
- [ ] Library layout: loose files only, or encourage Show/Season folders as well.
- [ ] Create a TMDB API key now; OpenSubtitles account before v1.
- [ ] Trakt sync: wanted, or skip.
- [ ] Install Android Studio on your PC before v0 starts.
- [ ] Screen layouts: design several options as mockups, compare them on the Thor, pick one (v0).
- [ ] **(research)** Licence: borrowing code from Loki, melonDS or Azahar (GPL-3.0) makes the app GPL-3.0, which is fine for a personal app. Wayfinder's code (PolyForm Strict) can't be used.

**Sources**

- Research notes: [research/thor-dual-screen.md](research/thor-dual-screen.md), [research/thor-platform-and-issues.md](research/thor-platform-and-issues.md)
- [Held Games: AYN Thor device page](https://heldgames.com/devices/ayn-thor)
- Reddit r/AynThor thread on adding videos to Cocoon (screenshot you shared)
