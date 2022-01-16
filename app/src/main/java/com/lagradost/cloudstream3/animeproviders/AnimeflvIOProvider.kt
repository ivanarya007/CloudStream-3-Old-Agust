package com.lagradost.cloudstream3.movieproviders

import com.lagradost.cloudstream3.*

import com.lagradost.cloudstream3.extractors.Vidstream

import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.getQualityFromName
import org.jsoup.Jsoup
import java.net.URI
import java.util.*
import kotlin.collections.ArrayList

class AnimeflvIOProvider:MainAPI() {
    override val mainUrl: String
        get() = "https://animeflv.io"
    override val name: String
        get() = "Animeflv.io"
    override val lang = "es"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.AnimeMovie,
        TvType.ONA,
        TvType.Anime,
    )

    override fun getMainPage(): HomePageResponse {
        val items = ArrayList<HomePageList>()
        val urls = listOf(
            Pair("$mainUrl/series", "Series actualizadas",),
            Pair("$mainUrl/peliculas", "Peliculas actualizadas"),
        )
        items.add(HomePageList("Estrenos", Jsoup.parse(app.get(mainUrl).text).select("div#owl-demo-premiere-movies .pull-left").map{
            val title = it.selectFirst("p").text()
            AnimeSearchResponse(
                title,
                fixUrl(it.selectFirst("a").attr("href")),
                this.name,
                TvType.Anime,
                it.selectFirst("img").attr("src"),
                it.selectFirst("span.year").toString().toIntOrNull(),
                EnumSet.of(DubStatus.Subbed),
            )
        }))
        for (i in urls) {
            try {
                val response = app.get(i.first)
                val soup = Jsoup.parse(response.text)
                val home = soup.select("div.item-pelicula").map {
                    val title = it.selectFirst(".item-detail p").text()
                    val poster = it.selectFirst("figure img").attr("src")
                    AnimeSearchResponse(
                        title,
                        fixUrl(it.selectFirst("a").attr("href")),
                        this.name,
                        TvType.Anime,
                        poster,
                        null,
                        if (title.contains("Latino") || title.contains("Castellano")) EnumSet.of(DubStatus.Dubbed) else EnumSet.of(DubStatus.Subbed),
                    )
                }

                items.add(HomePageList(i.second, home))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (items.size <= 0) throw ErrorLoadingException()
        return HomePageResponse(items)
    }

    override fun search(query: String): ArrayList<SearchResponse> {

        val headers = mapOf(
            "Host" to "animeflv.io",
            "User-Agent" to USER_AGENT,
            "X-Requested-With" to "XMLHttpRequest",
            "DNT" to "1",
            "Alt-Used" to "animeflv.io",
            "Connection" to "keep-alive",
            "Referer" to "https://animeflv.io",
        )
        val url = "${mainUrl}/search.html?keyword=${query}"
        val html = app.get(
            url,
            headers = headers
        ).text
        val document = Jsoup.parse(html)
         val episodes = document.select(".item-pelicula.pull-left").map {
            val title = it.selectFirst("div.item-detail p").text()
            val href = fixUrl(it.selectFirst("a").attr("href"))
            var image = it.selectFirst("figure img").attr("src")
            val isMovie = href.contains("/pelicula/")
             if (image.contains("/static/img/picture.png")) { image = null}

            if (isMovie) {
                MovieSearchResponse(
                    title,
                    href,
                    this.name,
                    TvType.AnimeMovie,
                    image,
                    null
                )
            } else {
                AnimeSearchResponse(
                    title,
                    href,
                    this.name,
                    TvType.Anime,
                    image,
                    null,
                    EnumSet.of(DubStatus.Subbed),
                )
            }
        }
        return ArrayList(episodes)
    }

    override fun load(url: String): LoadResponse? {
        // Gets the url returned from searching.
        val html = app.get(url).text
        val soup = Jsoup.parse(html)
        val title = soup.selectFirst(".info-content h1").text()
        val description = soup.selectFirst("span.sinopsis")?.text()?.trim()
        val poster: String? = soup.selectFirst(".poster img").attr("src")
        val episodes = soup.select(".item-season-episodes a").map { li ->
            val href = fixUrl(li.selectFirst("a").attr("href"))
            val name = li.selectFirst("a").text()
            AnimeEpisode(
                href, name,
            )
        }.reversed()

        val year = Regex("(\\d*)").find(soup.select(".info-half").text())

        val tvType = if (url.contains("/pelicula/")) TvType.AnimeMovie else TvType.Anime
        val genre = soup.select(".content-type-a a")
            .map { it?.text()?.trim().toString().replace(", ","") }
        val duration = Regex("""(\d*)""").find(
            soup.select("p.info-half:nth-child(4)").text())

        return when (tvType) {
            TvType.Anime -> {
                return newAnimeLoadResponse(title, url, tvType) {
                    japName = null
                    engName = title
                    posterUrl = poster
                    this.year = null
                    addEpisodes(DubStatus.Subbed, episodes)
                    plot = description
                    tags = genre

                    showStatus = null
                }
            }
            TvType.AnimeMovie -> {
                MovieLoadResponse(
                    title,
                    url,
                    this.name,
                    tvType,
                    url,
                    poster,
                    year.toString().toIntOrNull(),
                    description,
                    null,
                    null,
                    genre,
                    duration.toString().toIntOrNull(),
                    )
            }
            else -> null
        }
    }
    override fun loadLinks(
        data: String,
        isCasting: Boolean,
        // These callbacks are functions you should call when you get a link to a subtitle file or media file.
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // "?: return" is a very useful statement which returns if the iframe link isn't found.
        val iframeLink = Jsoup.parse(app.get(data).text).selectFirst(".tab-video")?.attr("data-video") ?: return false
        // In this case the video player is a vidstream clone and can be handled by the vidstream extractor.
        // This case is a both unorthodox and you normally do not call extractors as they detect the url returned and does the rest.
        val vidstreamObject = Vidstream("https://animeid.cc")
        // https://vidembed.cc/streaming.php?id=MzUwNTY2&... -> MzUwNTY2
        val id = Regex("""id=([^?]*)""").find(iframeLink)?.groupValues?.get(1)
        if (id != null) {
            vidstreamObject.getUrl(id, isCasting, callback)
        }
        val html = app.get(fixUrl(iframeLink)).text
        val soup = Jsoup.parse(html)
        val servers = soup.select(".list-server-items > .linkserver").mapNotNull { li ->
            if (!li?.attr("data-video").isNullOrEmpty()) {
                Pair(li.text(), fixUrl(li.attr("data-video")))
            } else {
                null
            }
        }
        servers.forEach {
            // When checking strings make sure to make them lowercase and trimmed because edgecases like "beta server " wouldn't work otherwise.
            if (it.first.trim().equals("beta server", ignoreCase = true)) {
                // Group 1: link, Group 2: Label
                // Regex can be used to effectively parse small amounts of json without bothering with writing a json class.
                val sourceRegex = Regex("""sources:[\W\w]*?file:\s*["'](.*?)["'][\W\w]*?label:\s*["'](.*?)["']""")

                val serverHtml = app.get(it.second, headers = mapOf("referer" to iframeLink)).text
                sourceRegex.findAll(serverHtml).forEach { match ->
                    callback.invoke(
                        ExtractorLink(
                            this.name,
                            match.groupValues.getOrNull(2)?.let { "${this.name} $it" } ?: this.name,
                            match.groupValues[1],
                            it.second,
                            // Useful function to turn something like "1080p" to an app quality.
                            getQualityFromName(match.groupValues.getOrNull(2) ?: ""),
                            // Kinda risky
                            // isM3u8 makes the player pick the correct extractor for the source.
                            // If isM3u8 is wrong the player will error on that source.
                            URI(match.groupValues[1]).path.endsWith(".m3u8"),
                        )
                    )
                }
            }
        }

        return true
    }

}