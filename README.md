# StreamHub

Plugin-based Android video player with Material 3 / Material You UI. **It hosts no content** — you install plugins to add sources; like CloudStream, but plugins are **compile-free**: put a `plugin.json` inside a zip, import it in the app, and it works.

> **⚠️ Legal Disclaimer:** StreamHub does not host, store, upload or distribute any video/movie/series file. The app is only a player + plugin platform; the user loads content from third-party sites through plugins and bears all responsibility. Full text: [DISCLAIMER.md](DISCLAIMER.md). The app shows this notice as a mandatory dialog on first launch; if you decline, it closes.

## Features (v2.5)

- **🔌 Compile-free plugin system** — `plugin.json` inside a zip; static video list or site scraping with CSS selectors; add/remove in-app
- **🧩 Built-in resolvers** — NP_CONFIG (m3u8 + SHA-256 proof-of-work gate + subtitles), the ContentX iframe family, in-page m3u8/mp4 regexes — plugins use them automatically
- **🎬 ExoPlayer (Media3)** — HLS + MP4 + local files (content://), source/quality picker, subtitle support, **resume where you left off**
- **📺 Picture-in-Picture (PiP)** — keep watching in a floating window when you press Home (Android 8+); the real video aspect ratio is preserved
- **⛶ Fullscreen** — system bars hide automatically in landscape (immersive); one-tap toggle
- **🔄 Orientation lock** — Auto / Portrait / Landscape from the player
- **💬 Subtitles, done right** — plain text (no background box), fully customizable: **size, color, position, delay and font**; **external .srt/.vtt import** with exact delay in both directions; embedded track selection for multi-language videos
- **🔊 Audio language** — pick the audio track when a video has more than one
- **⚙️ In-player settings panel** — tap the video and the panel pops up bottom-right: fullscreen, PiP, orientation, sources, subtitles, audio language
- **🌐 App language** — Turkish / English, chosen right after the first-launch legal notice (also changeable in Settings)
- **⭐ Favorites & 🕒 Watch history** — stored on-device
- **▶ Play from URL & 📁 local file playback** — the player works even without a plugin
- **🎨 Material 3 + Material You** — dark theme, bottom navigation, dynamic wallpaper colors, chips, cards, snackbars
- **🐞 Visible errors** — in-screen error + snackbar + "Open site in WebView" (passes bot-protection cookies) + **Last Crash Log** in Settings
- **⚖️ Mandatory legal notice** — first-launch acceptance dialog, always reachable from Settings

## Install

1. Releases → `latest` → download `StreamHub-debug.apk` and install
2. Open the app → accept the legal notice → pick your language
3. **Install the sample plugin:** download [`plugins/ornek-plugin.zip`](plugins/ornek-plugin.zip) from the repo → Settings → ＋ Add Plugin (.zip)
4. To write your own plugin: [PLUGINS.md](PLUGINS.md)

## Plugin — 60-second summary

A zip containing `plugin.json`:

```json
{
  "name": "Demo Videos",
  "kind": "static",
  "entries": [
    { "title": "Test video", "url": "https://example.com/video.m3u8" }
  ]
}
```

Import the zip in-app — done. Full documentation including site scraping, search, episodes and video extraction rules: **[PLUGINS.md](PLUGINS.md)**.

## Attribution — CloudStream

Some player and networking features in this project were inspired by and adapted from **[CloudStream](https://github.com/recloudstream/cloudstream)** (recloudstream, **GPL-3.0** licensed):

- single OkHttp network stack (Cloudflare DoH + persistent cookies + one UA) — modeled after CloudStream's single-point network layer
- auto-retry with growing windows for 429/5xx + automatic fallback to an alternative source
- switching to an alternative decoder on codec mismatch for Mali/Adreno GPUs
- source/quality picker and resume-from-position behavior

Thanks to CloudStream and its community. For GPL-3.0 compliance StreamHub is also licensed under **GPL-3.0**: [LICENSE](LICENSE).

## Build

```bash
./gradlew assembleDebug
# output: app/build/outputs/apk/debug/app-debug.apk
```

CI: every push, GitHub Actions builds the APK and puts it under Releases → `latest` (`.github/workflows/build.yml`).

## Project structure

```
app/src/main/java/com/streamhub/app/
├── core/       Provider interface, JSON plugin engine, plugin manager, favorites/history, i18n, crash handler
├── net/        OkHttp client + persistent cookies + bot-protection detection
├── providers/  Built-in video resolvers (NP, ContentX)
└── ui/         Material 3 screens (Home/Search/Favorites/Settings/Detail/Player/WebView)
plugins/        Sample plugin (plugin.json + zip)
```

## Legal

This app hosts no videos and is not affiliated with any website. Intended for personal and educational use. Details: [DISCLAIMER.md](DISCLAIMER.md). License: **GPL-3.0** ([LICENSE](LICENSE)).
