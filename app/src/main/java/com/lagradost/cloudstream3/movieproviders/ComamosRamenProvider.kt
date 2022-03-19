package com.lagradost.cloudstream3.movieproviders

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson

class ComamosRamenProvider : MainAPI() {
    override var mainUrl = "https://comamosramen.com"
    override var name = "ComamosRamen"
    override val lang = "es"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
    )

    data class HomeMain (
        @JsonProperty("props") var props : HomeProps?  = HomeProps(),
    )

    data class HomeProps (
        @JsonProperty("pageProps") var pageProps : HomePageProps? = HomePageProps(),
    )

    data class HomePageProps (
        @JsonProperty("data") var data : HomeData? = HomeData()
    )

    data class HomeData (
        @JsonProperty("sections") var sections : List<HomeSections> = listOf(),
    )

    data class HomeSections (
        @JsonProperty("data") var data : List<HomeDatum> = listOf()
    )
    data class HomeDatum (
        @JsonProperty("_id") var Id                : String,
        @JsonProperty("status") var status            : Status? = Status(),
        @JsonProperty("title") var title             : String,
        @JsonProperty("img") var img               : Img    = Img(),
        @JsonProperty("createdBy") var createdBy         : String? = null,
        @JsonProperty("updatedAt") var updatedAt         : String? = null,
        @JsonProperty("lastEpisodeEdited") var lastEpisodeEdited : String? = null
    )

    data class Status (
        @JsonProperty("isOnAir") var isOnAir : Boolean? = null,
        @JsonProperty("isSubtitling") var isSubtitling : Boolean? = null
    )
    data class Img (
        @JsonProperty("vertical") var vertical   : String? = null,
        @JsonProperty("horizontal") var horizontal : String? = null
    )
    override suspend fun getMainPage(): HomePageResponse {
        val items = ArrayList<HomePageList>()
        val tvseries = ArrayList<TvSeriesSearchResponse>()
        val test = app.get(mainUrl).document
            test.select("script[type=application/json]").map { script ->
                if (script.data().contains("pageProps")) {
                    val json = parseJson<HomeMain>(script.data())
                     json.props?.pageProps?.data?.sections?.map { sections ->
                        sections.data.map { data ->
                            val title = data.title
                            val link = "$mainUrl/v/${data.Id}/${title.replace(" ","-")}"
                            val img = "https://img.comamosramen.com/${data.img.vertical}-high.jpg"
                            tvseries.add(TvSeriesSearchResponse(
                                title,
                                link,
                                this.name,
                                TvType.TvSeries,
                                img,
                                null,
                                null
                            ))
                        }
                    }
                }
            }
        items.add(HomePageList("Doramas", tvseries))

        if (items.size <= 0) throw ErrorLoadingException()
        return HomePageResponse(items)
    }

    data class SearchOb (
        @JsonProperty("props") var props        : SearchProps?            = SearchProps(),
    )
    data class SearchProps (
        @JsonProperty("pageProps" ) var pageProps : SearchPageProps? = SearchPageProps(),
    )

    data class SearchPageProps (
        @JsonProperty("data"         ) var data         : DataSS?         = DataSS(),
    )
    data class DataSS (
        @JsonProperty("data" ) var datum : ArrayList<DatumSearch> = arrayListOf()
    )

    data class DatumSearch (
        @JsonProperty("_id") var Id    : String? = null,
        @JsonProperty("img") var img   : Img?    = Img(),
        @JsonProperty("title") var title : String? = null
    )

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/buscar/${query}"
        val document = app.get(url).document
        val search = ArrayList<TvSeriesSearchResponse>()
         document.select("script[type=application/json]").map { script ->
            val json = parseJson<SearchOb>(script.data())
              json.props?.pageProps?.data?.datum?.map {
                 val title = it.title
                 val img = "https://img.comamosramen.com/${it.img?.vertical}-high.jpg"
                 val link = "$mainUrl/v/${it.Id}/${title?.replace(" ", "-")}"
                 search.add(TvSeriesSearchResponse(
                     title!!,
                     link,
                     this.name,
                     TvType.TvSeries,
                     img,
                     null,
                     null
                 ))
             }
        }
        return search
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        val title = document.selectFirst(".col-11").text()
        val poster = document.selectFirst("img.rounded-8").attr("src")
        val desc = document.selectFirst(".text-black").text()
        val tags = document.select(".col-lg-8 div div.btn")
            .map { it?.text()?.trim().toString() }
        return TvSeriesLoadResponse(
            title,
            url,
            this.name,
            TvType.Movie,
            emptyList(),
            poster,
            null,
            desc,
            null,
            null,
            null,
            tags
        )
    }
}