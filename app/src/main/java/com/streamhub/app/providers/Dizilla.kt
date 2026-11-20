package com.streamhub.app.providers

import com.streamhub.app.core.Episode
import com.streamhub.app.core.HomeSection
import com.streamhub.app.core.Provider
import com.streamhub.app.core.SearchResult
import com.streamhub.app.core.SeriesInfo
import com.streamhub.app.core.VideoSource
import com.streamhub.app.net.Http
import org.jsoup.nodes.Element

/**
 * dizilla.la gomulu saglayici.
 * URL kaliplari (eski Tailwind + yeni Next.js temada ayni):
 *   dizi:   /dizi/<slug>
 *   bolum:  /<slug>-<sezon>-sezon-<bolum>-bolum
 *   arama:  /ara?q=<sorgu>
 * Oynatici: iframe.php?v= -> NP_CONFIG (NpPlayer) | ContentX ailesi | sayfa ici m3u8.
 */
class Dizilla(private val http: Http) : Provider {

    override val name = "Dizilla"
    private val mainUrl = "https://dizilla.la"

    private val epRegex = Regex("""([A-Za-z0-9\-]+)-(\d+)-sezon-(\d+)-bolum""")

    override val sections = listOf(
        HomeSection("Son Bölümler", "$mainUrl/tum-bolumler"),
        HomeSection("Dublaj Bölümler", "$mainUrl/dublaj-bolumler"),
        HomeSection("Aksiyon", "$mainUrl/dizi-turu/aksiyon"),
        HomeSection("Bilim Kurgu", "$mainUrl/dizi-turu/bilim-kurgu"),
        HomeSection("Komedi", "$mainUrl/dizi-turu/komedi"),
        HomeSection("Aile", "$mainUrl/dizi-turu/aile"),
        HomeSection("Romantik", "$mainUrl/dizi-turu/romantik"),
    )

    private fun slugToTitle(slug: String): String =
        slug.replace('-', ' ').replace(Regex("\\s+"), " ").trim()
            .replaceFirstChar { it.uppercase() }

    private fun parseSeriesAnchor(el: Element): SearchResult? {
        val hrefRaw = el.attr("href").trim()
        if (!hrefRaw.contains("dizi/")) return null
        val href = if (hrefRaw.startsWith("http")) hrefRaw else "$mainUrl/${hrefRaw.trimStart('/')}"
        val img = el.selectFirst("img")
        val title = img?.attr("alt")?.trim()?.removeSuffix(" izle")?.trim()?.takeIf { it.isNotEmpty() }
            ?: el.text().trim().takeIf { it.isNotEmpty() && it.length < 80 }
            ?: slugToTitle(href.substringAfterLast('/'))
        val poster = el.attr("abs:src").takeIf { it.isNotBlank() }
            ?: img?.attr("abs:src")?.takeIf { it.isNotBlank() }
            ?: img?.attr("data-src")?.takeIf { it.isNotBlank() }
        return SearchResult(title, href, poster, name)
    }

    override suspend fun section(section: HomeSection): List<SearchResult> {
        val doc = http.getDocument(section.url, referer = mainUrl)
        val out = LinkedHashMap<String, SearchResult>()
        doc.select("a[href*='dizi/']").forEach { el ->
            parseSeriesAnchor(el)?.let { out.putIfAbsent(it.url, it) }
        }
        return out.values.toList()
    }

    override suspend fun search(query: String): List<SearchResult> {
        val q = query.trim()
        val out = LinkedHashMap<String, SearchResult>()

        // 1) sunucu tarafi arama (yeni tema)
        if (q.isNotEmpty()) {
            runCatching {
                val doc = http.getDocument("$mainUrl/ara?q=${java.net.URLEncoder.encode(q, "UTF-8")}", referer = mainUrl)
                doc.select("a[href*='dizi/']").forEach { el ->
                    parseSeriesAnchor(el)?.let { out.putIfAbsent(it.url, it) }
                }
            }
        }

        // 2) yedek: arsiv sayfalarindan istemci tarafi filtre (eski tema)
        if (out.isEmpty()) {
            listOf("$mainUrl/arsiv", "$mainUrl/yabanci-dizi-izle", mainUrl).forEach { page ->
                runCatching {
                    val doc = http.getDocument(page, referer = mainUrl)
                    doc.select("a[href*='dizi/']").forEach { el ->
                        val item = parseSeriesAnchor(el) ?: return@forEach
                        val qq = q.lowercase()
                        if (qq.isNotEmpty() &&
                            !item.title.lowercase().contains(qq) &&
                            !item.url.lowercase().contains(qq)
                        ) return@forEach
                        out.putIfAbsent(item.url, item)
                    }
                }
            }
        }
        return out.values.toList()
    }

