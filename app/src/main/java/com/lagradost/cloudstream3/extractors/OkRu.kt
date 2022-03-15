package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.app


open class OkRu : ExtractorApi() {
    override val name = "Okru"
    override val mainUrl = "http://ok.ru"
    override val requiresReferer = false

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val doc = app.get(url).document
        val sources = ArrayList<ExtractorLink>()
        val urlString = doc.select("div[data-options]").attr("data-options")
            .substringAfter("\\\"videos\\\":[{\\\"name\\\":\\\"")
            .substringBefore("]")
       urlString.split("{\\\"name\\\":\\\"").reversed().let { list ->
           list.map {
               val extractedUrl = it.substringAfter("url\\\":\\\"")
                   .substringBefore("\\\"")
                   .replace("\\\\u0026", "&")
               val Quality = it.uppercase().substringBefore("\\\"")
                   .replace("MOBILE","144p")
                   .replace("LOWEST","240p")
                   .replace("LOW","360p")
                   .replace("SD","480p")
                   .replace("HD","720p")
                   .replace("FULL","1080p")
                   .replace("QUAD","1440p")
                   .replace("ULTRA","4k")
               if (extractedUrl.isNotBlank())
                   sources.add(
                       ExtractorLink(
                       name,
                       "$name ${Quality}",
                       extractedUrl,
                       url,
                       getQualityFromName(Quality),
                       isM3u8 = false
                       )
                   )
           }
       }
        return sources
    }
}