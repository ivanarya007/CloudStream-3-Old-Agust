package com.lagradost.cloudstream3.animeproviders

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.extractors.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.extractorApis
import com.lagradost.cloudstream3.utils.httpsify
import org.jsoup.Jsoup
import java.net.URLDecoder
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
        TvType.OVA,
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

    override suspend fun load(url: String): LoadResponse? {
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

        val href= doc.selectFirst(".anime-page__episode-list li")
        val hrefmovie = href.selectFirst("a").attr("href")
        val type = if (doc.selectFirst("ul.has-text-light").text().contains("Película") && episodes.size == 1) TvType.AnimeMovie else TvType.Anime

        return when (type) {
            TvType.Anime -> {
                return newAnimeLoadResponse(title, url, type) {
                    japName = null
                    engName = title
                    posterUrl = poster
                    addEpisodes(DubStatus.Subbed, episodes)
                    plot = description
                    tags = genres
                    showStatus = status

                }
            }
            TvType.AnimeMovie -> {
                MovieLoadResponse(
                    title,
                    url,
                    this.name,
                    type,
                    hrefmovie,
                    poster,
                    null,
                    description,
                    null,
                    null,
                    genres,
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
        app.get(data).document.select(".player-container script").apmap { script ->
            if (script.data().contains("var tabsArray =")) {
                val html = app.get(data).text
                val feRegex = Regex("player=2&amp;code(.*)&")
                val link1 = feRegex.findAll(html).map {
                    "https://embedsito.com/v/"+(it.value).replace("player=2&amp;code=","").replace("&","")
                }.toList() // --> https://embesito.com/v/jt47bkh307jl
                for (link in link1) {
                    for (extractor in extractorApis) {
                        if (link.startsWith(extractor.mainUrl)) {
                            extractor.getSafeUrl(link, data)?.apmap {
                                callback(it)
                            }
                        }
                    }
                }
                val mp4Regex = Regex("player=3&amp;code=(.*)&")
                val link2 = mp4Regex.findAll(html).map {
                    "https://www.mp4upload.com/embed-"+(it.value).replace("player=3&amp;code=","").replace("&","")+".html"
                }.toList() // --> https://www.mp4upload.com/embed-g4yu7p4u.html
                for (link in link2) {
                    for (extractor in extractorApis) {
                        if (link.startsWith(extractor.mainUrl)) {
                            extractor.getSafeUrl(link, data)?.apmap {
                                callback(it)
                            }
                        }
                    }
                }
                val youruploadRegex = Regex("player=6&amp;code=(.*)&")
                val link3 = youruploadRegex.findAll(html).map {
                    "https://www.yourupload.com/embed/"+(it.value).replace("player=6&amp;code=","").replace("&","")
                }.toList() // --> https://www.yourupload.com/embed/g4yu7p4u
                for (link in link3) {
                    for (extractor in extractorApis) {
                        if (link.startsWith(extractor.mainUrl)) {
                            extractor.getSafeUrl(link, data)?.apmap {
                                callback(it)
                            }
                        }
                    }
                }
                val okruRegex = Regex("player=12&amp;code=(.*)&")
                val link4 = okruRegex.findAll(html).map {
                    "https://ok.ru/videoembed/"+(it.value).replace("player=12&amp;code=","").replace("&","")
                }.toList() // --> https://ok.ru/videoembed/3276668340777
                for (link in link4) {
                    for (extractor in extractorApis) {
                        if (link.startsWith(extractor.mainUrl)) {
                            extractor.getSafeUrl(link, data)?.apmap {
                                callback(it)
                            }
                        }
                    }
                }
                val sendvidRegex = Regex("player=4&amp;code=(.*)&")
                val link5 = sendvidRegex.findAll(html).map {
                    "https://sendvid.com/"+(it.value).replace("player=4&amp;code=","").replace("&","")
                }.toList() // --> https://sendvid.com/embed/ny8fzfd8
                for (link in link5) {
                    for (extractor in extractorApis) {
                        if (link.startsWith(extractor.mainUrl)) {
                            extractor.getSafeUrl(link, data)?.apmap {
                                callback(it)
                            }
                        }
                    }
                }
                val fireloadRegex = Regex("player=22&amp;code=(.*)&")
                val link6 = fireloadRegex.findAll(html).map {
                    "https://www.animefenix.com/stream/fl.php?v="+URLDecoder.decode((it.value).replace("player=22&amp;code=","").replace("&",""), "UTF-8")
                }.toList().first()
                val directlinkRegex = Regex("(file\":\".*.mp4.*\",\"t)")
                with(app.get(link6, allowRedirects = false)) {
                    directlinkRegex.find(this.text)?.let { link ->
                        callback(
                            ExtractorLink(
                                "Fireload",
                                "Fireload",
                                ("https://"+link.value).replace("file\":\"","").replace("\",\"t",""),
                                "",
                                Qualities.Unknown.value,
                                isM3u8 = false
                            )
                        )
                    }
                }
            }
        }
        return true
    }
}