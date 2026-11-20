package com.streamhub.app

import android.app.Application
import com.streamhub.app.core.PluginLoader
import com.streamhub.app.core.Provider
import com.streamhub.app.net.Http
import com.streamhub.app.providers.Dizilla

class App : Application() {

    lateinit var http: Http
        private set

    val providers = mutableListOf<Provider>()

    /** Eklenti yukleme sirasinda bir sey patlarsa burada tutulur, ana ekranda gosterilir. */
    var pluginError: String? = null
        private set

    override fun onCreate() {
        super.onCreate()
        http = Http(this)
        providers.add(Dizilla(http))
        runCatching { PluginLoader.loadPlugins(this, http) }
            .onSuccess { providers.addAll(it) }
            .onFailure { pluginError = "Eklenti yukleme hatasi: ${it.message}" }
    }
}
