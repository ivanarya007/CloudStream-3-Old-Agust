package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.apmap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.network.WebViewResolver
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.getQualityFromName

open class Zplayer : ExtractorApi() {
    override val name = "Zplayer"
    override val mainUrl = "https://zplayer.live"
    override val requiresReferer = false

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink> {
        val response = app.get(url)
        val response2 = app.get(
            response.url, interceptor = WebViewResolver(
                Regex("""master\.m3u8""")
            )
        )
        return M3u8Helper().m3u8Generation(
            M3u8Helper.M3u8Stream(
                response2.url,
                headers = response2.headers.toMap()
            ), true
        )
            .apmap  { stream ->
                val qualityString = if ((stream.quality ?: 0) == 0) "" else "${stream.quality}p"
                ExtractorLink(
                    name,
                    "$name $qualityString",
                    stream.streamUrl,
                    url,
                    getQualityFromName(stream.quality.toString()),
                    true
                )
            }
    }
}