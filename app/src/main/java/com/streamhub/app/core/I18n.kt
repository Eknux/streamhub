package com.streamhub.app.core

import android.content.Context
import android.content.SharedPreferences

/**
 * Iki dilli metin sistemi (tr/en). Kullanicinin secimi SharedPreferences'ta tutulur;
 * ilk acilista yasal uyariyi kabul edince dil secilir. Tum ekranlar metinlerini
 * I18n.s("anahtar") ile alir — dil degisince ekran yeniden kurulur.
 */
object I18n {

    const val TR = "tr"
    const val EN = "en"

    @Volatile
    var lang: String = TR
        private set

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.getSharedPreferences("streamhub_prefs", Context.MODE_PRIVATE)
        lang = prefs?.getString("app_lang", TR) ?: TR
    }

    /** Dil degistirir ve kalici kaydeder. */
    fun set(context: Context, newLang: String) {
        lang = if (newLang == EN) EN else TR
        prefs?.edit()?.putString("app_lang", lang)?.apply()
    }

    /** Anahtar metni aktif dilde dondurur; bulunamazsa Turkce, o da yoksa anahtari dondurur. */
    fun s(key: String): String {
        val e = STRINGS[key] ?: return key
        return e[lang] ?: e[TR] ?: key
    }

    /** %s yer tutucularini sirayla doldurur. */
    fun f(key: String, vararg args: String): String {
        var out = s(key)
        for (a in args) out = out.replaceFirst("%s", a)
        return out
    }

    // ------------------------------------------------------------

    private val STRINGS: Map<String, Map<String, String>> = mapOf(

        // ---------- genel ----------
        "ok" to mapOf(TR to "Tamam", EN to "OK"),
        "cancel" to mapOf(TR to "İptal", EN to "Cancel"),
        "close" to mapOf(TR to "Kapat", EN to "Close"),
        "copy" to mapOf(TR to "Kopyala", EN to "Copy"),
        "copied" to mapOf(TR to "Kopyalandı", EN to "Copied"),
        "delete" to mapOf(TR to "🗑 Sil", EN to "🗑 Delete"),
        "copy_emoji" to mapOf(TR to "📋 Kopyala", EN to "📋 Copy"),
        "copy_error" to mapOf(TR to "📋 Hatayı Kopyala", EN to "📋 Copy Error"),
        "play" to mapOf(TR to "Oynat", EN to "Play"),
        "error_prefix" to mapOf(TR to "Hata:", EN to "Error:"),
        "retry" to mapOf(TR to "Yeniden dene", EN to "Retry"),
        "open_in_webview" to mapOf(TR to "Siteyi WebView ile aç", EN to "Open site in WebView"),
        "language_title" to mapOf(TR to "Dil seç / Choose language", EN to "Choose language / Dil seç"),
        "language_row" to mapOf(TR to "🌐 Dil / Language", EN to "🌐 Language / Dil"),

        // ---------- ana ekran ----------
        "toolbar_subtitle" to mapOf(TR to "Eklenti tabanlı video oynatıcı", EN to "Plugin-based video player"),
        "nav_home" to mapOf(TR to "Ana", EN to "Home"),
        "nav_search" to mapOf(TR to "Arama", EN to "Search"),
        "nav_favs" to mapOf(TR to "Favoriler", EN to "Favorites"),
        "nav_settings" to mapOf(TR to "Ayarlar", EN to "Settings"),
        "ui_failed" to mapOf(TR to "Arayüz kurulamadı — hata ekranda gösterildi", EN to "Could not build the UI — error shown on screen"),
        "ui_failed_title" to mapOf(TR to "StreamHub başlatılamadı", EN to "StreamHub failed to start"),
        "ui_failed_msg" to mapOf(
            TR to "Arayüz kurulamadı. Aşağıdaki hatayı ekrandan seçip kopyala ve geliştiriciye gönder:",
            EN to "Could not build the UI. Select and copy the error below and send it to the developer:"),
        "crash_dialog_title" to mapOf(TR to "Son çökme kaydı", EN to "Last crash log"),
        "url_play" to mapOf(TR to "▶ URL ile Oynat", EN to "▶ Play from URL"),
        "local_file" to mapOf(TR to "⟳ Yerel Dosya", EN to "⟳ Local File"),
        "continue_watching" to mapOf(TR to "Izlemeye Devam Et", EN to "Continue Watching"),
        "no_content" to mapOf(TR to "· İçerik bulunamadı", EN to "· No content found"),
        "bot_protection_tap" to mapOf(TR to "Bot koruması — dokun: WebView ile aç", EN to "Bot protection — tap: open in WebView"),
        "error_tap_retry" to mapOf(TR to "Hata: %s — dokun: yeniden dene", EN to "Error: %s — tap: retry"),
        "no_plugin_title" to mapOf(TR to "🔌 Eklenti kurulu değil", EN to "🔌 No plugin installed"),
        "no_plugin_msg" to mapOf(
            TR to "StreamHub sadece bir oynatıcıdır — kaynak eklemek için eklenti kur. Eklenti, içinde plugin.json olan bir .zip dosyasıdır; derleme gerekmez, eklediğin an çalışır.",
            EN to "StreamHub is just a player — install a plugin to add sources. A plugin is a .zip file containing plugin.json; no compilation needed, it works as soon as you add it."),
        "add_plugin" to mapOf(TR to "＋ Eklenti Ekle (.zip)", EN to "＋ Add Plugin (.zip)"),
        "plugins_doc_github" to mapOf(TR to "📖 Eklenti Yapımı (GitHub)", EN to "📖 Plugin Guide (GitHub)"),
        "plugins_doc_guide" to mapOf(TR to "Eklenti Yapımı Rehberi (GitHub)", EN to "Plugin Guide (GitHub)"),
        "search_hint" to mapOf(TR to "Eklentide ara...", EN to "Search in plugin..."),
        "search_empty_hint" to mapOf(
            TR to "Yukarıya yaz ve ara. Arama, seçili eklentinin arama kurallarını kullanır.",
            EN to "Type above and search. Search uses the selected plugin's search rules."),
        "need_plugin_first" to mapOf(TR to "Önce bir eklenti kur", EN to "Install a plugin first"),
        "no_results" to mapOf(TR to "Sonuç bulunamadı: %s", EN to "No results: %s"),
        "favs_empty" to mapOf(
            TR to "⭐ Favori yok\n\nDetay ekranındaki yıldıza dokunarak dizi/video ekle.",
            EN to "⭐ No favorites\n\nTap the star on the detail screen to add a series/video."),
        "section_plugins" to mapOf(TR to "Eklentiler", EN to "Plugins"),
        "no_plugins_installed" to mapOf(
            TR to "Kurulu eklenti yok. \"Eklenti Ekle\" ile zip yükle.",
            EN to "No plugins installed. Upload a zip via \"Add Plugin\"."),
        "remove" to mapOf(TR to "Kaldır", EN to "Remove"),
        "plugin_removed" to mapOf(TR to "Eklenti kaldırıldı", EN to "Plugin removed"),
        "plugin_added" to mapOf(TR to "Eklenti eklendi: %s (%s kaynak yüklü)", EN to "Plugin added: %s (%s source(s) loaded)"),
        "plugin_added_with_errors" to mapOf(TR to "Eklenti yüklendi ama hata var: %s", EN to "Plugin loaded but with errors: %s"),
        "plugin_add_failed" to mapOf(TR to "Eklenti eklenemedi: %s", EN to "Could not add plugin: %s"),
        "section_data" to mapOf(TR to "Veriler", EN to "Data"),
        "clear_history" to mapOf(TR to "İzleme Geçmişini Temizle", EN to "Clear Watch History"),
        "history_cleared" to mapOf(TR to "Geçmiş temizlendi", EN to "History cleared"),
        "clear_favs" to mapOf(TR to "Favorileri Temizle", EN to "Clear Favorites"),
        "favs_cleared" to mapOf(TR to "Favoriler temizlendi", EN to "Favorites cleared"),
        "section_about" to mapOf(TR to "Hakkında", EN to "About"),
        "legal_notice" to mapOf(TR to "⚖ Yasal Uyarı", EN to "⚖ Legal Disclaimer"),
        "crash_row" to mapOf(TR to "🐞 Son Çökme Kaydı", EN to "🐞 Last Crash Log"),
        "version_footer" to mapOf(TR to "StreamHub v2.5 — Material You\n github.com/Eknux/streamhub", EN to "StreamHub v2.5 — Material You\n github.com/Eknux/streamhub"),
        "url_dialog_title" to mapOf(TR to "URL ile Oynat", EN to "Play from URL"),
        "url_hint" to mapOf(TR to "Video bağlantısı (m3u8 / mp4)", EN to "Video link (m3u8 / mp4)"),
        "invalid_url" to mapOf(TR to "Geçersiz bağlantı", EN to "Invalid link"),
        "local_video" to mapOf(TR to "Yerel video", EN to "Local video"),
        "local_file_source" to mapOf(TR to "Yerel dosya", EN to "Local file"),
        "browser_failed" to mapOf(TR to "Tarayıcı açılamadı", EN to "Could not open browser"),

        // ---------- yasal uyarı ----------
        "disclaimer_title" to mapOf(
            TR to "Yasal Uyarı — Devam Etmeden Önce Oku",
            EN to "Legal Disclaimer — Read Before Continuing"),
        "disclaimer_msg" to mapOf(
            TR to "StreamHub hiçbir video, film veya dizi dosyasını barındırmaz, depolamaz veya yüklemez. Uygulama yalnızca bir oynatıcı ve eklenti altyapısıdır; içeriği eklentiler üzerinden üçüncü taraf sitelerden alan kullanıcıdır ve tüm sorumluluk kullanıcıya aittir.\n\nUygulama kişisel ve eğitim amaçlı kullanım içindir. Ülkenizin telif yasalarına aykırı kullanımdan tüm sorumluluk kullanıcıya aittir. Hak sahibiyseniz kaldırma talebinizi içeriğin yayınlandığı kaynağa iletebilirsiniz.\n\nDevam ederek bu şartları kabul etmiş olursunuz.",
            EN to "StreamHub does not host, store or upload any video, movie or series file. The app is only a player and a plugin platform; the user loads content from third-party sites via plugins and bears all responsibility.\n\nThe app is intended for personal and educational use. Compliance with your country's copyright laws is entirely the user's responsibility. If you are a rights holder, direct removal requests to the source that publishes the content.\n\nBy continuing you accept these terms."),
        "disclaimer_accept" to mapOf(TR to "Kabul ediyorum", EN to "I accept"),
        "disclaimer_decline" to mapOf(TR to "Kabul etmiyorum, çık", EN to "I don't accept, exit"),

        // ---------- detay ----------
        "episodes" to mapOf(TR to "Bölümler", EN to "Episodes"),
        "episode_n" to mapOf(TR to "Bölüm %s", EN to "Episode %s"),
        "plugin_not_found" to mapOf(TR to "Eklenti bulunamadı — Ayarlar'dan eklenti yükle", EN to "Plugin not found — install one from Settings"),
        "load_failed" to mapOf(TR to "İçerik yüklenemedi", EN to "Could not load content"),
        "no_episodes" to mapOf(TR to "Bölüm bulunamadı — eklentinin episodeSelector kurallarını kontrol et", EN to "No episodes — check your plugin's episodeSelector rules"),
        "resolving" to mapOf(TR to "Kaynaklar çözülüyor...", EN to "Resolving sources..."),
        "no_video_source" to mapOf(TR to "Video kaynağı bulunamadı — eklentinin player kuralları eksik olabilir", EN to "No video source — your plugin's player rules may be missing"),
        "source_error" to mapOf(TR to "Kaynak hatası: %s", EN to "Source error: %s"),
        "fav_added" to mapOf(TR to "⭐ Favorilere eklendi", EN to "⭐ Added to favorites"),
        "fav_removed" to mapOf(TR to "Favorilerden çıkarıldı", EN to "Removed from favorites"),

        // ---------- webview ----------
        "cookies_save" to mapOf(TR to "✓ Çerezleri Kaydet ve Dön", EN to "✓ Save Cookies and Return"),
        "cookies_saved" to mapOf(TR to "Çerezler kaydedildi — geri dönüp tekrar dene", EN to "Cookies saved — go back and try again"),
        "cookies_failed" to mapOf(TR to "Çerez okunamadı; sayfa tamamen açıldıktan sonra tekrar bas", EN to "Could not read cookies; wait until the page fully loads and tap again"),
        "webview_hint" to mapOf(TR to "Koruma ekranı görürsen 5–10 sn bekle; sayfa açılınca üstteki düğmeye bas.", EN to "If you see a protection screen, wait 5–10 s; tap the button above when the page opens."),

        // ---------- oynatıcı ----------
        "no_source_toast" to mapOf(TR to "Oynanacak kaynak yok", EN to "No source to play"),
        "direct_source" to mapOf(TR to "Doğrudan", EN to "Direct"),
        "pick_source" to mapOf(TR to "Kaynak seç", EN to "Choose source"),
        "player_settings_title" to mapOf(TR to "⚙ Video Ayarları", EN to "⚙ Video Settings"),
        "fullscreen_on" to mapOf(TR to "⇔ Tam ekrandan çık", EN to "⇔ Exit fullscreen"),
        "fullscreen_off" to mapOf(TR to "⛶ Tam ekran", EN to "⛶ Full screen"),
        "pip_btn" to mapOf(TR to "PiP", EN to "PiP"),
        "sources_btn" to mapOf(TR to "⟳ Kaynaklar", EN to "⟳ Sources"),
        "landscape" to mapOf(TR to "Yatay", EN to "Landscape"),
        "portrait" to mapOf(TR to "Dikey", EN to "Portrait"),
        "auto_orient" to mapOf(TR to "Oto", EN to "Auto"),
        "subtitles_btn" to mapOf(TR to "CC Altyazı", EN to "CC Subtitles"),
        "audio_lang_btn" to mapOf(TR to "🔊 Ses Dili", EN to "🔊 Audio Language"),
        "pip_need_8" to mapOf(TR to "PiP için Android 8.0+ gerekli", EN to "PiP requires Android 8.0+"),
        "pip_failed" to mapOf(TR to "PiP başlatılamadı: %s", EN to "Could not start PiP: %s"),
        "play_error" to mapOf(TR to "Oynatma hatası: %s", EN to "Playback error: %s"),
        "err_429" to mapOf(TR to "HTTP 429 — kaynak sunucusu hız limiti", EN to "HTTP 429 — source server rate limit"),
        "err_403" to mapOf(TR to "HTTP 403 — sunucu isteği reddetti", EN to "HTTP 403 — server rejected the request"),
        "err_404" to mapOf(TR to "HTTP 404 — dosya bulunamadı", EN to "HTTP 404 — file not found"),
        "err_http" to mapOf(TR to "HTTP %s", EN to "HTTP %s"),
        "try_other_source" to mapOf(TR to "Başka kaynak dene (⟳).", EN to "Try another source (⟳)."),
        "rate_limit_wait" to mapOf(
            TR to "Kaynak sunucusu hız limiti (HTTP 429)\n%s sn sonra otomatik tekrar (deneme %s/3)…",
            EN to "Source server rate limit (HTTP 429)\nAuto-retry in %s s (attempt %s/3)…"),
        "net_error_wait" to mapOf(
            TR to "Geçici ağ hatası (HTTP %s)\n%s sn sonra otomatik tekrar (deneme %s/3)…",
            EN to "Temporary network error (HTTP %s)\nAuto-retry in %s s (attempt %s/3)…"),
        "pick_other_source" to mapOf(TR to "⟳ Başka kaynak seç", EN to "⟳ Choose another source"),

        // ---------- altyazı ----------
        "sub_dialog_title" to mapOf(TR to "Altyazı Ayarları", EN to "Subtitle Settings"),
        "sub_size" to mapOf(TR to "Boyut", EN to "Size"),
        "sub_color" to mapOf(TR to "Renk", EN to "Color"),
        "sub_pos" to mapOf(TR to "Konum", EN to "Position"),
        "sub_delay" to mapOf(TR to "Gecikme", EN to "Delay"),
        "sub_font" to mapOf(TR to "Font", EN to "Font"),
        "sub_size_s" to mapOf(TR to "Küçük", EN to "Small"),
        "sub_size_m" to mapOf(TR to "Orta", EN to "Medium"),
        "sub_size_l" to mapOf(TR to "Büyük", EN to "Large"),
        "sub_size_xl" to mapOf(TR to "Çok Büyük", EN to "X-Large"),
        "sub_pos_bottom" to mapOf(TR to "Alt", EN to "Bottom"),
        "sub_pos_middle" to mapOf(TR to "Orta", EN to "Middle"),
        "sub_pos_high" to mapOf(TR to "Yüksek", EN to "High"),
        "sub_font_default" to mapOf(TR to "Varsayılan", EN to "Default"),
        "sub_font_condensed" to mapOf(TR to "Dar", EN to "Condensed"),
        "sub_font_serif" to mapOf(TR to "Serif", EN to "Serif"),
        "sub_font_mono" to mapOf(TR to "Monospace", EN to "Monospace"),
        "sub_external" to mapOf(TR to "📁 Harici altyazı ekle (.srt/.vtt)", EN to "📁 Add external subtitle (.srt/.vtt)"),
        "sub_ext_active" to mapOf(TR to "Harici altyazı aktif: %s", EN to "External subtitle active: %s"),
        "sub_ext_parse_failed" to mapOf(TR to "Altyazı dosyası okunamadı (.srt/.vtt bekleniyor)", EN to "Could not parse the subtitle file (.srt/.vtt expected)"),
        "sub_embedded_tracks" to mapOf(TR to "Gömülü altyazı parçaları", EN to "Embedded subtitle tracks"),
        "sub_off" to mapOf(TR to "Kapalı", EN to "Off"),
        "sub_track_auto" to mapOf(TR to "Otomatik", EN to "Auto"),
        "sub_delay_note" to mapOf(
            TR to "Gecikme: harici altyazıda tam çalışır; gömülü altyazıda 'sonra' yönü uygulanır.",
            EN to "Delay: exact for external subtitles; embedded tracks only apply the 'later' direction."),
        "sec_later" to mapOf(TR to "%s sn sonra", EN to "%s s later"),
        "sec_earlier" to mapOf(TR to "%s sn önce", EN to "%s s earlier"),
        "sec_off" to mapOf(TR to "0 (kapalı)", EN to "0 (off)"),
        "audio_auto" to mapOf(TR to "Otomatik", EN to "Auto"),
        "audio_single" to mapOf(TR to "Tek ses kanalı — dil seçimi yok", EN to "Single audio track — no language selection"),
        "audio_dialog_title" to mapOf(TR to "Ses dili seç", EN to "Choose audio language"),
        "track_unknown" to mapOf(TR to "Parça %s", EN to "Track %s"),

        // ---------- v2.6: bolumler / hiz / otomatik-ileri / pip kontrolleri ----------
        "episodes_btn" to mapOf(TR to "📺 Bölümler", EN to "📺 Episodes"),
        "season_title" to mapOf(TR to "Sezon seç", EN to "Choose season"),
        "episode_title" to mapOf(TR to "Bölüm seç", EN to "Choose episode"),
        "season_n" to mapOf(TR to "%s. Sezon", EN to "Season %s"),
        "season_all" to mapOf(TR to "Tüm sezonlar", EN to "All seasons"),
        "speed_title" to mapOf(TR to "Oynatma hızı", EN to "Playback speed"),
        "next_episode_toast" to mapOf(TR to "Sonraki bölüm: %s", EN to "Next episode: %s"),
        "no_next_episode" to mapOf(TR to "Sonraki bölüm yok", EN to "No next episode"),
        "no_prev_episode" to mapOf(TR to "Önceki bölüm yok", EN to "No previous episode"),
        "loading_episode" to mapOf(TR to "Bölüm yükleniyor...", EN to "Loading episode..."),
        "pip_play" to mapOf(TR to "Oynat", EN to "Play"),
        "pip_pause" to mapOf(TR to "Duraklat", EN to "Pause"),
        "pip_rewind" to mapOf(TR to "10 sn geri", EN to "Rewind 10 s"),
        "pip_forward" to mapOf(TR to "10 sn ileri", EN to "Forward 10 s"),
        "pip_prev_ep" to mapOf(TR to "Önceki bölüm", EN to "Previous episode"),
        "pip_next_ep" to mapOf(TR to "Sonraki bölüm", EN to "Next episode"),
        "version_footer" to mapOf(TR to "StreamHub v2.6 — Material You\n github.com/Eknux/streamhub", EN to "StreamHub v2.6 — Material You\n github.com/Eknux/streamhub"),
    )
}
