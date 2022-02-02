package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.app
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.mapper


open class Tomatomatela : ExtractorApi() {
    override val name = "Tomatomatela"
    override val mainUrl = "https://tomatomatela.com"
    override val requiresReferer = false
    private data class tomato (
        @JsonProperty("status") val status: Int,
        @JsonProperty("file") val file: String
    )
    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val link = url.replace("https://tomatomatela.com/embed.html#","https://tomatomatela.com/details.php?v=")
        val server = app.get(link, allowRedirects = false).text
        val json = mapper.readValue<tomato>(server)
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