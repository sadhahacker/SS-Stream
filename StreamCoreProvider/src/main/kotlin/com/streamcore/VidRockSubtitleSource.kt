package com.streamcore

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app

/**
 * Multilingual subtitle source backed by the CinePro/VidRock subtitle endpoint.
 * Isolated behind [SubtitleSource] so a fallback provider can be added later without
 * touching [StreamCoreProvider.loadLinks].
 */
object VidRockSubtitleSource : SubtitleSource {
    override val name = "VidRock"

    private const val SUB_API = "https://sub.vdrk.site"
    private val HEADERS = mapOf("Referer" to "https://vidrock.net/")

    override suspend fun fetchSubtitles(request: MediaRequest): List<SubtitleFile> {
        val subUrl = if (request.isMovie) {
            "$SUB_API/v2/movie/${request.tmdbId}"
        } else {
            "$SUB_API/v2/tv/${request.tmdbId}/${request.season ?: 1}/${request.episode ?: 1}"
        }

        val subs = app.get(subUrl, headers = HEADERS, timeout = 6L)
            .parsedSafe<List<SubtitleItem>>() ?: emptyList()

        return subs.mapNotNull { item ->
            val file = item.file?.trim()
            val label = item.label?.trim()
            if (file.isNullOrBlank() || label.isNullOrBlank()) return@mapNotNull null

            // The API is expected to return absolute URLs; guard against a relative
            // path slipping through and being handed to the player as-is.
            val absoluteUrl = if (file.startsWith("http://") || file.startsWith("https://")) {
                file
            } else {
                "$SUB_API${if (file.startsWith("/")) file else "/$file"}"
            }
            label to absoluteUrl
        }.distinct().map { (label, fileUrl) -> SubtitleFile(lang = label, url = fileUrl) }
    }

    private data class SubtitleItem(
        @JsonProperty("label") val label: String?,
        @JsonProperty("file") val file: String?
    )
}
