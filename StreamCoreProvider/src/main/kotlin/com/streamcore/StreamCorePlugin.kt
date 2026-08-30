package com.streamcore

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class StreamCorePlugin : Plugin() {
    override var openSettings: ((context: Context) -> Unit)? = { context ->
        // Triggered when user taps Settings on this extension in Cloudstream
        StreamSettingsManager.showSettingsDialog(context)
    }

    override fun load(context: Context) {
        // Initialize persistent preferences with application context
        StreamSettingsManager.init(context)

        // Register the main 4-in-1 multi-server provider
        registerMainAPI(StreamCoreProvider())
    }
}
