package com.lagradost.cloudstream3.movieproviders

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import java.util.*

class VumooProvider:MainAPI() {
    override val mainUrl: String
        get() = "https://vumoo.to"
    override val name: String
        get() = "Vumoo"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
    )
    override suspend fun getMainPage(): HomePageResponse {
        val items = ArrayList<HomePageList>()
        val urls = listOf(
            Pair("$mainUrl/movies/", "Movies"),
            Pair("$mainUrl/tv-series/", "TV Series"),
        )
        for ((url, name) in urls) {
            try {
                val soup = app.get(url).document
                val home = soup.select("div.row .intro").map {
                    val title = it.selectFirst("h3").text()
                    val link = it.selectFirst("a").attr("href")
                    TvSeriesSearchResponse(
                        title,
                        fixUrl(link),
                        this.name,
                        if (link.contains("/movies/")) TvType.Movie else TvType.TvSeries,
                        it.selectFirst("img").attr("src"),
                        null,
                        null,
                    )
                }

                items.add(HomePageList(name, home))
            } catch (e: Exception) {
                logError(e)
            }
        }

        if (items.size <= 0) throw ErrorLoadingException()
        return HomePageResponse(items)
    }

    data class SearchJson (
        @JsonProperty("suggestions") val suggestions: List<Suggestions>
    )

    data class Data (
        @JsonProperty("href") val href: String,
        @JsonProperty("image") val image: String,
        @JsonProperty("type") val type: String
    )

    data class Suggestions (
        @JsonProperty("value") val value: String,
        @JsonProperty("data") val data: Data
    )

    override suspend fun search(query: String): List<SearchResponse> {
        val tokenid = app.get("$mainUrl/javascripts/vumoo-v1.0.1.min.js").text
        val tokenregex = Regex("serviceUrl:\"\\/search\\?t=.*\",min")
        val token = tokenregex.find(tokenid)?.value?.replace("\",min","")?.replace("serviceUrl:\"","")
        val url = "$mainUrl$token&q=$query"
        val test = app.get(url).text
        val json = parseJson<SearchJson>(test)
        return json.suggestions.map {
            val title = it.value
            val isMovie = it.data.type.contains("movies")
            val image = it.data.image.replace(Regex("(\\/w(\\d+)\\/)"),"/w500/")
                .replace(Regex("\\/s(\\d+)\\/"),"/s1000/")
            println(image)
            val href = fixUrl(it.data.href)
            if (isMovie) {
                MovieSearchResponse(
                    title,
                    href,
                    this.name,
                    TvType.Movie,
                    image,
                    null
                )
            } else {
                TvSeriesSearchResponse(
                    title,
                    href,
                    this.name,
                    TvType.TvSeries,
                    image,
                    null,
                    null
                )
            }
        }
    }


    override suspend fun load(url: String): LoadResponse? {
        val soup = app.get(url, timeout = 120).document
        val title = soup.selectFirst("div.row.film h1").text()
        val description = soup.selectFirst("div.row.film span")?.text()?.trim()
        val poster = soup.selectFirst("div.row.film img.poster").attr("src")
            .replace(Regex("(\\/w(\\d+)\\/)"),"/w500/")
            .replace(Regex("\\/s(\\d+)\\/"),"/s1000/")
        val episodes = soup.select("ul.episodes li a").map {
            val link = it.attr("embedurl")
            TvSeriesEpisode(
                null,
                null,
                null,
                link
            )
        }
        val tvType = if (url.contains("/movies/")) TvType.Movie else TvType.TvSeries
        val datamovie = if (tvType == TvType.Movie) {
            soup.selectFirst("ul.episodes li a.play").attr("embedurl")
        } else null
        return when (tvType) {
            TvType.TvSeries -> {
                TvSeriesLoadResponse(
                    title,
                    url,
                    this.name,
                    tvType,
                    episodes.reversed(),
                    poster,
                    null,
                    description,
                )
            }
            TvType.Movie -> {
                MovieLoadResponse(
                    title,
                    url,
                    this.name,
                    tvType,
                    datamovie!!,
                    poster,
                    null,
                    description,
                )
            }
            else -> null
        }
    }

    data class ServerJson (
        @JsonProperty("playlist") val playlist: List<Playlist>?
    )

    data class Tracks (
        @JsonProperty("file") val file: String?,
        @JsonProperty("kind") val kind: String?,
        @JsonProperty("label") val label: String?,
        @JsonProperty("default") val default: Boolean?
    )

    data class Playlist (
        @JsonProperty("image") val image: String?,
        @JsonProperty("file") val filestream: String?,
        @JsonProperty("tracks") val tracks: List<Tracks>?
    )


    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data, referer = mainUrl).document
        doc.select("script").map { script ->
            if (script.data().contains("jwplayer('player').setup(")) {
                val scripttext = script.data().substringAfter("jwplayer('player').setup(").substringBefore(");")
                val mapped = parseJson<ServerJson>(scripttext)
                mapped.playlist?.forEach { playlist ->
                    if (playlist.filestream?.contains("m3u8") == true) {
                        callback(
                            ExtractorLink(
                                "Vumoo",
                                "Vumoo",
                                "https:"+playlist.filestream,
                                data,
                                Qualities.Unknown.value,
                                true,
                            )
                        )
                    }
                    playlist.tracks?.forEach {
                        val lang = it.label
                        val sublink = "https://"+it.file
                        subtitleCallback(
                            SubtitleFile(lang!!, sublink)
                        )
                    }
                }
            }
        }
        return true
    }
}
