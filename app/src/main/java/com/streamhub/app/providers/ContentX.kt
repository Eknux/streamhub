package com.streamhub.app.providers

import com.streamhub.app.core.SubtitleTrack
import com.streamhub.app.core.VideoSource
import com.streamhub.app.net.Http

/**
 * ContentX ailesi (contentx.me, hotlinger.com, playru.net, pichive.online ve
 * "four." varyantlari). window.openPlayer('<id>') -> source2.php?v=<id> -> m3u8.
 * Kaynak: BTVault uzerinden @keyiflerolsun / KekikAkademi kodu.
 */
object ContentX {

    val HOSTS = listOf(
        "contentx.me", "four.contentx.me",
        "hotlinger.com", "four.hotlinger.com",
        "playru.net", "four.playru.net",
        "pichive.online", "four.pichive.online",
    )

    fun handles(url: String): Boolean = HOSTS.any { url.contains("://$it") || url.contains(".$it/") || url.startsWith("https://$it") }

    private suspend fun resolveOne(http: Http, base: String, videoId: String, referer: String?): VideoSource? {
        val srcPage = try {
            http.getText("$base/source2.php?v=$videoId", referer = referer)
        } catch (_: Exception) {
            return null
        }
        val file = Regex(""""file":"([^"]+)"""").find(srcPage)?.groups?.get(1)?.value ?: return null
        val m3u = file.replace("\\", "")
        return VideoSource(
            name = base.substringAfter("://").substringBefore('/'),
            url = m3u,
            referer = referer,
            isM3u8 = true,
        )
    }

    suspend fun resolve(http: Http, iframeUrl: String, referer: String?): List<VideoSource> {
        val origin = Regex("""^(https?://[^/]+)""").find(iframeUrl)?.groupValues?.get(1) ?: return emptyList()
        val page = try {
            http.getText(iframeUrl, referer = referer ?: "$origin/")
        } catch (_: Exception) {
            return emptyList()
        }
        val id = Regex("""window\.openPlayer\('([^']+)'""").find(page)?.groups?.get(1)?.value
            ?: return emptyList()

        val out = mutableListOf<VideoSource>()
        resolveOne(http, origin, id, referer ?: "$origin/")?.let { out += it }

        // Turkce dublaj varyanti varsa onu da ekle
        Regex(""","([^']+)","Türkçe"""").find(page)?.groups?.get(1)?.value?.let { dubId ->
            resolveOne(http, origin, dubId, referer ?: "$origin/")?.let {
                out += it.copy(name = "${it.name} Türkçe Dublaj")
            }
        }

        // altyazilar
        Regex(""""file":"([^"]+)","label":"([^"]+)"""").findAll(page).forEach { m ->
            val subUrl = m.groupValues[1].replace("\\", "")
            val label = m.groupValues[2]
                .replace("\\u0131", "ı").replace("\\u0130", "İ")
                .replace("\\u00fc", "ü").replace("\\u00e7", "ç")
            if (out.isNotEmpty()) {
                out[0] = out[0].copy(subtitles = out[0].subtitles + SubtitleTrack(label, subUrl))
            }
        }
        return out
    }
}
