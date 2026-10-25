# Thor Media Player: product features

What the app does, and the version each feature arrives in. The reasons and the build plan are in [PLAN.md](PLAN.md).

**Versions:** v0 = two-screen test app and layout mockups · v1 = first usable player · v2 = comfort and stats · v3 = extras.
Rows marked **(research)** were added or changed after the [Thor research](research/).

## Baseline: what every good player does

These are the features people expect from VLC, MX Player, Just Player and Kodi. The app must match the v1 rows before it's worth switching to.

| Feature | What it means | Version |
| --- | --- | --- |
| Wide format support | mkv, mp4, avi, webm, ts; H.264, HEVC (incl. 10-bit), VP9, AV1; AAC, AC3, E-AC3, DTS, FLAC, Opus | v1 |
| Hardware decoding | Uses the video chip by default; software decoding only as a fallback | v1 |
| Unsupported audio notice **(research)** | If a file's audio can't be decoded (for example TrueHD), say so instead of playing silently | v1 |
| Seek and skip | Tap or drag the progress bar; L2 / R2 ±10 s. **Chapter jump isn't possible with ExoPlayer, which doesn't read MKV chapters (D47)** | v1 |
| Resume | Opens where you stopped | v1 |
| Audio track switch | Pick language or commentary track | v1 |
| Subtitle track switch | Pick an embedded or external track, or off | v1 |
| Subtitle and audio delay | Shift timing in 50 ms steps: subtitles on the player screen, audio in Playback settings (Start), kept per file (D45) | v1 |
| Subtitle styling | Size, position and a dark background box in Playback settings (D45); full ASS styles with libass later | v1 (basic), v2 (full ASS) |
| Aspect and zoom | Fit, Zoom to fill, 16:9, 4:3, Stretch, in Playback settings (D45) | v1 |
| Playback speed | 0.5× to 2× | v1 |
| Volume and brightness | Volume bar on the player's bottom screen with a boost to 200 %; the volume buttons move it without Android's pop-up (D41). Quick brightness (top screen) not yet | v1 |
| Screen lock | Dropped: the bottom screen is the only touch screen, and ⏻ already turns it off (D47) | — |
| Sleep timer | Stop after this episode or after N minutes | v2 |
| Background audio | Keep audio going with the screen off; notification controls come with it (D47). Headset play / pause works now | v2 |
| Picture-in-picture | Small floating video while using another app | v3 |
| Night mode | Quieter loud scenes (dynamic range compression), on top of AYN's system EQ | v2 |
| Bluetooth audio delay | Auto or manual offset for headphones | v2 |
| Folder browser | R1 on Browsing: your folders as they are on the SD card, folders first, then videos with progress (D46) | v1 |
| Search, sort, filter | X on Browsing searches titles, years and genres, typed on the app's own keyboard on the bottom screen (D25); Select steps through A–Z, Recently added, Year and Unwatched only (D46) | v1 |
| Playlists and queue | Queue several files, repeat, shuffle | v2 |
| TV output | Plays correctly when docked over USB-C DisplayPort: video on the TV, controls on the bottom screen | v2 |
| Network shares | SMB, DLNA, Jellyfin | Later |
| Casting | Chromecast | Not planned |

## Our features

These are what make the app better than the baseline. Each one is done only when its acceptance test passes.

| Feature | Done when | Version |
| --- | --- | --- |
| Library scan | Offline. You pick a folder once (SD card); it finds every video in it and its subfolders, whether loose or in Show/Season folders (D33), and rescans only changed files | v1 |
| Filename identification | Reads names like `The.Dark.Knight.2008.1080p.mkv` and `Breaking.Bad.S02E05.mkv` correctly; 95% of a test set of 100 real names match | v1 |
| Artwork and info | **Fetched only when you press Fetch info (D32), and only what's missing (D38):** titles that already have their info and images are skipped; for shows, only new seasons, episodes and missing images. Poster, backdrop, logo, plot, year, rating, runtime from TMDB, with fanart.tv and AniList as fallbacks; episode stills and titles for series. **Each image is fetched through the API in the size its screen needs: 16:9 textless backdrops for the top screen, posters at card size for the bottom screen (D7)** | v1 |
| Series grouping | Episodes grouped Show › Season › Episode, even when the files sit loose in one folder | v1 |
| Fix a match | Any title can be re-searched or given a TMDB ID by hand; the fix survives rescans | v1 |
| Missing episodes | Shows episodes not on the card, greyed out, from TMDB's episode list | v1 |
| Resume and watched | Position saved; marked watched at 90%; can be marked watched or unwatched by hand | v1 |
| Continue watching | Home row with the next unwatched episode of each show and half-watched movies | v1 |
| Auto-play next | 10 s countdown at the credits, then the next episode; cancel with one button | v1 |
| Two-screen playback **(research)** | Video full-screen on the top screen; controls, episode list and pickers on the bottom screen; the controller keeps working on the top screen even after you touch the bottom one | v1 |
| Bottom screen off **(research)** | A button combo and an on-screen button blank the bottom screen to pure black and switch to the single-screen layout without stopping playback (AYN's own "hold the AYN button" can't be detected by apps) | v1 |
| Survives screen changes **(research)** | Swapping or moving screens (AYN swap, Wayfinder, docking, sleep and wake, lid) never restarts playback | v1 |
| Local subtitles | Loads .srt, .ass and .vtt files next to the video automatically, matched by name and language | v1 |
| Launch from frontends | RetroHrai!, Cocoon or ES-DE can open the app, or a specific file in it. **If launched on the bottom screen, the player moves itself to the top screen (research)** | v2 |
| Pause when leaving | Home, switching apps, closing, power button or lid: the film pauses and its position is saved (D40) | v1 |
| Settings | Refresh rate for each screen: Auto or a fixed rate (D42). API keys (TMDB, fanart.tv, OpenSubtitles) and the OpenSubtitles login, typed on the app's own keyboard or pasted; stored encrypted on the device (D39). Also the video folder and About with the TMDB credit. Opened with Start or ⚙ on Browsing | v1 |
| Subtitle search | Searches OpenSubtitles by file hash and title, downloads the chosen file next to the video, named like a local subtitle (`Name.en.srt`) so it travels with the file (D37). Reached from the Movie screen's Subtitles block when the file has none (D9) | v1 (moved up from v2) |
| Watching stats | Total time watched, time per show, episodes finished, genres, streaks, history list. Not needed for now (D27); watch sessions are still recorded so it can be added later | Later |
| Stats export | Export stats and watch history as CSV | Later |
| Backup | Export and import settings, matches and watch history in one file | v2 |
| Skip intro | Set the intro start and end once per show; a Skip button appears in later episodes | v3 |
| Trakt sync | Optional two-way sync of watched status, only when you ask (D35) | v3 |
| Themes | True-black default, plus accent colours | v3 |

## Not in scope

- Streaming services, downloading films, or any piracy sources.
- Music playback.
- Publishing on the Play Store. It's a personal app, installed as an APK.
- Media servers (Plex, Jellyfin) and network shares in v1.
- Root or AYN's privileged service (`PServerBinder`). The app uses public Android APIs only. **(research)**
