package com.lagradost.cloudstream3.animeproviders

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import java.util.*
import kotlin.collections.ArrayList


class MundoDonghuaProvider : MainAPI() {

    override val mainUrl = "https://www.mundodonghua.com"
    override val name = "MundoDonghua"
    override val lang = "es"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Donghua,
    )

    override suspend fun getMainPage(): HomePageResponse {
        val urls = listOf(
            Pair("$mainUrl/lista-donghuas", "Donghuas"),
        )

        val items = ArrayList<HomePageList>()
        items.add(
            HomePageList(
                "Últimos episodios",
                app.get(mainUrl, timeout = 120).document.select("div.row .col-xs-4").map {
                    val title = it.selectFirst("h5").text()
                    val poster = it.selectFirst(".fit-1 img").attr("src")
                    val epRegex = Regex("(\\/(\\d+)\$)")
                    val url = it.selectFirst("a").attr("href").replace(epRegex,"").replace("/ver/","/donghua/")
                    val epnumRegex = Regex("(\\d+)\$|((\\d+) \$)")
                    AnimeSearchResponse(
                        title,
                        fixUrl(url),
                        this.name,
                        TvType.Donghua,
                        fixUrl(poster),
                        null,
                        if (title.contains("Latino") || title.contains("Castellano")) EnumSet.of(
                            DubStatus.Dubbed
                        ) else EnumSet.of(DubStatus.Subbed),

                    )
                })
        )
        for (i in urls) {
            try {
                val home = app.get(i.first, timeout = 120).document.select(".col-xs-4").map {
                    val title = it.selectFirst(".fs-14").text()
                    val poster = it.selectFirst(".fit-1 img").attr("src")
                    AnimeSearchResponse(
                        title,
                        fixUrl(it.selectFirst("a").attr("href")),
                        this.name,
                        TvType.Donghua,
                        fixUrl(poster),
                        null,
                        if (title.contains("Latino") || title.contains("Castellano")) EnumSet.of(
                            DubStatus.Dubbed
                        ) else EnumSet.of(DubStatus.Subbed),
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
            app.get("$mainUrl/busquedas/$query", timeout = 120).document.select(".col-xs-4").map {
                val title = it.selectFirst(".fs-14").text()
                val href = fixUrl(it.selectFirst("a").attr("href"))
                val image = it.selectFirst(".fit-1 img").attr("src")
                AnimeSearchResponse(
                    title,
                    href,
                    this.name,
                    TvType.Donghua,
                    fixUrl(image),
                    null,
                    if (title.contains("Latino") || title.contains("Castellano")) EnumSet.of(
                        DubStatus.Dubbed
                    ) else EnumSet.of(DubStatus.Subbed),
                )
            }
        return ArrayList(search)
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url, timeout = 120).document
        val poster = doc.selectFirst("head meta[property=og:image]").attr("content")
        val title = doc.selectFirst(".ls-title-serie").text()
        val description = doc.selectFirst("p.text-justify.fc-dark").text()
        val genres = doc.select("span.label.label-primary.f-bold").map { it.text() }
        val status = when (doc.selectFirst("div.col-md-6.col-xs-6.align-center.bg-white.pt-10.pr-15.pb-0.pl-15 p span.badge.bg-default")?.text()) {
            "En Emisión" -> ShowStatus.Ongoing
            "Finalizada" -> ShowStatus.Completed
            else -> null
        }
        val episodes = doc.select("ul.donghua-list a").map {
            val name = it.selectFirst(".fs-16").text()
            val link = it.attr("href")
            AnimeEpisode(fixUrl(link), name)
        }.reversed()
        return newAnimeLoadResponse(title, url, TvType.Donghua) {
            posterUrl = poster
            addEpisodes(DubStatus.Subbed, episodes)
            showStatus = status
            plot = description
            tags = genres
        }
    }
    data class Protea (
        @JsonProperty("source") val source: List<Source>,
        @JsonProperty("poster") val poster: String
    )

    data class Source (
        @JsonProperty("file") val file: List<String>,
        @JsonProperty("label") val label: List<String>,
        @JsonProperty("type") val type: List<String>,
        @JsonProperty("default") val default: List<String>
    )


    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        app.get(data).document.select("script").apmap { script ->
            if (script.data().contains("eval(function(p,a,c,k,e,d)")) {
                val packedRegex = Regex("eval\\(function\\(p,a,c,k,e,.*\\)\\)")
                packedRegex.findAll(script.data()).map {
                    it.value
                }.toList().apmap {
                    val unpack = getAndUnpack(it).replace("diasfem","suzihaza")
                    fetchUrls(unpack).apmap { url ->
                        loadExtractor(url, data, callback)
                    }
                    if (unpack.contains("protea_tab")) {
                        val protearegex = Regex("(protea_tab.*slug.*,type)")
                        val slug = protearegex.findAll(unpack).map {
                            it.value.replace(Regex("(protea_tab.*slug\":\")"),"").replace("\"},type","")
                        }.first()
                        val requestlink = "https://www.mundodonghua.com/api_donghua.php?slug=$slug"
                        val response = app.get(requestlink, headers =
                        mapOf("Host" to "www.mundodonghua.com",
                            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; rv:91.0) Gecko/20100101 Firefox/91.0",
                            "Accept" to "*/*",
                            "Accept-Language" to "en-US,en;q=0.5",
                            "Referer" to data,
                            "X-Requested-With" to "XMLHttpRequest",
                            "DNT" to "1",
                            "Connection" to "keep-alive",
                            "Sec-Fetch-Dest" to "empty",
                            "Sec-Fetch-Mode" to "no-cors",
                            "Sec-Fetch-Site" to "same-origin",
                            "TE" to "trailers",
                            "Pragma" to "no-cache",
                            "Cache-Control" to "no-cache",)
                        ).text.replace("\\/", "/").replace("=","%3D")
                        val nemonicregex = Regex("www\\.nemonicplayer\\.xyz\\/player\\/play\\/[a-zA-Z0-9]{0,8}[a-zA-Z0-9_-]+.*label")
                        nemonicregex.findAll(response).map {
                            it.value.replace("\",\"label","")
                                .replace("\",\"type\":\"video/mp4\",\"default\":\"true\"},{\"file\":\"","")
                                .replace(Regex("\":\"(\\d+)p\""),"")
                                .replace(",\"type\":\"video/mp4\"},{\"file\":\"","")
                                .replace(Regex("(\\d+)p"),"")
                                .replace("\":\"","")
                        }.toList().apmap { nemonicurl ->
                            nemonicurl.split("//").apmap { urlext ->
                                    callback(
                                        ExtractorLink(
                                            "Protea",
                                            "Protea",
                                            "https://"+urlext,
                                            "",
                                            Qualities.Unknown.value,
                                            isM3u8 = false
                                        )
                                    )
                            }
                        }
                    }
                }
            }
        }
        return true
    }
}