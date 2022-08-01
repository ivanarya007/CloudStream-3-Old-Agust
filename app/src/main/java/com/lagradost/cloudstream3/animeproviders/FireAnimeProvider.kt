package com.lagradost.cloudstream3.animeproviders

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.M3u8Helper.Companion.generateM3u8
import kotlin.collections.ArrayList


class FireAnimeProvider:MainAPI() {
    override var mainUrl = "https://api.fireani.me"
    override var name = "FireAnime"
    override var lang = "de"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.AnimeMovie,
        TvType.OVA,
        TvType.Anime,
    )

    fun getDubStatus(isSub: Int): DubStatus {
        return if (isSub == 0)
            DubStatus.Dubbed
        else DubStatus.Subbed
    }

    data class MainJson (
        @JsonProperty("episode"      ) val episode      : Int?    = null,
        @JsonProperty("time"         ) val time         : Int?    = null,
        @JsonProperty("info"         ) val info         : String? = null,
        @JsonProperty("status"       ) val status       : String? = null,
        @JsonProperty("isSub"        ) val isSub        : Int?    = null,
        @JsonProperty("url"          ) val url          : String? = null,
        @JsonProperty("title"        ) val title        : String? = null,
        @JsonProperty("imgPoster"    ) val imgPoster    : String? = null,
        @JsonProperty("imgWallpaper" ) val imgWallpaper : String? = null,
        @JsonProperty("season"       ) val season       : Int?    = null,
        @JsonProperty("part"         ) val part         : String? = null,
        @JsonProperty("langId"       ) val langId       : Int?    = null,
        @JsonProperty("imgThumb"     ) val imgThumb     : String? = null,
        @JsonProperty("episodes"     ) var episodes     : Int?    = null
    )

    override suspend fun getMainPage(page: Int, request : MainPageRequest): HomePageResponse {
        val urls = listOf(
            Pair("$mainUrl/api/public/airing", "Airings"),
            Pair("$mainUrl/api/public/new","Neu hinzugefügt")
        )

        val items = ArrayList<HomePageList>()

        urls.apmap { (url, name) ->
            val postdata = if (name.contains("new")) mapOf(
                Pair("langs[0]","de-DE"),
                Pair("limit","30"),
                Pair("offset","0")
            ) else mapOf(Pair("langs[0]","de-DE"))
            val response = app.post(url,
            data = postdata).text
            val json = parseJson<List<MainJson>>(response)
            val home =  json.map {
               val title = it.title
               val poster = "$mainUrl/api/get/img/${it.imgPoster}-normal-poster.webp"
               val urldata = it.url
               val episode = it.episode ?: it.episodes
               val issubbed = it.isSub ?: 1
               newAnimeSearchResponse(title!!, urldata!!) {
                   this.posterUrl = poster
                   if (url.contains("airing")) addDubStatus(getDubStatus(issubbed.toString().toIntOrNull()!!), episode)
               }
            }
            items.add(HomePageList(name, home))
        }

        if (items.size <= 0) throw NotImplementedError()
        return HomePageResponse(items)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val response = app.post(
            "$mainUrl/api/public/search",
            data = mapOf(Pair("q", query))
        ).text
        val json = parseJson<List<MainJson>>(response)
        return json.map {
            val title = it.title
            val poster = "$mainUrl/api/get/img/${it.imgPoster}-normal-poster.webp"
            val urldata = it.url
            val episode = it.episode ?: it.episodes
            val issubbed = it.isSub ?: 1
            newAnimeSearchResponse(title!!, urldata!!) {
                this.posterUrl = poster
                addDubStatus(getDubStatus(issubbed.toString().toIntOrNull()!!), episode)
            }
        }
    }

    data class LoadJson (
        @JsonProperty("status"   ) var status   : Int?      = null,
        @JsonProperty("response" ) var response : Response? = Response()
    )

    data class Response (
        @JsonProperty("title"        ) var title        : String?            = null,
        @JsonProperty("season"       ) var season       : Int?               = null,
        @JsonProperty("part"         ) var part         : String?            = null,
        @JsonProperty("description"  ) var description  : String?            = null,
        @JsonProperty("imgPoster"    ) var imgPoster    : String?            = null,
        @JsonProperty("imgWallpaper" ) var imgWallpaper : String?            = null,
        @JsonProperty("generes"      ) var generes      : ArrayList<Generes> = arrayListOf(),
        @JsonProperty("tags"         ) var tags         : ArrayList<Tags>  = arrayListOf(),
        @JsonProperty("fsk"          ) var fsk          : Int?               = null,
        @JsonProperty("voting"       ) var voting       : String?            = null
    )

    data class Generes (
        @JsonProperty("id"     ) var id     : Int?    = null,
        @JsonProperty("genere" ) var genere : String? = null
    )

    data class Tags (
        @JsonProperty("id"          ) var id          : Int?    = null,
        @JsonProperty("name"        ) var name        : String? = null,
        @JsonProperty("description" ) var description : String? = null,
        @JsonProperty("category"    ) var category    : String? = null,
        @JsonProperty("isAdult"     ) var isAdult     : Int?    = null
    )



    data class EpisodesJson (
        @JsonProperty("status"   ) var status   : Int?                = null,
        @JsonProperty("response" ) var response : ArrayList<EpisodesResponse> = arrayListOf()
    )

    data class EpisodesResponse (
        @JsonProperty("img"     ) var img     : String? = null,
        @JsonProperty("episode" ) var episode : Int?    = null,
        @JsonProperty("version" ) var version : String? = null,
        @JsonProperty("title"   ) var title   : String? = null
    )

    override suspend fun load(url: String): LoadResponse {
        val response = app.post("$mainUrl/api/public/anime",
        data = mapOf(Pair("url", url.replace("$mainUrl/","")))).text
        val json = parseJson<LoadJson>(response)
        val title = json.response?.title
        val poster = "$mainUrl/api/get/img/${json.response?.imgPoster}-normal-poster.webp"
        val description = json.response?.description!!
        val genres = json.response?.generes?.map { it.genere }?.toList()
        val episodesresponse = app.post("$mainUrl/api/public/episodes",
            data = mapOf(Pair("url", url.replace("$mainUrl/","")))).text
        val episodesjson = parseJson<EpisodesJson>(episodesresponse)
        val episodes = episodesjson.response.map { ep ->
            val name = ep.title
            val data =
                mapOf(
                    Pair("url", url.replace("$mainUrl/","")),
                    Pair("ep",ep.episode)
                )

            Episode(data.toString(), name)
        }
        return newAnimeLoadResponse(title!!, url, TvType.Anime) {
            engName = title
            posterUrl = poster
            addEpisodes(DubStatus.Subbed, episodes)
            tags = genres?.map { it.toString() }
            plot = description
        }
    }

    data class LoadlinksJson (
        @JsonProperty("status"   ) var status   : Int?      = null,
        @JsonProperty("response" ) var response : ResponseEps? = ResponseEps()
    )

    data class ResponseEps (
        @JsonProperty("ep"   ) var ep   : Ep?               = Ep(),
        @JsonProperty("next" ) var next : ArrayList<Next> = arrayListOf()
    )

    data class Ep (
        @JsonProperty("id"      ) var id      : Int?              = null,
        @JsonProperty("img"     ) var img     : String?           = null,
        @JsonProperty("episode" ) var episode : Int?              = null,
        @JsonProperty("title"   ) var title   : String?           = null,
        @JsonProperty("links"   ) var links   : ArrayList<Links>  = arrayListOf(),
        @JsonProperty("cdns"    ) var cdns    : ArrayList<Cdns> = arrayListOf()
    )

    data class Links (
        @JsonProperty("id"     ) var id     : Int?    = null,
        @JsonProperty("isSub"  ) var isSub  : Int?    = null,
        @JsonProperty("hoster" ) var hoster : String? = null

    )

    data class Cdns (
        @JsonProperty("id"    ) var id    : Int? = null,
        @JsonProperty("isSub" ) var isSub : Int? = null
    )

    data class Next (
        @JsonProperty("img"     ) var img     : String? = null,
        @JsonProperty("episode" ) var episode : Int?    = null,
        @JsonProperty("title"   ) var title   : String? = null,
        @JsonProperty("version" ) var version : String? = null
    )

    data class LinksResponse (
        @JsonProperty("status"   ) var status   : Int?    = null,
        @JsonProperty("response" ) var links : String? = null
    )


    data class FireCDNJson (
        @JsonProperty("status" ) var status : Int?    = null,
        @JsonProperty("proxy"  ) var proxy  : String? = null,
        @JsonProperty("file"   ) var file   : String? = null
    )

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val animeid = data.substringAfter("url=").substringBefore(",")
        val episodeid = data.substringAfter("ep=").substringBefore("}")
        val response = app.post("$mainUrl/api/public/episode",
        data = mapOf(
            Pair("url",animeid),
            Pair("ep",episodeid)
        )).text
        val json = parseJson<LoadlinksJson>(response)
        val links = json.response?.ep?.links
        val cdns = json.response?.ep?.cdns
        if (links != null) {
           links.apmap {
               val responselink = app.post("$mainUrl/api/public/link",
               data = mapOf(
                   Pair("id",it.id.toString())
               )
                   ).text
               val jsonlinks = parseJson<LinksResponse>(responselink)
               loadExtractor(jsonlinks.links!!, subtitleCallback, callback)
           }
        }
        if (cdns!!.isNotEmpty()) {
            cdns.apmap {
                val responsecdn = app.post("$mainUrl/api/public/cdn",
                data = mapOf(
                    Pair("id",it.id.toString()
                    )
                )
                ).text
                val jsoncdn = parseJson<LinksResponse>(responsecdn)
                val cdnid = jsoncdn.links?.substringAfter("firecdn.cc/")
                val deploycdn = app.post("https://firecdn.cc/api/stream/deploy",
                data = mapOf(Pair("file",cdnid!!))).text
                val jsonfirecdn = parseJson<FireCDNJson>(deploycdn)
                val test = "${jsonfirecdn.proxy}/api/${jsonfirecdn.file}.m3u8"
                generateM3u8(
                    "FireCDN",
                    test,
                    "",
                ).apmap {
                    callback(
                        ExtractorLink(
                            "FireCDN",
                            "FireCDN",
                            it.url,
                            "",
                            getQualityFromName(it.quality.toString()),
                            true
                        )
                    )
                }
            }
        }
        return true
    }
}