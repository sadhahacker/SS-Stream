package com.streamcore

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addDuration
import com.lagradost.cloudstream3.LoadResponse.Companion.addScore
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * TMDB-backed catalog that fans a single title out across every source registered
 * in [REGISTERED_SOURCES] (see StreamSources.kt), in parallel, with runtime priority
 * and enable/disable controlled from the in-app Settings dialog.
 */
class StreamCoreProvider : MainAPI() {
    override var name = "StreamCore"
    override var mainUrl = "https://vidcore.org"
    override var lang = "en"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override val hasMainPage = true
    override val hasQuickSearch = true

    private companion object {
        const val TMDB_API = "https://api.themoviedb.org/3"
        const val API_KEY = "15d2ea6d0dc1d476efbca3eba2b9bbfb"
        const val IMAGE_BASE = "https://image.tmdb.org/t/p/w500"
    }

    override val mainPage = mainPageOf(
        "$TMDB_API/trending/movie/day?api_key=$API_KEY" to "Trending Movies",
        "$TMDB_API/trending/tv/day?api_key=$API_KEY" to "Trending TV Shows",
        "$TMDB_API/movie/popular?api_key=$API_KEY" to "Popular Movies",
        "$TMDB_API/tv/popular?api_key=$API_KEY" to "Popular TV Shows"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val response = app.get("${request.data}&page=$page").parsedSafe<TmdbResultsList>()
            ?: return newHomePageResponse(request, emptyList())

        return newHomePageResponse(request, response.results.mapNotNull { it.toSearchResponse() }, hasNext = true)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val response = app.get("$TMDB_API/search/multi?api_key=$API_KEY&query=${query.replace(" ", "+")}")
            .parsedSafe<TmdbResultsList>() ?: return emptyList()

        return response.results.mapNotNull { it.toSearchResponse() }
    }

