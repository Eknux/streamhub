package com.streamhub.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.streamhub.app.App
import com.streamhub.app.core.Episode
import com.streamhub.app.net.ChallengeException
import kotlinx.coroutines.launch

class DetailActivity : AppCompatActivity() {

    private lateinit var app: App
    private lateinit var url: String
    private lateinit var titleView: TextView
    private lateinit var descView: TextView
    private lateinit var posterView: ImageView
    private lateinit var progress: ProgressBar
    private lateinit var errorSlot: LinearLayout
    private lateinit var episodeList: RecyclerView
    private var loadingLinks = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        app = application as App
        url = intent.getStringExtra("url") ?: finish().let { return }
        val startTitle = intent.getStringExtra("title") ?: ""

        val scroll = android.widget.ScrollView(this).apply { setBackgroundColor(Ui.BG) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(this@DetailActivity, 16), Ui.dp(this@DetailActivity, 12), Ui.dp(this@DetailActivity, 16), Ui.dp(this@DetailActivity, 16))
        }
        scroll.addView(root, android.widget.FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        posterView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        header.addView(posterView, LinearLayout.LayoutParams(
            Ui.dp(this, 110), Ui.dp(this, 160)))
        val infoCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titleView = TextView(this).apply {
            text = startTitle
            setTextColor(Ui.TEXT_MAIN)
            textSize = 20f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        descView = TextView(this).apply {
            setTextColor(Ui.TEXT_DIM)
            textSize = 13f
        }
        infoCol.addView(titleView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        infoCol.addView(descView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = Ui.dp(this@DetailActivity, 8)
        })
        header.addView(infoCol, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = Ui.dp(this@DetailActivity, 14)
        })
        root.addView(header, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        root.addView(Ui.sectionTitle(this, "Bölümler"))

        progress = ProgressBar(this)
        root.addView(progress, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        errorSlot = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(errorSlot, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        episodeList = RecyclerView(this).apply { layoutManager = LinearLayoutManager(this@DetailActivity) }
        root.addView(episodeList, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        setContentView(scroll)
        showProgress(true)
        lifecycleScope.launch { load() }
    }

    private suspend fun load() {
        val provider = app.providers.firstOrNull() ?: run {
            showError("Sağlayıcı yok", allowWebView = false); showProgress(false); return
        }
        try {
            val info = provider.load(url)
            if (info == null) {
                showError("Dizi yüklenemedi", allowWebView = true); return
            }
            runOnUiThread {
                titleView.text = info.title
                descView.text = info.description ?: ""
                Ui.loadPoster(lifecycleScope, app.http, posterView, info.posterUrl,
                    Ui.dp(this, 110), Ui.dp(this, 160))
                episodeList.adapter = EpisodeAdapter(info.episodes) { index ->
                    openPlayer(info.episodes[index], provider)
                }
            }
        } catch (e: ChallengeException) {
            showError(e.message ?: "Bot koruması", allowWebView = true, challengeUrl = e.pageUrl)
        } catch (e: Exception) {
            showError(e.message ?: e.javaClass.simpleName, allowWebView = false, retry = { lifecycleScope.launch { load() } })
        } finally {
            runOnUiThread { showProgress(false) }
        }
    }

    private fun openPlayer(ep: Episode, provider: com.streamhub.app.core.Provider) {
        if (loadingLinks) return
        loadingLinks = true
        showProgress(true)
        Ui.toast(this, "Kaynaklar çözülüyor...")
        lifecycleScope.launch {
            try {
                val sources = provider.loadLinks(ep.url)
                if (sources.isEmpty()) {
                    Ui.toast(this@DetailActivity, "Video kaynağı bulunamadı — WebView ile siteyi açıp çerezleri yenilemeyi dene")
                } else {
                    StreamSession.sources = sources
                    StreamSession.title = "${titleView.text} — ${ep.title ?: ""}"
                    startActivity(Intent(this@DetailActivity, PlayerActivity::class.java))
                }
            } catch (e: ChallengeException) {
                showError(e.message ?: "Bot koruması", allowWebView = true, challengeUrl = e.pageUrl)
            } catch (e: Exception) {
                Ui.toast(this@DetailActivity, "Kaynak hatası: ${e.message ?: e.javaClass.simpleName}")
            } finally {
                loadingLinks = false
                runOnUiThread { showProgress(false) }
            }
        }
    }

    private fun showProgress(show: Boolean) {
        progress.visibility = if (show) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun showError(
        message: String,
        allowWebView: Boolean,
        retry: (() -> Unit)? = null,
        challengeUrl: String? = null,
    ) {
        runOnUiThread {
            errorSlot.removeAllViews()
            errorSlot.addView(
                Ui.errorBox(this, message, onRetry = retry, onWebview = if (allowWebView) {
                    { startActivity(Intent(this, WebViewActivity::class.java).putExtra("url", challengeUrl ?: url)) }
                } else null)
            )
        }
    }
}
