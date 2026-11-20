# StreamHub

CloudStream benzeri, tamamen kendi yazdığımız Android video uygulaması.

## Özellikler (v1)
- **Gömülü Dizilla sağlayıcısı** — dizilla.la: ana sayfa satırları, `/ara?q=` araması,
  dizi + bölüm listesi, NP/Pichive oynatıcı çözücü (`window.NP_CONFIG` → m3u8 +
  SHA-256 proof-of-work kapısı), ContentX ailesi yedekleri, sayfa içi m3u8 fallback
- **ExoPlayer (Media3)** — m3u8 + mp4, kaynak/quality seçici, altyazı desteği
- **Görünür hatalar** — ekran içi hata kutusu + toast + "Yeniden dene" +
  "Siteyi WebView ile aç" (bot koruması çerezlerini uygulamaya aktarır)
- **Eklenti sistemi** — `Android/data/com.streamhub.app/files/plugins/` klasörüne
  atılan `.zip` dosyaları açılışta yüklenir (aşağıdaki biçime bakın)

## Kurulum
1. GitHub Actions sekmesinden ya da Releases → `latest` etiketinden `StreamHub-debug.apk` indir
2. APK'yi aç, "bilinmeyen kaynaklara izin ver" de ve kur

## Eklenti biçimi (v1)
Bir `.zip` içi:
```
manifest.json   -> {"providers":[{"name":"Örnek","class":"com.ornek.OrnekProvider"}]}
classes.dex     -> Kotlin/Java derlenmiş dex (Provider arayüzünü gerçekleyen sınıflar)
```
Provider sınıfı `com.streamhub.app.core.Provider` arayüzünü uygulamalı ve tek
parametreli `constructor(http: Http)` almalıdır. Yüklenen eklentiler gömülü
sağlayıcıların yanında ana sayfada listelenir.

## Yerel derleme
```bash
./gradlew assembleDebug
# çıktı: app/build/outputs/apk/debug/app-debug.apk
```

## Yasal not
Bu uygulama hiçbir video barındırmaz; yalnızca hedef sitelerin herkese açık
sayfalarındaki bağlantıları kullanıcı için ayrıştırır. Kişisel kullanım içindir.
