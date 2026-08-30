package com.streamcore

import android.annotation.SuppressLint
import android.content.Context
import android.net.http.SslError
import android.webkit.SslErrorHandler
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.lagradost.api.Log
import com.lagradost.api.getContext
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.utils.Coroutines.main
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.coroutines.delay

/**
 * Cloudstream's own WebViewResolver reroutes page requests through its okhttp client
 * (useOkhttp=true by default) and doesn't disable Android's autoplay-requires-a-real-touch
 * restriction - both of which broke every one of our sources on a real device (they all
 * depend on their own session cookies/tokens surviving XHR calls, and several only start
 * fetching their stream once "playback" begins). This is a trimmed-down reimplementation
 * that: (1) never reroutes traffic - every request goes through the WebView's own native
 * network stack, exactly like a real browser tab, so session state stays intact, and
 * (2) disables mediaPlaybackRequiresUserGesture, since our injected click can never carry
 * a browser-trusted gesture flag.
 */
abstract class WebViewEmbedExtractor : ExtractorApi() {
    override val requiresReferer = false

    /** Matched against every outgoing request until the real stream manifest is found. */
    open val streamUrlRegex = Regex("""\.m3u8(\?|$)""")

    open val timeoutMs = 45_000L

    /**
     * Most of these players wait for a click on their poster/play button before requesting
     * the stream, and it may not exist yet the moment the page "finishes" loading (React
     * hydration lag) - so this is safe to re-run repeatedly, and only actually clicks once.
     */
    open val autoplayScript: String = """
        (function() {
            if (!window.__csClicked) {
                var clickable = document.querySelector('[class*=play],button,[role=button]');
                if (clickable) { window.__csClicked = true; clickable.click(); }
            }
            document.querySelectorAll('video').forEach(function(v) {
                v.muted = true;
                v.play().catch(function(){});
            });
        })();
    """.trimIndent()

    @SuppressLint("SetJavaScriptEnabled")
    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        var webView: WebView? = null
        var resolvedUrl: String? = null
        var resolvedHeaders: Map<String, String> = emptyMap()
        var finished = false

        fun destroy() {
            main {
                webView?.stopLoading()
                webView?.destroy()
                webView = null
                finished = true
            }
        }

        main {
            try {
                webView = WebView(
                    (getContext() as? Context) ?: throw IllegalStateException("No context")
                ).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.userAgentString = USER_AGENT

                    webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(
                            view: WebView,
                            request: WebResourceRequest
                        ): WebResourceResponse? {
                            val requestUrl = request.url.toString()
                            if (streamUrlRegex.containsMatchIn(requestUrl)) {
                                resolvedUrl = requestUrl
                                resolvedHeaders = request.requestHeaders
                                Log.i("StreamCore", "Resolved stream via WebView: $requestUrl")
                                destroy()
                            }
                            // Never reroute - let the WebView's own network stack (with its
                            // own cookies/session) handle every request, matching what a
                            // real browser tab would do.
                            return super.shouldInterceptRequest(view, request)
                        }

                        @SuppressLint("WebViewClientOnReceivedSslError")
                        override fun onReceivedSslError(
                            view: WebView?,
                            handler: SslErrorHandler?,
                            error: SslError?
                        ) {
                            handler?.proceed()
                        }
                    }
                    loadUrl(url, referer?.let { mapOf("Referer" to it) } ?: emptyMap())
                }
            } catch (e: Exception) {
                logError(e)
                finished = true
            }
        }

        // Re-inject the click script periodically: the play button may not exist yet the
        // moment the page "finishes" loading (client-side hydration lag). Already on the
        // main dispatcher here, so evaluateJavascript can be called directly.
        main {
            while (!finished && resolvedUrl == null) {
                delay(500)
                webView?.evaluateJavascript(autoplayScript, null)
            }
        }

        var waited = 0L
        val step = 200L
        while (waited < timeoutMs && !finished && resolvedUrl == null) {
            delay(step)
            waited += step
        }
        if (resolvedUrl == null) destroy()

        val finalUrl = resolvedUrl ?: return
        callback(
            newExtractorLink(source = name, name = name, url = finalUrl) {
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
