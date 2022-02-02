package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.app
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.mapper


open class Cinestart : ExtractorApi() {
    override val name = "Cinestart"
    override val mainUrl = "https://cinestart.net"
    override val requiresReferer = false
    private data class cinestart (
        @JsonProperty("status") val status: Int,
        @JsonProperty("file") val file: String
    )
    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val link = url.replace("https://cinestart.net/embed.html#","https://cinestart.net/vr.php?v=")
        val server = app.get(link, allowRedirects = false).text
        val json = mapper.readValue<cinestart>(server)
        if (json.status == 200) return listOf(
            ExtractorLink(
                name,
                name,
                json.file,
                "",
                Qualities.Unknown.value,
                isM3u8 = false
            )
        )
        return null
    }
}