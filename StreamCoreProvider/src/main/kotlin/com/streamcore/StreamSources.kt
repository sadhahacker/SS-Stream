package com.streamcore

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.Qualities
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink

/**
 * ========================================================================================
 * STREAMING SOURCE CONTRACT
 * ========================================================================================
 */
interface StreamingSource {
    val name: String
    val priority: Int get() = 0 // Higher number = higher priority (appears first in the player)
    val enabled: Boolean get() = true

    suspend fun loadStreams(
        tmdbId: Int,
        type: String, // "movie" or "tv"
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean
}

/**
 * Direct REST API source querying high-performance streaming backends.
 * Resolves directly to M3U8 streams in milliseconds without needing WebViews or browser emulation.
 */
class VidrackSource(
    override val name: String,
    val apiKey: String,
    override val priority: Int = 0,
    override val enabled: Boolean = true
) : StreamingSource {
    override suspend fun loadStreams(
        tmdbId: Int,
        type: String,
        season: Int?,
        episode: Int?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val apiUrl = if (type == "tv") {
            "https://vidrack.created.app/api/sources/$apiKey?id=$tmdbId&type=tv&season=${season ?: 1}&episode=${episode ?: 1}"
        } else {
            "https://vidrack.created.app/api/sources/$apiKey?id=$tmdbId"
        }

        val res = app.get(apiUrl, timeout = 10L).parsedSafe<VidrackResponse>()
        var list = res?.sources?.filter { !it.url.isNullOrBlank() } ?: emptyList()

        // Fallback for primary source (VidCore) if primary endpoint returned no streams
        if (list.isEmpty() && apiKey == "vidrift") {
            val fallbackUrl = if (type == "tv") {
                "https://vidrack.created.app/api/sources/movy?id=$tmdbId&type=tv&season=${season ?: 1}&episode=${episode ?: 1}"
            } else {
                "https://vidrack.created.app/api/sources/movy?id=$tmdbId"
            }
            val fallbackRes = app.get(fallbackUrl, timeout = 10L).parsedSafe<VidrackResponse>()
            list = fallbackRes?.sources?.filter { !it.url.isNullOrBlank() } ?: emptyList()
        }

        if (list.isEmpty()) return false

        list.forEach { src ->
            val url = src.url ?: return@forEach
            val qualityInt = parseQuality(src.quality)
            val label = src.label ?: src.quality ?: "Auto"
            val headers = src.headers ?: emptyMap()
            val referer = headers["Referer"] ?: "https://embed.vidrift.in/"

            callback(
                newExtractorLink(
                    source = name,
                    name = "[$name] $label",
                    url = url,
                    type = ExtractorLinkType.M3U8
                ) {
                    this.referer = referer
                    this.headers = headers
                    this.quality = qualityInt
                }
            )
        }

        return true
    }
}

/**
 * Pattern-based source for standard embed URL templates that load via Cloudstream extractors.
 */
data class PatternSource(
    override val name: String,
    val moviePattern: String,
    val tvPattern: String,
    override val priority: Int = 0,
    override val enabled: Boolean = true,
    val referer: String? = null
) : StreamingSource {
    override suspend fun loadStreams(
        tmdbId: Int,
        type: String,
        season: Int?,
        episode: Int?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val streamUrl = if (type == "tv") {
            String.format(tvPattern, tmdbId, season ?: 1, episode ?: 1)
        } else {
            String.format(moviePattern, tmdbId)
        }

        return loadExtractor(
            url = streamUrl,
            referer = referer,
            subtitleCallback = subtitleCallback,
            callback = callback
        )
    }
}

fun parseQuality(quality: String?): Int {
    val q = quality?.lowercase()?.trim() ?: return Qualities.Unknown.value
    return when {
        q.contains("2160") || q.contains("4k") -> Qualities.P2160.value
        q.contains("1080") -> Qualities.P1080.value
        q.contains("720") -> Qualities.P720.value
        q.contains("480") -> Qualities.P480.value
        q.contains("360") -> Qualities.P360.value
        else -> Qualities.Unknown.value
    }
}

data class VidrackResponse(
    @JsonProperty("sources") val sources: List<VidrackSourceItem>? = null
)

data class VidrackSourceItem(
    @JsonProperty("url") val url: String? = null,
    @JsonProperty("type") val type: String? = null,
    @JsonProperty("quality") val quality: String? = null,
    @JsonProperty("label") val label: String? = null,
    @JsonProperty("provider") val provider: String? = null,
    @JsonProperty("headers") val headers: Map<String, String>? = null
)

data class SubtitleItem(
    @JsonProperty("label") val label: String? = null,
    @JsonProperty("file") val file: String? = null,
    @JsonProperty("type") val type: String? = null,
    @JsonProperty("source") val source: String? = null
)

/**
 * ========================================================================================
 * REGISTERED SOURCES & PRIORITY RANKING
 *
 * - Sources with a HIGHER priority number are queried first and appear at the top of
 *   Cloudstream's link selection list.
 * - Set enabled = false to disable a server without deleting it.
 * - The in-app Settings dialog can bump any one source to the very top at runtime,
 *   overriding the priority below (see StreamSettingsManager.getRuntimeSortedSources).
 * ========================================================================================
 */
val REGISTERED_SOURCES: List<StreamingSource> = listOf(
    VidrackSource(
        name = "VidCore",
        apiKey = "vidrift",
        priority = 100
    ),
    VidrackSource(
        name = "Movy",
        apiKey = "movy",
        priority = 95
    ),
    VidrackSource(
        name = "VidNest",
        apiKey = "vidnest",
        priority = 90
    ),
    VidrackSource(
        name = "Filmubox",
        apiKey = "filmubox",
        priority = 85
    ),
    VidrackSource(
        name = "Cinextream",
        apiKey = "cinextream",
        priority = 80
    ),
    VidrackSource(
        name = "VAPlayer",
        apiKey = "vaplayer",
        priority = 75
    ),
    VidrackSource(
        name = "Overlook",
        apiKey = "overlook",
        priority = 70
    ),
    VidrackSource(
        name = "Viduki",
        apiKey = "viduki",
        priority = 65
    ),
    PatternSource(
        name = "VidLink",
        moviePattern = "https://vidlink.pro/movie/%d",
        tvPattern = "https://vidlink.pro/tv/%d/%d/%d",
        referer = "https://vidlink.pro/",
        priority = 50
    ),
    PatternSource(
        name = "Videasy",
        moviePattern = "https://player.videasy.to/movie/%d",
        tvPattern = "https://player.videasy.to/tv/%d/%d/%d",
        referer = "https://player.videasy.to/",
        priority = 45
    ),
    PatternSource(
        name = "AutoEmbed",
        moviePattern = "https://autoembed.co/movie/tmdb/%d",
        tvPattern = "https://autoembed.co/tv/tmdb/%d/%d/%d",
        referer = "https://autoembed.co/",
        priority = 40
    ),
    PatternSource(
        name = "2Embed",
        moviePattern = "https://www.2embed.cc/embed/%d",
        tvPattern = "https://www.2embed.cc/embedtv/%d&s=%d&e=%d",
        priority = 30
    )
)
