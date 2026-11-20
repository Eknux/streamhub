package com.streamhub.app.core

import android.content.Context
import android.util.Log
import com.streamhub.app.net.Http
import dalvik.system.DexClassLoader
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipFile

/**
 * Basit eklenti sistemi: uygulamanin harici dosya klasorundeki `plugins/` altina
 * atilan .zip dosyalarini acilista yukler.
 *
 * Zip bicimi:
 *   manifest.json  -> {"providers":[{"name":"Ornek","class":"com.ornek.OrnekProvider"}]}
 *   classes.dex    -> derlenmis dex; siniflar com.streamhub.app.core.Provider
 *                     arayuzunu gerceklestirir ve (http: Http) parametreli
 *                     kurucu tanimlar.
 *
 * (.apk / .dex dogrudan atilirsa manifest yoksa "<DosyaAdi>Provider" ya da
 *  "<DosyaAdi>Plugin" sinifi denenir.)
 */
object PluginLoader {

    private const val TAG = "StreamHub"

    fun pluginsDir(context: Context): File {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        val dir = File(base, "plugins")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun loadPlugins(context: Context, http: Http): List<Provider> {
        val out = mutableListOf<Provider>()
        val dir = pluginsDir(context)
        val optDir = File(context.cacheDir, "plugin_opt").also { it.mkdirs() }
        val extractDir = File(context.cacheDir, "plugin_extract").also {
            it.deleteRecursively(); it.mkdirs()
        }

        val files = dir.listFiles()?.filter {
            it.isFile && (it.extension == "zip" || it.extension == "apk" || it.extension == "dex")
        }?.sortedBy { it.name } ?: return out

        for (f in files) {
            runCatching { loadOne(f, context, http, optDir, extractDir) }
                .onSuccess { out += it }
                .onFailure { e ->
                    Log.w(TAG, "Eklenti yuklenemedi: ${f.name}", e)
                }
        }
        return out
    }

    private fun loadOne(
        file: File,
        context: Context,
        http: Http,
        optDir: File,
        extractDir: File,
    ): List<Provider> {
        val workDir = File(extractDir, file.nameWithoutExtension).also { it.mkdirs() }
        var dexPath: String? = null
        val entries = mutableListOf<Pair<String, String>>() // name, class

        if (file.extension == "zip" || file.extension == "apk") {
            ZipFile(file).use { zip ->
                val manifestEntry = zip.getEntry("manifest.json")
                if (manifestEntry != null) {
                    val json = JSONObject(zip.getInputStream(manifestEntry).bufferedReader().readText())
                    val arr = json.optJSONArray("providers")
                    for (i in 0 until (arr?.length() ?: 0)) {
                        val obj = arr?.optJSONObject(i) ?: continue
                        val cls = obj.optString("class")
                        if (cls.isNotBlank()) entries += (obj.optString("name") to cls)
                    }
                }
                for (e in zip.entries()) {
                    if (e.isDirectory) continue
                    when {
                        e.name.endsWith(".dex") -> {
                            val out = File(workDir, File(e.name).name)
                            zip.getInputStream(e).use { input ->
                                out.outputStream().use { input.copyTo(it) }
                            }
                            dexPath = out.absolutePath
                        }
                    }
                }
            }
        } else {
            dexPath = file.absolutePath
        }

        val dex = dexPath ?: return emptyList()

        // manifest yoksa: dosya adi ipucuyla sinif dene
        if (entries.isEmpty()) {
            val stem = file.nameWithoutExtension
            entries += ("${stem}Provider" to "$stem.Provider")
            entries += ("${stem}Plugin" to "$stem.Plugin")
        }

        val loader = DexClassLoader(dex, optDir.absolutePath, null, context.classLoader)
        val providers = mutableListOf<Provider>()
        for ((label, cls) in entries) {
            val clazz = runCatching { loader.loadClass(cls) }.getOrNull() ?: continue
            val instance = clazz.getDeclaredConstructor(Http::class.java).newInstance(http)
            val provider = instance as? Provider ?: continue
            if (label.isNotBlank()) {
                // saglayici kendi adini zaten bildirir; manifest etiketi yedek
            }
            providers += provider
        }
        return providers
    }
}
