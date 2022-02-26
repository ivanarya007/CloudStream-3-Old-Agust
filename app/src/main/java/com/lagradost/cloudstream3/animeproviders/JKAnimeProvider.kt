package com.lagradost.cloudstream3.animeproviders

import androidx.core.util.rangeTo
import androidx.core.util.toRange
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.extractors.FEmbed
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.extractorApis
import com.lagradost.cloudstream3.utils.loadExtractor
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.List


class JKAnimeProvider : MainAPI() {
    companion object {
        fun getType(t: String): TvType {
            return if (t.contains("OVA") || t.contains("Especial")) TvType.OVA
            else if (t.contains("Pelicula")) TvType.AnimeMovie
            else TvType.Anime
        }
    }

    override val mainUrl = "https://jkanime.net"
    override val name = "JKAnime"
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
            Pair("$mainUrl/directorio/?filtro=fecha&tipo=TV&estado=1&fecha=none&temporada=none&orden=desc", "En emisión"),
            Pair("$mainUrl/directorio/?filtro=fecha&tipo=none&estado=none&fecha=none&temporada=none&orden=none", "Animes"),
            Pair("$mainUrl/directorio/?filtro=fecha&tipo=Movie&estado=none&fecha=none&temporada=none&orden=none", "Películas"),
        )

        val items = ArrayList<HomePageList>()

        for (i in urls) {
            try {
                val home = app.get(i.first).document.select(".g-0").map {
                    val title = it.selectFirst("h5 a").text()
                    val poster = it.selectFirst("img").attr("src")
                    AnimeSearchResponse(
                        title,
                        fixUrl(it.selectFirst("a").attr("href")),
                        this.name,
                        TvType.Anime,
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
            app.get("$mainUrl/buscar/$query/", timeout = 120).document.select("div.col-lg-12  div.row div.col-lg-2.col-md-6.col-sm-6").map {
                val title = it.selectFirst("h5 a").text()
                val href = fixUrl(it.selectFirst("a").attr("href"))
                val image = it.selectFirst(".set-bg").attr("data-setbg")
                AnimeSearchResponse(
                    title,
                    href,
                    this.name,
                    TvType.Anime,
                    fixUrl(image),
                    null,
                    if (title.contains("Latino") || title.contains("Castellano")) EnumSet.of(
                        DubStatus.Dubbed
                    ) else EnumSet.of(DubStatus.Subbed),
                )
            }
        return ArrayList(search)
    }



    data class Numberep (
        @JsonProperty("epnums") val epnums: List<String>
    )

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url, timeout = 120).document
        val poster = doc.selectFirst(".set-bg").attr("data-setbg")
        val title = doc.selectFirst(".anime__details__title > h3").text()
        val type = doc.selectFirst(".anime__details__text").text()
        val description = doc.selectFirst(".anime__details__text > p").text()
        val genres = doc.select("div.col-lg-6:nth-child(1) > ul:nth-child(1) > li:nth-child(2) > a").map { it.text() }
        val status = when (doc.selectFirst("span.enemision")?.text()) {
            "En emisión" -> ShowStatus.Ongoing
            "Concluido" -> ShowStatus.Completed
            else -> null
        }
        val animeID = doc.selectFirst("div.ml-2").attr("data-anime").toInt()
        val animeeps = "https://jkanime.net/ajax/last_episode/$animeID/"
        val jsoneps = app.get(animeeps).text
        val json = jsoneps.substringAfter("{\"number\":\"").substringBefore("\",\"title\"").toInt()
        val test =  (1..json).map { "{\"epnums\":[\""+it+"\"]}" }.toList().toString()
        val jsontest = parseJson<Numberep>(test)
        val subEpisodes = ArrayList<AnimeEpisode>()
        println(jsontest.epnums)

        val epi = subEpisodes.add(
            AnimeEpisode(
            "$url${jsontest.epnums}"
        )
        )
        return newAnimeLoadResponse(title, url, getType(type)) {
            posterUrl = poster
            addEpisodes(DubStatus.Subbed, subEpisodes)
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
        app.get(data).document.select("div.playother p").apmap {
            val encodedurl = it.select("p").attr("data-player")
            val urlDecoded = base64Decode(encodedurl)
            val url = (urlDecoded).replace("https://monoschinos2.com/reproductor?url=", "")
                .replace("https://repro.monoschinos2.com/aqua/sv?url=","")
            for (extractor in extractorApis) {
                if (url.startsWith(extractor.mainUrl)) {
                    extractor.getSafeUrl(url, data)?.apmap {
                        callback(it)
                    }
                }
            }
        }
        return true
    }
}