    private fun TmdbItem.toSearchResponse(): SearchResponse? {
        val id = id ?: return null
        val poster = posterPath?.let { "$IMAGE_BASE$it" }
        val title = this.title ?: this.name ?: return null

        return if (mediaType == "tv" || (mediaType == null && this.title == null)) {
            newTvSeriesSearchResponse(title, "$mainUrl/tv/$id", TvType.TvSeries) {
                this.posterUrl = poster
                this.year = firstAirDate?.take(4)?.toIntOrNull()
            }
        } else {
            newMovieSearchResponse(title, "$mainUrl/movie/$id", TvType.Movie) {
                this.posterUrl = poster
                this.year = releaseDate?.take(4)?.toIntOrNull()
            }
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val isMovie = url.contains("/movie/")
        val tmdbId = url.substringAfterLast("/").toIntOrNull() ?: return null

        return if (isMovie) loadMovie(url, tmdbId) else loadTvSeries(url, tmdbId)
    }

    private suspend fun loadMovie(url: String, tmdbId: Int): LoadResponse? {
        val movie = app.get("$TMDB_API/movie/$tmdbId?api_key=$API_KEY&append_to_response=videos")
            .parsedSafe<TmdbMovieDetails>() ?: return null

        val payload = MediaPayload(id = tmdbId, type = "movie")
        return newMovieLoadResponse(movie.title, url, TvType.Movie, payload.toJson()) {
            posterUrl = movie.posterPath?.let { "$IMAGE_BASE$it" }
            plot = movie.overview
            year = movie.releaseDate?.take(4)?.toIntOrNull()
            movie.runtime?.let { addDuration(it.toString()) }
            movie.voteAverage?.let { addScore(it.toString()) }
            movie.videos?.results?.firstOrNull { it.site == "YouTube" && it.type == "Trailer" }?.key?.let {
                addTrailer("https://www.youtube.com/watch?v=$it")
            }
        }
    }

    private suspend fun loadTvSeries(url: String, tmdbId: Int): LoadResponse? {
        val show = app.get("$TMDB_API/tv/$tmdbId?api_key=$API_KEY").parsedSafe<TmdbTvDetails>() ?: return null
        val seasonCount = show.numberOfSeasons ?: 1

        val episodes = (1..seasonCount).mapNotNull { season ->
            app.get("$TMDB_API/tv/$tmdbId/season/$season?api_key=$API_KEY").parsedSafe<TmdbSeasonDetails>()
        }.flatMap { seasonDetails ->
            seasonDetails.episodes.map { ep ->
                val epNumber = ep.episodeNumber ?: 1
                val payload = MediaPayload(id = tmdbId, type = "tv", season = seasonDetails.seasonNumber, episode = epNumber)
                newEpisode(payload.toJson()) {
                    name = ep.name
                    season = seasonDetails.seasonNumber
                    episode = epNumber
                    posterUrl = ep.stillPath?.let { "$IMAGE_BASE$it" }
                    description = ep.overview
                }
            }
        }

        return newTvSeriesLoadResponse(show.name, url, TvType.TvSeries, episodes) {
            posterUrl = show.posterPath?.let { "$IMAGE_BASE$it" }
            plot = show.overview
            year = show.firstAirDate?.take(4)?.toIntOrNull()
            show.voteAverage?.let { addScore(it.toString()) }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val payload = parseJson<MediaPayload>(data)
        val sources = StreamSettingsManager.getRuntimeSortedSources()

        coroutineScope {
            launch {
                runCatching {
                    val subUrl = if (payload.type == "tv") {
                        "https://api.shows.st/subtitles/tv/${payload.id}/${payload.season ?: 1}/${payload.episode ?: 1}"
                    } else {
                        "https://api.shows.st/subtitles/movie/${payload.id}"
                    }
                    val subs = app.get(subUrl, timeout = 6L).parsedSafe<List<SubtitleItem>>() ?: emptyList()
                    subs.forEach { sub ->
                        val file = sub.file
                        val label = sub.label
                        if (!file.isNullOrBlank() && !label.isNullOrBlank()) {
                            subtitleCallback(
                                SubtitleFile(
                                    lang = label,
                                    url = file
                                )
                            )
                        }
                    }
                }
            }

            sources.map { source ->
                async {
                    runCatching {
                        source.loadStreams(
                            tmdbId = payload.id,
                            type = payload.type,
                            season = payload.season,
                            episode = payload.episode,
                            subtitleCallback = subtitleCallback,
                            callback = callback
                        )
                    }
                }
            }.awaitAll()
        }

        return true
    }

    data class MediaPayload(
        val id: Int,
        val type: String,
        val season: Int? = null,
        val episode: Int? = null
    )

    data class TmdbResultsList(
        @JsonProperty("results") val results: List<TmdbItem> = emptyList()
    )

    data class TmdbItem(
        @JsonProperty("id") val id: Int?,
        @JsonProperty("title") val title: String?,
        @JsonProperty("name") val name: String?,
        @JsonProperty("poster_path") val posterPath: String?,
        @JsonProperty("release_date") val releaseDate: String?,
        @JsonProperty("first_air_date") val firstAirDate: String?,
        @JsonProperty("media_type") val mediaType: String?
    )

    data class TmdbMovieDetails(
        @JsonProperty("title") val title: String,
        @JsonProperty("overview") val overview: String?,
        @JsonProperty("poster_path") val posterPath: String?,
        @JsonProperty("release_date") val releaseDate: String?,
        @JsonProperty("runtime") val runtime: Int?,
        @JsonProperty("vote_average") val voteAverage: Double?,
        @JsonProperty("videos") val videos: TmdbVideoResults?
    )

    data class TmdbTvDetails(
        @JsonProperty("name") val name: String,
        @JsonProperty("overview") val overview: String?,
        @JsonProperty("poster_path") val posterPath: String?,
        @JsonProperty("first_air_date") val firstAirDate: String?,
        @JsonProperty("number_of_seasons") val numberOfSeasons: Int?,
        @JsonProperty("vote_average") val voteAverage: Double?
    )

    data class TmdbSeasonDetails(
        @JsonProperty("season_number") val seasonNumber: Int = 1,
        @JsonProperty("episodes") val episodes: List<TmdbEpisodeItem> = emptyList()
    )

    data class TmdbEpisodeItem(
        @JsonProperty("episode_number") val episodeNumber: Int?,
        @JsonProperty("name") val name: String?,
        @JsonProperty("overview") val overview: String?,
        @JsonProperty("still_path") val stillPath: String?
    )

    data class TmdbVideoResults(
        @JsonProperty("results") val results: List<TmdbVideoItem> = emptyList()
    )

    data class TmdbVideoItem(
        @JsonProperty("key") val key: String?,
        @JsonProperty("site") val site: String?,
        @JsonProperty("type") val type: String?
    )
}
