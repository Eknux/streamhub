package com.streamhub.app.core

import com.streamhub.app.net.Http
import com.streamhub.app.providers.ContentX
import com.streamhub.app.providers.NpPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLEncoder

/**
 * Derlemesiz eklenti motoru: eklenti, zip icindeki bir `plugin.json` dosyasidir
 * (dokumantasyon: PLUGINS.md). Iki cesit:
 *
 *  kind=static -> "entries" listesindeki dogrudan video linkleri (tarama yok)
 *  kind=site   -> CSS secicileriyle site tarayan tanimlar
 *
 * Video cikarmada gomulu cozuculer otomatik devreye girer:
 *  - NP_CONFIG (window.NP_CONFIG -> m3u8 + proof-of-work kapisi)
 *  - ContentX ailesi iframeleri
 *  - plugin.json'da tanimlanan urlRegexes
 */
class JsonProvider(private val http: Http, private val manifest: JSONObject) : Provider {

    override val name: String = manifest.optString("name").ifBlank { "Adsız Eklenti" }
    override val description: String? = manifest.optString("description").blankToNull()
    private val baseUrl: String = manifest.optString("baseUrl").trimEnd('/')
    private val kind: String = manifest.optString("kind").ifBlank {
        if (manifest.has("entries")) "static" else "site"
    }

    // ---------- tanim kutuphaneleri ----------

    private data class CardDef(
        val cardSelector: String,
        val titleSelector: String?,
        val titleAttr: String?,
        val posterSelector: String?,
        val posterAttr: String?,
        val linkAttr: String?,
    )

    private fun cardDef(obj: JSONObject?): CardDef? = obj?.let {
        CardDef(
            cardSelector = it.optString("cardSelector").ifBlank { "a[href]" },
            titleSelector = it.optString("titleSelector").blankToNull(),
            titleAttr = it.optString("titleAttr").blankToNull(),
            posterSelector = it.optString("posterSelector").blankToNull(),
            posterAttr = it.optString("posterAttr").blankToNull(),
            linkAttr = it.optString("linkAttr").blankToNull(),
        )
    }

    private val sectionDefs: List<Pair<HomeSection, CardDef>> = run {
        val arr = manifest.optJSONArray("sections")
        (0 until (arr?.length() ?: 0)).mapNotNull { i ->
            val s = arr?.optJSONObject(i) ?: return@mapNotNull null
            val url = s.optString("url")
            if (url.isBlank()) return@mapNotNull null
            HomeSection(
                s.optString("title").ifBlank { "Bölüm" },
                if (url.startsWith("http")) url else fixUrl(url),
            ) to cardDef(s)!!
        }
    }

    override val sections: List<HomeSection> =
        if (kind == "static") listOf(HomeSection("Videolar", STATIC_URL))
        else sectionDefs.map { it.first }

    private val entryList: List<SearchResult> by lazy {
        val arr = manifest.optJSONArray("entries")
        (0 until (arr?.length() ?: 0)).mapNotNull { i ->
            val e = arr?.optJSONObject(i) ?: return@mapNotNull null
            val url = e.optString("url")
            if (url.isBlank()) return@mapNotNull null
            SearchResult(
                title = e.optString("title").ifBlank { url.substringAfterLast('/') },
                url = url,
                posterUrl = e.optString("poster").blankToNull(),
                providerName = name,
                directVideo = true,
            )
        }
    }

    private val searchDef: CardDef? = cardDef(manifest.optJSONObject("search"))
    private val searchUrl: String? = manifest.optJSONObject("search")?.optString("url")?.blankToNull()

    // ---------- yardimcilar ----------

    private fun fixUrl(path: String): String =
        if (path.startsWith("http")) path
        else baseUrl + (if (path.startsWith("/")) path else "/$path")

    private fun headersOf(playerDef: JSONObject?): Map<String, String> {
        val out = mutableMapOf<String, String>()
        val h = playerDef?.optJSONObject("headers") ?: return out
        for (key in h.keys()) out[key] = h.optString(key)
        return out
    }

    private val playerDef: JSONObject? get() = manifest.optJSONObject("player")

