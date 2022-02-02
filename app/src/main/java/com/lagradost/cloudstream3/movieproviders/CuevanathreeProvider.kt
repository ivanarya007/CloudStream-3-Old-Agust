package com.lagradost.cloudstream3.movieproviders

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.extractors.Evoload
import com.lagradost.cloudstream3.extractors.FEmbed
import com.lagradost.cloudstream3.extractors.StreamTape
import com.lagradost.cloudstream3.utils.*
import java.util.*

class CuevanathreeProvider:MainAPI() {
    override val mainUrl: String
        get() = "https://cuevana3.io"
    override val name: String
        get() = "Cuevana"
    override val lang = "es"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
    )
    override val vpnStatus = VPNStatus.MightBeNeeded //Due to evoload sometimes not loading

    override suspend fun getMainPage(): HomePageResponse {
        val items = ArrayList<HomePageList>()
        val urls = listOf(
            Pair("$mainUrl", "Recientemente actualizadas"),
            Pair("$mainUrl/estrenos/", "Estrenos"),
        )

        for (i in urls) {
            try {
                val soup = app.get(i.first).document
                val home = soup.select("section li.xxx.TPostMv").map {
                    val title = it.selectFirst("h2.Title").text()
                    val link = it.selectFirst("a").attr("href")
                    TvSeriesSearchResponse(
                        title,
                        link,
                        this.name,
                        if (link.contains("/pelicula/")) TvType.Movie else TvType.TvSeries,
                        it.selectFirst("img.lazy").attr("data-src"),
                        null,
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
        val url = "$mainUrl/?s=${query}"
        val document = app.get(url).document

        return document.select("li.xxx.TPostMv").map {
            val title = it.selectFirst("h2.Title").text()
            val href = it.selectFirst("a").attr("href")
            val image = it.selectFirst("img.lazy").attr("data-src")
            val isSerie = href.contains("/serie/")

            if (isSerie) {
                TvSeriesSearchResponse(
                    title,
                    href,
                    this.name,
                    TvType.TvSeries,
                    image,
                    null,
                    null
                )
            } else {
                MovieSearchResponse(
                    title,
                    href,
                    this.name,
                    TvType.Movie,
                    image,
                    null
                )
            }
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val soup = app.get(url, timeout = 120).document

        val title = soup.selectFirst("h1.Title").text()
        val description = soup.selectFirst(".Description p")?.text()?.trim()
        val poster: String? = soup.selectFirst(".movtv-info div.Image img").attr("data-src")
        val episodes = soup.select(".TPostMv article").map { li ->
            val href = li.select("a").attr("href")
            val epThumb = try {
               li.select("img.lazy").attr("src")
            } catch (e: Exception) {
                li.select("img.lazy").attr("data-src")
            } catch (e: Exception) {
                li.select("div.Image img").attr("data-src")
            }
            val name = li.selectFirst("h2.Title").text()
            TvSeriesEpisode(
                name,
                null,
                null,
                href,
                fixUrl(epThumb)
            )
        }
        return when (val tvType = if (episodes.isEmpty()) TvType.Movie else TvType.TvSeries) {
            TvType.TvSeries -> {
                TvSeriesLoadResponse(
                    title,
                    url,
                    this.name,
                    tvType,
                    episodes,
                    poster,
                    null,
                    description,
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
                    null,
                    description,
                )
            }
            else -> null
        }
    }

}