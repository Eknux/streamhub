package com.streamhub.app.core

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** Favoriler ve izleme gecmisi — SharedPreferences uzerinde JSON olarak saklanir. */
object Store {

    data class HistoryItem(
        val title: String,
        val seriesUrl: String?,
        val episodeUrl: String,
        val posterUrl: String?,
        val positionMs: Long,
        val durationMs: Long,
        val time: Long,
    )

    data class FavItem(
        val title: String,
        val url: String,
        val posterUrl: String?,
        val providerName: String,
        val direct: Boolean,
        val time: Long,
    )

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.getSharedPreferences("streamhub_store", Context.MODE_PRIVATE)
    }

    // ---------- gecmis ----------

    fun history(): List<HistoryItem> {
        val arr = runCatching { JSONArray(prefs?.getString("history", "[]").orEmpty()) }.getOrNull()
            ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            HistoryItem(
                title = o.optString("title"),
                seriesUrl = o.optString("seriesUrl").blankToNull(),
                episodeUrl = o.optString("episodeUrl"),
                posterUrl = o.optString("poster").blankToNull(),
                positionMs = o.optLong("pos"),
                durationMs = o.optLong("dur"),
                time = o.optLong("time"),
            )
        }
    }

    fun addHistory(item: HistoryItem) {
        if (item.episodeUrl.isBlank()) return
        val list = history().toMutableList()
        list.removeAll { it.episodeUrl == item.episodeUrl }
        list.add(0, item)
        while (list.size > 40) list.removeAt(list.lastIndex)
        saveHistory(list)
    }

    fun positionFor(episodeUrl: String): Long =
        history().firstOrNull { it.episodeUrl == episodeUrl }?.positionMs ?: 0L

    fun removeHistory(episodeUrl: String) = saveHistory(history().filterNot { it.episodeUrl == episodeUrl })

    fun clearHistory() = saveHistory(emptyList())

    private fun saveHistory(list: List<HistoryItem>) {
        val arr = JSONArray()
        list.forEach { h ->
            arr.put(JSONObject().apply {
                put("title", h.title)
                put("seriesUrl", h.seriesUrl ?: "")
                put("episodeUrl", h.episodeUrl)
                put("poster", h.posterUrl ?: "")
                put("pos", h.positionMs)
                put("dur", h.durationMs)
                put("time", h.time)
            })
        }
        prefs?.edit()?.putString("history", arr.toString())?.apply()
    }

    // ---------- favoriler ----------

    fun favorites(): List<FavItem> {
        val arr = runCatching { JSONArray(prefs?.getString("favs", "[]").orEmpty()) }.getOrNull()
            ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            FavItem(
                title = o.optString("title"),
                url = o.optString("url"),
                posterUrl = o.optString("poster").blankToNull(),
                providerName = o.optString("provider"),
                direct = o.optBoolean("direct"),
                time = o.optLong("time"),
            )
        }
    }

    fun isFavorite(url: String): Boolean = favorites().any { it.url == url }

    /** Yeni durumu dondurur: true = favoriye eklendi, false = cikarildi. */
    fun toggleFavorite(item: FavItem): Boolean {
        val list = favorites().toMutableList()
        val existing = list.firstOrNull { it.url == item.url }
        val added: Boolean
        if (existing != null) {
            list.remove(existing); added = false
        } else {
            list.add(0, item); added = true
        }
        val arr = JSONArray()
        list.forEach { f ->
            arr.put(JSONObject().apply {
                put("title", f.title)
                put("url", f.url)
                put("poster", f.posterUrl ?: "")
                put("provider", f.providerName)
                put("direct", f.direct)
                put("time", f.time)
            })
        }
        prefs?.edit()?.putString("favs", arr.toString())?.apply()
        return added
    }

    fun clearFavorites() {
        prefs?.edit()?.putString("favs", "[]")?.apply()
    }
}
