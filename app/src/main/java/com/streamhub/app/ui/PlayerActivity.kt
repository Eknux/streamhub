package com.streamhub.app.ui

import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
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
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.streamhub.app.core.VideoSource

class PlayerActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var overlaySlot: LinearLayout
    private var lastSourceIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val frame = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }

        playerView = PlayerView(this).apply {
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            useController = true
        }
        frame.addView(playerView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        overlaySlot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(this@PlayerActivity, 12), Ui.dp(this@PlayerActivity, 8), Ui.dp(this@PlayerActivity, 12), 0)
        }
        val sourceBtn = TextView(this).apply {
            text = "⟳ Kaynak değiştir"
            setTextColor(Ui.ACCENT)
            textSize = 14f
            setOnClickListener { pickSource(replay = true) }
        }
        overlaySlot.gravity = Gravity.END
        overlaySlot.addView(sourceBtn)
        frame.addView(overlaySlot, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP))

        setContentView(frame)

        val sources = StreamSession.sources
        if (sources.isEmpty()) {
            Ui.toast(this, "Kaynak yok — detay ekranından tekrar dene")
            finish()
            return
        }
        pickSource(replay = false)
    }

    private fun pickSource(replay: Boolean) {
        val sources = StreamSession.sources
        if (sources.isEmpty()) return
        if (sources.size == 1) {
            play(0)
        } else {
            val names = sources.map { it.name }.toTypedArray()
            AlertDialog.Builder(this)
                .setTitle("Kaynak seç")
                .setItems(names) { _, which -> play(which) }
                .setOnCancelListener { if (player == null) play(lastSourceIndex) }
                .show()
            if (!replay && player == null) {
                // dialog acilirken arka plan kara — ilk kaynakla sessizce hazirlan
                play(lastSourceIndex)
            }
        }
    }

    private fun play(index: Int) {
        val source = StreamSession.sources.getOrNull(index) ?: return
        lastSourceIndex = index

        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(source.headers["User-Agent"] ?: com.streamhub.app.net.Http.UA)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(20_000)
            .setReadTimeoutMs(40_000)
            .setDefaultRequestProperties(
                buildMap {
                    source.referer?.let { put("Referer", it) }
                    put("User-Agent", source.headers["User-Agent"] ?: com.streamhub.app.net.Http.UA)
                    for ((k, v) in source.headers) if (k != "User-Agent") put(k, v)
                }
            )

        if (player == null) {
            player = ExoPlayer.Builder(this)
                .setMediaSourceFactory(DefaultMediaSourceFactory(httpFactory))
                .build().also {
                    playerView.player = it
                    it.addListener(object : Player.Listener {
                        override fun onPlayerError(error: PlaybackException) {
                            showErrorOverlay("Oynatma hatası: ${error.errorCodeName}. Başka kaynak dene (⟳).")
                        }
                    })
                }
        }

        val subtitleConfigs = source.subtitles.map { sub ->
            MediaItem.SubtitleConfiguration.Builder(Uri.parse(sub.url))
                .setMimeType(subMime(sub.url))
                .setLabel(sub.label)
                .setLanguage("tr")
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build()
        }

        val mediaItem = MediaItem.Builder()
            .setUri(source.url)
            .setMimeType(if (source.isM3u8) MimeTypes.APPLICATION_M3U8 else null)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(StreamSession.title).build())
            .setSubtitleConfigurations(subtitleConfigs)
            .build()

        hideErrorOverlay()
        player?.apply {
            setMediaItem(mediaItem)
            prepare()
            play()
        }
    }

    private fun subMime(url: String): String = when {
        url.endsWith(".srt", true) -> MimeTypes.APPLICATION_SUBRIP
        else -> MimeTypes.TEXT_VTT
    }

    private fun showErrorOverlay(message: String) {
        runOnUiThread {
            overlaySlot.removeAllViews()
            overlaySlot.gravity = Gravity.CENTER
            overlaySlot.addView(TextView(this).apply {
                text = message
                setTextColor(Ui.ERROR_RED)
                textSize = 14f
                gravity = Gravity.CENTER
            })
            overlaySlot.addView(TextView(this).apply {
                text = "Yeniden dene"
                setTextColor(Ui.ACCENT)
                textSize = 15f
                gravity = Gravity.CENTER
                setPadding(0, Ui.dp(this@PlayerActivity, 12), 0, 0)
                setOnClickListener {
                    hideErrorOverlay()
                    restoreSourceButton()
                    player?.apply { prepare(); play() }
                }
            })
        }
    }

    private fun hideErrorOverlay() {
        // hata kutusunu temizle ama kaynak butonu geri gelsin
        restoreSourceButton()
    }

    private fun restoreSourceButton() {
        if (overlaySlot.childCount == 0 || overlaySlot.getChildAt(0).id == View.NO_ID) {
            overlaySlot.removeAllViews()
            overlaySlot.gravity = Gravity.END
            overlaySlot.addView(TextView(this).apply {
                text = "⟳ Kaynak değiştir"
                setTextColor(Ui.ACCENT)
                textSize = 14f
                setOnClickListener { pickSource(replay = true) }
            })
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        playerView.player = null
        player?.release()
        player = null
    }
}
