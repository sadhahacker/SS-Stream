package com.streamcore

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink

object VidCoreExtractor {
    private const val API_URL = "https://vidrack.created.app/api/sources/vidrift"

    suspend fun resolveStreams(
        tmdbId: Int,
        isMovie: Boolean,
        season: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit
    ) {
        val url = if (isMovie) {
            "$API_URL?id=$tmdbId"
        } else {
            "$API_URL?id=$tmdbId&type=tv&season=${season ?: 1}&episode=${episode ?: 1}"
        }

        val res = app.get(url, timeout = 10L).parsedSafe<VidCoreResponse>()
        val sources = res?.sources?.filter { !it.url.isNullOrBlank() } ?: return

        sources.forEach { src ->
            val streamUrl = src.url ?: return@forEach
            val qualityInt = parseQuality(src.quality)
            val headers = src.headers ?: mapOf(
                "Referer" to "https://embed.vidrift.in/",
                "Origin" to "https://embed.vidrift.in"
            )
            val referer = headers["Referer"] ?: "https://embed.vidrift.in/"

            callback(
                newExtractorLink(
                    source = "VidCore",
                    name = "[VidCore] 1080p",
                    url = streamUrl,
                    type = ExtractorLinkType.M3U8
                ) {
                    this.referer = referer
                    this.headers = headers
                    this.quality = qualityInt
                }
            )
        }
    }

    private fun parseQuality(quality: String?): Int {
        val q = quality?.lowercase()?.trim() ?: return 1080
        return when {
            q.contains("2160") || q.contains("4k") -> 2160
            q.contains("1080") -> 1080
            q.contains("720") -> 720
            q.contains("480") -> 480
            q.contains("360") -> 360
            else -> 1080
        }
    }

    data class VidCoreResponse(
        @JsonProperty("sources") val sources: List<VidCoreSourceItem>? = null
    )

    data class VidCoreSourceItem(
        @JsonProperty("url") val url: String? = null,
        @JsonProperty("type") val type: String? = null,
        @JsonProperty("quality") val quality: String? = null,
        @JsonProperty("label") val label: String? = null,
        @JsonProperty("provider") val provider: String? = null,
        @JsonProperty("headers") val headers: Map<String, String>? = null
    )
}
