package com.lagradost.cloudstream3.animeproviders

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.extractorApis
import org.jsoup.Jsoup
import java.util.*
import kotlin.collections.ArrayList


class AnimefenixProvider:MainAPI() {

    override val mainUrl: String
        get() = "https://animefenix.com"
    override val name: String
        get() = "Animefenix"
    override val lang = "es"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.AnimeMovie,
        TvType.ONA,
        TvType.Anime,
    )

    override suspend fun getMainPage(): HomePageResponse {
        val urls = listOf(
            Pair("$mainUrl/", "Últimos animes agregados",),
            Pair("$mainUrl/animes?estado[]=2", "Animes finalizados",),
            Pair("$mainUrl/animes?type[]=movie&order=default", "Peliculas",),
            Pair("$mainUrl/animes?type[]=ova&order=default", "OVA's",),
        )

        val items = ArrayList<HomePageList>()
        for (i in urls) {
            try {
                val response = app.get(i.first)
                val soup = Jsoup.parse(response.text)
                val home = soup.select(".list-series article").map {
                    val title = it.selectFirst("h3 a").text()
                    val poster = it.selectFirst("figure img").attr("src")
                    AnimeSearchResponse(
                        title,
                        it.selectFirst("a").attr("href"),
                        this.name,
                        TvType.Anime,
                        poster,
                        null,
                        if (title.contains("Latino")) EnumSet.of(DubStatus.Dubbed) else EnumSet.of(DubStatus.Subbed),
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

    override suspend fun search(query: String): ArrayList<SearchResponse> {
        val search = Jsoup.parse(app.get("$mainUrl/animes?q=$query", timeout = 120).text).select(".list-series article").map {
            val title = it.selectFirst("h3 a").text()
            val href = it.selectFirst("a").attr("href")
            val image = it.selectFirst("figure img").attr("src")
            AnimeSearchResponse(
                title,
                href,
                this.name,
                TvType.Anime,
                fixUrl(image),
                null,
                if (title.contains("Latino") || title.contains("Castellano")) EnumSet.of(DubStatus.Dubbed) else EnumSet.of(DubStatus.Subbed),
            )
        }
        return ArrayList(search)
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = Jsoup.parse(app.get(url, timeout = 120).text)
        val poster = doc.selectFirst(".image > img").attr("src")
        val title = doc.selectFirst("h1.title.has-text-orange").text()
        val description = doc.selectFirst("p.has-text-light").text()
        val genres = doc.select(".genres a").map { it.text() }
        val status = when (doc.selectFirst(".is-narrow-desktop a.button")?.text()) {
            "Emisión" -> ShowStatus.Ongoing
            "Finalizado" -> ShowStatus.Completed
            else -> null
        }
        val episodes = doc.select(".anime-page__episode-list li").map {
            val name = it.selectFirst("span").text()
            val link = it.selectFirst("a").attr("href")
            AnimeEpisode(link, name)
        }.reversed()
        return newAnimeLoadResponse(title, url, TvType.Anime) {
            posterUrl = poster
            addEpisodes(DubStatus.Subbed, episodes)
            showStatus = status
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
        //Just this two servers can be extracted for now
        val selector = app.get(data).document.select("div.player-container script").toString()
        val feRegex = Regex("player=2&amp;code(.*)&")
        val link1 = feRegex.findAll(selector).map {
            "https://www.fembed.com/v/"+(it.value).replace("player=2&amp;code=","").replace("&","")
        }.toList() // --> https://www.fembed.com/v/jt47bkh307jl
        for (link in link1) {
            for (extractor in extractorApis) {
                if (link.startsWith(extractor.mainUrl)) {
                    extractor.getSafeUrl(link, data)?.forEach {
                        callback(it)
                    }
                }
            }
        }
        val mp4Regex = Regex("player=3&amp;code=(.*)&amp")
        val link2 = mp4Regex.findAll(selector).map {
            "https://www.mp4upload.com/embed-"+(it.value).replace("player=3&amp;code=","").replace("&amp","")+".html"
        }.toList() // --> https://www.mp4upload.com/embed-g4yu7p4u.html
        for (link in link2) {
            for (extractor in extractorApis) {
                if (link.startsWith(extractor.mainUrl)) {
                    extractor.getSafeUrl(link, data)?.forEach {
                        callback(it)
                    }
                }
            }
        }
        return true
    }
}