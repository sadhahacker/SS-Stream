package com.streamcore

import com.lagradost.cloudstream3.TvType

/**
 * ========================================================================================
 * 1. PROVIDER GLOBAL CONFIGURATION
 * Edit provider-level settings here instead of editing the engine code.
 * ========================================================================================
 */
object ProviderConfig {
    const val NAME = "StreamCore"
    const val MAIN_URL = "https://vidcore.org"
    val SUPPORTED_TYPES = setOf(TvType.Movie, TvType.TvSeries)
    const val LANG = "en"
    const val HAS_MAIN_PAGE = true
    const val HAS_QUICK_SEARCH = true
}

/**
 * ========================================================================================
 * 2. STREAMING SOURCE CONTRACT
 * ========================================================================================
 */
interface StreamingSource {
    val name: String
    val priority: Int get() = 0 // Higher number = Higher Priority (Appears first in player)
    val enabled: Boolean get() = true
    val referer: String? get() = null

    suspend fun getMovieUrl(tmdbId: Int): String?
    suspend fun getTvUrl(tmdbId: Int, season: Int, episode: Int): String?
}

/**
 * Pattern-based source for standard URL templates.
 * Use %d for TMDB ID, Season, and Episode.
 */
data class PatternSource(
    override val name: String,
    val moviePattern: String,
    val tvPattern: String,
    override val priority: Int = 0, // Set priority here (e.g., 100, 90, 80...)
    override val enabled: Boolean = true,
    override val referer: String? = null
) : StreamingSource {
    override suspend fun getMovieUrl(tmdbId: Int): String = String.format(moviePattern, tmdbId)
    override suspend fun getTvUrl(tmdbId: Int, season: Int, episode: Int): String = String.format(tvPattern, tmdbId, season, episode)
}

/**
 * Custom source with custom lambda resolvers for non-standard endpoints.
 */
class CustomSource(
    override val name: String,
    override val priority: Int = 0,
    override val enabled: Boolean = true,
    override val referer: String? = null,
    private val movieResolver: suspend (tmdbId: Int) -> String?,
    private val tvResolver: suspend (tmdbId: Int, season: Int, episode: Int) -> String?
) : StreamingSource {
    override suspend fun getMovieUrl(tmdbId: Int) = movieResolver(tmdbId)
    override suspend fun getTvUrl(tmdbId: Int, season: Int, episode: Int) = tvResolver(tmdbId, season, episode)
}

/**
 * ========================================================================================
 * 3. REGISTERED SOURCES & PRIORITY RANKING
 *
 * HOW PRIORITY WORKS:
 * - Sources with a HIGHER priority number (e.g. 100) are queried first and their streams
 *   appear at the VERY TOP of Cloudstream's link selection list.
 * - To move a source to top priority, simply give it a higher priority number.
 * - Set enabled = false to disable any server without deleting it.
 * ========================================================================================
 */
val REGISTERED_SOURCES: List<StreamingSource> = listOf(
    // 🥇 Priority 100 (Top Priority)
    PatternSource(
        name = "VidCore",
        moviePattern = "https://vidcore.org/embed/movie/%d?autoplay=true",
        tvPattern = "https://vidcore.org/embed/tv/%d/%d/%d?autoplay=true",
        referer = "https://vidcore.org/",
        priority = 100,
        enabled = true
    ),

    // 🥈 Priority 90
    PatternSource(
        name = "VidLink",
        moviePattern = "https://vidlink.pro/movie/%d",
        tvPattern = "https://vidlink.pro/tv/%d/%d/%d",
        referer = "https://vidlink.pro/",
        priority = 90,
        enabled = true
    ),

    // 🥉 Priority 80
    PatternSource(
        name = "Videasy",
        moviePattern = "https://player.videasy.to/movie/%d",
        tvPattern = "https://player.videasy.to/tv/%d/%d/%d",
        referer = "https://player.videasy.to/",
        priority = 80,
        enabled = true
    ),

    // Priority 70
    PatternSource(
        name = "EmbedMaster",
        moviePattern = "https://embedmaster.link/movie/%d",
        tvPattern = "https://embedmaster.link/tv/%d/%d/%d",
        referer = "https://embedmaster.link/",
        priority = 70,
        enabled = true
    ),

    // Priority 60 (Backup)
    PatternSource(
        name = "AutoEmbed",
        moviePattern = "https://autoembed.co/movie/tmdb/%d",
        tvPattern = "https://autoembed.co/tv/tmdb/%d/%d/%d",
        priority = 60,
        enabled = true
    ),

    // Priority 50 (Backup)
    PatternSource(
        name = "2Embed",
        moviePattern = "https://www.2embed.cc/embed/%d",
        tvPattern = "https://www.2embed.cc/embedtv/%d&s=%d&e=%d",
        priority = 50,
        enabled = true
    ),

    // Priority 40 (Backup)
    PatternSource(
        name = "LordFlix",
        moviePattern = "https://lordflix.to/embed/movie/%d",
        tvPattern = "https://lordflix.to/embed/tv/%d/%d/%d",
        priority = 40,
        enabled = true
    ),

    // Priority 30 (Backup)
    PatternSource(
        name = "VidLove",
        moviePattern = "https://player.vidlove.cc/embed/movie/%d",
        tvPattern = "https://player.vidlove.cc/embed/tv/%d/%d/%d",
        priority = 30,
        enabled = true
    )
)
