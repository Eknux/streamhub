package com.streamhub.app.net

import android.content.Context
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** Site bot korumasina takildiginda firlatilir; UI "WebView ile ac" butonu gosterir. */
class ChallengeException(val pageUrl: String, message: String) : Exception(message)

class Http(context: Context) {

    companion object {
        const val UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
        private val CHALLENGE_MARKERS = listOf(
            "Just a moment", "One moment, please", "Attention Required",
            "Checking your browser", "cf-browser-verification", "Please wait while your request is being verified"
        )
    }

    private val prefs = context.getSharedPreferences("streamhub_cookies", Context.MODE_PRIVATE)
    private val cookieStore = ConcurrentHashMap<String, MutableList<Cookie>>()

    init {
        // kalici cerezleri geri yukle
        runCatching {
            val json = JSONObject(prefs.getString("cookies", "{}") ?: "{}")
            for (domain in json.keys()) {
                val obj = json.optJSONObject(domain) ?: continue
                val list = mutableListOf<Cookie>()
                for (name in obj.keys()) {
                    runCatching {
                        list += Cookie.Builder().name(name).value(obj.getString(name))
                            .domain(domain).path("/").build()
                    }
                }
                if (list.isNotEmpty()) cookieStore[domain] = list
            }
        }
    }

    private fun persist() {
        runCatching {
            val json = JSONObject()
            for ((domain, cookies) in cookieStore) {
                val obj = JSONObject()
                for (c in cookies) obj.put(c.name, c.value)
                json.put(domain, obj)
            }
            prefs.edit().putString("cookies", json.toString()).apply()
        }
    }

    val client: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(object : CookieJar {
            override fun loadForRequest(url: HttpUrl): List<Cookie> =
                cookieStore[url.host] ?: emptyList()

            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                val list = cookieStore.getOrPut(url.host) { mutableListOf() }
                synchronized(list) {
                    for (c in cookies) {
                        list.removeAll { it.name == c.name }
                        list.add(c)
                    }
                }
                persist()
            }
        })
        .followRedirects(true)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .build()

    /** WebView ekranindan gelen cerezleri kalici depoya yazar. */
    fun importCookies(domain: String, cookieHeader: String) {
        val list = cookieStore.getOrPut(domain) { mutableListOf() }
        synchronized(list) {
            for (kv in cookieHeader.split("; ")) {
                val i = kv.indexOf('=')
                if (i <= 0) continue
                val name = kv.substring(0, i).trim()
                val value = kv.substring(i + 1).trim()
                if (name.isEmpty()) continue
                list.removeAll { it.name == name }
                runCatching {
                    list += Cookie.Builder().name(name).value(value).domain(domain).path("/").build()
                }
            }
        }
        persist()
    }

    fun currentCookieHeader(url: String): String? {
        val host = url.toHttpUrlOrNull()?.host ?: return null
        val cookies = cookieStore[host] ?: return null
        return cookies.joinToString("; ") { "${it.name}=${it.value}" }
    }

    suspend fun getText(
        url: String,
        referer: String? = null,
        headers: Map<String, String> = emptyMap(),
    ): String = withContext(Dispatchers.IO) {
        val builder = Request.Builder().url(url)
            .header("User-Agent", UA)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "tr,tr-TR;q=0.9,en;q=0.6")
        if (referer != null) builder.header("Referer", referer)
        for ((k, v) in headers) builder.header(k, v)

        val response: Response = try {
            client.newCall(builder.build()).execute()
        } catch (e: Exception) {
            throw Exception("Ag hatasi: ${e.message ?: e.javaClass.simpleName}")
        }

        response.use { r ->
            val body = r.body?.string().orEmpty()
            if (!r.isSuccessful) {
                throw Exception("HTTP ${r.code} — $url")
            }
            if (CHALLENGE_MARKERS.any { body.contains(it, ignoreCase = true) }) {
                throw ChallengeException(url, "Site bot korumasi devrede")
            }
            if (body.isBlank()) throw Exception("Bos yanit — $url")
            body
        }
    }

    suspend fun getDocument(
        url: String,
        referer: String? = null,
        headers: Map<String, String> = emptyMap(),
    ): Document {
        val text = getText(url, referer, headers)
        return Jsoup.parse(text, url)
    }
}
