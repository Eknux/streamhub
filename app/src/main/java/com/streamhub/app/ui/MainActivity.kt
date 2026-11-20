package com.streamhub.app.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.streamhub.app.App
import com.streamhub.app.core.SearchResult
import com.streamhub.app.net.ChallengeException
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var app: App
    private lateinit var homeList: RecyclerView
    private lateinit var searchList: RecyclerView
    private lateinit var progress: ProgressBar
    private lateinit var errorSlot: LinearLayout
    private lateinit var searchInput: EditText
    private lateinit var homeAdapter: HomeAdapter

    private val rows = mutableListOf<SectionRow>()
    private var searchJob: kotlinx.coroutines.Job? = null

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        app = application as App

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.BG)
        }

        // ust bar: baslik + arama
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(this@MainActivity, 16), Ui.dp(this@MainActivity, 12), Ui.dp(this@MainActivity, 16), Ui.dp(this@MainActivity, 4))
        }
        header.addView(TextView(this).apply {
            text = "StreamHub · ${app.providers.firstOrNull()?.name ?: "sağlayıcı yok"}"
            setTextColor(Ui.TEXT_MAIN)
            textSize = 22f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        val searchRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        searchInput = EditText(this).apply {
            hint = "Dizide ara..."
            setTextColor(Ui.TEXT_MAIN)
            setHintTextColor(Ui.TEXT_DIM)
            setSingleLine(true)
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
        }
        searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                doSearch(searchInput.text.toString()); true
            } else false
        }
        searchRow.addView(searchInput, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        searchRow.addView(TextView(this).apply {
            text = "Ara"
            setTextColor(Ui.ACCENT)
            textSize = 16f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(Ui.dp(this@MainActivity, 16), Ui.dp(this@MainActivity, 8), Ui.dp(this@MainActivity, 4), Ui.dp(this@MainActivity, 8))
            setOnClickListener { doSearch(searchInput.text.toString()) }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))
        header.addView(searchRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(header)

        errorSlot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(this@MainActivity, 12), 0, Ui.dp(this@MainActivity, 12), 0)
        }
        root.addView(errorSlot, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val frame = FrameLayout(this)
        homeList = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            setBackgroundColor(Color.TRANSPARENT)
        }
        homeAdapter = HomeAdapter(rows, { item -> openDetail(item) }, { index -> loadSection(index) }, lifecycleScope, app.http)
        homeList.adapter = homeAdapter
        frame.addView(homeList, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        searchList = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            visibility = android.view.View.GONE
        }
        frame.addView(searchList, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        progress = ProgressBar(this).apply {
            val p = Ui.dp(this@MainActivity, 56)
            layoutParams = FrameLayout.LayoutParams(p, p, Gravity.CENTER)
        }
        frame.addView(progress)
        root.addView(frame, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        setContentView(root)

        app.pluginError?.let { Ui.toast(this, it) }

        val provider = app.providers.firstOrNull()
        if (provider == null) {
            showError("Yüklü sağlayıcı yok", allowWebView = false)
        } else {
            showProgress(true)
            lifecycleScope.launch {
                for ((index, section) in provider.sections.withIndex()) {
                    val row = SectionRow(section.title, emptyList(), null, section)
                    rows.add(row)
                    homeAdapter.notifyItemInserted(rows.lastIndex)
                    loadSectionInto(row, index, section)
                }
                showProgress(false)
                if (rows.all { it.items.isEmpty() && it.error != null }) {
                    showError("Ana sayfa yüklenemedi", allowWebView = true, retry = { reloadAll() })
                }
            }
        }
    }

    private fun loadSection(index: Int) {
        val row = rows.getOrNull(index) ?: return
        lifecycleScope.launch { loadSectionInto(row, index, row.section) }
    }

    private fun reloadAll() {
        rows.clear()
        homeAdapter.notifyDataSetChanged()
        val provider = app.providers.firstOrNull() ?: return
        showProgress(true)
        lifecycleScope.launch {
            for ((index, section) in provider.sections.withIndex()) {
                val row = SectionRow(section.title, emptyList(), null, section)
                rows.add(row)
                homeAdapter.notifyItemInserted(rows.lastIndex)
                loadSectionInto(row, index, section)
            }
            showProgress(false)
        }
    }

    private suspend fun loadSectionInto(
        row: SectionRow,
        index: Int,
        section: com.streamhub.app.core.HomeSection,
    ) {
        val provider = app.providers.firstOrNull() ?: return
        try {
            val items = provider.section(section)
            row.items = items
            row.error = if (items.isEmpty()) "İçerik bulunamadı" else null
        } catch (e: ChallengeException) {
            row.error = "Bot koruması: ${e.message}"
            row.challengeUrl = e.pageUrl
        } catch (e: Exception) {
            row.error = e.message ?: e.javaClass.simpleName
        }
        runOnUiThread { homeAdapter.notifyItemChanged(index) }
    }

    private fun doSearch(query: String) {
        if (query.isBlank()) return
        val provider = app.providers.firstOrNull() ?: return
        searchJob?.cancel()
        showProgress(true)
        searchList.visibility = android.view.View.VISIBLE
        homeList.visibility = android.view.View.GONE
        errorSlot.removeAllViews()
        searchJob = lifecycleScope.launch {
            try {
                val results = provider.search(query)
                searchList.adapter = SearchAdapter(results, { openDetail(it) }, lifecycleScope, app.http)
                if (results.isEmpty()) showError("Sonuç bulunamadı: $query", allowWebView = false)
            } catch (e: ChallengeException) {
                showError(e.message ?: "Bot koruması", allowWebView = true, challengeUrl = e.pageUrl)
            } catch (e: Exception) {
                showError(e.message ?: e.javaClass.simpleName, allowWebView = false, retry = { doSearch(query) })
            } finally {
                showProgress(false)
            }
        }
    }

    private fun openDetail(item: SearchResult) {
        startActivity(
            Intent(this, DetailActivity::class.java)
                .putExtra("url", item.url)
                .putExtra("title", item.title)
                .putExtra("poster", item.posterUrl)
        )
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
        errorSlot.removeAllViews()
        errorSlot.addView(
            Ui.errorBox(
                this,
                message,
                onRetry = retry,
                onWebview = if (allowWebView) {
                    { startActivity(Intent(this, WebViewActivity::class.java).putExtra("url", challengeUrl ?: (app.providers.firstOrNull()?.sections?.firstOrNull()?.url ?: "https://dizilla.la/"))) }
                } else null
            )
        )
    }

    override fun onBackPressed() {
        if (searchList.visibility == android.view.View.VISIBLE) {
            searchList.visibility = android.view.View.GONE
            homeList.visibility = android.view.View.VISIBLE
            errorSlot.removeAllViews()
        } else {
            super.onBackPressed()
        }
    }
}

class SectionRow(
    val title: String,
    var items: List<SearchResult>,
    var error: String?,
    val section: com.streamhub.app.core.HomeSection,
) {
    var challengeUrl: String? = null
}
