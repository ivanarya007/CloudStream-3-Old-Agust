package com.lagradost.cloudstream3.animeproviders

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.extractors.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.Jsoup
import java.net.URLDecoder
import java.util.*
import kotlin.collections.ArrayList


class AnimefenixProvider:MainAPI() {

    override val mainUrl = "https://animefenix.com"
    override val name = "Animefenix"
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
            Pair("$mainUrl/", "Animes"),
            Pair("$mainUrl/animes?type[]=movie&order=default", "Peliculas", ),
            Pair("$mainUrl/animes?type[]=ova&order=default", "OVA's", ),
        )

        val items = ArrayList<HomePageList>()

        items.add(
            HomePageList(
                "Últimos episodios",
                app.get(mainUrl).document.select(".capitulos-grid div.item").map {
                    val title = it.selectFirst("div.overtitle").text()
                    val poster = it.selectFirst("a img").attr("src")
                    val epRegex = Regex("(-(\\d+)\$)")
                    val url = it.selectFirst("a").attr("href").replace(epRegex,"")
                        .replace("/ver/","/")
                    val epNum = it.selectFirst(".is-size-7").text().replace("Episodio ","").toIntOrNull()
                    AnimeSearchResponse(
                        title,
                        url,
                        this.name,
                        TvType.Anime,
                        poster,
                        null,
                        if (title.contains("Latino") || title.contains("Castellano")) EnumSet.of(
                            DubStatus.Dubbed
                        ) else EnumSet.of(DubStatus.Subbed),
                        subEpisodes = epNum,
                        dubEpisodes = epNum,
                    )
                })
        )

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
        val search =
            Jsoup.parse(app.get("$mainUrl/animes?q=$query", timeout = 120).text).select(".list-series article").map {
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
                    if (title.contains("Latino") || title.contains("Castellano")) EnumSet.of(DubStatus.Dubbed) else EnumSet.of(
                        DubStatus.Subbed),
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

        val href = doc.selectFirst(".anime-page__episode-list li")
        val hrefmovie = href.selectFirst("a").attr("href")
        val type = if (doc.selectFirst("ul.has-text-light").text()
                .contains("Película") && episodes.size == 1
        ) TvType.AnimeMovie else TvType.Anime

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
                feRegex.findAll(html).map {
                    "https://embedsito.com/v/"+(it.value).replace("player=2&amp;code=","").replace("&","")
                }.toList().apmap {
                    loadExtractor(it, data, callback)
                }
                val mp4Regex = Regex("player=3&amp;code=(.*)&")
                mp4Regex.findAll(html).map {
                    "https://www.mp4upload.com/embed-"+(it.value).replace("player=3&amp;code=","").replace("&","")+".html"
                }.toList().apmap {
                    loadExtractor(it, data, callback)
                }
                val youruploadRegex = Regex("player=6&amp;code=(.*)&")
                youruploadRegex.findAll(html).map {
                    "https://www.yourupload.com/embed/"+(it.value).replace("player=6&amp;code=","").replace("&","")
                }.toList().apmap {
                    loadExtractor(it, data, callback)
                }
                val okruRegex = Regex("player=12&amp;code=(.*)&")
                okruRegex.findAll(html).map {
                    "https://ok.ru/videoembed/"+(it.value).replace("player=12&amp;code=","").replace("&","")
                }.toList().apmap {
                    loadExtractor(it, data, callback)
                }
                val sendvidRegex = Regex("player=4&amp;code=(.*)&")
                sendvidRegex.findAll(html).map {
                    "https://sendvid.com/"+(it.value).replace("player=4&amp;code=","").replace("&","")
                }.toList().apmap {
                    loadExtractor(it, data, callback)
                }
                val fireloadRegex = Regex("player=22&amp;code=(.*)&")
                val link6 = fireloadRegex.findAll(html).map {
                    "https://www.animefenix.com/stream/fl.php?v="+URLDecoder.decode((it.value).replace("player=22&amp;code=","").replace("&",""), "UTF-8")
                }.toList()
                if (link6.isNotEmpty()) {
                    link6.apmap {
                        val fireload = app.get(it).text
                        val extractedlink = fireload.substringAfter("{\"file\":\"").substringBefore("\",\"type")
                            .replace("\\/", "/")
                        val quality = fireload.substringAfter("\"label\":\"").substringBefore("\"}")
                        val testlink = app.get("https://$extractedlink")
                        if (testlink.url.contains("error")) null else
                            callback(
                                ExtractorLink(
                                    "Fireload",
                                    "Fireload $quality",
                                    "https://$extractedlink",
                                    "",
                                    Qualities.Unknown.value,
                                    isM3u8 = false
                                )
                            )
                    }
                }
                val amazonRegex = Regex("player=9&amp;code=(.*)&")
                val link7 = amazonRegex.findAll(html).map {
                    "https://www.animefenix.com/stream/amz.php?v="+(it.value).replace("player=9&amp;code=","").replace("&","")
                }.toList()
                if (link7.isNotEmpty()) {
                    link7.apmap {
                        val amazon = app.get(it).text
                        val extractedlink = amazon.substringAfter("{\"file\":\"").substringBefore("\",\"type")
                            .replace("\\/", "/")
                        val quality = amazon.substringAfter("\"label\":\"").substringBefore("\"}")
                        if (extractedlink.contains("amazon"))
                            callback(
                                ExtractorLink(
                                    "Amazon",
                                    "Amazon $quality",
                                    extractedlink,
                                    "",
                                    Qualities.Unknown.value,
                                    isM3u8 = false
                                )
                            )
                    }
                }
                val amazonesRegex = Regex("player=11&amp;code=(.*)&")
                val link8 = amazonesRegex.findAll(html).map {
                    "https://www.animefenix.com/stream/amz.php?v="+(it.value).replace("player=11&amp;code=","").replace("&","")
                }.toList()
                if (link8.isNotEmpty()) {
                    link8.apmap {
                        val amazones = app.get("$it&ext=es").text
                        val extractedlink = amazones.substringAfter("{\"file\":\"").substringBefore("\",\"type")
                            .replace("\\/", "/")
                        val quality = amazones.substringAfter("\"label\":\"").substringBefore("\"}")
                        if (extractedlink.contains("amazon"))
                            callback(
                                ExtractorLink(
                                    "AmazonES",
                                    "AmazonES $quality",
                                    extractedlink,
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