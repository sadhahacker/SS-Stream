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

class StreamCoreProvider : MainAPI() {
    override var name = ProviderConfig.NAME
    override var mainUrl = ProviderConfig.MAIN_URL
    override val supportedTypes = ProviderConfig.SUPPORTED_TYPES
    override var lang = ProviderConfig.LANG
    override val hasMainPage = ProviderConfig.HAS_MAIN_PAGE
    override val hasQuickSearch = ProviderConfig.HAS_QUICK_SEARCH

    companion object {
        private const val TMDB_API = "https://api.themoviedb.org/3"
        private const val API_KEY = "15d2ea6d0dc1d476efbca3eba2b9bbfb"
        private const val IMAGE_BASE = "https://image.tmdb.org/t/p/w500"
    }

    override val mainPage = mainPageOf(
        "$TMDB_API/trending/movie/day?api_key=$API_KEY" to "Trending Movies",
        "$TMDB_API/trending/tv/day?api_key=$API_KEY" to "Trending TV Shows",
        "$TMDB_API/movie/popular?api_key=$API_KEY" to "Popular Movies",
        "$TMDB_API/tv/popular?api_key=$API_KEY" to "Popular TV Shows"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = "${request.data}&page=$page"
        val response = app.get(url).parsedSafe<TmdbResultsList>() ?: return newHomePageResponse(request, emptyList())

        val results = response.results.mapNotNull { item ->
            val isMovie = item.title != null
            val id = item.id ?: return@mapNotNull null
            val title = item.title ?: item.name ?: return@mapNotNull null
            val poster = item.posterPath?.let { "$IMAGE_BASE$it" }

            if (isMovie) {
                newMovieSearchResponse(title, "$mainUrl/movie/$id", TvType.Movie) {
                    this.posterUrl = poster
                    this.year = item.releaseDate?.take(4)?.toIntOrNull()
                }
            } else {
                newTvSeriesSearchResponse(title, "$mainUrl/tv/$id", TvType.TvSeries) {
                    this.posterUrl = poster
                    this.year = item.firstAirDate?.take(4)?.toIntOrNull()
                }
            }
        }

        return newHomePageResponse(request, results, hasNext = true)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchUrl = "$TMDB_API/search/multi?api_key=$API_KEY&query=${query.replace(" ", "+")}"
        val response = app.get(searchUrl).parsedSafe<TmdbResultsList>() ?: return emptyList()

        return response.results.mapNotNull { item ->
            val isMovie = item.mediaType == "movie" || item.title != null
            val id = item.id ?: return@mapNotNull null
            val title = item.title ?: item.name ?: return@mapNotNull null
            val poster = item.posterPath?.let { "$IMAGE_BASE$it" }

            if (isMovie) {
                newMovieSearchResponse(title, "$mainUrl/movie/$id", TvType.Movie) {
                    this.posterUrl = poster
                    this.year = item.releaseDate?.take(4)?.toIntOrNull()
                }
            } else {
                newTvSeriesSearchResponse(title, "$mainUrl/tv/$id", TvType.TvSeries) {
                    this.posterUrl = poster
                    this.year = item.firstAirDate?.take(4)?.toIntOrNull()
                }
            }
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val isMovie = url.contains("/movie/")
        val tmdbId = url.substringAfterLast("/").toIntOrNull() ?: return null

        return if (isMovie) {
            val detailsUrl = "$TMDB_API/movie/$tmdbId?api_key=$API_KEY&append_to_response=videos"
            val movie = app.get(detailsUrl).parsedSafe<TmdbMovieDetails>() ?: return null

            val payload = MediaPayload(id = tmdbId, type = "movie")
            newMovieLoadResponse(movie.title, url, TvType.Movie, payload.toJson()) {
                this.posterUrl = movie.posterPath?.let { "$IMAGE_BASE$it" }
                this.plot = movie.overview
                this.year = movie.releaseDate?.take(4)?.toIntOrNull()
                movie.runtime?.let { addDuration(it.toString()) }
                movie.voteAverage?.let { addScore(it.toString()) }
                movie.videos?.results?.firstOrNull { it.site == "YouTube" && it.type == "Trailer" }?.key?.let {
                    addTrailer("https://www.youtube.com/watch?v=$it")
                }
            }
        } else {
            val detailsUrl = "$TMDB_API/tv/$tmdbId?api_key=$API_KEY"
            val show = app.get(detailsUrl).parsedSafe<TmdbTvDetails>() ?: return null

            val episodes = mutableListOf<Episode>()
            val seasonCount = show.numberOfSeasons ?: 1

            for (s in 1..seasonCount) {
                val seasonUrl = "$TMDB_API/tv/$tmdbId/season/$s?api_key=$API_KEY"
                val seasonDetails = app.get(seasonUrl).parsedSafe<TmdbSeasonDetails>() ?: continue

                seasonDetails.episodes.forEach { ep ->
                    val epNumber = ep.episodeNumber ?: 1
                    val payload = MediaPayload(id = tmdbId, type = "tv", season = s, episode = epNumber)
                    episodes.add(
                        newEpisode(payload.toJson()) {
                            this.name = ep.name
                            this.season = s
                            this.episode = epNumber
                            this.posterUrl = ep.stillPath?.let { "$IMAGE_BASE$it" }
                            this.description = ep.overview
                        }
                    )
                }
            }

            newTvSeriesLoadResponse(show.name, url, TvType.TvSeries, episodes) {
                this.posterUrl = show.posterPath?.let { "$IMAGE_BASE$it" }
                this.plot = show.overview
                this.year = show.firstAirDate?.take(4)?.toIntOrNull()
                show.voteAverage?.let { addScore(it.toString()) }
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCdn: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val payload = parseJson<MediaPayload>(data)
        val tmdbId = payload.id

        val sortedSources = StreamSettingsManager.getRuntimeSortedSources()

        coroutineScope {
            sortedSources.map { source ->
                async {
                    runCatching {
                        val streamUrl = if (payload.type == "tv") {
                            source.getTvUrl(tmdbId, payload.season ?: 1, payload.episode ?: 1)
                        } else {
                            source.getMovieUrl(tmdbId)
                        } ?: return@runCatching

                        loadExtractor(
                            url = streamUrl,
                            referer = source.referer,
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
        @JsonProperty("overview") val overview: String?,
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
