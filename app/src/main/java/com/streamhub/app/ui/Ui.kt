package com.streamhub.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.LruCache
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.streamhub.app.net.Http
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

object Ui {
    // sabit koyu yedekler: tema cozulemediginde (fallback ekran vb.) kullanilir
    private val BG_DARK = 0xFF0b0e14.toInt()
    private val SURFACE_DARK = 0xFF141926.toInt()
    private val SURFACE_2_DARK = 0xFF1d2334.toInt()
    private val ACCENT_DARK = 0xFF8b9dff.toInt()
    private val ACCENT_2_DARK = 0xFFffd166.toInt()
    private val TEXT_MAIN_DARK = 0xFFe6e9f2.toInt()
    private val TEXT_DIM_DARK = 0xFF9aa3b5.toInt()
    private val ERROR_DARK = 0xFFff6b6b.toInt()

    // Material You: renkler aktif activity temasi uzerinden cozulur —
    // Android 12+ duvar kagidi paleti (dinamik renk) otomatik yansir
    val BG get() = themed(com.google.android.material.R.attr.colorSurface, BG_DARK)
    val SURFACE get() = themed(com.google.android.material.R.attr.colorSurface, SURFACE_DARK)
    val SURFACE_2 get() = themed(com.google.android.material.R.attr.colorSurfaceVariant, SURFACE_2_DARK)
    val ACCENT get() = themed(com.google.android.material.R.attr.colorPrimary, ACCENT_DARK)
    val ACCENT_2 get() = themed(com.google.android.material.R.attr.colorTertiary, ACCENT_2_DARK)
    val TEXT_MAIN get() = themed(com.google.android.material.R.attr.colorOnSurface, TEXT_MAIN_DARK)
    val TEXT_DIM get() = themed(com.google.android.material.R.attr.colorOnSurfaceVariant, TEXT_DIM_DARK)
    val ERROR_RED get() = themed(com.google.android.material.R.attr.colorError, ERROR_DARK)
    val DIVIDER = 0x22ffffff

    private fun themed(attr: Int, fallback: Int): Int {
        val ctx = com.streamhub.app.App.uiContext ?: return fallback
        return runCatching {
            com.google.android.material.color.MaterialColors.getColor(ctx, attr, fallback)
        }.getOrDefault(fallback)
    }

    val posterCache = object : LruCache<String, Bitmap>(24 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount / 1024
    }

    fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    fun loadPoster(
        scope: CoroutineScope,
        http: Http,
        view: ImageView,
        url: String?,
        widthPx: Int,
        heightPx: Int,
    ) {
        view.setImageDrawable(null)
        view.tag = url
        if (url.isNullOrBlank()) return
        posterCache.get(url)?.let { view.setImageBitmap(it); return }
        scope.launch {
            val bmp = runCatching {
                withContext(Dispatchers.IO) {
                    val req = okhttp3.Request.Builder().url(url)
                        .header("User-Agent", Http.UA)
                        .build()
                    http.client.newCall(req).execute().use { r ->
                        if (!r.isSuccessful) return@use null
                        r.body?.bytes()?.let { bytes ->
                            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                            opts.inSampleSize = calcSample(opts.outWidth, opts.outHeight, widthPx, heightPx)
                            opts.inJustDecodeBounds = false
                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                        }
                    }
                }
            }.getOrNull()
            if (bmp != null && view.tag == url) {
                posterCache.put(url, bmp)
                view.setImageBitmap(bmp)
            }
        }
    }

    private fun calcSample(w: Int, h: Int, tw: Int, th: Int): Int {
        var sample = 1
        if (w > tw * 2 || h > th * 2) {
            while (w / (sample * 2) >= tw && h / (sample * 2) >= th) sample *= 2
        }
        return sample
    }

    fun toast(context: Context, message: String) =
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()

    fun fmtTime(ms: Long): String {
        val total = (ms.coerceAtLeast(0)) / 1000
        val h = total / 3600; val m = (total % 3600) / 60; val s = total % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.US, "%02d:%02d", m, s)
    }

    fun card(context: Context): com.google.android.material.card.MaterialCardView =
        com.google.android.material.card.MaterialCardView(context).apply {
            radius = dp(context, 14).toFloat()
            cardElevation = 0f
            strokeWidth = 0
            setCardBackgroundColor(SURFACE)
        }

    fun sectionTitle(context: Context, text: String): TextView = TextView(context).apply {
        this.text = text
        setTextColor(TEXT_MAIN)
        textSize = 17f
        setPadding(dp(context, 16), dp(context, 14), dp(context, 8), dp(context, 6))
        setTypeface(typeface, Typeface.BOLD)
    }

    fun divider(context: Context): View = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1).apply {
            setMargins(dp(context, 16), 0, dp(context, 16), 0)
        }
        setBackgroundColor(DIVIDER)
    }

    fun roundedBg(context: Context, color: Int, radiusDp: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(context, radiusDp).toFloat()
        }

    /** Ekran ici hata kutusu: mesaj + "Yeniden dene" + "Siteyi WebView ile ac". */
    fun errorBoxCompat(
        context: Context,
        message: String,
        onRetry: (() -> Unit)? = null,
        onWebview: (() -> Unit)? = null,
    ): LinearLayout {
        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 14), dp(context, 10), dp(context, 14), dp(context, 10))
            background = roundedBg(context, 0x33ff6b6b, 12)
        }
        box.addView(TextView(context).apply {
            text = com.streamhub.app.core.I18n.s("error_prefix") + " $message"
            setTextColor(ERROR_RED)
            textSize = 13f
        })
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(context, 8), 0, 0)
        }
        if (onRetry != null) row.addView(TextView(context).apply {
            text = "  " + com.streamhub.app.core.I18n.s("retry")
            setTextColor(ACCENT)
            textSize = 14f
            setOnClickListener { onRetry() }
        })
        if (onWebview != null) row.addView(TextView(context).apply {
            text = "  " + com.streamhub.app.core.I18n.s("open_in_webview")
            setTextColor(ACCENT_2)
            textSize = 14f
            setOnClickListener { onWebview() }
        })
        box.addView(row)
        return box
    }
}

/** Oynaticiya gecirilen aktif kaynaklar (ayni process icerisinde paylasilir).
 *  Bolum listesi: seri izlerken oynatici sonraki/onceki bolume gecmek ve
 *  sezon/bolum menusunu gostermek icin bunu kullanir (Cs tarzi otomatik-ileri). */
object StreamSession {
    var sources: List<com.streamhub.app.core.VideoSource> = emptyList()
    var episodes: List<com.streamhub.app.core.Episode> = emptyList()
    var currentEpisodeIndex: Int = -1
    var providerName: String? = null
    var seriesTitle: String = ""
}
