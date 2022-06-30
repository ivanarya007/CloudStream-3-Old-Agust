package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.apmap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper.Companion.generateM3u8

class Fastream: ExtractorApi() {
    override var mainUrl = "https://fastream.to"
    override var name = "Fastream"
    override val requiresReferer = false


    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val id = Regex("(embedapp-|emb\\.html\\?)(.*)(\\=(enc|)|\\.html)").find(url)?.destructured?.component2() ?: return emptyList()
        println("ID $id")
        val sources = mutableListOf<ExtractorLink>()
        val response = app.post("$mainUrl/dl",
        data = mapOf(
            Pair("op","embed"),
            Pair("file_code",id),
            Pair("auto","1")
        )).document
        response.select("script").apmap { script ->
            if (script.data().contains("sources")) {
                val m3u8regex = Regex("((https:|http:)\\/\\/.*\\.m3u8)")
                val m3u8 = m3u8regex.find(script.data())?.value ?: return@apmap
                generateM3u8(
                    name,
                    m3u8,
                    mainUrl
                ).forEach { link ->
                    sources.add(link)
                }
            }
        }
        return sources
    }
}