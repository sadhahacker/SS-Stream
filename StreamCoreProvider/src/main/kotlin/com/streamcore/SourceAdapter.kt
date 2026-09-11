package com.streamcore

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorLink

/**
 * Everything needed to resolve a piece of media, independent of where the request originated.
 * Shared by every [StreamExtractor] and [SubtitleSource] so adapters don't each invent their
 * own (tmdbId, isMovie, season, episode) parameter list.
 */
data class MediaRequest(
    val tmdbId: Int,
    val isMovie: Boolean,
    val season: Int? = null,
    val episode: Int? = null
)

/**
 * A single video-source adapter (VidCore, Vidnest, ...). Each implementation is responsible for
 * one upstream service; [StreamSourceRegistry] fans a [MediaRequest] out to all of them.
 *
 * To add a new video source: implement this interface and add the object to
 * [StreamSourceRegistry.extractors] - no other file needs to change.
 */
interface StreamExtractor {
    /** Short identifier used in link labels and failure logs, e.g. "VidCore". */
    val name: String

    suspend fun resolveStreams(request: MediaRequest, callback: (ExtractorLink) -> Unit)
}

/**
 * A single subtitle-source adapter. Each implementation is responsible for one upstream
 * subtitle API and returns whatever tracks it found (already validated/deduplicated).
 *
 * To add a new subtitle source (or a fallback for when the primary one is down): implement
 * this interface and add the object to [StreamSourceRegistry.subtitleSources].
 */
interface SubtitleSource {
    /** Short identifier used in failure logs, e.g. "VidRock". */
    val name: String

    suspend fun fetchSubtitles(request: MediaRequest): List<SubtitleFile>
}

/**
 * Central registry of every source adapter the provider knows about. This is the one place
 * that needs editing to add/remove/reorder a video or subtitle source - [StreamCoreProvider]
 * itself never changes.
 */
object StreamSourceRegistry {
    val extractors: List<StreamExtractor> = listOf(
        VidCoreExtractor,
        VidnestExtractor
    )

    val subtitleSources: List<SubtitleSource> = listOf(
        VidRockSubtitleSource
    )
}
