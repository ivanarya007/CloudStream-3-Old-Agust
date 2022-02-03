package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.app
import java.net.URLDecoder

class Sendvid1: Sendvid() {
    override val mainUrl: String = "https://www.sendvid.com"
}

open class Sendvid : ExtractorApi() {
    override val name = "Sendvid m3u8"
    override val mainUrl = "https://sendvid.com"
    override val requiresReferer = false

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val doc = app.get(url).document
         doc.select("script").forEach { script ->
              if (script.data().contains("var video_source =")) {
              val extractedlink =  script.toString().substringAfter("var video_source = \"").substringBefore("\";")
              if (extractedlink.isNotBlank())  return listOf(
                    ExtractorLink(
                    name,
                    name,
                    extractedlink,
                    url,
                    Qualities.Unknown.value,
                    true
                ))
            }
        }
        return null
    }
}