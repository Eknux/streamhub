# StreamHub

Eklenti tabanlı, Material 3 arayüzlü Android video oynatıcısı. **Hiçbir içerik barındırmaz** — kaynak eklemek için eklenti kurarsın; CloudStream'in yaptığı gibi ama eklentiler **derlemesiz**: zip içine `plugin.json` koy, uygulama içinden yükle, çalışır.

> **⚠️ Yasal Uyarı:** StreamHub hiçbir video/film/dizi dosyasını barındırmaz, depolamaz veya yüklemez. Uygulama yalnızca bir oynatıcı + eklenti altyapısıdır; içeriği eklentiler üzerinden alan kullanıcının tüm sorumluluğu kendisine aittir. Tam metin: [DISCLAIMER.md](DISCLAIMER.md). Uygulama ilk açılışta bu uyarıyı zorunlu diyalogla gösterir; "Kabul etmiyorum" dersen kapanır.

## Özellikler (v2.0)

- **🔌 Derlemesiz eklenti sistemi** — zip içinde `plugin.json`; statik video listesi ya da CSS seçicileriyle site taraması; uygulama içinden ekle/kaldır
- **🧩 Gömülü çözücüler** — NP_CONFIG (m3u8 + SHA-256 proof-of-work kapısı + altyazı), ContentX ailesi iframe'leri, sayfa içi m3u8/mp4 regex'leri — eklentiler otomatik kullanır
- **🎬 ExoPlayer (Media3)** — HLS + MP4 + yerel dosya (content://), kaynak/quality seçici, altyazı desteği, **kaldığın yerden devam**
- **⭐ Favoriler & 🕒 İzleme geçmişi** — cihazda saklanır
- **▶ URL ile oynatma & 📁 yerel dosya oynatma** — eklentisiz de oynatıcı kullanılabilir
- **🎨 Material 3** — koyu tema, bottom navigation, chip'ler, kartlar, snackbar'lar
- **🐞 Görünür hatalar** — ekran içi hata + snackbar + "Siteyi WebView ile aç" (bot koruması çerezi aktarma) + Ayarlar'da **Son Çökme Kaydı** (çökme olursa sebebi görünür, kopyalanabilir)
- **⚖️ Zorunlu yasal uyarı** — ilk açılışta kabul diyalogu, Ayarlar'dan her zaman ulaşılabilir

## Kurulum

1. Releases → `latest` → `StreamHub-debug.apk` indir ve kur
2. Uygulamayı aç → yasal uyarıyı kabul et
3. **Örnek eklentiyi kur:** repo'daki [`plugins/ornek-plugin.zip`](plugins/ornek-plugin.zip) indir → Ayarlar → ＋ Eklenti Ekle
4. Kendi eklentini yazmak için: [PLUGINS.md](PLUGINS.md)

## Eklenti — 60 saniyelik özet

`plugin.json` içeren bir zip:

```json
{
  "name": "Demo Videolar",
  "kind": "static",
  "entries": [
    { "title": "Test videosu", "url": "https://ornek.com/video.m3u8" }
  ]
}
```

Zip'i uygulama içinden yükle — bitti. Site tarama, arama, bölüm ve video çıkarma kuralları dahil tam dokümantasyon: **[PLUGINS.md](PLUGINS.md)**.

## Derleme

```bash
./gradlew assembleDebug
# çıktı: app/build/outputs/apk/debug/app-debug.apk
```

CI: her push'ta GitHub Actions APK derleyip Releases → `latest` altına koyar (`.github/workflows/build.yml`).

## Proje yapısı

```
app/src/main/java/com/streamhub/app/
├── core/       Provider arayüzü, JSON eklenti motoru, eklenti yöneticisi, favoriler/geçmiş, crash handler
├── net/        OkHttp istemcisi + kalıcı çerezler + bot koruması algılama
├── providers/  Gömülü video çözücüler (NP, ContentX)
└── ui/         Material 3 ekranlar (Ana/Arama/Favoriler/Ayarlar/Detay/Oynatıcı/WebView)
plugins/        Örnek eklenti (plugin.json + zip)
```

## Yasal

Bu uygulama hiçbir video barındırmaz ve hiçbir siteyle bağlantılı değildir. Kişisel ve eğitim amaçlıdır. Ayrıntılar: [DISCLAIMER.md](DISCLAIMER.md).
