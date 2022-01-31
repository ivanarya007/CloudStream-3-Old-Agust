package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.app



open class YourUpload : ExtractorApi() {
    override val name = "YourUpload"
    override val mainUrl = "https://www.yourupload.com"
    override val requiresReferer = false

    private val linkRegex =
        Regex("""(video" content="https:\/\/.*?\.mp4)""")

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        with(app.get(url)) {
            linkRegex.find(this.text)?.let { link ->
                val extractedlink = link.value.replace("video\" content=\"","")
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