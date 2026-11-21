package com.streamhub.app.core

import android.content.Context
import android.net.Uri
import com.streamhub.app.net.Http
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipFile

/**
 * Derlemesiz eklenti yoneticisi.
 *
 * Eklenti = `plugin.json` iceren bir .zip dosyasi. Derleme YOKTUR; zip'i
 * uygulama icinden (Ayarlar -> Eklenti ekle) ya da
 * Android/data/com.streamhub.app/files/plugins/ klasorune atarak yuklersin.
 * Uygulama acilista tum zip'leri okur ve JSON kurallarina gore kaynak uretir.
 */
object PluginManager {

    fun pluginsDir(context: Context): File {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        return File(base, "plugins").apply { mkdirs() }
    }

    fun installed(context: Context): List<File> =
        pluginsDir(context).listFiles()?.filter { it.isFile && it.extension == "zip" }
            ?.sortedBy { it.name.lowercase() } ?: emptyList()

    /** (yuklenen eklentiler, hata mesajlari) dondurur. */
    fun loadAll(context: Context, http: Http): Pair<List<Provider>, List<String>> {
        val providers = mutableListOf<Provider>()
        val errors = mutableListOf<String>()
        installed(context).forEach { file ->
            runCatching {
                ZipFile(file).use { zip ->
                    val entry = zip.entries().toList().firstOrNull {
                        it.name == "plugin.json" || it.name == "manifest.json" ||
                            it.name.endsWith("/plugin.json") || it.name.endsWith("/manifest.json")
                    } ?: throw Exception("zip içinde plugin.json yok")
                    val json = zip.getInputStream(entry).bufferedReader().readText()
                    val manifest = JSONObject(json)
                    if (manifest.optString("name").isBlank()) throw Exception("plugin.json içinde \"name\" yok")
                    providers += JsonProvider(http, manifest)
                }
            }.onFailure { errors += "${file.name} — ${it.message}" }
        }
        return providers to errors
    }

    /** Uygulama ici eklenti ekleme (SAF ile secilen zip'i plugins klasorune kopyalar). */
    fun importZip(context: Context, uri: Uri): String {
        val dir = pluginsDir(context)
        val raw = uri.lastPathSegment ?: "plugin.zip"
        val safe = raw.substringAfterLast('/').replace(Regex("[^A-Za-z0-9._\\-]+"), "_")
        val target = File(dir, if (safe.endsWith(".zip", true)) safe else "$safe.zip")
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { input.copyTo(it) }
        } ?: throw Exception("dosya okunamadı")
        return target.name
    }

    fun delete(context: Context, fileName: String): Boolean =
        File(pluginsDir(context), fileName).delete()
}
