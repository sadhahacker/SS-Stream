package com.streamcore

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.network.WebViewResolver
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.newExtractorLink

/**
 * None of our multi-embed sources are recognized by Cloudstream's built-in extractors, and
 * several of them protect their stream APIs with per-session signed requests and/or
 * obfuscated/encrypted client-side JS (confirmed via live testing: WASM+libsodium on
 * vidlink.pro, a custom non-WebCrypto JS cipher behind a single-use signed "seed" on
 * player.videasy.to, headless-browser fingerprinting on vidcore.org).
 *
 * Rather than reverse-engineering each site's protection (fragile - breaks the moment the
 * site changes it), we load the real embed page in a hidden Android WebView so the site's
 * own JavaScript does the decrypting/signing exactly as it would for a real viewer, nudge
 * playback started, and capture the network request it makes for the actual stream
 * manifest. This is Cloudstream's own sanctioned approach for exactly this class of site
 * (see WebViewResolver in the Cloudstream library).
 */
abstract class WebViewEmbedExtractor : ExtractorApi() {
    override val requiresReferer = false

    /** Matched against every outgoing request until the real stream manifest is found. */
    open val streamUrlRegex = Regex("""\.m3u8(\?|$)""")

    /**
     * Most of these players wait for a click on their poster/play button before requesting
     * the stream. Re-run on every intercepted request (idempotent) until it lands one click.
     */
    open val autoplayScript: String? = """
        if (!window.__csAutoplay) {
            var btn = document.querySelector('button');
            if (btn) { window.__csAutoplay = true; btn.click(); }
        }
    """.trimIndent()

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val resolver = WebViewResolver(
            interceptUrl = streamUrlRegex,
            script = autoplayScript
        )
        val (request, _) = resolver.resolveUsingWebView(url, referer = referer)
        val resolvedUrl = request?.url?.toString() ?: return
        val resolvedHeaders = request.headers.names().associateWith { request.headers[it] ?: "" }

        callback(
            newExtractorLink(source = name, name = name, url = resolvedUrl) {
                this.referer = url
                this.headers = resolvedHeaders
            }
        )
    }
}

class VidCoreExtractor : WebViewEmbedExtractor() {
    override val name = "VidCore"
    override val mainUrl = "https://vidcore.org"
}

class VidLinkExtractor : WebViewEmbedExtractor() {
    override val name = "VidLink"
    override val mainUrl = "https://vidlink.pro"
}

class VideasyExtractor : WebViewEmbedExtractor() {
    override val name = "Videasy"
    override val mainUrl = "https://player.videasy.to"
}

class EmbedMasterExtractor : WebViewEmbedExtractor() {
    override val name = "EmbedMaster"
    override val mainUrl = "https://embedmaster.link"
}

class AutoEmbedExtractor : WebViewEmbedExtractor() {
    override val name = "AutoEmbed"
    override val mainUrl = "https://autoembed.co"
}

class TwoEmbedExtractor : WebViewEmbedExtractor() {
    override val name = "2Embed"
    override val mainUrl = "https://www.2embed.cc"
}

class LordFlixExtractor : WebViewEmbedExtractor() {
    override val name = "LordFlix"
    override val mainUrl = "https://lordflix.to"
}

class VidLoveExtractor : WebViewEmbedExtractor() {
    override val name = "VidLove"
    override val mainUrl = "https://player.vidlove.cc"
}
