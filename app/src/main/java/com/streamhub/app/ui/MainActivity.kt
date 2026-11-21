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
            runCatching { Ui.toast(this, "Arayüz kurulamadı — hata ekranda gösterildi") }
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
            subtitle = "Eklenti tabanlı video oynatıcı"
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
        nav.menu.add(Menu.NONE, PAGE_HOME, Menu.NONE, "Ana").setIcon(R.drawable.ic_home)
        nav.menu.add(Menu.NONE, PAGE_SEARCH, Menu.NONE, "Arama").setIcon(R.drawable.ic_search)
        nav.menu.add(Menu.NONE, PAGE_FAVS, Menu.NONE, "Favoriler").setIcon(R.drawable.ic_fav)
        nav.menu.add(Menu.NONE, PAGE_SETTINGS, Menu.NONE, "Ayarlar").setIcon(R.drawable.ic_settings)
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
            text = "StreamHub başlatılamadı"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
        })
        root.addView(TextView(ctx).apply {
            text = "Arayüz kurulamadı. Aşağıdaki hatayı ekrandan seçip kopyala ve geliştiriciye gönder:"
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
            text = "📋 Hatayı Kopyala"
            setTextColor(0xFF8b9dff.toInt())
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, 0)
            setOnClickListener {
                val full = t.toString() + "\n" + t.stackTrace.joinToString("\n") { it.toString() }
                (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                    .setPrimaryClip(ClipData.newPlainText("streamhub_error", full))
                Ui.toast(ctx, "Kopyalandı")
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
            .setTitle("Son çökme kaydı")
            .setMessage(crash.take(3500))
            .setPositiveButton("📋 Kopyala") { _, _ ->
                (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                    .setPrimaryClip(ClipData.newPlainText("streamhub_crash", crash))
                mark()
            }
            .setNeutralButton("🗑 Sil") { _, _ -> CrashHandler.clear(this); mark() }
            .setNegativeButton("Kapat") { _, _ -> mark() }
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
            text = "▶ URL ile Oynat"
            setPadding(Ui.dp(ctx, 12), 0, Ui.dp(ctx, 12), 0)
            setOnClickListener { urlPlayDialog() }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(4, 0, 4, 0) })
        actions.addView(MaterialButton(ctx).apply {
            text = "⟳ Yerel Dosya"
            setPadding(Ui.dp(ctx, 12), 0, Ui.dp(ctx, 12), 0)
            setOnClickListener { videoPicker?.launch(arrayOf("video/*")) }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(4, 0, 4, 0) })
        col.addView(actions, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))

        // izlemeye devam et
        val history = Store.history()
        if (history.isNotEmpty()) {
            col.addView(Ui.sectionTitle(ctx, "Izlemeye Devam Et"))
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
                        err.text = "· İçerik bulunamadı"
                        err.visibility = View.VISIBLE
                    } else {
                        inner.adapter = CardAdapter(items, { openResult(it) }, lifecycleScope, app.http)
                    }
                } catch (e: ChallengeException) {
                    showRowError(err, "Bot koruması — dokun: WebView ile aç") {
                        startActivity(Intent(this@MainActivity, WebViewActivity::class.java).putExtra("url", e.pageUrl))
                    }
                } catch (e: Exception) {
                    showRowError(err, "Hata: ${e.message ?: e.javaClass.simpleName} — dokun: yeniden dene") {
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
            text = "🔌 Eklenti kurulu değil"
            setTextColor(Ui.TEXT_MAIN)
            textSize = 18f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        inner.addView(TextView(ctx).apply {
            text = "StreamHub sadece bir oynatıcıdır — kaynak eklemek için eklenti kur. Eklenti, içinde plugin.json olan bir .zip dosyasıdır; derleme gerekmez, eklediğin an çalışır."
            setTextColor(Ui.TEXT_DIM)
            textSize = 13f
            setPadding(0, Ui.dp(ctx, 10), 0, Ui.dp(ctx, 16))
        })
        inner.addView(MaterialButton(ctx).apply {
            text = "＋ Eklenti Ekle (.zip)"
            setOnClickListener { pluginPicker?.launch(arrayOf("application/zip", "application/octet-stream", "application/x-zip-compressed")) }
        }, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, Ui.dp(ctx, 10)) })
        inner.addView(MaterialButton(ctx).apply {
            text = "📖 Eklenti Yapımı (GitHub)"
            setOnClickListener { openUrl(PLUGINS_DOC_URL) }
        }, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, Ui.dp(ctx, 10)) })
        inner.addView(MaterialButton(ctx).apply {
            text = "▶ URL ile Oynat"
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
        val field = TextInputLayout(ctx).apply { hint = "Eklentide ara..." }
        val input = TextInputEditText(ctx).apply {
            setSingleLine(true)
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
        }
        field.addView(input, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))
        page.addView(field, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))

        val results = RecyclerView(ctx).apply { layoutManager = LinearLayoutManager(ctx) }
        page.addView(results, LinearLayout.LayoutParams(-1, 0, 1f))
        val empty = TextView(ctx).apply {
            text = "Yukarıya yaz ve ara. Arama, seçili eklentinin arama kurallarını kullanır."
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
                Ui.toast(ctx, "Önce bir eklenti kur"); return
            }
            empty.visibility = View.GONE
            searchJob?.cancel()
            searchJob = lifecycleScope.launch {
                try {
                    val found = provider.search(q)
                    results.adapter = SearchAdapter(found, { openResult(it) }, lifecycleScope, app.http)
                    if (found.isEmpty()) {
                        empty.text = "Sonuç bulunamadı: $q"
                        empty.visibility = View.VISIBLE
                    }
                } catch (e: ChallengeException) {
                    showErrorPage(empty, e.message ?: "Bot koruması")
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
        view.text = "Hata: $message"
        view.setTextColor(Ui.ERROR_RED)
        view.visibility = View.VISIBLE
    }

    // ---------- FAVORILER ----------

    private fun refreshFavs() {
        pageFavs.removeAllViews()
        val favs = Store.favorites()
        if (favs.isEmpty()) {
            val t = TextView(this).apply {
                text = "⭐ Favori yok\n\nDetay ekranındaki yıldıza dokunarak dizi/video ekle."
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

        col.addView(Ui.sectionTitle(ctx, "Eklentiler"))
        val installed = com.streamhub.app.core.PluginManager.installed(ctx)
        if (installed.isEmpty()) {
            col.addView(listSubtitle("Kurulu eklenti yok. \"Eklenti Ekle\" ile zip yükle."))
        }
        installed.forEach { f ->
            col.addView(settingsRow(null, f.name, "${f.length() / 1024} KB") { })
            col.addView(iconRow(R.drawable.ic_delete, "Kaldır") {
                com.streamhub.app.core.PluginManager.delete(ctx, f.name)
                app.reloadPlugins()
                refreshSettings(); refreshHome()
                snack("Eklenti kaldırıldı")
            })
            col.addView(Ui.divider(ctx))
        }
        col.addView(iconRow(R.drawable.ic_add, "Eklenti Ekle (.zip)") {
            pluginPicker?.launch(arrayOf("application/zip", "application/octet-stream", "application/x-zip-compressed"))
        })
        col.addView(iconRow(R.drawable.ic_book, "Eklenti Yapımı Rehberi (GitHub)") { openUrl(PLUGINS_DOC_URL) })
        col.addView(Ui.divider(ctx))

        col.addView(Ui.sectionTitle(ctx, "Veriler"))
        col.addView(iconRow(R.drawable.ic_delete, "İzleme Geçmişini Temizle") {
            Store.clearHistory(); refreshHome(); snack("Geçmiş temizlendi")
        })
        col.addView(iconRow(R.drawable.ic_delete, "Favorileri Temizle") {
            Store.clearFavorites(); snack("Favoriler temizlendi")
        })
        col.addView(Ui.divider(ctx))

        col.addView(Ui.sectionTitle(ctx, "Hakkında"))
        col.addView(iconRow(R.drawable.ic_fav, "⚖ Yasal Uyarı") { showDisclaimerDialog(false) })
        val crash = CrashHandler.lastCrash(ctx)
        if (crash != null) {
            col.addView(iconRow(R.drawable.ic_bug, "🐞 Son Çökme Kaydı") {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Son çökme kaydı")
                    .setMessage(crash.take(3000))
                    .setPositiveButton("Kopyala") { _, _ ->
                        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                            .setPrimaryClip(ClipData.newPlainText("crash", crash))
                        snack("Kopyalandı")
                    }
                    .setNeutralButton("Sil") { _, _ -> CrashHandler.clear(ctx); refreshSettings() }
                    .setNegativeButton("Kapat", null)
                    .show()
            })
        }
        col.addView(listSubtitle("StreamHub v2.2.1 — Material You\n github.com/Eknux/streamhub"))

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
                    if (app.pluginErrors.isEmpty()) "Eklenti eklendi: $name (${app.providers.size} kaynak yüklü)"
                    else "Eklenti yüklendi ama hata var: ${app.pluginErrors.joinToString("; ").take(120)}"
                )
            }
            .onFailure { Ui.toast(this, "Eklenti eklenemedi: ${it.message}") }
    }

    private fun urlPlayDialog() {
        val ctx = this
        val field = TextInputLayout(ctx).apply { hint = "Video bağlantısı (m3u8 / mp4)" }
        val input = TextInputEditText(ctx).apply { setSingleLine(true) }
        field.addView(input, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))
        AlertDialog.Builder(ctx)
            .setTitle("URL ile Oynat")
            .setView(field)
            .setPositiveButton("Oynat") { _, _ ->
                val url = input.text.toString().trim()
                if (url.startsWith("http")) playDirect(url, url.substringAfterLast('/'), null, null)
                else Ui.toast(ctx, "Geçersiz bağlantı")
            }
            .setNegativeButton("İptal", null)
            .show()
    }

    private fun playLocalFile(uri: Uri) {
        runCatching {
            contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val name = uri.lastPathSegment?.substringAfterLast('/') ?: "Yerel video"
        StreamSession.sources = listOf(VideoSource(name = "Yerel dosya", url = uri.toString(), isM3u8 = false))
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
                ?: VideoSource(name = "Doğrudan", url = url, isM3u8 = url.contains(".m3u8"))
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
            .onFailure { Ui.toast(this, "Tarayıcı açılamadı") }
    }

    private fun snack(message: String) =
        Snackbar.make(contentFrame, message, Snackbar.LENGTH_SHORT).show()

    // ---------- disclaimer ----------

    private fun showDisclaimerDialog(firstLaunch: Boolean) {
        val message = """StreamHub hiçbir video, film veya dizi dosyasını barındırmaz, depolamaz veya yüklemez. Uygulama yalnızca bir oynatıcı ve eklenti altyapısıdır; içeriği eklentiler üzerinden üçüncü taraf sitelerden alan kullanıcıdır ve tüm sorumluluk kullanıcıya aittir.

Uygulama kişisel ve eğitim amaçlı kullanım içindir. Ülkenizin telif yasalarına aykırı kullanımdan tüm sorumluluk kullanıcıya aittir. Hak sahibiyseniz kaldırma talebinizi içeriğin yayınlandığı kaynağa iletebilirsiniz.

Devam ederek bu şartları kabul etmiş olursunuz."""
        val builder = AlertDialog.Builder(this)
            .setTitle("Yasal Uyarı — Devam Etmeden Önce Oku")
            .setMessage(message)
            .setCancelable(false)
        if (firstLaunch) {
            builder.setPositiveButton("Kabul ediyorum") { _, _ ->
                prefs.edit().putBoolean("disclaimer_accepted", true).apply()
            }
            builder.setNegativeButton("Kabul etmiyorum, çık") { _, _ -> finishAffinity() }
        } else {
            builder.setPositiveButton("Tamam", null)
        }
        builder.show()
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
