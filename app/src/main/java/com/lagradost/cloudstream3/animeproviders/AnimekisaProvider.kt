package com.lagradost.cloudstream3.animeproviders

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import org.json.JSONObject
import org.jsoup.Jsoup
import java.util.*
import kotlin.collections.ArrayList


class AnimekisaProvider : MainAPI() {

    override val mainUrl = "https://animekisa.in"
    override val name = "Animekisa"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val usesWebView = true
    override val supportedTypes = setOf(
        TvType.AnimeMovie,
        TvType.OVA,
        TvType.Anime,
    )

    data class Response (
        @JsonProperty("html") val html: String
    )

    override suspend fun getMainPage(): HomePageResponse {
        val urls = listOf(
            Pair("$mainUrl/ajax/list/recently_updated?type=tv", "Recently Updated Anime"),
            Pair("$mainUrl/ajax/list/recently_updated?type=movie", "Recently Updated Movies"),
            Pair("$mainUrl/ajax/list/recently_added?type=tv", "Recently Added Anime"),
            Pair("$mainUrl/ajax/list/recently_added?type=movie", "Recently Added Movies"),

            )

        val items = ArrayList<HomePageList>()

        for (i in urls) {
            try {
                val response = JSONObject(
                    app.get(
                        i.first,
                    ).text
                ).getString("html") // I won't make a dataclass for this shit
                val document = Jsoup.parse(response)
                val results = document.select("div.flw-item").map {
                    val filmPoster = it.selectFirst("> div.film-poster")
                    val filmDetail = it.selectFirst("> div.film-detail")
                    val nameHeader = filmDetail.selectFirst("> h3.film-name > a")
                    val title = nameHeader.text().replace(" (Dub)", "")
                    val href =
                        nameHeader.attr("href").replace("/watch/", "/anime/")
                            .replace("-episode-.*".toRegex(), "/")
                    val isDub =
                        filmPoster.selectFirst("> div.film-poster-quality")?.text()?.contains("DUB")
                            ?: false
                    val poster = filmPoster.selectFirst("> img").attr("data-src")
                    val set: EnumSet<DubStatus> =
                        EnumSet.of(if (isDub) DubStatus.Dubbed else DubStatus.Subbed)
                    AnimeSearchResponse(title, href, this.name, TvType.Anime, poster, null, set)
                }
                items.add(HomePageList(i.second, results))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (items.size <= 0) throw ErrorLoadingException()
        return HomePageResponse(items)
    }
    override suspend fun search(query: String): List<SearchResponse> {
        return app.get("$mainUrl/search/?keyword=$query").document.select("div.flw-item").map {
            val title = it.selectFirst("h3 a").text()
            val url = it.selectFirst("a.film-poster-ahref").attr("href")
                .replace("watch/","anime/").replace(Regex("(-episode-(\\d+)\\/\$|-episode-(\\d+)\$|-episode-full|-episode-.*-.(\\/|))"),"")
            val poster = it.selectFirst(".film-poster img").attr("data-src")
            AnimeSearchResponse(
                title,
                url,
                this.name,
                TvType.Anime,
                poster,
                null,
                if (title.contains("(DUB)") || title.contains("(Dub)")) EnumSet.of(
                    DubStatus.Dubbed
                ) else EnumSet.of(DubStatus.Subbed),
            )
        }.toList()
    }
    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url, timeout = 120).document
        val poster = doc.selectFirst(".mb-2 img").attr("src") ?: doc.selectFirst("head meta[property=og:image]").attr("content")
        val title = doc.selectFirst("h1.heading-name a").text()
        val description = doc.selectFirst("div.description p").text().trim()
        val genres = doc.select("div.row-line a").map { it.text() }
        val test = if (doc.selectFirst("div.dp-i-c-right").toString().contains("Airing")) ShowStatus.Ongoing else ShowStatus.Completed
        val episodes = doc.select("div.tab-content ul li.nav-item").map {
            val link = it.selectFirst("a").attr("href")
            AnimeEpisode(link)
        }
        val type = if (doc.selectFirst(".dp-i-stats").toString().contains("Movies")) TvType.AnimeMovie else TvType.Anime
        return newAnimeLoadResponse(title, url, type) {
            posterUrl = poster
            addEpisodes(DubStatus.Subbed, episodes)
            showStatus = test
            plot = description
            tags = genres
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        app.get(data).document.select("#servers-list ul.nav li a").apmap {
            val server = it.attr("data-embed")
            loadExtractor(server, data, callback)
        }
        return true
    }
}