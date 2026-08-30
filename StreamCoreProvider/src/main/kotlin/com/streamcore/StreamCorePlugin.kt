package com.streamcore

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class StreamCorePlugin : Plugin() {
    override fun load(context: Context) {
        // Set in-app settings handler
        openSettings = { ctx ->
            StreamSettingsManager.showSettingsDialog(ctx)
        }

        // Initialize preferences
        StreamSettingsManager.init(context)

        // Register the provider
        registerMainAPI(StreamCoreProvider())
    }
}
