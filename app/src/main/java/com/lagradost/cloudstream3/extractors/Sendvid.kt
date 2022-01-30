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

    private val linkRegex =
        Regex("""(https:\/\/.*?\.m3u8.*")""")

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        with(app.get(url)) {
            linkRegex.find(this.text)?.let { link ->
                val extractedlink = URLDecoder.decode(link.value,"UTF-8")
                return listOf(
                    ExtractorLink(
                        name,
                        name,
                        link.value.replace("\"",""),
                        url,
                        Qualities.Unknown.value,
                        isM3u8 = true
                    )
                )
            }
        }
        return null
    }
}