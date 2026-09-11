package com.streamcore

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

/**
 * Native decryptor ported from cinepro-org/core (VidNest provider).
 * Decodes VidNest custom base64 substitution cipher without needing external servers.
 */
object VidnestDecryptor {
    private const val ALPHABET = "RB0fpH8ZEyVLkv7c2i6MAJ5u3IKFDxlS1NTsnGaqmXYdUrtzjwObCgQP94hoeW+/="
    private val REVERSE_MAP = IntArray(128) { 64 }.apply {
        for (i in ALPHABET.indices) {
            val c = ALPHABET[i].code
            if (c < 128) this[c] = i
        }
    }

    fun decode(input: String?): String? {
        if (input.isNullOrBlank()) return null
        var padded = input
        val mod = padded.length % 4
        if (mod != 0) {
            padded += "=".repeat(4 - mod)
        }

        val out = ByteArrayOutputStream(padded.length)
        var i = 0
        while (i < padded.length) {
            val chunk0 = padded[i].code
            val chunk1 = padded[i + 1].code
            val chunk2 = padded[i + 2]
            val chunk3 = padded[i + 3]

            val c0 = if (chunk0 < 128) REVERSE_MAP[chunk0] else 64
            val c1 = if (chunk1 < 128) REVERSE_MAP[chunk1] else 64
            val c2 = if (chunk2 == '=') 64 else if (chunk2.code < 128) REVERSE_MAP[chunk2.code] else 64
            val c3 = if (chunk3 == '=') 64 else if (chunk3.code < 128) REVERSE_MAP[chunk3.code] else 64

            val b0 = ((c0 shl 2) or (c1 shr 4)) and 0xFF
            out.write(b0)

            if (c2 != 64) {
                val b1 = (((c1 and 0x0F) shl 4) or (c2 shr 2)) and 0xFF
                out.write(b1)
            }
            if (c3 != 64) {
                val b2 = (((c2 and 0x03) shl 6) or c3) and 0xFF
                out.write(b2)
            }
            i += 4
        }
        return String(out.toByteArray(), StandardCharsets.UTF_8)
    }
}

object VidnestExtractor {
    private const val BASE_API = "https://new.vidnest.fun"
    private val API_HEADERS = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/150 Safari/537.36",
        "Accept" to "application/json, text/javascript, */*; q=0.01",
        "Referer" to "https://vidnest.fun/",
        "Origin" to "https://vidnest.fun"
    )

    // Verified working servers from VidNest network (moviebox removed as it returns dummy loop)
    private val WORKING_SERVERS = listOf("allmovies", "hollymoviehd", "klikxxi")

    suspend fun resolveStreams(
        tmdbId: Int,
        isMovie: Boolean,
        season: Int? = null,
        episode: Int? = null,
        callback: (ExtractorLink) -> Unit
    ) = coroutineScope {
        WORKING_SERVERS.map { server ->
            async {
                runCatching {
                    val url = if (isMovie) {
                        "$BASE_API/$server/movie/$tmdbId"
                    } else {
                        "$BASE_API/$server/tv/$tmdbId/${season ?: 1}/${episode ?: 1}"
                    }

                    val res = app.get(url, headers = API_HEADERS, timeout = 10L).parsedSafe<VidnestEncryptedResponse>()
                    val rawDecrypted = VidnestDecryptor.decode(res?.data) ?: return@async

                    when (server) {
                        "allmovies" -> parseAllMovies(rawDecrypted, callback)
                        "hollymoviehd" -> parseHollyMovieHd(rawDecrypted, callback)
                        "klikxxi" -> parseKlikxxi(rawDecrypted, callback)
                    }
                }
            }
        }.awaitAll()
    }

    private fun parseAllMovies(json: String, callback: (ExtractorLink) -> Unit) {
        val root = runCatching { parseJson<AllMoviesPayload>(json) }.getOrNull() ?: return
        root.streams?.forEach { stream ->
            val link = stream.url ?: return@forEach
            val lang = stream.language ?: "Auto"
            val headers = stream.headers ?: emptyMap()
            val referer = headers["Referer"] ?: "https://slast430did.com"
            callback(
                ExtractorLink(
                    source = "AllMovies",
                    name = "[AllMovies] $lang",
                    url = link,
                    referer = referer,
                    quality = 1080,
                    type = ExtractorLinkType.M3U8,
                    headers = headers
                )
            )
        }
    }

    private fun parseHollyMovieHd(json: String, callback: (ExtractorLink) -> Unit) {
        val root = runCatching { parseJson<HollyMovieHdPayload>(json) }.getOrNull() ?: return
        root.streams?.forEach { stream ->
            val link = stream.url ?: return@forEach
            val lang = stream.language ?: "Auto"
            val headers = stream.headers ?: emptyMap()
            val referer = headers["Referer"] ?: "https://goodstream.cc"
            callback(
                ExtractorLink(
                    source = "HollyMovieHD",
                    name = "[HollyMovieHD] $lang",
                    url = link,
                    referer = referer,
                    quality = 1080,
                    type = ExtractorLinkType.M3U8,
                    headers = headers
                )
            )
        }
    }

    private fun parseKlikxxi(json: String, callback: (ExtractorLink) -> Unit) {
        val root = runCatching { parseJson<KlikxxiPayload>(json) }.getOrNull() ?: return
        root.sources?.forEach { src ->
            val link = src.url ?: return@forEach
            val qualityInt = parseQuality(src.quality)
            callback(
                ExtractorLink(
                    source = "Klikxxi",
                    name = "[Klikxxi] ${src.quality ?: "Auto"}",
                    url = link,
                    referer = "https://klikxxi.me/",
                    quality = qualityInt,
                    type = ExtractorLinkType.M3U8
                )
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

    data class VidnestEncryptedResponse(
        @JsonProperty("data") val data: String? = null,
        @JsonProperty("encrypted") val encrypted: Boolean? = null
    )

    data class AllMoviesPayload(
        @JsonProperty("streams") val streams: List<AllMoviesStream>? = null
    )

    data class AllMoviesStream(
        @JsonProperty("url") val url: String? = null,
        @JsonProperty("type") val type: String? = null,
        @JsonProperty("language") val language: String? = null,
        @JsonProperty("headers") val headers: Map<String, String>? = null
    )

    data class HollyMovieHdPayload(
        @JsonProperty("streams") val streams: List<HollyMovieHdStream>? = null
    )

    data class HollyMovieHdStream(
        @JsonProperty("url") val url: String? = null,
        @JsonProperty("type") val type: String? = null,
        @JsonProperty("language") val language: String? = null,
        @JsonProperty("headers") val headers: Map<String, String>? = null
    )

    data class KlikxxiPayload(
        @JsonProperty("sources") val sources: List<KlikxxiSourceItem>? = null
    )

    data class KlikxxiSourceItem(
        @JsonProperty("url") val url: String? = null,
        @JsonProperty("quality") val quality: String? = null,
        @JsonProperty("type") val type: String? = null
    )
}
