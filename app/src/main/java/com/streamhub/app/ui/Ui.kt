package com.streamhub.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.LruCache
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.streamhub.app.net.Http
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object Ui {

    val BG = 0xFF0b0e14.toInt()
    val CARD_BG = 0xFF161b26.toInt()
    val ACCENT = 0xFF7c8cff.toInt()
    val TEXT_MAIN = 0xFFe6e9f2.toInt()
    val TEXT_DIM = 0xFF9aa3b5.toInt()
    val ERROR_RED = 0xFFff5d5d.toInt()

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
        val cached = posterCache.get(url)
        if (cached != null) {
            view.setImageBitmap(cached)
            return
        }
        scope.launch {
            val bmp = runCatching {
                withContext(Dispatchers.IO) {
                    val req = okhttp3.Request.Builder().url(url)
                        .header("User-Agent", Http.UA)
                        .header("Referer", "https://dizilla.la/")
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

    fun errorBox(
        context: Context,
        message: String,
        onRetry: (() -> Unit)? = null,
        onWebview: (() -> Unit)? = null,
    ): LinearLayout {
        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 16), dp(context, 12), dp(context, 16), dp(context, 12))
            background = GradientDrawable().apply {
                setColor(0x33ff5d5d)
                cornerRadius = dp(context, 10).toFloat()
            }
        }
        box.addView(TextView(context).apply {
            text = "Hata: $message"
            setTextColor(ERROR_RED)
            textSize = 14f
        })
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(context, 8), 0, 0)
        }
        if (onRetry != null) {
            row.addView(TextView(context).apply {
                text = "  Yeniden dene  "
                setTextColor(ACCENT)
                textSize = 14f
                setOnClickListener { onRetry() }
            })
        }
        if (onWebview != null) {
            row.addView(TextView(context).apply {
                text = "  Siteyi WebView ile aç  "
                setTextColor(0xFFffd166.toInt())
                textSize = 14f
                setOnClickListener { onWebview() }
            })
        }
        box.addView(row)
        return box
    }

    fun sectionTitle(context: Context, text: String): TextView = TextView(context).apply {
        this.text = text
        setTextColor(TEXT_MAIN)
        textSize = 18f
        setPadding(dp(context, 16), dp(context, 14), dp(context, 8), dp(context, 4))
        setTypeface(typeface, android.graphics.Typeface.BOLD)
    }
}

/** Oynaticiya gecirilen aktif kaynaklar (ayni process icerisinde paylasilir). */
object StreamSession {
    var sources: List<com.streamhub.app.core.VideoSource> = emptyList()
    var title: String = ""
}
