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

        // None of these sources are recognized by Cloudstream's built-in extractors,
        // and several protect their stream APIs with encryption/signing (see
        // StreamExtractors.kt) - so we ship our own, WebView-based ones.
        registerExtractorAPI(VidCoreExtractor())
        registerExtractorAPI(VidLinkExtractor())
        registerExtractorAPI(VideasyExtractor())
        registerExtractorAPI(EmbedMasterExtractor())
        registerExtractorAPI(AutoEmbedExtractor())
        registerExtractorAPI(TwoEmbedExtractor())
        registerExtractorAPI(LordFlixExtractor())
        registerExtractorAPI(VidLoveExtractor())
    }
}
