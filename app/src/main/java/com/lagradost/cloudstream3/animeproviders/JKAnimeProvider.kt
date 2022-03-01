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

    data class MainSearch (
        @JsonProperty("animes") val animes: List<Animes>,
        @JsonProperty("anime_types") val animeTypes: AnimeTypes
    )

    data class Animes (
        @JsonProperty("id") val id: String,
        @JsonProperty("slug") val slug: String,
        @JsonProperty("title") val title: String,
        @JsonProperty("image") val image: String,
        @JsonProperty("synopsis") val synopsis: String,
        @JsonProperty("type") val type: String,
        @JsonProperty("status") val status: String,
        @JsonProperty("thumbnail") val thumbnail: String
    )

    data class AnimeTypes (
        @JsonProperty("TV") val TV: String,
        @JsonProperty("OVA") val OVA: String,
        @JsonProperty("Movie") val Movie: String,
        @JsonProperty("Special") val Special: String,
        @JsonProperty("ONA") val ONA: String,
        @JsonProperty("Music") val Music: String
    )

    override suspend fun search(query: String): ArrayList<SearchResponse> {
        val main = app.get("https://jkanime.net/ajax/ajax_search/?q=$query").text
        val json = parseJson<MainSearch>(main)
        val search = ArrayList<AnimeSearchResponse>()
         json.animes.forEach {
            val title = it.title
            val href = "$mainUrl/${it.slug}"
            val image = "https://cdn.jkanime.net/assets/images/animes/image/${it.slug}.jpg"
          search.add(
              AnimeSearchResponse(
                title,
                href,
                this.name,
                TvType.Anime,
                image,
                null,
                if (title.contains("Latino") || title.contains("Castellano")) EnumSet.of(
                    DubStatus.Dubbed
                ) else EnumSet.of(DubStatus.Subbed),
            )
          )
        }

        return ArrayList(search)
    }


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
        val episodes = ArrayList<AnimeEpisode>()
        val json = jsoneps.substringAfter("{\"number\":\"").substringBefore("\",\"title\"").toInt()
        (1..json).map { it }.map {
            val link = "$url$it"
            episodes.add(AnimeEpisode(link))
        }



        return newAnimeLoadResponse(title, url, getType(type)) {
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
        app.get(data).document.select("script").apmap { script ->
            if (script.data().contains("var video = []")) {
                val videos = script.data().replace("\\/", "/")
                fetchUrls(videos).map {
                    it.replace("https://jkanime.net/jkfembed.php?u=","https://suzihaza.com/v/")
                        .replace("https://jkanime.net/jkokru.php?u=","http://ok.ru/videoembed/")
                        .replace("https://jkanime.net/jkvmixdrop.php?u=","https://mixdrop.co/e/")
                }.toList().apmap {
                    loadExtractor(it, data, callback)
                }
            }
        }
        return true
    }
}