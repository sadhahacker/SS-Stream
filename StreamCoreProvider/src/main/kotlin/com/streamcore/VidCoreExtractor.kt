package com.streamcore

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType

object VidCoreExtractor : StreamExtractor {
    override val name = "VidCore"

    private const val API_URL = "https://vidrack.created.app/api/sources/vidrift"

    override suspend fun resolveStreams(request: MediaRequest, callback: (ExtractorLink) -> Unit) {
        val url = if (request.isMovie) {
            "$API_URL?id=${request.tmdbId}"
        } else {
            "$API_URL?id=${request.tmdbId}&type=tv&season=${request.season ?: 1}&episode=${request.episode ?: 1}"
        }

        val res = app.get(url, timeout = 10L).parsedSafe<VidCoreResponse>()
        val sources = res?.sources?.filter { !it.url.isNullOrBlank() } ?: return

        sources.forEach { src ->
            val streamUrl = src.url ?: return@forEach
            val qualityInt = QualityUtils.parseQuality(src.quality)
            val headers = src.headers ?: mapOf(
                "Referer" to "https://embed.vidrift.in/",
                "Origin" to "https://embed.vidrift.in"
            )
            val referer = headers["Referer"] ?: "https://embed.vidrift.in/"

            val qualityLabel = when {
                qualityInt >= 2160 -> "4K"
                qualityInt > 0 -> "${qualityInt}p"
                else -> src.quality ?: "Auto"
            }

            callback(
                ExtractorLink(
                    source = "VidCore",
                    name = "[VidCore] $qualityLabel",
                    url = streamUrl,
                    referer = referer,
                    quality = qualityInt,
                    type = ExtractorLinkType.M3U8,
                    headers = headers
                )
            )
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
