package com.lagradost.cloudstream3.movieproviders

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.extractors.Pelisplus

import com.lagradost.cloudstream3.extractors.Vidstream
import com.lagradost.cloudstream3.extractors.XStreamCdn
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.loadExtractor
import kotlinx.coroutines.selects.select
import org.jsoup.Jsoup
import java.net.URI
import java.util.*

class PelisplusProvider:MainAPI() {
    override val mainUrl: String
        get() = "https://pelisplus.so"
    override val name: String
        get() = "Pelisplus.so"
    override val lang = "es"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime,
    )
    override suspend fun getMainPage(): HomePageResponse? {
        val items = ArrayList<HomePageList>()
        val urls = listOf(
            Pair("$mainUrl/series", "Series actualizadas",),
            Pair("$mainUrl/", "Peliculas actualizadas"),
        )

        items.add(HomePageList("Estrenos", Jsoup.parse(app.get(mainUrl).text).select("div#owl-demo-premiere-movies .pull-left").map{
            val title = it.selectFirst("p").text()
            TvSeriesSearchResponse(
                title,
                fixUrl(it.selectFirst("a").attr("href")),
                this.name,
                TvType.Movie,
                it.selectFirst("img").attr("src"),
                it.selectFirst("span.year").toString().toIntOrNull(),
                null,
            )
        }))

        for (i in urls) {
            try {
                val response = app.get(i.first)
                val soup = Jsoup.parse(response.text)
                val home = soup.select(".main-peliculas div.item-pelicula").map {
                    val title = it.selectFirst(".item-detail p").text()
                    val titleRegex = Regex("(\\d+)x(\\d+)")
                    TvSeriesSearchResponse(
                        title.replace(titleRegex,""),
                        fixUrl(it.selectFirst("a").attr("href")),
                        this.name,
                        TvType.Movie,
                        it.selectFirst("img").attr("src"),
                        it.selectFirst("span.year").toString().toIntOrNull(),
                        null,
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

    override suspend fun search(query: String): List<SearchResponse> {

        val headers = mapOf(
            "Host" to "pelisplus.so",
            "User-Agent" to USER_AGENT,
            "Accept" to "*/*",
            "Accept-Language" to "en-US,en;q=0.5",
            "Referer" to "https://pelisplus.so/",
            "X-Requested-With" to "XMLHttpRequest",
            "DNT" to "1",
            "Connection" to "keep-alive",
            "Sec-Fetch-Dest" to "document",
            "Sec-Fetch-Mode" to "navigate",
            "Sec-Fetch-Site" to "same-origin",
            "TE" to "trailers",
            "Pragma" to "no-cache",
            "Cache-Control" to "no-cache",
            "Upgrade-Insecure-Requests" to "1"
        )
        val url = "https://pelisplus.so/search.html?keyword=${query}"
        val html = app.get(
            url,
            headers = headers
        ).text
        val document = Jsoup.parse(html)

        return document.select(".item-pelicula.pull-left").map {
            val title = it.selectFirst("div.item-detail p").text()
            val href = fixUrl(it.selectFirst("a").attr("href"))
            val year = it.selectFirst("span.year").text().toIntOrNull()
            val image = it.selectFirst("figure img").attr("src")
            val isMovie = href.contains("/pelicula/")

            if (isMovie) {
                MovieSearchResponse(
                    title,
                    href,
                    this.name,
                    TvType.Movie,
                    image,
                    year
                )
            } else {
                TvSeriesSearchResponse(
                    title,
                    href,
                    this.name,
                    TvType.TvSeries,
                    image,
                    year,
                    null
                )
            }
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        // Gets the url returned from searching.
        val html = app.get(url).text
        val soup = Jsoup.parse(html)

        val title = soup.selectFirst(".info-content h1").text()

        val description = soup.selectFirst("span.sinopsis")?.text()?.trim()
        val poster: String? = soup.selectFirst(".poster img").attr("src")
        val episodes = soup.select(".item-season-episodes a").map { li ->
            val epTitle = li.selectFirst("a").text()
            val epThumb = null
            val href = fixUrl(li.selectFirst("a").attr("href"))
            val epDate = null
            val epNum = null

            TvSeriesEpisode(
                epTitle,
                null,
                epNum,
                href,
                epThumb,
                epDate
            )
        }.reversed()

        val year = Regex("(\\d*)").find(soup.select(".info-half").text())

        // Make sure to get the type right to display the correct UI.
        val tvType = if (url.contains("/pelicula/")) TvType.Movie else TvType.TvSeries
        val tags = soup.select(".content-type-a a")
            .map { it?.text()?.trim().toString().replace(", ","") }
        val duration = Regex("""(\d*)""").find(
            soup.select("p.info-half:nth-child(4)").text())

        return when (tvType) {
            TvType.TvSeries -> {
                TvSeriesLoadResponse(
                    title,
                    url,
                    this.name,
                    tvType,
                    episodes,
                    poster,
                    year.toString().toIntOrNull(),
                    description,
                    ShowStatus.Ongoing,
                    null,
                    null,
                    tags,
                )
            }
            TvType.Movie -> {
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
                    tags,
                    duration.toString().toIntOrNull(),

                    )
            }
            else -> null
        }
    }

    override suspend fun loadLinks(
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
        val vidstreamObject = Pelisplus("https://pelisplus.icu")
        // https://vidembed.cc/streaming.php?id=MzUwNTY2&... -> MzUwNTY2
        val id = Regex("""id=([^?]*)""").find(iframeLink)?.groupValues?.get(1)

        if (id != null) {
            vidstreamObject.getUrl(id, isCasting, callback) &&  vidstreamObject.getUrl2(id, isCasting, callback) && vidstreamObject.getUrl3(id, isCasting, callback)
                    &&  vidstreamObject.getUrl3(id, isCasting, callback)
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

                // Having a referer is often required. It's a basic security check most providers have.
                // Try to replicate what your browser does.
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