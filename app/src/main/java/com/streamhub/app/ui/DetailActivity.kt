package com.streamhub.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.streamhub.app.App
import com.streamhub.app.R
import com.streamhub.app.core.Provider
import com.streamhub.app.core.SeriesInfo
import com.streamhub.app.core.Store
import com.streamhub.app.net.ChallengeException
import kotlinx.coroutines.launch

class DetailActivity : AppCompatActivity() {

    private lateinit var app: App
    private lateinit var url: String
    private var providerName: String? = null
    private lateinit var titleView: TextView
    private lateinit var descView: TextView
    private lateinit var posterView: ImageView
    private lateinit var favIcon: ImageView
    private lateinit var progress: ProgressBar
    private lateinit var errorSlot: LinearLayout
    private lateinit var episodeList: RecyclerView
    private var info: SeriesInfo? = null
    private var posterUrl: String? = null
    private var seriesTitle: String = ""
    private var loadingLinks = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        app = application as App
        url = intent.getStringExtra("url") ?: run { finish(); return }
        providerName = intent.getStringExtra("providerName")
        posterUrl = intent.getStringExtra("poster")
        seriesTitle = intent.getStringExtra("title") ?: url.substringAfterLast('/')

        val scroll = ScrollView(this).apply { setBackgroundColor(Ui.BG) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(this@DetailActivity, 14), Ui.dp(this@DetailActivity, 10), Ui.dp(this@DetailActivity, 14), Ui.dp(this@DetailActivity, 20))
        }
        scroll.addView(root, ViewGroup.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))

        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val posterCard = Ui.card(this)
        posterView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            layoutParams = ViewGroup.LayoutParams(Ui.dp(this@DetailActivity, 108), Ui.dp(this@DetailActivity, 156))
        }
        posterCard.addView(posterView)
        header.addView(posterCard)

        val infoCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titleView = TextView(this).apply {
            text = seriesTitle
            setTextColor(Ui.TEXT_MAIN)
            textSize = 19f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        infoCol.addView(titleView, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))
        favIcon = ImageView(this).apply {
            setImageResource(R.drawable.ic_fav)
            val isFav = Store.isFavorite(url)
            setColorFilter(if (isFav) Ui.ACCENT_2 else Ui.TEXT_DIM)
            layoutParams = LinearLayout.LayoutParams(Ui.dp(this@DetailActivity, 30), Ui.dp(this@DetailActivity, 30)).apply {
                topMargin = Ui.dp(this@DetailActivity, 10)
            }
            setOnClickListener { toggleFav() }
        }
        infoCol.addView(favIcon)
        providerName?.let {
            infoCol.addView(TextView(this).apply {
                text = "🔌 $it"
                setTextColor(Ui.TEXT_DIM)
                textSize = 12f
                setPadding(0, Ui.dp(this@DetailActivity, 8), 0, 0)
            })
        }
        header.addView(infoCol, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = Ui.dp(this@DetailActivity, 14)
        })
        root.addView(header, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))

        descView = TextView(this).apply {
            setTextColor(Ui.TEXT_DIM)
            textSize = 13f
        }
        root.addView(descView, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = Ui.dp(this@DetailActivity, 12)
        })

        root.addView(Ui.sectionTitle(this, com.streamhub.app.core.I18n.s("episodes")))
        progress = ProgressBar(this)
        root.addView(progress, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))
        errorSlot = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(errorSlot, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))
        episodeList = RecyclerView(this).apply { layoutManager = LinearLayoutManager(this@DetailActivity) }
        root.addView(episodeList, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))

        setContentView(scroll)
        showProgress(true)
        lifecycleScope.launch { load() }
    }

    private suspend fun load() {
        val provider = app.providerByName(providerName)
        if (provider == null) {
            showError(com.streamhub.app.core.I18n.s("plugin_not_found"), allowWebView = false)
            runOnUiThread { showProgress(false) }
            return
        }
        try {
            val loaded = provider.load(url)
            if (loaded == null) {
                showError(com.streamhub.app.core.I18n.s("load_failed"), allowWebView = true)
                runOnUiThread { showProgress(false) }
                return
            }
            info = loaded
            info?.posterUrl?.let { posterUrl = it }
            runOnUiThread {
                titleView.text = loaded.title
                seriesTitle = loaded.title
                descView.text = loaded.description ?: ""
                Ui.loadPoster(lifecycleScope, app.http, posterView, posterUrl,
                    Ui.dp(this, 108), Ui.dp(this, 156))
                updateFavIcon()
                episodeList.adapter = EpisodeAdapter(loaded.episodes) { i -> openPlayer(provider, loaded.episodes[i]) }
                if (loaded.episodes.isEmpty()) {
                    Snackbar.make(episodeList, com.streamhub.app.core.I18n.s("no_episodes"), Snackbar.LENGTH_LONG).show()
                }
            }
        } catch (e: ChallengeException) {
            showError(e.message ?: com.streamhub.app.core.I18n.s("bot_protection_tap").substringBefore(" —"), allowWebView = true, challengeUrl = e.pageUrl)
        } catch (e: Exception) {
            showError(e.message ?: e.javaClass.simpleName, allowWebView = false,
                retry = { lifecycleScope.launch { load() } })
        } finally {
            runOnUiThread { showProgress(false) }
        }
    }

    private fun openPlayer(provider: Provider, ep: com.streamhub.app.core.Episode) {
        if (loadingLinks) return
        loadingLinks = true
        showProgress(true)
        Snackbar.make(episodeList, com.streamhub.app.core.I18n.s("resolving"), Snackbar.LENGTH_SHORT).show()
        lifecycleScope.launch {
            try {
                val sources = provider.loadLinks(ep.url)
                if (sources.isEmpty()) {
                    Ui.toast(this@DetailActivity, com.streamhub.app.core.I18n.s("no_video_source"))
                } else {
                    StreamSession.sources = sources
                    val epTitle = ep.title ?: com.streamhub.app.core.I18n.f("episode_n", "${ep.number ?: 1}")
                    startActivity(
                        Intent(this@DetailActivity, PlayerActivity::class.java)
                            .putExtra("title", "$seriesTitle — $epTitle")
                            .putExtra("seriesUrl", url)
                            .putExtra("episodeUrl", ep.url)
                            .putExtra("poster", posterUrl)
                            .putExtra("startMs", Store.positionFor(ep.url))
                    )
                }
            } catch (e: ChallengeException) {
                showError(e.message ?: com.streamhub.app.core.I18n.s("bot_protection_tap").substringBefore(" —"), allowWebView = true, challengeUrl = e.pageUrl)
            } catch (e: Exception) {
                Ui.toast(this@DetailActivity, com.streamhub.app.core.I18n.f("source_error", e.message ?: e.javaClass.simpleName))
            } finally {
                loadingLinks = false
                runOnUiThread { showProgress(false) }
            }
        }
    }

    private fun toggleFav() {
        val added = Store.toggleFavorite(
            Store.FavItem(seriesTitle, url, posterUrl, providerName ?: "", false, System.currentTimeMillis())
        )
        updateFavIcon()
        Snackbar.make(episodeList,
            if (added) com.streamhub.app.core.I18n.s("fav_added") else com.streamhub.app.core.I18n.s("fav_removed"),
            Snackbar.LENGTH_SHORT).show()
    }

    private fun updateFavIcon() {
        favIcon.setColorFilter(if (Store.isFavorite(url)) Ui.ACCENT_2 else Ui.TEXT_DIM)
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
                Ui.errorBoxCompat(this, message, retry, if (allowWebView) {
                    { startActivity(Intent(this, WebViewActivity::class.java).putExtra("url", challengeUrl ?: url)) }
                } else null)
            )
        }
    }
}