    private fun posterFrom(el: Element, def: CardDef): String? {
        val img = def.posterSelector?.let { el.selectFirst(it) } ?: el.selectFirst("img")
            ?: return null
        val candidates = listOf(def.posterAttr, "data-src", "data-original", "abs:src", "src")
        val raw = candidates.mapNotNull { a -> a?.let { img.attr(it) }.blankToNull() }.firstOrNull()
            ?: return null
        return fixUrl(raw)
    }

    private fun titleFrom(el: Element, def: CardDef, fallback: String): String {
        def.titleSelector?.let { sel ->
            el.selectFirst(sel)?.text()?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        }
        def.titleAttr?.let { attr ->
            el.attr(attr).trim().takeIf { it.isNotEmpty() }?.let { return it }
        }
        el.text().trim().takeIf { it.isNotEmpty() && it.length < 100 }?.let { return it }
        return fallback
    }

    private fun parseCards(doc: Document, def: CardDef): List<SearchResult> =
        doc.select(def.cardSelector).mapNotNull { el ->
            val link = def.linkAttr?.let { el.attr(it).blankToNull() }
                ?: el.attr("abs:href").blankToNull()
                ?: el.selectFirst("a[href]")?.attr("abs:href")?.blankToNull()
                ?: return@mapNotNull null
            val abs = fixUrl(link)
            val title = titleFrom(el, def, abs.substringAfterLast('/'))
            SearchResult(title, abs, posterFrom(el, def), name)
        }.distinctBy { it.url }

    // ---------- Provider ----------

    override suspend fun section(section: HomeSection): List<SearchResult> = withContext(Dispatchers.IO) {
        if (kind == "static" || section.url == STATIC_URL) return@withContext entryList
        val def = sectionDefs.firstOrNull { it.first.url == section.url }?.second
            ?: return@withContext emptyList()
        runCatching { parseCards(http.getDocument(section.url, referer = baseUrl), def) }
            .getOrElse { throw it }
    }

