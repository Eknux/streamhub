package com.streamhub.app.core

import android.content.Context
import android.content.Intent
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * CloudStream tarzi cokme yoneticisi:
 *  - cokme dosyaya yazilir (ilk satir = zaman damgasi, sonrasi = stack trace)
 *  - uygulama daha once acilmissa (appStarted) otomatik yeniden baslatilir
 *  - startup'ta cokerse yeniden baslatma YAPMAZ (sonsuz dongu olmasin);
 *    MainActivity acilista kaydi diyalogla ekranda gosterir.
 */
object CrashHandler : Thread.UncaughtExceptionHandler {

    @Volatile
    var appStarted = false

    private var appContext: Context? = null
    private var previous: Thread.UncaughtExceptionHandler? = null

    fun install(context: Context) {
        appContext = context.applicationContext
        previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(this)
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        record(throwable, thread)
        val prev = previous
        if (appStarted) {
            if (prev != null) prev.uncaughtException(thread, throwable)
            else restartApp()
        } else {
            if (prev != null) prev.uncaughtException(thread, throwable)
            else kotlin.system.exitProcess(2)
        }
    }

    private fun restartApp() {
        val ctx = appContext
        val launch = ctx?.packageManager?.getLaunchIntentForPackage(ctx.packageName)
        if (ctx != null && launch != null) {
            runCatching { ctx.startActivity(Intent.makeRestartActivityTask(launch.component)) }
        }
        kotlin.system.exitProcess(2)
    }

    /** Koddan da cagrilabilir: hatayi dosyaya kaydeder. */
    fun record(throwable: Throwable, thread: Thread = Thread.currentThread()) {
        runCatching {
            val ctx = appContext ?: return
            val trace = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()
            val stamp = System.currentTimeMillis()
            File(ctx.filesDir, "last_crash.txt").writeText("$stamp\n$trace")
        }
    }

    fun crashStamp(context: Context): Long {
        val f = File(context.filesDir, "last_crash.txt")
        if (!f.exists()) return -1L
        // eski formatli (v2.0) dosya da raporlansin diye 0 doner
        return f.readLines().firstOrNull()?.toLongOrNull() ?: 0L
    }

    fun lastCrash(context: Context): String? =
        File(context.filesDir, "last_crash.txt").takeIf { it.exists() }?.readText()

    fun clear(context: Context) {
        File(context.filesDir, "last_crash.txt").delete()
    }
}
