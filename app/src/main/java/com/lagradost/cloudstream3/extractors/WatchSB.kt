package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.apmap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.network.WebViewResolver
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.getQualityFromName


class Watchsb1 : WatchSB() {
    override val mainUrl = "https://sbplay1.com"
}

class Watchsb2 : WatchSB() {
    override val mainUrl = "https://sbplay2.com"
}

class Watchsb3 : WatchSB() {
    override val mainUrl = "https://sbplay.one"
}

class Watchsb4 : WatchSB() {
    override val mainUrl = "https://cloudemb.com"
}


class Upstream : WatchSB() {
    override val name = "Upstream"
    override val mainUrl = "https://upstream.to"
}

class ZplayerV2 : WatchSB() {
    override val name = "Zplayer V2"
    override val mainUrl = "https://v2.zplayer.live"
}

open class WatchSB : ExtractorApi() {
    override val name = "WatchSB"
    override val mainUrl = "https://watchsb.com"
    override val requiresReferer = false

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink> {
        val response = app.get(
            url, interceptor = WebViewResolver(
                Regex("""master\.m3u8""")
            )
        )
        val sources = mutableListOf<ExtractorLink>()
        if (response.url.contains("m3u8")) M3u8Helper().m3u8Generation(
            M3u8Helper.M3u8Stream(
                response.url,
                headers = response.headers.toMap()
            ), true
        )
            .apmap { stream ->
                val qualityString = if ((stream.quality ?: 0) == 0) "" else "${stream.quality}p"
               sources.add( ExtractorLink(
                    name,
                    "$name $qualityString",
                    stream.streamUrl,
                    url,
                    getQualityFromName(stream.quality.toString()),
                    true
                ))
            }
        return sources
    }
}