    override suspend fun search(query: String): List<SearchResult> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (kind == "static") {
            return@withContext if (q.isEmpty()) entryList
            else entryList.filter { it.title.contains(q, ignoreCase = true) }
        }
        val def = searchDef ?: return@withContext emptyList()
        val url = searchUrl ?: return@withContext emptyList()
        val target = url.replace("{query}", URLEncoder.encode(q, "UTF-8"))
        runCatching { parseCards(http.getDocument(fixUrl(target), referer = baseUrl), def) }
            .getOrElse { throw it }
    }

    override suspend fun load(url: String): SeriesInfo? = withContext(Dispatchers.IO) {
        val detail = manifest.optJSONObject("detail") ?: return@withContext null
        val doc = http.getDocument(url, referer = baseUrl)

        val title = detail.optString("titleSelector").blankToNull()
            ?.let { doc.selectFirst(it)?.text()?.trim()?.takeIf { t -> t.isNotEmpty() } }
            ?: doc.selectFirst("h1")?.text()?.trim()?.takeIf { t -> t.isNotEmpty() }
            ?: url.substringAfterLast('/')

        val poster = detail.optString("posterSelector").blankToNull()?.let { sel ->
            doc.selectFirst(sel)?.let { posterFrom(it.parent() ?: it, CardDef("", null, null, null, detail.optString("posterAttr").blankToNull(), null)) }
        } ?: doc.selectFirst("img")?.let { posterFrom(it, CardDef("", null, null, null, null, null)) }

        val description = detail.optString("descriptionSelector").blankToNull()
            ?.let { doc.selectFirst(it)?.text()?.trim() }
            ?: doc.selectFirst("meta[name=description]")?.attr("content")?.trim()
            ?: doc.selectFirst("meta[property=og:description]")?.attr("content")?.trim()

        val epSelector = detail.optString("episodeSelector").blankToNull()
            ?: return@withContext SeriesInfo(title, url, poster, description, emptyList())
        val epUrlAttr = detail.optString("episodeUrlAttr").blankToNull()
        val epTitleAttr = detail.optString("episodeTitleAttr").blankToNull()

        val episodes = doc.select(epSelector).mapIndexedNotNull { index, a ->
            val href = epUrlAttr?.let { a.attr(it).blankToNull() } ?: a.attr("abs:href").blankToNull()
                ?: return@mapIndexedNotNull null
            val epTitle = epTitleAttr?.let { a.attr(it).trim().blankToNull() }
                ?: a.text().trim().takeIf { it.isNotEmpty() && it.length < 120 }
            val (season, number) = parseNumbering(href, a.text(), index)
            Episode(epTitle, fixUrl(href), season, number)
        }.distinctBy { it.url }

        SeriesInfo(title, url, poster, description, episodes)
    }

    private fun parseNumbering(href: String, text: String, index: Int): Pair<Int?, Int?> {
        val source = "$href $text"
        Regex("""s(\d+)[e\-.](\d+)""", RegexOption.IGNORE_CASE).find(source)?.let {
            return it.groupValues[1].toIntOrNull() to it.groupValues[2].toIntOrNull()
        }
        Regex("""(\d+)\s*[- ]*sezon.*?(\d+)\s*[- ]*bolum""", RegexOption.IGNORE_CASE).find(source)?.let {
            return it.groupValues[1].toIntOrNull() to it.groupValues[2].toIntOrNull()
        }
        Regex("""(?:bolum|bölüm|episode|ep)[\s\-.]*(\d+)""", RegexOption.IGNORE_CASE).find(source)?.let {
            return 1 to it.groupValues[1].toIntOrNull()
        }
        return 1 to (index + 1)
    }

    override suspend fun loadLinks(episodeUrl: String): List<VideoSource> = withContext(Dispatchers.IO) {
        val html = http.getText(episodeUrl, referer = baseUrl)
        val headers = headersOf(playerDef)
        val referer = headers["Referer"] ?: baseUrl.takeIf { it.isNotBlank() }
        val out = mutableListOf<VideoSource>()

        // 1) eklentinin tanimladigi regexler
        val regexes = playerDef?.optJSONArray("urlRegexes")
        for (i in 0 until (regexes?.length() ?: 0)) {
            val pattern = regexes?.optString(i) ?: continue
            runCatching { Regex(pattern) }.getOrNull()?.findAll(html)?.forEach { m ->
                val v = m.value
                out += VideoSource(
                    name = "Kaynak ${out.size + 1}",
                    url = v,
                    referer = referer,
                    headers = headers,
                    isM3u8 = v.contains(".m3u8"),
                )
            }
        }

        // 2) sayfada gomulu NP_CONFIG oynaticisi
        if (html.contains("window.NP_CONFIG")) {
            runCatching { NpPlayer.resolve(http, episodeUrl) }.getOrNull()?.let { out += it }
        }

        // 3) iframe secicisi (NP / ContentX otomatik tanima)
        val iframeSel = playerDef?.optString("iframeSelector")?.blankToNull()
        if (iframeSel != null) {
            val src = Jsoup.parse(html, episodeUrl).selectFirst(iframeSel)
                ?.attr("abs:src")?.blankToNull()
            if (src != null) {
                if (src.contains("/iframe.php?v=")) {
                    runCatching { NpPlayer.resolve(http, src) }.getOrNull()?.let { out += it }
                }
                if (ContentX.handles(src)) {
                    runCatching { ContentX.resolve(http, src, episodeUrl) }.getOrNull()?.let { out += it }
                }
            }
        }

        // 4) yedek: sayfada gecen tum m3u8/mp4 linkleri
        if (out.isEmpty()) {
            Regex("""https?://[^"'\\\s<>]+\.(?:m3u8|mp4)[^"'\\\s<>]*""").findAll(html)
                .map { it.value }.distinct().take(5).forEach { v ->
                    out += VideoSource("Doğrudan ${out.size + 1}", v, referer, headers, v.contains(".m3u8"))
                }
        }
        out.distinctBy { it.url }
    }

    override suspend fun directSource(url: String): VideoSource? = withContext(Dispatchers.IO) {
        val headers = headersOf(playerDef)
        VideoSource(
            name = name,
            url = url,
            referer = headers["Referer"] ?: baseUrl.takeIf { it.isNotBlank() },
            headers = headers,
            isM3u8 = url.contains(".m3u8"),
        )
    }

    companion object {
        const val STATIC_URL = "static://entries"
    }
}
