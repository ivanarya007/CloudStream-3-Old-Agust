package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.apmap
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.app


class StreamSB1 : StreamSB() {
    override val mainUrl = "https://sbplay1.com"
}

class StreamSB2 : StreamSB() {
    override val mainUrl = "https://sbplay2.com"
}

class StreamSB3 : StreamSB() {
    override val mainUrl = "https://sbplay.one"
}

class StreamSB4 : StreamSB() {
    override val mainUrl = "https://cloudemb.com"
}

class StreamSB5 : StreamSB() {
    override val mainUrl = "https://sbplay.org"
}

class StreamSB6 : StreamSB() {
    override val mainUrl = "https://embedsb.com"
}

class StreamSB7 : StreamSB() {
    override val mainUrl = "https://pelistop.co"
}

open class StreamSB : ExtractorApi() {
    override val name = "StreamSB"
    override val mainUrl = "https://watchsb.com"
    override val requiresReferer = false

    private val hexArray = "0123456789ABCDEF".toCharArray()

    private fun bytesToHex(bytes: ByteArray): String {
        val hexChars = CharArray(bytes.size * 2)
        for (j in bytes.indices) {
            val v = bytes[j].toInt() and 0xFF

            hexChars[j * 2] = hexArray[v ushr 4]
            hexChars[j * 2 + 1] = hexArray[v and 0x0F]
        }
        return String(hexChars)
    }

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val id = url.substringAfter("embed-").substringAfter("/e/").substringBefore(".html")
        val bytes = id.toByteArray()
        val bytesToHex = bytesToHex(bytes)
        val master = "$mainUrl/sources40/566d337678566f743674494a7c7c${bytesToHex}7c7c346b6767586d6934774855537c7c73747265616d7362/6565417268755339773461447c7c346133383438333436313335376136323337373433383634376337633465366534393338373136643732373736343735373237613763376334363733353737303533366236333463353333363534366137633763373337343732363536313664373336327c7c6b586c3163614468645a47617c7c73747265616d7362"
        val headers = mapOf(
            "watchsb" to "streamsb",
            "accept-language" to "en-US,en;q=0.5",
            "Referer" to url,
            "User-Agent" to "Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:96.0) Gecko/20100101 Firefox/96.0",)
        val urltext = app.get(master,
            headers = headers
        ).text
        val sources = mutableListOf<ExtractorLink>()
        val extractedlink = urltext.substringAfter("\"file\":\"").substringBefore("\",\"")
        val extractedbackup = urltext.substringAfter("\"backup\":\"").substringBefore("\",\"")
        if (extractedlink.contains("m3u8"))  M3u8Helper().m3u8Generation(
            M3u8Helper.M3u8Stream(
                if (extractedlink.isBlank()) extractedbackup else extractedlink,
                headers = app.get(url).headers.toMap()
            ), true
        )
            .apmap { stream ->
                val qualityString = if ((stream.quality ?: 0) == 0) "" else "${stream.quality}p"
                sources.add(  ExtractorLink(
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