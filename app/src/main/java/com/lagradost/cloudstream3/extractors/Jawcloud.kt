package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.app


class Jawcloud : ExtractorApi() {
    override val name = "Jawcloud m3u8"
    override val mainUrl = "https://jawcloud.co"
    override val requiresReferer = false

    private val linkRegex =
        Regex("""(source src="https:\/\/.*?\.m3u8)""")

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        with(app.get(url)) {
            linkRegex.find(this.text)?.let { link ->
                val extractedlink = link.value.replace("source src=\"","")
                return listOf(
                    ExtractorLink(
                        name,
                        name,
                        extractedlink,
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