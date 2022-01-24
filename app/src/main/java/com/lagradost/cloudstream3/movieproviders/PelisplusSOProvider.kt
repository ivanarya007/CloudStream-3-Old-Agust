package com.lagradost.cloudstream3.movieproviders


import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.extractors.Pelisplus

import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.extractorApis
import org.jsoup.Jsoup
import java.util.*

class PelisplusSOProvider:MainAPI() {
    override val mainUrl: String
        get() = "https://pelisplus.so"
    override val name: String
        get() = "Pelisplus.so" //Also scrapes from Pelisplus.icu
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
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val iframeLink = Jsoup.parse(app.get(data).text).selectFirst(".tab-video")?.attr("data-video") ?: return false
        val vidstreamObject = Pelisplus("https://pelisplus.icu")
        // https://vidembed.cc/streaming.php?id=MzUwNTY2&... -> MzUwNTY2
        val id = Regex("""id=([^?]*)""").find(iframeLink)?.groupValues?.get(1)
        if (id != null) {
            vidstreamObject.getUrl(id, isCasting, callback) &&  vidstreamObject.getUrl2(id, isCasting, callback ) &&  vidstreamObject.getUrl3(id, isCasting, callback)
        }

        val html = app.get(data).document
        val selector = html.selectFirst(".server-item-1").toString()
        val episodeRegex = Regex("""(https?:\/\/(www\.)?[-a-zA-Z0-9@:%._\+~#=]{1,256}\.[a-zA-Z0-9()]{1,6}\b([-a-zA-Z0-9()@:%_\+.~#?&\/\/=]*))""")
        val links = episodeRegex.findAll(selector).map {
            it.value.replace("https://pelispng.online/v/","https://www.fembed.com/v/").replace("https://dood.ws","https://dood.la")
                .replace("https://fembed-hd.com","https://embedsito.com")     }.toList()
        for (link in links) {
            for (extractor in extractorApis) {
                if (link.startsWith(extractor.mainUrl)) {
                    extractor.getSafeUrl(link, data)?.forEach {
                        it.name += " Latino 2"
                        callback(it)
                    }
                }
            }
        }
        val selector2 = html.selectFirst(".server-item-0").toString()
        val linkssub = episodeRegex.findAll(selector2).map {
            it.value.replace("https://pelispng.online/v/","https://www.fembed.com/v/").replace("https://dood.ws","https://dood.la")
                .replace("https://fembed-hd.com","https://embedsito.com")      }.toList()
        for (link in linkssub) {
            for (extractor in extractorApis) {
                if (link.startsWith(extractor.mainUrl)) {
                    extractor.getSafeUrl(link, data)?.forEach {
                        it.name += " Subtitulado 2"
                        callback(it)
                    }
                }
            }
        }
        val selector3 = html.selectFirst(".server-item-2").toString()
        val linkscast = episodeRegex.findAll(selector3).map {
            it.value.replace("https://pelispng.online/v/","https://www.fembed.com/v/").replace("https://dood.ws","https://dood.la")
                .replace("https://fembed-hd.com","https://embedsito.com")
        }.toList()
        for (link in linkscast) {
            for (extractor in extractorApis) {
                if (link.startsWith(extractor.mainUrl)) {
                    extractor.getSafeUrl(link, data)?.forEach {
                        it.name += " Castellano 2"
                        callback(it)
                    }
                }
            }
        }
        return true
    }

}