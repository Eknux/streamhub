package com.streamhub.app.ui

import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Rational
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.text.CueGroup
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.streamhub.app.core.I18n
import com.streamhub.app.core.Store
import com.streamhub.app.core.VideoSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlayerActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var overlay: LinearLayout
    private lateinit var frame: FrameLayout
    private lateinit var subView: TextView
    private var lastSourceIndex = 0
    private var resumed = false
    private var autoRetries = 0
    private lateinit var http: com.streamhub.app.net.Http

    // Oynatici ekstra kontrolleri: yon kilidi, tam ekran, PiP
    private var orientationMode = 0 // 0=oto (sensor), 1=dikey, 2=yatay
    private var fullscreen = false
    private var inPip = false
    private var showingControls = true

    /** 429/5xx icin uzayan sessiz yeniden deneme pencereleri (Wikimedia Retry-After 600 sn verir). */
    private val autoRetryDelaysMs = longArrayOf(10_000L, 60_000L, 300_000L)
    private var startMs = 0L
    private var epUrl = ""
    private var seriesUrl: String? = null
    private var titleText = ""
    private var poster: String? = null

    // ---------- altyazi motoru ----------
    /** Tek altyazi satiri: harici SRT/VTT dosyasindan cozulur. */
    private data class SubCue(val startMs: Long, val endMs: Long, val text: String)

    private val subHandler = Handler(Looper.getMainLooper())
    private var externalSubs: List<SubCue>? = null
    private var externalSubName = ""
    /** Gomulu (player'dan gelen) altyazilarin tamponu: (mediaTimeUs, metin). */
    private val embeddedBuffer = ArrayDeque<Pair<Long, String>>()
    private var subSizeIdx = 1
    private var subColorIdx = 0
    private var subPosIdx = 0
    private var subDelayIdx = 4
    private var subFontIdx = 0
    private var subtitlePicker: ActivityResultLauncher<Array<String>>? = null

    private val subDelaysSec = intArrayOf(-10, -5, -2, -1, 0, 1, 2, 5, 10)
    private val subSizesSp = floatArrayOf(14f, 18f, 22f, 28f)
    private val subColors = intArrayOf(
        0xFFFFFFFF.toInt(), 0xFFFFD54F.toInt(), 0xFF4DD0E1.toInt(),
        0xFF81C784.toInt(), 0xFFF48FB1.toInt(), 0xFF64B5F6.toInt())
    /** Alt kenardan bosluk orani (kucuk=altyazi asagida). */
    private val subPosFractions = floatArrayOf(0.07f, 0.24f, 0.42f)
    private val subFonts by lazy {
        arrayOf(Typeface.DEFAULT, Typeface.create("sans-serif-condensed", Typeface.NORMAL),
            Typeface.SERIF, Typeface.MONOSPACE)
    }
    private val subDelayLabels by lazy {
        arrayOf(
            I18n.f("sec_earlier", "10"), I18n.f("sec_earlier", "5"), I18n.f("sec_earlier", "2"), I18n.f("sec_earlier", "1"),
            I18n.s("sec_off"),
            I18n.f("sec_later", "1"), I18n.f("sec_later", "2"), I18n.f("sec_later", "5"), I18n.f("sec_later", "10"))
    }

    /** Altyazinin guncellenmesini saglayan tik (150 ms yeterli hassasiyet). */
    private val subTick = object : Runnable {
        override fun run() {
            renderSubtitle()
            subHandler.postDelayed(this, 150)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        http = com.streamhub.app.net.Http(this)

        titleText = intent.getStringExtra("title") ?: "StreamHub"
        startMs = intent.getLongExtra("startMs", 0L)
        seriesUrl = intent.getStringExtra("seriesUrl")
        epUrl = intent.getStringExtra("episodeUrl") ?: intent.getStringExtra("uri").orEmpty()
        poster = intent.getStringExtra("poster")
        loadSubPrefs()

        frame = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }

        playerView = PlayerView(this).apply {
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            useController = true
        }
        // media3'un kendi SubtitleView'i kapali: altyaziyi kendi motorumuz duz yazi olarak cizer
        // (arka plan kutusu olmayan; boyut/renk/font/konum/gecikme ayarlanabilir)
        playerView.subtitleView?.visibility = View.GONE
        frame.addView(playerView, FrameLayout.LayoutParams(-1, -1))

        subView = TextView(this).apply {
            setTextColor(subColors[subColorIdx])
            textSize = subSizesSp[subSizeIdx]
            setShadowLayer(6f, 0f, 2f, Color.BLACK)
            gravity = Gravity.CENTER
            visibility = View.GONE
        }
        frame.addView(subView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL))

        overlay = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        addControls()
        // sag alttaki video ayarlari paneli; videoya dokununca (kontroller gorunur olunca) acilir
        frame.addView(overlay, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.END).apply {
            setMargins(0, 0, Ui.dp(this@PlayerActivity, 10), Ui.dp(this@PlayerActivity, 18))
        })

        playerView.setControllerVisibilityListener { vis ->
            if (inPip) return@setControllerVisibilityListener
            overlay.visibility = if (vis == View.VISIBLE) View.VISIBLE else View.GONE
        }

        setContentView(frame)
        applySubPosition()
        subHandler.post(subTick)

        subtitlePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { loadExternalSubtitle(it) }
        }

        if (StreamSession.sources.isEmpty() && epUrl.isNotBlank()) {
            StreamSession.sources = listOf(
                VideoSource(name = I18n.s("direct_source"), url = epUrl, isM3u8 = epUrl.contains(".m3u8"))
            )
        }
        if (StreamSession.sources.isEmpty()) {
            Ui.toast(this, I18n.s("no_source_toast"))
            finish()
            return
        }
        if (StreamSession.sources.size > 1) pickSource() else play(0)
    }

    // ==================== SAG ALT VIDEO AYARLARI PANELI ====================

    /** Sag alttaki ayarlar paneli: tam ekran + PiP + yon kilidi + kaynak + altyazi + ses dili.
     *  Metin butonlari kullanilir (XML drawable yok — MIUI Resources$NotFoundException'tan kacinma). */
    private fun addControls() {
        showingControls = true
        overlayAsPanel()
        overlay.removeAllViews()
        overlay.background = Ui.roundedBg(this, 0xE6141926.toInt(), 14)
        overlay.setPadding(Ui.dp(this, 10), Ui.dp(this, 8), Ui.dp(this, 10), Ui.dp(this, 10))
        overlay.gravity = Gravity.END

        overlay.addView(TextView(this).apply {
            text = I18n.s("player_settings_title")
            setTextColor(Ui.ACCENT)
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 0, 0, Ui.dp(this@PlayerActivity, 4))
        })

        val row1 = row()
        row1.addView(controlButton(
            if (fullscreen) I18n.s("fullscreen_on") else I18n.s("fullscreen_off"), fullscreen) { toggleFullscreen() })
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            row1.addView(controlButton(I18n.s("pip_btn"), false) { enterPip() })
        }
        row1.addView(controlButton(I18n.s("sources_btn"), false) { pickSource() })
        overlay.addView(row1)

        val row2 = row()
        row2.addView(controlButton(I18n.s("landscape"), orientationMode == 2) { applyOrientation(2) })
        row2.addView(controlButton(I18n.s("portrait"), orientationMode == 1) { applyOrientation(1) })
        row2.addView(controlButton(I18n.s("auto_orient"), orientationMode == 0) { applyOrientation(0) })
        overlay.addView(row2)

        val row3 = row()
        row3.addView(controlButton(I18n.s("subtitles_btn"), false) { showSubtitleSettings() })
        if (hasMultiAudio()) {
            row3.addView(controlButton(I18n.s("audio_lang_btn"), false) { pickAudioLanguage() })
        }
        overlay.addView(row3)
    }

    private fun row() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

    /** Panel modu: sag altta kucuk, koyu yuvarlak kutu. */
    private fun overlayAsPanel() {
        overlay.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.END).apply {
            setMargins(0, 0, Ui.dp(this@PlayerActivity, 10), Ui.dp(this@PlayerActivity, 18))
        }
    }

    /** Hata/bekleme modu: tam ekrani kaplayan yari saydam, ortalanmis kutu. */
    private fun overlayAsCentered() {
        overlay.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        overlay.setBackgroundColor(0x99000000.toInt())
    }

    private fun controlButton(label: String, active: Boolean, onClick: () -> Unit) = TextView(this).apply {
        text = label
        setTextColor(if (active) Ui.ACCENT else Ui.TEXT_MAIN)
        textSize = 14f
        setPadding(Ui.dp(this@PlayerActivity, 10), Ui.dp(this@PlayerActivity, 6), Ui.dp(this@PlayerActivity, 10), Ui.dp(this@PlayerActivity, 6))
        setOnClickListener { onClick() }
    }

    private fun toggleFullscreen() = setFullscreen(!fullscreen)

    /** Tam ekran: sistem cubuklarini gizler (immersive), yatayda otomatik acilir. */
    private fun setFullscreen(on: Boolean) {
        fullscreen = on
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        if (on) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
        if (showingControls) addControls()
    }

    /** 0=oto (sensor), 1=dikey, 2=yatay (Cs tarzi kilit duzenine benzer). */
    private fun applyOrientation(mode: Int) {
        orientationMode = mode
        requestedOrientation = when (mode) {
            1 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            2 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else -> ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        }
        if (showingControls) addControls()
    }

    /** Resim icinde resim: video oranini koruyarak ana ekrana donup kucuk pencerede oynatmayi surdurur. */
    private fun enterPip() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            Ui.toast(this, I18n.s("pip_need_8"))
            return
        }
        val p = player ?: return
        try {
            enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(pipRatio(p)).build())
        } catch (e: Exception) {
            Ui.toast(this, I18n.f("pip_failed", e.message ?: ""))
        }
    }

    /** Video gercek boyutundan PiP orani hesaplar; Android'in [0.418, 2.39] sinirina kirpar. */
    private fun pipRatio(p: ExoPlayer): Rational {
        val v = p.videoSize
        var r = if (v.width > 0 && v.height > 0) Rational(v.width, v.height) else Rational(16, 9)
        val maxR = Rational(239, 100)
        val minR = Rational(100, 239)
        if (r > maxR) r = maxR
        if (r < minR) r = minR
        return r
    }

    private fun hasMultiAudio(): Boolean {
        val p = player ?: return false
        return runCatching {
            p.currentTracks.groups.count { g ->
                g.type == C.TRACK_TYPE_AUDIO && g.length > 0
            } > 1
        }.getOrDefault(false)
    }

    // ==================== SES DILI SECIMI ====================

    private fun pickAudioLanguage() {
        val p = player ?: return
        data class Opt(val label: String, val lang: String?)
        val opts = mutableListOf(Opt(I18n.s("audio_auto"), null))
        val seen = mutableSetOf<String>()
        runCatching {
            p.currentTracks.groups.forEach { g ->
                if (g.type != C.TRACK_TYPE_AUDIO) return@forEach
                for (i in 0 until g.length) {
                    val f = g.getTrackFormat(i)
                    val key = f.language ?: f.label ?: "track$i"
                    if (seen.add(key)) {
                        val name = f.label ?: f.language ?: I18n.f("track_unknown", "${seen.size}")
                        opts.add(Opt(if (g.isTrackSelected(i)) "● $name" else name, f.language))
                    }
                }
            }
        }
        if (opts.size < 2) {
            Ui.toast(this, I18n.s("audio_single"))
            return
        }
        AlertDialog.Builder(this)
            .setTitle(I18n.s("audio_dialog_title"))
            .setItems(opts.map { it.label }.toTypedArray()) { _, which ->
                val chosen = opts[which].lang ?: ""
                p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
                    .setPreferredAudioLanguage(chosen)
                    .build()
                addControls()
            }
            .setNegativeButton(I18n.s("cancel"), null)
            .show()
    }

    // ==================== ALTYAZI MOTORU ====================

    private fun loadSubPrefs() {
        val prefs = getSharedPreferences("streamhub_prefs", MODE_PRIVATE)
        subSizeIdx = prefs.getInt("sub_size", 1)
        subColorIdx = prefs.getInt("sub_color", 0)
        subPosIdx = prefs.getInt("sub_pos", 0)
        subDelayIdx = prefs.getInt("sub_delay", 4)
        subFontIdx = prefs.getInt("sub_font", 0)
    }

    private fun saveSubPrefs() {
        getSharedPreferences("streamhub_prefs", MODE_PRIVATE).edit()
            .putInt("sub_size", subSizeIdx)
            .putInt("sub_color", subColorIdx)
            .putInt("sub_pos", subPosIdx)
            .putInt("sub_delay", subDelayIdx)
            .putInt("sub_font", subFontIdx)
            .apply()
    }

    private fun currentDelayMs(): Long = subDelaysSec[subDelayIdx].toLong() * 1000L

    private fun applySubtitleStyle() {
        subView.textSize = subSizesSp[subSizeIdx]
        subView.setTextColor(subColors[subColorIdx])
        subView.typeface = subFonts[subFontIdx]
        applySubPosition()
    }

    private fun applySubPosition() {
        frame.post {
            if (frame.height > 0) {
                (subView.layoutParams as? FrameLayout.LayoutParams)?.let { lp ->
                    lp.bottomMargin = (frame.height * subPosFractions[subPosIdx]).toInt()
                    subView.layoutParams = lp
                }
            }
        }
    }

    /** Gomulu altyaziyi tamponla: CueGroup media zaman damgasiyla saklanir. */
    private fun bufferEmbedded(cueGroup: CueGroup) {
        val text = cueGroup.cues.mapNotNull { it.text?.toString()?.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
        if (text.isBlank()) return
        embeddedBuffer.addLast(cueGroup.presentationTimeUs to text)
        while (embeddedBuffer.size > 40) embeddedBuffer.removeFirst()
    }

    /** Tik icinde cagrilir: gecikmeye gore gosterilecek altyaziyi hesaplar ve cizer.
     *  Gecikme: +X sn = altyazi X sn sonra gosterilir; -X sn = X sn once.
     *  Harici altyazida iki yon de tam; gomulu altyazida gelecekteki metin
     *  onceden bilinemedigi icin "once" yonu aninda-gosterim olarak uygulanir. */
    private fun renderSubtitle() {
        val p = player
        if (p == null || inPip) return
        val delay = currentDelayMs()
        val text: String? = if (externalSubs != null) {
            val pos = p.currentPosition - delay
            externalSubs!!.firstOrNull { it.startMs <= pos && pos < it.endMs }?.text
        } else {
            if (embeddedBuffer.isEmpty()) null else {
                val targetUs = (p.currentPosition - delay) * 1000L
                var found: String? = null
                for (e in embeddedBuffer) {
                    if (e.first <= targetUs) found = e.second else break
                }
                // cok geride kalan kayitlari temizle (2 dakikadan eski)
                val floorUs = (p.currentPosition - 120_000L) * 1000L
                while (embeddedBuffer.isNotEmpty() && embeddedBuffer.first().first < floorUs) {
                    embeddedBuffer.removeFirst()
                }
                found
            }
        }
        if (text.isNullOrBlank()) {
            if (subView.visibility != View.GONE) subView.visibility = View.GONE
        } else {
            subView.text = text
            if (subView.visibility != View.VISIBLE) subView.visibility = View.VISIBLE
        }
    }

    /** Harici .srt/.vtt dosyasini cozer ve aktif altyazi yapar (gomulu altyazi kapanir). */
    private fun loadExternalSubtitle(uri: Uri) {
        lifecycleScope.launch {
            val parsed = withContext(Dispatchers.IO) {
                runCatching { parseSubtitleFile(uri) }.getOrDefault(emptyList())
            }
            if (parsed.isEmpty()) {
                Ui.toast(this@PlayerActivity, I18n.s("sub_ext_parse_failed"))
                return@launch
            }
            externalSubs = parsed
            externalSubName = uri.lastPathSegment?.substringAfterLast('/') ?: "subtitle"
            // gomulu altyaziyi kapat: cift gosterimi engeller
            player?.let { p ->
                p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    .build()
            }
            embeddedBuffer.clear()
            Ui.toast(this@PlayerActivity, I18n.f("sub_ext_active", externalSubName))
            if (showingControls) addControls()
        }
    }

    private fun parseSubtitleFile(uri: Uri): List<SubCue> {
        val raw = contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: return emptyList()
        val cues = mutableListOf<SubCue>()
        val fullTiming = Regex(
            "(\\d{1,2}):(\\d{2}):(\\d{2})[,.](\\d{1,3})\\s*-->\\s*(\\d{1,2}):(\\d{2}):(\\d{2})[,.](\\d{1,3})")
        val shortTiming = Regex(
            "(\\d{1,2}):(\\d{2})[,.](\\d{1,3})\\s*-->\\s*(\\d{1,2}):(\\d{2})[,.](\\d{1,3})")
        val tagRegex = Regex("<[^>]*>")

        fun msOf(h: String?, mm: String, ss: String, ms: String): Long {
            val millis = ms.padEnd(3, '0').take(3).toLong()
            return ((h?.toLongOrNull() ?: 0L) * 3600000L) + (mm.toLong() * 60000L) + (ss.toLong() * 1000L) + millis
        }

        for (block in raw.split(Regex("\r?\n[ \t]*\r?\n"))) {
            val lines = block.lines().map { it.trimEnd('\r') }.filter { it.isNotBlank() }
            val ti = lines.indexOfFirst { it.contains("-->") }
            if (ti < 0) continue
            val line = lines[ti]
            val mFull = fullTiming.find(line)
            val mShort = shortTiming.find(line)
            if (mFull != null) {
                val g = mFull.groupValues
                val start = msOf(g[1], g[2], g[3], g[4])
                val end = msOf(g[5], g[6], g[7], g[8])
                val text = lines.drop(ti + 1).joinToString("\n").replace(tagRegex, "").trim()
                if (text.isNotEmpty() && end > start) cues.add(SubCue(start, end, text))
            } else if (mShort != null) {
                val g = mShort.groupValues
                val start = msOf(null, g[1], g[2], g[3])
                val end = msOf(null, g[4], g[5], g[6])
                val text = lines.drop(ti + 1).joinToString("\n").replace(tagRegex, "").trim()
                if (text.isNotEmpty() && end > start) cues.add(SubCue(start, end, text))
            }
        }
        return cues.sortedBy { it.startMs }
    }

    // ==================== ALTYAZI AYARLARI DIYALOGU ====================

    private fun showSubtitleSettings() {
        val dialog = AlertDialog.Builder(this)
            .setTitle(I18n.s("sub_dialog_title"))
            .setPositiveButton(I18n.s("ok"), null)
            .create()
        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(this@PlayerActivity, 16), Ui.dp(this@PlayerActivity, 10),
                Ui.dp(this@PlayerActivity, 16), Ui.dp(this@PlayerActivity, 6))
        }
        scroll.addView(root)

        fun refresh() { fillSubtitleSettings(root); applySubtitleStyle(); saveSubPrefs() }
        fillSubtitleSettings(root) { refresh() }
        dialog.setView(scroll)
        dialog.show()
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    /** Ayar diyalogunun icerigini doldurur; her secimde basa cagrilir (butunler guncel kalsin). */
    private fun fillSubtitleSettings(root: LinearLayout, onChange: () -> Unit = {}) {
        root.removeAllViews()
        val ctx = this

        fun dimLabel(text: String) = TextView(ctx).apply {
            this.text = text
            setTextColor(Ui.TEXT_DIM)
            textSize = 12f
            setPadding(0, Ui.dp(ctx, 10), 0, Ui.dp(ctx, 2))
        }

        fun chipRow(labels: List<String>, selected: Int, colors: List<Int>? = null, onSelect: (Int) -> Unit) {
            val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
            labels.forEachIndexed { i, label ->
                row.addView(TextView(ctx).apply {
                    textSize = 13f
                    if (colors != null) {
                        // renk secicisi: metin yerine renkli daire
                        text = "⬤"
                        setTextColor(colors[i])
                    } else {
                        text = label
                        setTextColor(if (i == selected) Ui.ACCENT else Ui.TEXT_MAIN)
                    }
                    background = if (i == selected) Ui.roundedBg(ctx, 0x338b9dff.toInt(), 10)
                        else Ui.roundedBg(ctx, 0x22ffffff.toInt(), 10)
                    setPadding(Ui.dp(ctx, 10), Ui.dp(ctx, 5), Ui.dp(ctx, 10), Ui.dp(ctx, 5))
                    setOnClickListener { onSelect(i); onChange() }
                })
                (row.getChildAt(row.childCount - 1).layoutParams as? LinearLayout.LayoutParams)?.setMargins(0, 0, Ui.dp(ctx, 6), 0)
            }
            root.addView(row, LinearLayout.LayoutParams(-2, -2))
        }

        // boyut
        root.addView(dimLabel(I18n.s("sub_size")))
        chipRow(listOf(I18n.s("sub_size_s"), I18n.s("sub_size_m"), I18n.s("sub_size_l"), I18n.s("sub_size_xl")), subSizeIdx) {
            subSizeIdx = it
        }

        // renk
        root.addView(dimLabel(I18n.s("sub_color")))
        chipRow(List(subColors.size) { "" }, subColorIdx, subColors) { subColorIdx = it }

        // konum
        root.addView(dimLabel(I18n.s("sub_pos")))
        chipRow(listOf(I18n.s("sub_pos_bottom"), I18n.s("sub_pos_middle"), I18n.s("sub_pos_high")), subPosIdx) {
            subPosIdx = it
        }

        // gecikme
        root.addView(dimLabel(I18n.s("sub_delay")))
        chipRow(subDelayLabels.toList(), subDelayIdx) { subDelayIdx = it }

        // font
        root.addView(dimLabel(I18n.s("sub_font")))
        chipRow(listOf(I18n.s("sub_font_default"), I18n.s("sub_font_condensed"), I18n.s("sub_font_serif"), I18n.s("sub_font_mono")), subFontIdx) {
            subFontIdx = it
        }

        root.addView(dimLabel(I18n.s("sub_delay_note")))

        // harici altyazi ekleme
        root.addView(TextView(ctx).apply {
            text = if (externalSubs != null) I18n.f("sub_ext_active", externalSubName)
                   else I18n.s("sub_external")
            setTextColor(Ui.ACCENT_2)
            textSize = 13f
            setPadding(0, Ui.dp(ctx, 10), 0, Ui.dp(ctx, 4))
            setOnClickListener { subtitlePicker?.launch(arrayOf("*/*")) }
        })

        // gomulu altyazi parcasi secimi (varsa)
        val p = player
        val textTracks = mutableListOf<Pair<String, String?>>() // (etiket, dil)
        var anySelected = false
        var textDisabled = false
        runCatching {
            if (p != null) {
                textDisabled = p.trackSelectionParameters.isTrackTypeDisabled(C.TRACK_TYPE_TEXT)
                p.currentTracks.groups.forEach { g ->
                    if (g.type != C.TRACK_TYPE_TEXT) return@forEach
                    for (i in 0 until g.length) {
                        val f = g.getTrackFormat(i)
                        val name = f.label ?: f.language ?: I18n.f("track_unknown", "${textTracks.size + 1}")
                        textTracks.add(name to f.language)
                        if (g.isTrackSelected(i)) anySelected = true
                    }
                }
            }
        }
        if (textTracks.isNotEmpty()) {
            root.addView(dimLabel(I18n.s("sub_embedded_tracks")))
            val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
            row.addView(TextView(ctx).apply {
                text = I18n.s("sub_off")
                textSize = 13f
                setTextColor(if (textDisabled) Ui.ACCENT else Ui.TEXT_MAIN)
                background = if (textDisabled) Ui.roundedBg(ctx, 0x338b9dff.toInt(), 10) else Ui.roundedBg(ctx, 0x22ffffff.toInt(), 10)
                setPadding(Ui.dp(ctx, 10), Ui.dp(ctx, 5), Ui.dp(ctx, 10), Ui.dp(ctx, 5))
                setOnClickListener {
                    p?.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
                    onChange()
                }
            })
            (row.getChildAt(0).layoutParams as? LinearLayout.LayoutParams)?.setMargins(0, 0, Ui.dp(ctx, 6), 0)
            textTracks.forEachIndexed { i, (name, lang) ->
                val active = !textDisabled && anySelected && lang != null
                row.addView(TextView(ctx).apply {
                    text = if (active) "● $name" else name
                    textSize = 13f
                    setTextColor(if (active) Ui.ACCENT else Ui.TEXT_MAIN)
                    background = if (active) Ui.roundedBg(ctx, 0x338b9dff.toInt(), 10) else Ui.roundedBg(ctx, 0x22ffffff.toInt(), 10)
                    setPadding(Ui.dp(ctx, 10), Ui.dp(ctx, 5), Ui.dp(ctx, 10), Ui.dp(ctx, 5))
                    setOnClickListener {
                        p?.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                            .setPreferredTextLanguage(lang ?: "")
                            .build()
                        onChange()
                    }
                })
                (row.getChildAt(row.childCount - 1).layoutParams as? LinearLayout.LayoutParams)?.setMargins(0, 0, Ui.dp(ctx, 6), 0)
            }
            root.addView(row, LinearLayout.LayoutParams(-2, -2))
        }
    }

    // ==================== KAYNAK SECIMI VE OYNATMA ====================

    private fun pickSource() {
        val sources = StreamSession.sources
        if (sources.isEmpty()) return
        if (sources.size == 1) { play(0); return }
        AlertDialog.Builder(this)
            .setTitle(I18n.s("pick_source"))
            .setItems(sources.map { it.name }.toTypedArray()) { _, which -> play(which) }
            .setNegativeButton(I18n.s("cancel")) { d, _ ->
                if (player == null) { d.dismiss(); play(lastSourceIndex) } else d.dismiss()
            }
            .show()
    }

    private fun play(index: Int) {
        val source = StreamSession.sources.getOrNull(index) ?: return
        lastSourceIndex = index
        autoRetries = 0
        resetOverlay()

        val headers = buildMap {
            source.referer?.let { put("Referer", it) }
            for ((k, v) in source.headers) put(k, v)
            if (!containsKey("User-Agent")) put("User-Agent", com.streamhub.app.net.Http.UA)
        }
        // Cs mimarisi: oyatci dahil tum trafik tek OkHttp yigitindan gecer
        // (ayni DoH + cerez havuzu + UA; site istekleriyle ayni kimlik)
        val dataSourceFactory = DefaultDataSource.Factory(
            this,
            OkHttpDataSource.Factory(http.client)
                .setUserAgent(headers["User-Agent"] ?: com.streamhub.app.net.Http.UA)
                .setDefaultRequestProperties(headers)
        )

        if (player == null) {
            // Mali/Adreno GPU'larinda codec uyumsuzlugunda alternatif decoder'a gecer
            val renderersFactory = androidx.media3.exoplayer.DefaultRenderersFactory(this)
                .setEnableDecoderFallback(true)
            player = ExoPlayer.Builder(this, renderersFactory)
                .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
                .build().also { p ->
                    playerView.player = p
                    p.addListener(object : Player.Listener {
                        override fun onCues(cueGroup: CueGroup) {
                            if (externalSubs == null) bufferEmbedded(cueGroup)
                        }
                        override fun onPlaybackStateChanged(state: Int) {
                            if (state == Player.STATE_READY && !resumed && startMs > 0) {
                                p.seekTo(startMs)
                                resumed = true
                            }
                        }
                        override fun onPlayerError(error: PlaybackException) {
                            val code = httpStatusCode(error)
                            // 429 (hiz limiti) ve 5xx gecicidir: uzayan pencerelerle sessiz deneme
                            if (code == 429 || code in 500..599) {
                                if (autoRetries < autoRetryDelaysMs.size) {
                                    val attempt = autoRetries + 1
                                    val delay = autoRetryDelaysMs[autoRetries]
                                    autoRetries = attempt
                                    showRetryStatus(code, attempt, delay)
                                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                        player?.prepare()
                                        player?.playWhenReady = true
                                    }, delay)
                                    return
                                }
                                // denemeler bitti: alternatif kaynak varsa ona otomatik gec (Cs tarzi)
                                if (StreamSession.sources.size > 1) {
                                    play((lastSourceIndex + 1) % StreamSession.sources.size)
                                    return
                                }
                            }
                            val msg = when (code) {
                                null -> I18n.f("play_error", error.errorCodeName)
                                429 -> I18n.f("play_error", I18n.s("err_429"))
                                403 -> I18n.f("play_error", I18n.s("err_403"))
                                404 -> I18n.f("play_error", I18n.s("err_404"))
                                else -> I18n.f("play_error", I18n.f("err_http", "$code"))
                            }
                            showErrorOverlay("$msg ${I18n.s("try_other_source")}")
                        }
                    })
                }
        }

        val subtitleConfigs = if (externalSubs != null) emptyList() else source.subtitles.map { sub ->
            MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(sub.url))
                .setMimeType(subMime(sub.url))
                .setLabel(sub.label)
                .setLanguage("tr")
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build()
        }

        val mediaItem = MediaItem.Builder()
            .setUri(android.net.Uri.parse(source.url))
            .setMimeType(if (source.isM3u8) MimeTypes.APPLICATION_M3U8 else null)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(titleText).build())
            .setSubtitleConfigurations(subtitleConfigs)
            .build()

        player?.apply {
            setMediaItem(mediaItem)
            prepare()
            playWhenReady = true
        }
    }

    private fun subMime(url: String): String = when {
        url.endsWith(".srt", true) -> MimeTypes.APPLICATION_SUBRIP
        else -> MimeTypes.TEXT_VTT
    }

    /** HTTP durum kodunu cikarir (yuvaya gore 1-2 seviye derin olabilir). */
    private fun httpStatusCode(error: PlaybackException): Int? {
        var cause: Throwable? = error.cause
        repeat(3) {
            val c = cause ?: return null
            if (c is HttpDataSource.InvalidResponseCodeException) return c.responseCode
            cause = c.cause
        }
        return null
    }

    /** Hata/bekleme ekranini kapatip sag alt ayarlar panelini geri koyar. */
    private fun resetOverlay() {
        runOnUiThread {
            showingControls = true
            overlay.visibility = View.GONE
            addControls()
        }
    }

    private fun showRetryStatus(code: Int?, attempt: Int, delayMs: Long) {
        runOnUiThread {
            showingControls = false
            overlay.visibility = View.VISIBLE
            overlayAsCentered()
            overlay.removeAllViews()
            overlay.gravity = Gravity.CENTER
            overlay.setPadding(0, 0, 0, 0)
            overlay.addView(TextView(this).apply {
                text = if (code == 429) {
                    I18n.f("rate_limit_wait", "${delayMs / 1000}", "$attempt")
                } else {
                    I18n.f("net_error_wait", "$code", "${delayMs / 1000}", "$attempt")
                }
                setTextColor(Ui.ACCENT)
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(Ui.dp(this@PlayerActivity, 24), 0, Ui.dp(this@PlayerActivity, 24), 0)
            })
        }
    }

    private fun showErrorOverlay(message: String) {
        runOnUiThread {
            showingControls = false
            overlay.visibility = View.VISIBLE
            overlayAsCentered()
            overlay.removeAllViews()
            overlay.gravity = Gravity.CENTER
            overlay.addView(TextView(this).apply {
                text = message
                setTextColor(Ui.ERROR_RED)
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(Ui.dp(this@PlayerActivity, 24), 0, Ui.dp(this@PlayerActivity, 24), 0)
            })
            overlay.addView(TextView(this).apply {
                text = I18n.s("retry")
                setTextColor(Ui.ACCENT)
                textSize = 15f
                gravity = Gravity.CENTER
                setPadding(0, Ui.dp(this@PlayerActivity, 12), 0, 0)
                setOnClickListener {
                    this@PlayerActivity.player?.apply { prepare(); playWhenReady = true }
                }
            })
            if (StreamSession.sources.size > 1) {
                overlay.addView(TextView(this).apply {
                    text = I18n.s("pick_other_source")
                    setTextColor(Ui.ACCENT)
                    textSize = 15f
                    gravity = Gravity.CENTER
                    setPadding(0, Ui.dp(this@PlayerActivity, 8), 0, 0)
                    setOnClickListener {
                        this@PlayerActivity.resetOverlay()
                        this@PlayerActivity.pickSource()
                    }
                })
            }
        }
    }

    // ==================== YASAM DONGUSU ====================

    /** Yataya donunce otomatik tam ekran (immersive), dikeyde cubuklar geri gelir. */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (inPip) return
        applySubPosition()
        setFullscreen(newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE)
    }

    /** PiP'e girince kontrolleri ve altyazinin kendi cizimini gizle, cikinca geri koy. */
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip = isInPictureInPictureMode
        playerView.useController = !isInPictureInPictureMode
        if (isInPictureInPictureMode) {
            overlay.visibility = View.GONE
            subView.visibility = View.GONE
        } else {
            if (showingControls) addControls()
        }
    }

    /** Kullanici ana ekrana donerken video oynatiyorsa otomatik PiP'e gec (YouTube tarzi). */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (!inPip && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && player?.isPlaying == true) {
            try { enterPip() } catch (_: Exception) {}
        }
    }

    override fun onStop() {
        super.onStop()
        val p = player
        if (epUrl.isNotBlank() && p != null) {
            Store.addHistory(
                Store.HistoryItem(
                    title = titleText,
                    seriesUrl = seriesUrl,
                    episodeUrl = epUrl,
                    posterUrl = poster,
                    positionMs = p.currentPosition,
                    durationMs = if (p.duration > 0) p.duration else 0,
                    time = System.currentTimeMillis(),
                )
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        subHandler.removeCallbacks(subTick)
        playerView.player = null
        player?.release()
        player = null
    }
}
