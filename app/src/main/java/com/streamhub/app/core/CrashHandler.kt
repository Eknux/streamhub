package com.streamhub.app.core

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Her olasi cokmeyi dosyaya yazar; kullanici Ayarlar -> "Son cokme kaydi"ndan
 * hatayi gorur ve rapor edebilir. (Cokme gormezden gelinmez: once kayit, sonra
 * varsayilan davranis.)
 */
object CrashHandler : Thread.UncaughtExceptionHandler {

    private var appContext: Context? = null
    private var previous: Thread.UncaughtExceptionHandler? = null

    fun install(context: Context) {
        appContext = context.applicationContext
        previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(this)
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        runCatching {
            val ctx = appContext ?: return
            val trace = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()
            val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            File(ctx.filesDir, "last_crash.txt").writeText("$stamp · ${thread.name}\n\n$trace")
        }
        previous?.uncaughtException(thread, throwable) ?: kotlin.system.exitProcess(2)
    }

    fun lastCrash(context: Context): String? =
        File(context.filesDir, "last_crash.txt").takeIf { it.exists() }?.readText()

    fun clear(context: Context) {
        File(context.filesDir, "last_crash.txt").delete()
    }
}
