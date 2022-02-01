package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.app

class Solidfiles1: Solidfiles() {
    override val mainUrl: String = "https://solidfiles.com"
}

open class Solidfiles : ExtractorApi() {
    override val name = "Solidfiles"
    override val mainUrl = "https://www.solidfiles.com"
    override val requiresReferer = false

    private val linkRegex =
        Regex("""(streamUrl":"(https|http):\/\/.*?\.mp4)""")
    //Regex("""(downloadUrl("):"(https|http):\/\/.*?\.mp4)""") also works, don't know which one is faster
    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        with(app.get(url)) {
            linkRegex.find(this.text)?.let { link ->
                val extractedlink = link.value.replace("streamUrl\":\"","")
                return listOf(
                    ExtractorLink(
                        name,
                        name,
                        extractedlink,
                        url,
                        Qualities.Unknown.value,
                        isM3u8 = false
                    )
                )
            }
        }
        return null
    }
}