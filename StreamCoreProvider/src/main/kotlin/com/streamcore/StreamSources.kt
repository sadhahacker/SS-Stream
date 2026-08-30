package com.streamcore

/**
 * ========================================================================================
 * STREAMING SOURCE CONTRACT
 * ========================================================================================
 */
interface StreamingSource {
    val name: String
    val priority: Int get() = 0 // Higher number = higher priority (appears first in the player)
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
    override val priority: Int = 0,
    override val enabled: Boolean = true,
    override val referer: String? = null
) : StreamingSource {
    override suspend fun getMovieUrl(tmdbId: Int): String = String.format(moviePattern, tmdbId)
    override suspend fun getTvUrl(tmdbId: Int, season: Int, episode: Int): String =
        String.format(tvPattern, tmdbId, season, episode)
}

/**
 * Custom source with lambda resolvers for non-standard endpoints
 * (e.g. sources that require an API call before a playable URL can be built).
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
    PatternSource(
        name = "VidCore",
        moviePattern = "https://vidcore.org/embed/movie/%d?autoplay=true",
        tvPattern = "https://vidcore.org/embed/tv/%d/%d/%d?autoplay=true",
        referer = "https://vidcore.org/",
        priority = 100
    ),
    PatternSource(
        name = "VidLink",
        moviePattern = "https://vidlink.pro/movie/%d",
        tvPattern = "https://vidlink.pro/tv/%d/%d/%d",
        referer = "https://vidlink.pro/",
        priority = 90
    ),
    PatternSource(
        name = "Videasy",
        moviePattern = "https://player.videasy.to/movie/%d",
        tvPattern = "https://player.videasy.to/tv/%d/%d/%d",
        referer = "https://player.videasy.to/",
        priority = 80
    ),
    PatternSource(
        name = "EmbedMaster",
        moviePattern = "https://embedmaster.link/movie/%d",
        tvPattern = "https://embedmaster.link/tv/%d/%d/%d",
        referer = "https://embedmaster.link/",
        priority = 70
    ),
    PatternSource(
        name = "AutoEmbed",
        moviePattern = "https://autoembed.co/movie/tmdb/%d",
        tvPattern = "https://autoembed.co/tv/tmdb/%d/%d/%d",
        priority = 60
    ),
    PatternSource(
        name = "2Embed",
        moviePattern = "https://www.2embed.cc/embed/%d",
        tvPattern = "https://www.2embed.cc/embedtv/%d&s=%d&e=%d",
        priority = 50
    ),
    PatternSource(
        name = "LordFlix",
        moviePattern = "https://lordflix.to/embed/movie/%d",
        tvPattern = "https://lordflix.to/embed/tv/%d/%d/%d",
        priority = 40
    ),
    PatternSource(
        name = "VidLove",
        moviePattern = "https://player.vidlove.cc/embed/movie/%d",
        tvPattern = "https://player.vidlove.cc/embed/tv/%d/%d/%d",
        priority = 30
    )
)

/**
 * ========================================================================================
 * How to add a new provider:
 *
 * REGISTERED_SOURCES + PatternSource(
 *     name = "NewProvider",
 *     moviePattern = "https://newprovider.com/embed/movie/%d",
 *     tvPattern = "https://newprovider.com/embed/tv/%d/%d/%d",
 *     priority = 95
 * )
 * ========================================================================================
 */
