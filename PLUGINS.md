# Eklenti Yapımı (Plugin Guide)

StreamHub'da kaynak eklemenin tek yolu **eklenti** kurmaktır. İyi haber: **derleme yok, Kotlin yok** — eklenti, içinde bir tane `plugin.json` dosyası olan bir `.zip`'tir. Uygulama zip'i açılışta okur; JSON kurallarına göre siteyi tarar ya da doğrudan video listesi gösterir.

---

## 1) Kurulum

**Uygulama içinden (önerilen):**
1. `plugin.json` içeren zip dosyanı hazırla
2. StreamHub → **Ayarlar → ＋ Eklenti Ekle (.zip)**
3. Zip'i seç — bitti. Ana sayfada kaynağın görünür.

**Dosya yöneticisinden:**
`Android/data/com.streamhub.app/files/plugins/` klasörüne zip'i at ve uygulamayı yeniden aç. (Android 13+ dosya yöneticileri bu klasörü görmeyebilir; o yüzden uygulama içi ekleme daha kolay.)

**Örnek eklenti:** Repo'daki [`plugins/ornek-plugin.zip`](plugins/ornek-plugin.zip) — 4 tane herkese açık test videosu içerir. Kurup hemen deneyebilirsin.

---

## 2) plugin.json alanları

| Alan | Zorunlu? | Ne işe yarar |
|---|---|---|
| `name` | ✅ | Ana sayfa/Ayarlar'da görünen kaynak adı |
| `kind` | — | `static` (doğrudan video listesi) ya da `site` (CSS ile tarama). Boşsa içerikten otomatik anlaşılır |
| `version` | — | Eklenti sürümü (kendi notun için) |
| `description` | — | Ana sayfada küçük gri yazıyla görünür |
| `baseUrl` | site için önerilir | Göreceli linklerin başına eklenir + isteklerin `Referer`'ı olur |
| `entries` | static için ✅ | `[{title, url, poster}]` — doğrudan oynanacak videolar |
| `sections` | site için ✅ | Ana sayfa satırları: `[{title, url, cardSelector, ...}]` |
| `search` | site için | `{url, cardSelector, ...}` — `url` içinde `{query}` yazılır |
| `detail` | site için | `{titleSelector, posterSelector, descriptionSelector, episodeSelector, ...}` |
| `player` | site için önerilir | `{iframeSelector, urlRegexes, headers}` — video çıkarma kuralları |

---

## 3) `kind: "static"` — en basit eklenti

Hiç tarama yok; sadece oynatılacak linkleri listeler. `ornek-plugin.zip` tam olarak budur:

```json
{
  "name": "Demo Videolar",
  "kind": "static",
  "description": "Herkese açık test videoları",
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

`url` `.m3u8` ile bitiyorsa HLS olarak, `.mp4`/`.mkv` gibi bitiyorsa düz video olarak oynatılır.

---

## 4) `kind: "site"` — site tarama

Kurgusal bir site üzerinden tam örnek (alan adını kendi kaynağına göre değiştir):

```json
{
  "name": "Örnek Site",
  "kind": "site",
  "version": "1.0",
  "description": "Örnek tarama eklentisi",
  "baseUrl": "https://www.ornek-site.com",

  "sections": [
    {
      "title": "Son Eklenenler",
      "url": "https://www.ornek-site.com/yeni",
      "cardSelector": "div.card > a",
      "titleSelector": "h2.title",
      "posterSelector": "img",
      "posterAttr": "data-src"
    },
    { "title": "Popüler", "url": "https://www.ornek-site.com/populer", "cardSelector": "div.card > a" }
  ],

  "search": {
    "url": "https://www.ornek-site.com/ara?q={query}",
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
    "headers": { "Referer": "https://www.ornek-site.com/" }
  }
}
```

### Nasıl çalışır?

1. **Ana sayfa satırları** — her `sections` girişi için `url` açılır, `cardSelector` ile kartlar bulunur.
2. **Kart alanları** — her kartta:
   - başlık: `titleSelector` içi metin, yoksa `titleAttr` özniteliği, yoksa kart metni
   - poster: `posterSelector` içindeki `<img>`'ten sırayla `posterAttr → data-src → data-original → abs:src → src`
   - link: `linkAttr` ya da `href` (göreliyse `baseUrl` ile tamamlanır)
3. **Arama** — `search.url` içindeki `{query}` URL-encode edilmiş arama terimiyle değiştirilir, sonuçlar aynı kart kurallarıyla listelenir.
4. **Detay** — `detail.titleSelector / posterSelector / descriptionSelector / episodeSelector`. Bölüm numaraları link'ten otomatik okunur: `s01e05`, `1-sezon-5-bolum`, `bolum-5` kalıpları desteklenir, bulunamazsa sıra numarası verilir.
5. **Video çıkarma (bölüm sayfası açılınca)** — sırayla:
   - `player.urlRegexes` — sayfa HTML'inde regex eşleşmeleri (m3u8/mp4 linkleri)
   - sayfada `window.NP_CONFIG` varsa **gömülü NP çözücü** (m3u8 + SHA-256 kapısı) otomatik devreye girer
   - `player.iframeSelector` — iframe'in `src`'si NP (`/iframe.php?v=`) veya ContentX ailesiyse otomatik çözülür
   - hiçbiri bulamazsa: sayfada geçen ilk 5 `m3u8/mp4` linki yedek olarak denir

### Gomulu cozuculer

Eklentide tanımlamana gerek yok — uygulama içinde hazır gelir ve plugin sayfalarında otomatik çalışır:
- **NP çözücü:** `window.NP_CONFIG` → kaynak listesi + altyazılar + proof-of-work kapısı
- **ContentX çözücü:** contentx.me / hotlinger.com / playru.net / pichive.online (ve `four.` varyantları) iframe'leri
- **Düz link çıkarıcı:** sayfada geçen m3u8/mp4 regexleri

---

## 5) Zip yapısı

```
benim-eklentim.zip
└── plugin.json        <- zip kökünde (ya da tek klasör altında) olmalı
```

Zip'e ekstra dosya koymana gerek yok; StreamHub yalnızca `plugin.json` okur.

## 6) Test etme & sık sorunlar

| Belirti | Neden / çözüm |
|---|---|
| "zip içinde plugin.json yok" | JSON dosyasının adı `plugin.json` olmalı; zip'e yanlış klasörden (dosyanın kendisi değil) aldın |
| Ana sayfada "İçerik bulunamadı" | `cardSelector` yanlış — tarayıcıda F12 ile gerçek sınıfları kontrol et |
| "Video kaynağı bulunamadı" | `player.urlRegexes`'i bölüm sayfasındaki gerçek video linkine göre yaz; `headers.Referer` bazı sitelerde şart |
| Bot koruması hatası | Hata satırına dokun → "Siteyi WebView ile aç" → koruma ekranını geç → "Çerezleri Kaydet ve Dön" |
| Eklenti hiça görünmüyor | Ayarlar → eklenti listesinde hata satırı var mı bak; JSON geçerli mi (`jsonlint.com`) |

> **Yasal not:** Eklentiler üçüncü taraf sitelere bağlanır; eklediğin kaynağın kullanımının yasallığı sana aittir. StreamHub hiçbir içerik barındırmaz — ayrıntı: [DISCLAIMER.md](DISCLAIMER.md).
