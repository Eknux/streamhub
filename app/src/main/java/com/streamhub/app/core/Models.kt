package com.streamhub.app.core

/** Tum saglayicilarin (JSON eklentileri dahil) gerceklestirecegi sozlesme. */
interface Provider {
    val name: String
    val description: String?
    val sections: List<HomeSection>

    suspend fun section(section: HomeSection): List<SearchResult>
    suspend fun search(query: String): List<SearchResult>
    suspend fun load(url: String): SeriesInfo?
    suspend fun loadLinks(episodeUrl: String): List<VideoSource>

    /** Dogrudan video baglantisi (statik eklenti girisi, URL oynatma) icin kaynak uretir. */
    suspend fun directSource(url: String): VideoSource? =
        VideoSource(name = "Doğrudan", url = url, isM3u8 = url.contains(".m3u8"))
}

data class HomeSection(val title: String, val url: String)

data class SearchResult(
    val title: String,
    val url: String,
    val posterUrl: String? = null,
    val providerName: String,
    val directVideo: Boolean = false,
)

data class Episode(
    val title: String?,
    val url: String,
    val season: Int?,
    val number: Int?,
)

data class SeriesInfo(
    val title: String,
    val url: String,
    val posterUrl: String? = null,
    val description: String? = null,
    val episodes: List<Episode>,
)

data class SubtitleTrack(val label: String, val url: String)

data class VideoSource(
    val name: String,
    val url: String,
    val referer: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val isM3u8: Boolean = true,
    val subtitles: List<SubtitleTrack> = emptyList(),
)

internal fun String?.blankToNull(): String? = this?.takeIf { it.isNotBlank() }
