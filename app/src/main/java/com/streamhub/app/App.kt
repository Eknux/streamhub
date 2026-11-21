package com.streamhub.app

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import com.google.android.material.color.DynamicColors
import com.streamhub.app.core.CrashHandler
import com.streamhub.app.core.PluginManager
import com.streamhub.app.core.Provider
import com.streamhub.app.core.Store
import com.streamhub.app.net.Http

class App : Application() {

    companion object {
        /** Aktif (temali) activity — Ui renkleri Material You paletini buradan okur. */
        @Volatile
        var uiContext: Context? = null
    }

    lateinit var http: Http
        private set

    val providers = mutableListOf<Provider>()

    /** Yuklenen eklentilerin yukleme hatalari: "dosya — hata" biciminde. */
    val pluginErrors = mutableListOf<String>()

    var activeProviderName: String
        get() = getSharedPreferences("streamhub_prefs", MODE_PRIVATE)
            .getString("active_plugin", "").orEmpty()
        set(value) {
            getSharedPreferences("streamhub_prefs", MODE_PRIVATE).edit()
                .putString("active_plugin", value).apply()
        }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        // mumkun olan en erken noktada kur — her cokme kayda girsin
        runCatching { CrashHandler.install(this) }
    }

    override fun onCreate() {
        super.onCreate()
        CrashHandler.install(this)
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(a: Activity, b: Bundle?) { uiContext = a }
            override fun onActivityStarted(a: Activity) {}
            override fun onActivityResumed(a: Activity) {}
            override fun onActivityPaused(a: Activity) {}
            override fun onActivityStopped(a: Activity) {}
            override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
            override fun onActivityDestroyed(a: Activity) {
                if (uiContext === a) uiContext = null
            }
        })
        // Material You: Android 12+ duvar kagidi paleti tum aktivitelere uygulanir
        runCatching { DynamicColors.applyToActivitiesIfAvailable(this) }
        runCatching { Store.init(this) }
        runCatching { com.streamhub.app.core.I18n.init(this) }
        http = Http(this)
        // eklenti klasoru/zip hatalari uygulamayi asla kapatmamali
        runCatching { reloadPlugins() }.onFailure { CrashHandler.record(it) }
    }

    fun providerByName(name: String?): Provider? =
        providers.firstOrNull { it.name == name } ?: providers.firstOrNull()

    fun reloadPlugins() {
        providers.clear()
        pluginErrors.clear()
        val (loaded, errors) = PluginManager.loadAll(this, http)
        providers.addAll(loaded)
        pluginErrors.addAll(errors)
    }
}
