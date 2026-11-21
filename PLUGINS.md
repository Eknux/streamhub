# Plugin Guide

The only way to add sources to StreamHub is to install a **plugin**. Good news: **no compilation, no Kotlin** — a plugin is just a `.zip` containing a single `plugin.json` file. The app reads the zip at startup; based on the JSON rules it either scrapes a website or shows a plain video list.

---

## 1) Installation

**From inside the app (recommended):**
1. Prepare your zip with a `plugin.json` inside
2. StreamHub → **Settings → ＋ Add Plugin (.zip)**
3. Pick the zip — done. The source shows up on Home.

**From a file manager:**
Drop the zip into `Android/data/com.streamhub.app/files/plugins/` and restart the app. (On Android 13+ some file managers cannot see that folder; in-app import is easier.)

**Sample plugin:** [`plugins/ornek-plugin.zip`](plugins/ornek-plugin.zip) in this repo — 4 public test videos. Install it and try right away.

---

## 2) plugin.json fields

| Field | Required? | What it does |
|---|---|---|
| `name` | ✅ | Source name shown on Home/Settings |
| `kind` | — | `static` (plain video list) or `site` (CSS scraping). Empty = detected from content |
| `version` | — | Plugin version (for your own notes) |
| `description` | — | Small gray text on Home |
| `baseUrl` | recommended for sites | Prepended to relative links + used as the `Referer` of requests |
| `entries` | ✅ for static | `[{title, url, poster}]` — videos to play directly |
| `sections` | ✅ for sites | Home rows: `[{title, url, cardSelector, ...}]` |
| `search` | for sites | `{url, cardSelector, ...}` — `{query}` inside `url` is replaced |
| `detail` | for sites | `{titleSelector, posterSelector, descriptionSelector, episodeSelector, ...}` |
| `player` | recommended for sites | `{iframeSelector, urlRegexes, headers}` — video extraction rules |

---

## 3) `kind: "static"` — the simplest plugin

No scraping; just lists links to play. `ornek-plugin.zip` is exactly this:

```json
{
  "name": "Demo Videos",
  "kind": "static",
  "description": "Public test videos",
  "baseUrl": "",
  "entries": [
    { "title": "Big Buck Bunny (MP4)",
      "url": "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
      "poster": "https://.../BigBuckBunny.jpg" },
    { "title": "Mux HLS Demo (m3u8)",
      "url": "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8" }
  ]
}
```

If `url` ends with `.m3u8` it plays as HLS; if it ends with `.mp4`/`.mkv` etc. it plays as a plain video.

---

## 4) `kind: "site"` — site scraping

Full example over a fictional site (change the domain to your own source):

```json
{
  "name": "Example Site",
  "kind": "site",
  "version": "1.0",
  "description": "Example scraping plugin",
  "baseUrl": "https://www.example-site.com",

  "sections": [
    {
      "title": "Latest",
      "url": "https://www.example-site.com/new",
      "cardSelector": "div.card > a",
      "titleSelector": "h2.title",
      "posterSelector": "img",
      "posterAttr": "data-src"
    },
    { "title": "Popular", "url": "https://www.example-site.com/popular", "cardSelector": "div.card > a" }
  ],

  "search": {
    "url": "https://www.example-site.com/search?q={query}",
    "cardSelector": "div.result > a"
  },

  "detail": {
    "titleSelector": "h1.detail-title",
    "posterSelector": "div.poster img",
    "descriptionSelector": "div.summary",
    "episodeSelector": "ul.episodes li > a"
  },

  "player": {
    "iframeSelector": "iframe.player-frame",
    "urlRegexes": ["https?://[^\"'\\s<>]+\\.m3u8[^\"'\\s<>]*"],
    "headers": { "Referer": "https://www.example-site.com/" }
  }
}
```

### How does it work?

1. **Home rows** — for each `sections` entry the `url` is fetched and cards are found with `cardSelector`.
2. **Card fields** — for every card:
   - title: text inside `titleSelector`, else the `titleAttr` attribute, else the card text
   - poster: from the `<img>` inside `posterSelector`, trying `posterAttr → data-src → data-original → abs:src → src` in order
   - link: `linkAttr` or `href` (relative links are completed with `baseUrl`)
3. **Search** — `{query}` inside `search.url` is replaced with the URL-encoded search term; results are listed with the same card rules.
4. **Detail** — `detail.titleSelector / posterSelector / descriptionSelector / episodeSelector`. Episode numbers are parsed from the link automatically: patterns like `s01e05`, `1-sezon-5-bolum`, `bolum-5` are supported; otherwise the index number is used.
5. **Video extraction (when an episode page opens)** — in order:
   - `player.urlRegexes` — regex matches in the page HTML (m3u8/mp4 links)
   - if the page contains `window.NP_CONFIG`, the **built-in NP resolver** (m3u8 + SHA-256 gate) kicks in automatically
   - `player.iframeSelector` — if the iframe `src` is an NP (`/iframe.php?v=`) or ContentX-family URL it is resolved automatically
   - if none match: the first 5 m3u8/mp4 links found on the page are tried as a fallback

### Built-in resolvers

You don't need to define these — they ship with the app and run automatically on plugin pages:
- **NP resolver:** `window.NP_CONFIG` → source list + subtitles + proof-of-work gate
- **ContentX resolver:** contentx.me / hotlinger.com / playru.net / pichive.online (and `four.` variants) iframes
- **Plain link extractor:** m3u8/mp4 regexes found on the page

---

## 5) Zip layout

```
my-plugin.zip
└── plugin.json        <- must be at the zip root (or inside a single folder)
```

You don't need to put anything else in the zip; StreamHub only reads `plugin.json`.

## 6) Testing & common problems

| Symptom | Cause / fix |
|---|---|
| "plugin.json not found in zip" | The JSON file must be named exactly `plugin.json`; you probably zipped the wrong folder (not the file itself) |
| "No content found" on Home | Wrong `cardSelector` — inspect the real classes with F12 in your browser |
| "No video source" | Write `player.urlRegexes` based on the real video link on the episode page; `headers.Referer` is required on some sites |
| Bot-protection error | Tap the error row → "Open site in WebView" → pass the protection screen → "Save Cookies and Return" |
| Plugin doesn't show up at all | Check the error row in Settings → plugin list; validate the JSON (jsonlint.com) |

> **Legal note:** Plugins connect to third-party sites; the legality of what you add is on you. StreamHub hosts no content — see [DISCLAIMER.md](DISCLAIMER.md).
