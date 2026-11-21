package com.streamhub.app.ui

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.appbar.MaterialToolbar
import com.streamhub.app.App
import com.streamhub.app.R
import com.streamhub.app.core.CrashHandler
import com.streamhub.app.core.Provider
import com.streamhub.app.core.SearchResult
import com.streamhub.app.core.Store
import com.streamhub.app.core.VideoSource
import com.streamhub.app.net.ChallengeException
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    companion object {
        const val PAGE_HOME = 1
        const val PAGE_SEARCH = 2
        const val PAGE_FAVS = 3
        const val PAGE_SETTINGS = 4
        const val PLUGINS_DOC_URL = "https://github.com/Eknux/streamhub/blob/main/PLUGINS.md"
    }

    private lateinit var app: App
    private lateinit var contentFrame: FrameLayout
    private lateinit var pageHome: FrameLayout
    private lateinit var pageSearch: LinearLayout
    private lateinit var pageFavs: FrameLayout
    private lateinit var pageSettings: LinearLayout
    private lateinit var prefs: android.content.SharedPreferences

    private var pluginPicker: ActivityResultLauncher<Array<String>>? = null
    private var videoPicker: ActivityResultLauncher<Array<String>>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        app = application as App
        prefs = getSharedPreferences("streamhub_prefs", MODE_PRIVATE)

        // launcher'lar onCreate icinde kurulur (baslatma sirasi riski olmasin)
        pluginPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { importPlugin(it) }
        }
        videoPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { playLocalFile(it) }
        }

        try {
            buildFullUi()
        } catch (t: Throwable) {
            // Material arayuz her ne sebeple olursa bozulsa: uygulama YINE acilir
            // ve hatanin kendisi ekranda gosterilir ("direk cokuyor" degil).
            CrashHandler.record(t)
            runCatching { setContentView(buildFallbackUi(t)) }
            runCatching { Ui.toast(this, com.streamhub.app.core.I18n.s("ui_failed")) }
        }
        runCatching { maybeShowCrashReport() }
    }

    override fun onResume() {
        super.onResume()
        CrashHandler.appStarted = true
    }

    // ---------- tam arayuz ----------

    private fun buildFullUi() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Ui.BG) }

        val toolbar = MaterialToolbar(this).apply {
            title = "StreamHub"
            subtitle = com.streamhub.app.core.I18n.s("toolbar_subtitle")
            setTitleTextColor(Ui.TEXT_MAIN)
            setSubtitleTextColor(Ui.TEXT_DIM)
        }
        root.addView(toolbar, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(Ui.divider(this))

        contentFrame = FrameLayout(this)
        pageHome = FrameLayout(this)
        pageSearch = buildSearchPage()
        pageFavs = FrameLayout(this)
        pageSettings = buildSettingsPage()
        contentFrame.addView(pageHome, FrameLayout.LayoutParams(-1, -1))
        contentFrame.addView(pageSearch, FrameLayout.LayoutParams(-1, -1).apply {
            pageSearch.visibility = View.GONE })
        contentFrame.addView(pageFavs, FrameLayout.LayoutParams(-1, -1).apply {
            pageFavs.visibility = View.GONE })
        contentFrame.addView(pageSettings, FrameLayout.LayoutParams(-1, -1).apply {
            pageSettings.visibility = View.GONE })
        root.addView(contentFrame, LinearLayout.LayoutParams(-1, 0, 1f))

        val nav = BottomNavigationView(this).apply { id = R.id.bottom_nav }
        nav.menu.add(Menu.NONE, PAGE_HOME, Menu.NONE, com.streamhub.app.core.I18n.s("nav_home")).setIcon(R.drawable.ic_home)
        nav.menu.add(Menu.NONE, PAGE_SEARCH, Menu.NONE, com.streamhub.app.core.I18n.s("nav_search")).setIcon(R.drawable.ic_search)
        nav.menu.add(Menu.NONE, PAGE_FAVS, Menu.NONE, com.streamhub.app.core.I18n.s("nav_favs")).setIcon(R.drawable.ic_fav)
        nav.menu.add(Menu.NONE, PAGE_SETTINGS, Menu.NONE, com.streamhub.app.core.I18n.s("nav_settings")).setIcon(R.drawable.ic_settings)
        nav.labelVisibilityMode = BottomNavigationView.LABEL_VISIBILITY_LABELED
        nav.setOnItemSelectedListener { item ->
            showPage(item.itemId); true
        }
        root.addView(nav, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))

        setContentView(root)

        if (!prefs.getBoolean("disclaimer_accepted", false)) {
            runCatching { showDisclaimerDialog(firstLaunch = true) }
        }
        showPage(PAGE_HOME)
    }

    // ---------- guvenli geri dusme ekrani ----------

    private fun buildFallbackUi(t: Throwable): View {
        val ctx = this
        fun dp(v: Int) = Ui.dp(ctx, v)
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF101318.toInt())
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        root.addView(TextView(ctx).apply {
            text = com.streamhub.app.core.I18n.s("ui_failed_title")
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
        })
        root.addView(TextView(ctx).apply {
            text = com.streamhub.app.core.I18n.s("ui_failed_msg")
            setTextColor(0xFF9aa3b5.toInt())
            textSize = 13f
            setPadding(0, dp(8), 0, dp(8))
        })
        val scroll = ScrollView(ctx)
        scroll.addView(TextView(ctx).apply {
            text = t.toString() + "\n\n" + t.stackTrace.joinToString("\n") { it.toString() }
            setTextColor(0xFFe6e9f2.toInt())
            textSize = 12f
            setTypeface(Typeface.MONOSPACE)
            setTextIsSelectable(true)
        })
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(TextView(ctx).apply {
            text = com.streamhub.app.core.I18n.s("copy_error")
            setTextColor(0xFF8b9dff.toInt())
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, 0)
            setOnClickListener {
                val full = t.toString() + "\n" + t.stackTrace.joinToString("\n") { it.toString() }
                (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                    .setPrimaryClip(ClipData.newPlainText("streamhub_error", full))
                Ui.toast(ctx, com.streamhub.app.core.I18n.s("copied"))
            }
        })
        return root
    }

    /** Gecmis cokme kaydi varsa acilista raporla (kullanici ekran goruntusu alip gonderebilir). */
    private fun maybeShowCrashReport() {
        val crash = CrashHandler.lastCrash(this) ?: return
        val stamp = CrashHandler.crashStamp(this)
        if (prefs.getLong("crash_shown_at", -1L) == stamp) return
        val mark = { prefs.edit().putLong("crash_shown_at", stamp).apply() }
        // framework diyaloğu: AppCompat/M3 bozulsa bile calisir
        android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(com.streamhub.app.core.I18n.s("crash_dialog_title"))
            .setMessage(crash.take(3500))
            .setPositiveButton(com.streamhub.app.core.I18n.s("copy_emoji")) { _, _ ->
                (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                    .setPrimaryClip(ClipData.newPlainText("streamhub_crash", crash))
                mark()
            }
            .setNeutralButton(com.streamhub.app.core.I18n.s("delete")) { _, _ -> CrashHandler.clear(this); mark() }
            .setNegativeButton(com.streamhub.app.core.I18n.s("close")) { _, _ -> mark() }
            .show()
    }

    // ---------- sayfa gecisi ----------

    private fun showPage(id: Int) {
        pageHome.visibility = if (id == PAGE_HOME) View.VISIBLE else View.GONE
        pageSearch.visibility = if (id == PAGE_SEARCH) View.VISIBLE else View.GONE
        pageFavs.visibility = if (id == PAGE_FAVS) View.VISIBLE else View.GONE
        pageSettings.visibility = if (id == PAGE_SETTINGS) View.VISIBLE else View.GONE
        when (id) {
            PAGE_HOME -> refreshHome()
            PAGE_FAVS -> refreshFavs()
            PAGE_SETTINGS -> refreshSettings()
        }
    }

    private fun scrollWrap(view: View): ScrollView {
        val sv = ScrollView(this)
        sv.addView(view, ViewGroup.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))
        return sv
    }

    // ---------- ANA SAYFA ----------

    @SuppressLint("SetTextI18n")
    private fun refreshHome() {
        pageHome.removeAllViews()
        val provider = app.providerByName(app.activeProviderName)
        val content = if (provider == null) buildEmptyState() else buildHomeContent(provider)
        pageHome.addView(scrollWrap(content), FrameLayout.LayoutParams(-1, -1))
    }

    private fun buildHomeContent(provider: Provider): LinearLayout {
        val ctx = this
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(0, Ui.dp(ctx, 4), 0, Ui.dp(ctx, 24)) }

        // eklenti secici chip'leri
        if (app.providers.size > 1) {
            val scroll = HorizontalScrollView(ctx).apply {
                isHorizontalScrollBarEnabled = false
                setPadding(Ui.dp(ctx, 8), Ui.dp(ctx, 4), Ui.dp(ctx, 8), 0)
            }
            val chips = ChipGroup(ctx).apply {
                isSingleLine = true
                setSelectionRequired(true)
            }
            app.providers.forEach { p ->
                chips.addView(Chip(ctx).apply {
                    text = p.name
                    isCheckable = true
                    isClickable = true
                    isChecked = p.name == provider.name
                    setOnCheckedChangeListener { _, checked ->
                        if (checked) {
                            app.activeProviderName = p.name
                            refreshHome()
                        }
                    }
                })
            }
            scroll.addView(chips)
            col.addView(scroll, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

        // hizli eylemler
        val actions = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; setPadding(Ui.dp(ctx, 12), Ui.dp(ctx, 8), Ui.dp(ctx, 12), 0) }
        actions.addView(MaterialButton(ctx).apply {
            text = com.streamhub.app.core.I18n.s("url_play")
            setPadding(Ui.dp(ctx, 12), 0, Ui.dp(ctx, 12), 0)
            setOnClickListener { urlPlayDialog() }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(4, 0, 4, 0) })
        actions.addView(MaterialButton(ctx).apply {
            text = com.streamhub.app.core.I18n.s("local_file")
            setPadding(Ui.dp(ctx, 12), 0, Ui.dp(ctx, 12), 0)
            setOnClickListener { videoPicker?.launch(arrayOf("video/*")) }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(4, 0, 4, 0) })
        col.addView(actions, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))

        // izlemeye devam et
        val history = Store.history()
        if (history.isNotEmpty()) {
            col.addView(Ui.sectionTitle(ctx, com.streamhub.app.core.I18n.s("continue_watching")))
            val rv = RecyclerView(ctx).apply {
                layoutManager = LinearLayoutManager(ctx, LinearLayoutManager.HORIZONTAL, false)
            }
            rv.adapter = HistoryAdapter(history.take(10), { replayHistory(it) }, { Store.removeHistory(it.episodeUrl); refreshHome() }, lifecycleScope, app.http)
            col.addView(rv, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

        provider.description?.let {
            col.addView(TextView(ctx).apply {
                text = it
                setTextColor(Ui.TEXT_DIM)
                textSize = 12f
                setPadding(Ui.dp(ctx, 16), Ui.dp(ctx, 8), Ui.dp(ctx, 16), 0)
            })
        }

        // eklenti bolumleri
        provider.sections.forEach { sec ->
            val container = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
            container.addView(Ui.sectionTitle(ctx, sec.title))
            val inner = RecyclerView(ctx).apply {
                layoutManager = LinearLayoutManager(ctx, LinearLayoutManager.HORIZONTAL, false)
            }
            container.addView(inner, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))
            val err = TextView(ctx).apply {
                setTextColor(Ui.ERROR_RED)
                textSize = 13f
                visibility = View.GONE
                setPadding(Ui.dp(ctx, 16), Ui.dp(ctx, 4), Ui.dp(ctx, 16), Ui.dp(ctx, 8))
            }
            container.addView(err)
            col.addView(container)

            lifecycleScope.launch {
                try {
                    val items = provider.section(sec)
                if (items.isEmpty()) {
                    err.text = com.streamhub.app.core.I18n.s("no_content")
                    err.visibility = View.VISIBLE
                } else {
                    inner.adapter = CardAdapter(items, { openResult(it) }, lifecycleScope, app.http)
                }
            } catch (e: ChallengeException) {
                showRowError(err, com.streamhub.app.core.I18n.s("bot_protection_tap")) {
                    startActivity(Intent(this@MainActivity, WebViewActivity::class.java).putExtra("url", e.pageUrl))
                }
            } catch (e: Exception) {
                showRowError(err, com.streamhub.app.core.I18n.f("error_tap_retry", e.message ?: e.javaClass.simpleName)) {
                    refreshHome()
                }
            }
            }
        }
        return col
    }

    private fun showRowError(view: TextView, text: String, onClick: () -> Unit) {
        runOnUiThread {
            view.text = text
            view.visibility = View.VISIBLE
            view.setOnClickListener { onClick() }
        }
    }

    private fun buildEmptyState(): LinearLayout {
        val ctx = this
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(Ui.dp(ctx, 20), Ui.dp(ctx, 16), Ui.dp(ctx, 20), 0) }
        val card = Ui.card(ctx)
        val inner = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(ctx, 20), Ui.dp(ctx, 20), Ui.dp(ctx, 20), Ui.dp(ctx, 20))
        }
        inner.addView(TextView(ctx).apply {
            text = com.streamhub.app.core.I18n.s("no_plugin_title")
            setTextColor(Ui.TEXT_MAIN)
            textSize = 18f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        inner.addView(TextView(ctx).apply {
            text = com.streamhub.app.core.I18n.s("no_plugin_msg")
            setTextColor(Ui.TEXT_DIM)
            textSize = 13f
            setPadding(0, Ui.dp(ctx, 10), 0, Ui.dp(ctx, 16))
        })
        inner.addView(MaterialButton(ctx).apply {
            text = com.streamhub.app.core.I18n.s("add_plugin")
            setOnClickListener { pluginPicker?.launch(arrayOf("application/zip", "application/octet-stream", "application/x-zip-compressed")) }
        }, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, Ui.dp(ctx, 10)) })
        inner.addView(MaterialButton(ctx).apply {
            text = com.streamhub.app.core.I18n.s("plugins_doc_github")
            setOnClickListener { openUrl(PLUGINS_DOC_URL) }
        }, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, Ui.dp(ctx, 10)) })
        inner.addView(MaterialButton(ctx).apply {
            text = com.streamhub.app.core.I18n.s("url_play")
            setOnClickListener { urlPlayDialog() }
        }, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))
        card.addView(inner)
        col.addView(card, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))
        return col
    }

    // ---------- ARAMA SAYFASI ----------

    private fun buildSearchPage(): LinearLayout {
        val ctx = this
        val page = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(ctx, 12), Ui.dp(ctx, 8), Ui.dp(ctx, 12), Ui.dp(ctx, 12))
        }
        val field = TextInputLayout(ctx).apply { hint = com.streamhub.app.core.I18n.s("search_hint") }
        val input = TextInputEditText(ctx).apply {
            setSingleLine(true)
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
        }
        field.addView(input, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))
        page.addView(field, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))

        val results = RecyclerView(ctx).apply { layoutManager = LinearLayoutManager(ctx) }
        page.addView(results, LinearLayout.LayoutParams(-1, 0, 1f))
        val empty = TextView(ctx).apply {
            text = com.streamhub.app.core.I18n.s("search_empty_hint")
            setTextColor(Ui.TEXT_DIM)
            textSize = 13f
            setPadding(Ui.dp(ctx, 8), Ui.dp(ctx, 16), Ui.dp(ctx, 8), 0)
        }
        page.addView(empty)

        var searchJob: kotlinx.coroutines.Job? = null
        fun doSearch(q: String) {
            val provider = app.providerByName(app.activeProviderName)
            if (q.isBlank()) return
            if (provider == null) {
                Ui.toast(ctx, com.streamhub.app.core.I18n.s("need_plugin_first")); return
            }
            empty.visibility = View.GONE
            searchJob?.cancel()
            searchJob = lifecycleScope.launch {
                try {
                    val found = provider.search(q)
                    results.adapter = SearchAdapter(found, { openResult(it) }, lifecycleScope, app.http)
                    if (found.isEmpty()) {
                        empty.text = com.streamhub.app.core.I18n.f("no_results", q)
                        empty.visibility = View.VISIBLE
                    }
                } catch (e: ChallengeException) {
                    showErrorPage(empty, e.message ?: com.streamhub.app.core.I18n.s("bot_protection_tap").substringBefore(" —"))
                } catch (e: Exception) {
                    showErrorPage(empty, e.message ?: e.javaClass.simpleName)
                }
            }
        }
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                doSearch(input.text.toString()); true
            } else false
        }
        return page
    }

    @SuppressLint("SetTextI18n")
    private fun showErrorPage(view: TextView, message: String) {
        view.text = com.streamhub.app.core.I18n.s("error_prefix") + " $message"
        view.setTextColor(Ui.ERROR_RED)
        view.visibility = View.VISIBLE
    }

    // ---------- FAVORILER ----------

    private fun refreshFavs() {
        pageFavs.removeAllViews()
        val favs = Store.favorites()
        if (favs.isEmpty()) {
            val t = TextView(this).apply {
                text = com.streamhub.app.core.I18n.s("favs_empty")
                setTextColor(Ui.TEXT_DIM)
                textSize = 14f
                setPadding(Ui.dp(this@MainActivity, 16), Ui.dp(this@MainActivity, 16), Ui.dp(this@MainActivity, 16), 0)
            }
            pageFavs.addView(t)
            return
        }
        val rv = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(this@MainActivity, 3)
        }
        rv.adapter = FavAdapter(
            favs,
            { openFav(it) },
            { Store.toggleFavorite(Store.FavItem(it.title, it.url, it.posterUrl, it.providerName, it.direct, System.currentTimeMillis())); refreshFavs() },
            lifecycleScope, app.http,
        )
        pageFavs.addView(rv, FrameLayout.LayoutParams(-1, -1))
    }

    private fun openFav(item: Store.FavItem) {
        if (item.direct) playDirect(item.url, item.title, item.posterUrl, item.providerName)
        else startActivity(
            Intent(this, DetailActivity::class.java)
                .putExtra("url", item.url)
                .putExtra("title", item.title)
                .putExtra("poster", item.posterUrl)
                .putExtra("providerName", item.providerName)
        )
    }

    // ---------- AYARLAR ----------

    private fun buildSettingsPage(): LinearLayout {
        val page = LinearLayout(this)
        page.orientation = LinearLayout.VERTICAL
        return page
    }

    @SuppressLint("SetTextI18n")
    private fun refreshSettings() {
        pageSettings.removeAllViews()
        val ctx = this
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(0, Ui.dp(ctx, 4), 0, Ui.dp(ctx, 32)) }

        col.addView(Ui.sectionTitle(ctx, com.streamhub.app.core.I18n.s("section_plugins")))
        val installed = com.streamhub.app.core.PluginManager.installed(ctx)
        if (installed.isEmpty()) {
            col.addView(listSubtitle(com.streamhub.app.core.I18n.s("no_plugins_installed")))
        }
        installed.forEach { f ->
            col.addView(settingsRow(null, f.name, "${f.length() / 1024} KB") { })
            col.addView(iconRow(R.drawable.ic_delete, com.streamhub.app.core.I18n.s("remove")) {
                com.streamhub.app.core.PluginManager.delete(ctx, f.name)
                app.reloadPlugins()
                refreshSettings(); refreshHome()
                snack(com.streamhub.app.core.I18n.s("plugin_removed"))
            })
            col.addView(Ui.divider(ctx))
        }
        col.addView(iconRow(R.drawable.ic_add, com.streamhub.app.core.I18n.s("add_plugin")) {
            pluginPicker?.launch(arrayOf("application/zip", "application/octet-stream", "application/x-zip-compressed"))
        })
        col.addView(iconRow(R.drawable.ic_book, com.streamhub.app.core.I18n.s("plugins_doc_guide")) { openUrl(PLUGINS_DOC_URL) })
        col.addView(Ui.divider(ctx))

        col.addView(Ui.sectionTitle(ctx, com.streamhub.app.core.I18n.s("section_data")))
        col.addView(iconRow(R.drawable.ic_delete, com.streamhub.app.core.I18n.s("clear_history")) {
            Store.clearHistory(); refreshHome(); snack(com.streamhub.app.core.I18n.s("history_cleared"))
        })
        col.addView(iconRow(R.drawable.ic_delete, com.streamhub.app.core.I18n.s("clear_favs")) {
            Store.clearFavorites(); snack(com.streamhub.app.core.I18n.s("favs_cleared"))
        })
        col.addView(Ui.divider(ctx))

        col.addView(Ui.sectionTitle(ctx, com.streamhub.app.core.I18n.s("section_about")))
        col.addView(iconRow(R.drawable.ic_globe, com.streamhub.app.core.I18n.s("language_row")) { showLanguageDialog() })
        col.addView(iconRow(R.drawable.ic_fav, com.streamhub.app.core.I18n.s("legal_notice")) { showDisclaimerDialog(false) })
        val crash = CrashHandler.lastCrash(ctx)
        if (crash != null) {
            col.addView(iconRow(R.drawable.ic_bug, com.streamhub.app.core.I18n.s("crash_row")) {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle(com.streamhub.app.core.I18n.s("crash_dialog_title"))
                    .setMessage(crash.take(3000))
                    .setPositiveButton(com.streamhub.app.core.I18n.s("copy")) { _, _ ->
                        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                            .setPrimaryClip(ClipData.newPlainText("crash", crash))
                        snack(com.streamhub.app.core.I18n.s("copied"))
                    }
                    .setNeutralButton(com.streamhub.app.core.I18n.s("delete")) { _, _ -> CrashHandler.clear(ctx); refreshSettings() }
                    .setNegativeButton(com.streamhub.app.core.I18n.s("close"), null)
                    .show()
            })
        }
        col.addView(listSubtitle(com.streamhub.app.core.I18n.s("version_footer")))

        pageSettings.addView(col)
    }

    private fun listSubtitle(text: String): TextView = TextView(this).apply {
        this.text = text
        setTextColor(Ui.TEXT_DIM)
        textSize = 13f
        setPadding(Ui.dp(this@MainActivity, 16), Ui.dp(this@MainActivity, 6), Ui.dp(this@MainActivity, 16), Ui.dp(this@MainActivity, 6))
    }

    private fun settingsRow(icon: Int?, title: String, subtitle: String?, onClick: () -> Unit): View {
        val ctx = this
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Ui.dp(ctx, 16), Ui.dp(ctx, 12), Ui.dp(ctx, 16), Ui.dp(ctx, 12))
        }
        if (icon != null) {
            row.addView(ImageView(ctx).apply {
                setImageResource(icon)
                setColorFilter(Ui.TEXT_DIM)
                layoutParams = LinearLayout.LayoutParams(Ui.dp(ctx, 22), Ui.dp(ctx, 22)).apply {
                    marginEnd = Ui.dp(ctx, 14)
                }
            })
        }
        val texts = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(TextView(ctx).apply {
            this.text = title
            setTextColor(Ui.TEXT_MAIN)
            textSize = 15f
        })
        subtitle?.let {
            texts.addView(TextView(ctx).apply {
                this.text = it
                setTextColor(Ui.TEXT_DIM)
                textSize = 12f
            })
        }
        row.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.setOnClickListener { onClick() }
        return row
    }

    private fun iconRow(icon: Int, title: String, onClick: () -> Unit): View =
        settingsRow(icon, title, null, onClick)

    // ---------- eylemler ----------

    private fun importPlugin(uri: Uri) {
        runCatching { com.streamhub.app.core.PluginManager.importZip(this, uri) }
            .onSuccess { name ->
                app.reloadPlugins()
                refreshHome()
                snack(
                    if (app.pluginErrors.isEmpty()) com.streamhub.app.core.I18n.f("plugin_added", name, "${app.providers.size}")
                    else com.streamhub.app.core.I18n.f("plugin_added_with_errors", app.pluginErrors.joinToString("; ").take(120))
                )
            }
            .onFailure { Ui.toast(this, com.streamhub.app.core.I18n.f("plugin_add_failed", it.message ?: "")) }
    }

    private fun urlPlayDialog() {
        val ctx = this
        val field = TextInputLayout(ctx).apply { hint = com.streamhub.app.core.I18n.s("url_hint") }
        val input = TextInputEditText(ctx).apply { setSingleLine(true) }
        field.addView(input, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))
        AlertDialog.Builder(ctx)
            .setTitle(com.streamhub.app.core.I18n.s("url_dialog_title"))
            .setView(field)
            .setPositiveButton(com.streamhub.app.core.I18n.s("play")) { _, _ ->
                val url = input.text.toString().trim()
                if (url.startsWith("http")) playDirect(url, url.substringAfterLast('/'), null, null)
                else Ui.toast(ctx, com.streamhub.app.core.I18n.s("invalid_url"))
            }
            .setNegativeButton(com.streamhub.app.core.I18n.s("cancel"), null)
            .show()
    }

    private fun playLocalFile(uri: Uri) {
        runCatching {
            contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val name = uri.lastPathSegment?.substringAfterLast('/') ?: com.streamhub.app.core.I18n.s("local_video")
        // tek video: seri baglamini temizle (bolum menusu / otomatik-ileri devre disi)
        StreamSession.episodes = emptyList()
        StreamSession.currentEpisodeIndex = -1
        StreamSession.providerName = null
        StreamSession.seriesTitle = name
        StreamSession.sources = listOf(VideoSource(name = com.streamhub.app.core.I18n.s("local_file_source"), url = uri.toString(), isM3u8 = false))
        startActivity(
            Intent(this, PlayerActivity::class.java)
                .putExtra("title", name)
                .putExtra("uri", uri.toString())
                .putExtra("episodeUrl", uri.toString())
                .putExtra("startMs", Store.positionFor(uri.toString()))
        )
    }

    private fun playDirect(url: String, title: String, poster: String?, providerName: String?) {
        lifecycleScope.launch {
            val provider = app.providerByName(providerName)
            val src = provider?.directSource(url)
                ?: VideoSource(name = com.streamhub.app.core.I18n.s("direct_source"), url = url, isM3u8 = url.contains(".m3u8"))
            // tek video: seri baglamini temizle (bolum menusu / otomatik-ileri devre disi)
            StreamSession.episodes = emptyList()
            StreamSession.currentEpisodeIndex = -1
            StreamSession.providerName = null
            StreamSession.seriesTitle = title
            StreamSession.sources = listOf(src)
            startActivity(
                Intent(this@MainActivity, PlayerActivity::class.java)
                    .putExtra("title", title)
                    .putExtra("episodeUrl", url)
                    .putExtra("poster", poster)
                    .putExtra("startMs", Store.positionFor(url))
            )
        }
    }

    private fun replayHistory(item: Store.HistoryItem) {
        if (item.seriesUrl != null) {
            startActivity(
                Intent(this, DetailActivity::class.java)
                    .putExtra("url", item.seriesUrl)
                    .putExtra("title", item.title)
                    .putExtra("poster", item.posterUrl)
            )
        } else {
            playDirect(item.episodeUrl, item.title, item.posterUrl, null)
        }
    }

    private fun openResult(item: SearchResult) {
        if (item.directVideo) playDirect(item.url, item.title, item.posterUrl, item.providerName)
        else startActivity(
            Intent(this, DetailActivity::class.java)
                .putExtra("url", item.url)
                .putExtra("title", item.title)
                .putExtra("poster", item.posterUrl)
                .putExtra("providerName", item.providerName)
        )
    }

    private fun openUrl(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { Ui.toast(this, com.streamhub.app.core.I18n.s("browser_failed")) }
    }

    private fun snack(message: String) =
        Snackbar.make(contentFrame, message, Snackbar.LENGTH_SHORT).show()

    // ---------- disclaimer + dil secimi ----------

    private fun showDisclaimerDialog(firstLaunch: Boolean) {
        val builder = AlertDialog.Builder(this)
            .setTitle(com.streamhub.app.core.I18n.s("disclaimer_title"))
            .setMessage(com.streamhub.app.core.I18n.s("disclaimer_msg"))
            .setCancelable(false)
        if (firstLaunch) {
            builder.setPositiveButton(com.streamhub.app.core.I18n.s("disclaimer_accept")) { _, _ ->
                prefs.edit().putBoolean("disclaimer_accepted", true).apply()
                // yasal uyari kabulunden sonra dil secilir
                runCatching { showLanguageDialog() }
            }
            builder.setNegativeButton(com.streamhub.app.core.I18n.s("disclaimer_decline")) { _, _ -> finishAffinity() }
        } else {
            builder.setPositiveButton(com.streamhub.app.core.I18n.s("ok"), null)
        }
        builder.show()
    }

    /** Dil secici: Turkce / English. Secim kalici kaydedilir ve arayuz yeniden kurulur. */
    private fun showLanguageDialog() {
        val current = com.streamhub.app.core.I18n.lang
        val options = arrayOf("Türkçe", "English")
        val codes = arrayOf(com.streamhub.app.core.I18n.TR, com.streamhub.app.core.I18n.EN)
        AlertDialog.Builder(this)
            .setTitle(com.streamhub.app.core.I18n.s("language_title"))
            .setSingleChoiceItems(options, options.indices.firstOrNull { codes[it] == current } ?: 0) { d, which ->
                val chosen = codes[which]
                d.dismiss()
                if (chosen != com.streamhub.app.core.I18n.lang) {
                    com.streamhub.app.core.I18n.set(this, chosen)
                    // tum ekranlar yeni dille yeniden kurulur
                    runCatching { recreate() }
                }
            }
            .show()
    }

    override fun onBackPressed() {
        val nav = findViewById<BottomNavigationView>(R.id.bottom_nav)
        when {
            pageSearch.visibility == View.VISIBLE || pageFavs.visibility == View.VISIBLE ||
                pageSettings.visibility == View.VISIBLE -> {
                nav?.selectedItemId = PAGE_HOME
            }
            else -> super.onBackPressed()
        }
    }
}
