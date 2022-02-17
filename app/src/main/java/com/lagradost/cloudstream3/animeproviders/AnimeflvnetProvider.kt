package com.lagradost.cloudstream3.animeproviders

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import java.util.*
import kotlin.collections.ArrayList

class AnimeflvnetProvider:MainAPI() {
    companion object {
        fun getType(t: String): TvType {
            return if (t.contains("OVA") || t.contains("Especial")) TvType.OVA
            else if (t.contains("Película")) TvType.AnimeMovie
            else TvType.Anime
        }
    }
    override val mainUrl: String
        get() = "https://m.animeflv.net"
    override val name: String
        get() = "Animeflv.net"
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
            Pair("$mainUrl/browse?type[]=movie&order=updated", "Películas"),
            Pair("$mainUrl/browse?status[]=2&order=default", "Animes"),
            Pair("$mainUrl/browse?status[]=1&order=rating", "En emision"),
        )
        val items = ArrayList<HomePageList>()
        items.add(
            HomePageList(
                "Últimos episodios",
                app.get("https://www3.animeflv.net/").document.select("main.Main ul.ListEpisodios li").map {
                    val title = it.selectFirst("strong.Title").text()
                    val poster = it.selectFirst("span img").attr("src")
                    val epRegex = Regex("(-(\\d+)\$)")
                    val url = it.selectFirst("a").attr("href").replace(epRegex,"")
                        .replace("ver/","anime/")
                    val epNum = it.selectFirst("span.Capi").text().replace("Episodio ","").toIntOrNull()
                    AnimeSearchResponse(
                        title,
                        fixUrl(url),
                        this.name,
                        TvType.Anime,
                        fixUrl(poster),
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
                val doc = app.get(i.first).document
                val home = doc.select("ul.List-Animes li.Anime").map {
                    val title = it.selectFirst("h2.title").text()
                    val poster = it.selectFirst(".Image img").attr("src")
                    AnimeSearchResponse(
                        title,
                        fixUrl(it.selectFirst("a").attr("href")),
                        this.name,
                        TvType.Anime,
                        fixUrl(poster),
                        null,
                        if (title.contains("Latino") || title.contains("Castellano")) EnumSet.of(DubStatus.Dubbed) else EnumSet.of(DubStatus.Subbed),
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

        val url = "${mainUrl}/browse?q=${query}"
        val doc = app.get(url).document
        val episodes = doc.select("ul.List-Animes li.Anime").map {
            val title = it.selectFirst("h2.title").text()
            val href = fixUrl(it.selectFirst("a").attr("href"))
            val image = it.selectFirst(".Image img").attr("src")
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
        return ArrayList(episodes)
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val title = doc.selectFirst("h1.Title").text()
        val description = doc.selectFirst(".Anime > header:nth-child(1) > p:nth-child(3)").text().replace("Sinopsis: ","")
        val poster = doc.selectFirst(".Image  img").attr("src")
        val episodes = doc.select("li.Episode").map { li ->
            val href = fixUrl(li.selectFirst("a").attr("href"))
            AnimeEpisode(
                fixUrl(href), "Episodio" + li.selectFirst("a").text().replace(title,"")
            )
        }
        val type = doc.selectFirst("span.Type.A").text()
        val genre = doc.select("a.Tag")
            .map { it?.text()?.trim().toString() }

        val status = when (doc.selectFirst("article.Anime.Single.Bglg header p strong.Anm-On")?.text()) {
            "En emisión" -> ShowStatus.Ongoing
            "Finalizado" -> ShowStatus.Completed
            else -> null
        }
        return newAnimeLoadResponse(title, url, getType(type)) {
            posterUrl = fixUrl(poster)
            addEpisodes(DubStatus.Subbed, episodes)
            showStatus = status
            plot = description
            tags = genre
        }
    }
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        app.get(data).document.select("script").apmap { script ->
            if (script.data().contains("var videos = {")) {
                val linkRegex = Regex("""(https:.*?\.html.*)""")
                val videos = linkRegex.findAll(script.data()).map {
                    it.value.replace("\\/", "/")
                }.toList()
                val serversRegex = Regex("(https?:\\/\\/(www\\.)?[-a-zA-Z0-9@:%._\\+~#=]{1,256}\\.[a-zA-Z0-9()]{1,6}\\b([-a-zA-Z0-9()@:%_\\+.~#?&\\/\\/=]*))")
                serversRegex.findAll(videos.toString()).map {
                    it.value.replace("https://embedsb.com/e/","https://watchsb.com/e/")
                }.toList().apmap {
                    for (extractor in extractorApis) {
                        if (it.startsWith(extractor.mainUrl)) {
                            extractor.getSafeUrl(it, data)?.apmap {
                                callback(it)
                            }
                        }
                    }
                }
            }
        }
        return true
    }
}
