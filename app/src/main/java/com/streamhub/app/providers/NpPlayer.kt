package com.streamhub.app.providers

import com.streamhub.app.core.SubtitleTrack
import com.streamhub.app.core.VideoSource
import com.streamhub.app.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.security.MessageDigest

/**
 * NP/Pichive oynatici cozucusu (dizilla.la ve benzeri siteler).
 * Kaynak: BTVault (BTcozum) uzerinden @keyiflerolsun / KekikAkademi kazimasi.
 *
 * iframe sayfasindaki window.NP_CONFIG icinde:
 *   sources[]  -> {master: m3u8 url, title: kalite}
 *   subtitles[]-> {url, label}
 *   gate.n     -> opsiyonel SHA-256 proof-of-work kapisi
 *                 (salt oDesGxmweI; h[24..27] birlesik kelimesinin ushr 20 == 0)
 * Cozulen nonce master ve altyazi url'lerine &p=<nonce> olarak eklenir.
 */
object NpPlayer {

    private const val SALT = "oDesGxmweI"
    private const val USER_AGENT = Http.UA

    fun extractConfig(html: String): JSONObject? {
        val start = html.indexOf("window.NP_CONFIG")
        if (start < 0) return null
        val braceStart = html.indexOf('{', start)
        if (braceStart < 0) return null
        val endMarker = html.indexOf("window.localStorage2", braceStart).let {
            if (it < 0) html.indexOf("</script>", braceStart) else it
        }
        if (endMarker < 0) return null
        var json = html.substring(braceStart, endMarker).trim()
        json = json.removeSuffix(";").trim()
        val lastBrace = json.lastIndexOf('}')
        if (lastBrace >= 0) json = json.substring(0, lastBrace + 1)
        return try { JSONObject(json) } catch (_: Exception) { null }
    }

    private suspend fun solveGate(n: String): String = withContext(Dispatchers.Default) {
        val md = MessageDigest.getInstance("SHA-256")
        for (i in 0 until 5_000_000) {
            val nonce = i.toString(36)
            val h = md.digest((n + SALT + nonce + SALT).toByteArray(Charsets.UTF_8))
            val w6 = ((h[24].toInt() and 0xFF) shl 24) or
                ((h[25].toInt() and 0xFF) shl 16) or
                ((h[26].toInt() and 0xFF) shl 8) or
                (h[27].toInt() and 0xFF)
            if ((w6 ushr 20) == 0) return@withContext nonce
        }
        "0"
    }

    suspend fun resolve(http: Http, iframeUrl: String): List<VideoSource> {
        val origin = Regex("""^(https?://[^/]+)""").find(iframeUrl)?.groupValues?.get(1) ?: return emptyList()
        val referer = "$origin/"
        val html = try {
            http.getText(iframeUrl, referer = referer, headers = mapOf("User-Agent" to USER_AGENT))
        } catch (_: Exception) {
            return emptyList()
        }
        val config = extractConfig(html) ?: return emptyList()

        val gateN = config.optJSONObject("gate")?.optString("n").orEmpty()
        val suffix = if (gateN.isNotBlank()) "&p=${solveGate(gateN)}" else ""

        val subtitles = mutableListOf<SubtitleTrack>()
        config.optJSONArray("subtitles")?.let { subs ->
            for (i in 0 until subs.length()) {
                val sub = subs.optJSONObject(i) ?: continue
                val url = sub.optString("url").takeIf { it.isNotBlank() } ?: continue
                val label = sub.optString("label").ifBlank { "Altyazi" }
                subtitles += SubtitleTrack(label, if (suffix.isEmpty()) url else url + suffix)
            }
        }

        val sources = mutableListOf<VideoSource>()
        config.optJSONArray("sources")?.let { arr ->
            for (i in 0 until arr.length()) {
                val src = arr.optJSONObject(i) ?: continue
                val master = src.optString("master").takeIf { it.isNotBlank() } ?: continue
                val quality = src.optString("title").ifBlank { "NP" }
                sources += VideoSource(
                    name = "NP · $quality",
                    url = if (suffix.isEmpty()) master else master + suffix,
                    referer = referer,
                    headers = mapOf("User-Agent" to USER_AGENT),
                    isM3u8 = true,
                    subtitles = subtitles,
                )
            }
        }
        return sources
    }
}
