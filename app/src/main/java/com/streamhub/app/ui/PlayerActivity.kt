package com.streamhub.app.ui

import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.streamhub.app.core.Store
import com.streamhub.app.core.VideoSource

class PlayerActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var overlay: LinearLayout
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        http = com.streamhub.app.net.Http(this)

        titleText = intent.getStringExtra("title") ?: "StreamHub"
        startMs = intent.getLongExtra("startMs", 0L)
        seriesUrl = intent.getStringExtra("seriesUrl")
        epUrl = intent.getStringExtra("episodeUrl") ?: intent.getStringExtra("uri").orEmpty()
        poster = intent.getStringExtra("poster")

        val frame = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }

        playerView = PlayerView(this).apply {
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            useController = true
        }
        frame.addView(playerView, FrameLayout.LayoutParams(-1, -1))

        overlay = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
            setPadding(Ui.dp(this@PlayerActivity, 12), Ui.dp(this@PlayerActivity, 8), Ui.dp(this@PlayerActivity, 12), 0)
        }
        addControls()
        frame.addView(overlay, FrameLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP))

        setContentView(frame)

        if (StreamSession.sources.isEmpty() && epUrl.isNotBlank()) {
            StreamSession.sources = listOf(
                VideoSource(name = "Doğrudan", url = epUrl, isM3u8 = epUrl.contains(".m3u8"))
            )
        }
        if (StreamSession.sources.isEmpty()) {
            Ui.toast(this, "Oynanacak kaynak yok")
            finish()
            return
        }
        if (StreamSession.sources.size > 1) pickSource() else play(0)
    }

    /** Ust kontrol satirlari: tam ekran + PiP + yon kilidi + kaynak secici.
     *  Metin butonlari kullanilir (XML drawable yok — MIUI Resources$NotFoundException'tan kacinma). */
    private fun addControls() {
        showingControls = true
        overlay.removeAllViews()
        overlay.gravity = Gravity.END

        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row1.addView(controlButton(if (fullscreen) "⇔ Tam ekran çık" else "⛶ Tam ekran", fullscreen) { toggleFullscreen() })
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            row1.addView(controlButton("PiP", false) { enterPip() })
        }
        row1.addView(controlButton("⟳ Kaynaklar", false) { pickSource() })
        overlay.addView(row1, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val row2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row2.addView(controlButton("Oto", orientationMode == 0) { applyOrientation(0) })
        row2.addView(controlButton("Dikey", orientationMode == 1) { applyOrientation(1) })
        row2.addView(controlButton("Yatay", orientationMode == 2) { applyOrientation(2) })
        overlay.addView(row2, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
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
            Ui.toast(this, "PiP için Android 8.0+ gerekli")
            return
        }
        val p = player ?: return
        try {
            enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(pipRatio(p)).build())
        } catch (e: Exception) {
            Ui.toast(this, "PiP başlatılamadı: ${e.message}")
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

    private fun pickSource() {
        val sources = StreamSession.sources
        if (sources.isEmpty()) return
        if (sources.size == 1) { play(0); return }
        AlertDialog.Builder(this)
            .setTitle("Kaynak seç")
            .setItems(sources.map { it.name }.toTypedArray()) { _, which -> play(which) }
            .setNegativeButton("İptal") { d, _ ->
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
                                null -> "Oynatma hatası: ${error.errorCodeName}"
                                429 -> "Oynatma hatası: HTTP 429 — kaynak sunucusu hız limiti"
                                403 -> "Oynatma hatası: HTTP 403 — sunucu isteği reddetti"
                                404 -> "Oynatma hatası: HTTP 404 — dosya bulunamadı"
                                else -> "Oynatma hatası: HTTP $code"
                            }
                            showErrorOverlay("$msg. Başka kaynak dene (⟳).")
                        }
                    })
                }
        }

        val subtitleConfigs = source.subtitles.map { sub ->
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

    /** Hata/bekleme ekranini kapatip normal ust katmani geri koyar. */
    private fun resetOverlay() {
        runOnUiThread {
            overlay.visibility = View.VISIBLE
            addControls()
        }
    }

    private fun showRetryStatus(code: Int?, attempt: Int, delayMs: Long) {
        runOnUiThread {
            showingControls = false
            overlay.removeAllViews()
            overlay.gravity = Gravity.CENTER
            overlay.addView(TextView(this).apply {
                text = if (code == 429) {
                    "Kaynak sunucusu hız limiti (HTTP 429)\n${delayMs / 1000} sn sonra otomatik tekrar (deneme $attempt/3)…"
                } else {
                    "Geçici ağ hatası (HTTP $code)\n${delayMs / 1000} sn sonra otomatik tekrar (deneme $attempt/3)…"
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
                text = "Yeniden dene"
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
                    text = "⟳ Başka kaynak seç"
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

    /** Yataya donunce otomatik tam ekran (immersive), dikeyde cubuklar geri gelir. */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (inPip) return
        setFullscreen(newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE)
    }

    /** PiP'e girince kontrolleri gizle, cikinca geri koy. */
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip = isInPictureInPictureMode
        playerView.useController = !isInPictureInPictureMode
        if (isInPictureInPictureMode) {
            overlay.visibility = View.GONE
        } else {
            overlay.visibility = View.VISIBLE
            addControls()
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
        playerView.player = null
        player?.release()
        player = null
    }
}