    private fun addEpisodesFrom(doc: org.jsoup.nodes.Document, episodes: MutableList<Episode>) {
        val anchors = doc.select("article.ep4 a.ep4-open").ifEmpty {
            doc.select("a[href*=sezon-][href*=-bolum]")
        }
        anchors.forEach { a ->
            val href = a.attr("abs:href").takeIf { it.isNotBlank() } ?: return@forEach
            val m = epRegex.find(href) ?: return@forEach
            val epSeason = m.groupValues[2].toIntOrNull() ?: 1
            val epNum = m.groupValues[3].toIntOrNull() ?: return@forEach
            val epName = a.selectFirst("span.ep4-t")?.text()?.trim()
                ?: a.text().trim().takeIf { it.isNotEmpty() && it.length < 120 }
            episodes += Episode(epName, href, epSeason, epNum)
        }
    }

    override suspend fun load(url: String): SeriesInfo? {
        val doc = http.getDocument(url, referer = mainUrl)

        val title = doc.selectFirst("div.page-top h1")?.text()?.trim()
            ?: doc.selectFirst("h1")?.text()?.trim()?.removeSuffix(" izle")?.trim()
            ?: slugToTitle(url.substringAfterLast('/').removePrefix("dizi/"))
        val poster = doc.selectFirst("div.page-top img")?.attr("abs:src")?.takeIf { it.isNotBlank() }
            ?: doc.selectFirst("div.page-top img")?.attr("data-src")?.takeIf { it.isNotBlank() }
            ?: doc.selectFirst("img[src*=macellan]")?.attr("abs:src")?.takeIf { it.isNotBlank() }
        val description = doc.selectFirst("div.mv-det-p")?.text()?.trim()
            ?: doc.selectFirst("meta[name=description]")?.attr("content")?.trim()
            ?: doc.selectFirst("meta[property=og:description]")?.attr("content")?.trim()

        val jsonLd = doc.select("script[type=application/ld+json]").joinToString("\n") { it.data() }
        val numberOfSeasons = Regex(""""numberOfSeasons"\s*:\s*(\d+)""")
            .find(jsonLd)?.groupValues?.get(1)?.toIntOrNull() ?: 1

        val slug = url.substringAfterLast('/').removePrefix("dizi/").trim('/')
        val episodes = mutableListOf<Episode>()

        // 1) dizi sayfasinda bolum listesi olabilir (yeni tema)
        addEpisodesFrom(doc, episodes)

        // 2) sezon sayfalari (eski tema): /<slug>-<sezon>-sezon
        if (episodes.isEmpty()) {
            var misses = 0
            for (season in 1..maxOf(numberOfSeasons, 1).coerceAtMost(25)) {
                val seasonDoc = runCatching {
                    http.getDocument("$mainUrl/$slug-$season-sezon", referer = url)
                }.getOrNull()
                if (seasonDoc == null) { misses++; if (misses >= 2) break; continue }
                val before = episodes.size
                addEpisodesFrom(seasonDoc, episodes)
                if (episodes.size == before) { misses++; if (misses >= 2) break } else misses = 0
            }
        }

        val unique = episodes.distinctBy { it.url }
        return SeriesInfo(title, url, poster, description, unique)
    }

    override suspend fun loadLinks(episodeUrl: String): List<VideoSource> {
        val html = http.getText(episodeUrl, referer = mainUrl)
        val sources = mutableListOf<VideoSource>()

        // 1) NP/Pichive oynaticilari
        Regex("""https://[A-Za-z0-9.\-]+/iframe\.php\?v=[A-Za-z0-9_\-]+""")
            .findAll(html).map { it.value }.distinct().forEach { u ->
                sources += NpPlayer.resolve(http, u)
            }

        // 2) sayfada dogrudan gecen m3u8'ler (RSC/__NEXT_DATA__ payload dahil)
        Regex("""https?://[^"'\\\s<>]+\.m3u8[^"'\\\s<>]*""")
            .findAll(html).map { it.value }.distinct().take(6).forEach { m ->
                sources += VideoSource(
                    name = "Dizilla · doğrudan",
                    url = m,
                    referer = mainUrl,
                    isM3u8 = true,
                )
            }

        // 3) gomulu iframe'ler: ContentX ailesi hostlari
        Regex("""<iframe[^>]+src=["']([^"']+)["']""")
            .findAll(html).map { it.groupValues[1] }.distinct().take(6).forEach { raw ->
                val src = if (raw.startsWith("http")) raw else {
                    if (raw.startsWith("//")) "https:$raw"
                    else if (raw.startsWith("/")) "$mainUrl$raw"
                    else raw
                }
                if (ContentX.handles(src)) {
                    sources += ContentX.resolve(http, src, episodeUrl)
                }
            }

        return sources
    }
}
