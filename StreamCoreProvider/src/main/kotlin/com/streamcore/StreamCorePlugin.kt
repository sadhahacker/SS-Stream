package com.streamcore

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class StreamCorePlugin : Plugin() {
    override fun load(context: Context) {
        StreamSettingsManager.init(context)

        // Adds a "Settings" button under Settings > Extensions > StreamCore
        openSettings = { ctx -> StreamSettingsManager.showSettingsDialog(ctx) }

        registerMainAPI(StreamCoreProvider())
    }
}
