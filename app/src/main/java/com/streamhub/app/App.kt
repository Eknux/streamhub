package com.streamhub.app

import android.app.Application
import com.streamhub.app.core.CrashHandler
import com.streamhub.app.core.PluginManager
import com.streamhub.app.core.Provider
import com.streamhub.app.net.Http

class App : Application() {

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

    override fun onCreate() {
        super.onCreate()
        // Her olasi cokme kaydedilir; Ayarlar -> "Son cokme kaydi"ndan gorunur.
        CrashHandler.install(this)
        com.streamhub.app.core.Store.init(this)
        http = Http(this)
        reloadPlugins()
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
