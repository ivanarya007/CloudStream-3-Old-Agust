package com.lagradost.cloudstream3.extractors

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.apmap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.helper.WcoHelper
import com.lagradost.cloudstream3.extractors.helper.WcoHelper.Companion.getWcoKey
import com.lagradost.cloudstream3.mapper
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.M3u8Helper.Companion.generateM3u8

class Vidstreamz : WcoStream() {
    override var mainUrl = "https://vidstreamz.online"
}
class Vizcloud : WcoStream() {
    override var mainUrl = "https://vizcloud2.ru"
}
class Vizcloud2 : WcoStream() {
    override var mainUrl = "https://vizcloud2.online"
}

class VizcloudOnline : WcoStream() {
    override var mainUrl = "https://vizcloud.online"
}

class VizcloudXyz : WcoStream() {
    override var mainUrl = "https://vizcloud.xyz"
}

class VizcloudLive : WcoStream() {
    override var mainUrl = "https://vizcloud.live"
}

class VizcloudInfo : WcoStream() {
    override var mainUrl = "https://vizcloud.info"
}

class VizcloudDigital : WcoStream() {
    override var mainUrl = "https://vizcloud.digital"
}

class VizcloudCloud : WcoStream() {
    override var mainUrl = "https://vizcloud.cloud"
}

open class WcoStream : ExtractorApi() {
    override var name = "VidStream" // Cause works for animekisa and wco
    override var mainUrl = "https://vidstream.pro"
    override val requiresReferer = false

    companion object {
        var keytwo = ""
        fun encrypt(input: String): String {
            if (input.any { it.code >= 256 }) throw Exception("illegal characters!")
            var output = ""
            for (i in input.indices step 3) {
                val a = intArrayOf(-1, -1, -1, -1)
                a[0] = input[i].code shr 2
                a[1] = (3 and input[i].code) shl 4
                if (input.length > i + 1) {
                    a[1] = a[1] or (input[i + 1].code shr 4)
                    a[2] = (15 and input[i + 1].code) shl 2
                }
                if (input.length > i + 2) {
                    a[2] = a[2] or (input[i + 2].code shr 6)
                    a[3] = 63 and input[i + 2].code
                }
                for (n in a) {
                    if (n == -1) output += "="
                    else {
                        if (n in 0..63) output += keytwo[n]
                    }
                }
            }
            return output;
        }

        fun cipher(inputOne: String, inputTwo: String): String {
            val arr = IntArray(256) { it }
            var output = ""
            var u = 0
            var r: Int
            for (a in arr.indices) {
                u = (u + arr[a] + inputOne[a % inputOne.length].code) % 256
                r = arr[a]
                arr[a] = arr[u]
                arr[u] = r
            }
            u = 0
            var c = 0
            for (f in inputTwo.indices) {
                c = (c + f) % 256
                u = (u + arr[c]) % 256
                r = arr[c]
                arr[c] = arr[u]
                arr[u] = r
                output += (inputTwo[f].code xor arr[(arr[c] + arr[u]) % 256]).toChar()
            }
            return output
        }
    }

    private val key = "fsVFfz49gtVHPw6i"
    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink> {
        val baseUrl = url.split("/e/")[0]
        val (Id) = (Regex("/e/(.*?)?domain").find(url)?.destructured ?: Regex("""/e/(.*)""").find(
            url
        )?.destructured) ?: return emptyList()
        keytwo = getWcoKey() ?: return emptyList()
        val encryptedID = encrypt(cipher(key, encrypt(Id))).replace("/", "_").replace("=","")
        val apiLink = "$baseUrl/info/$encryptedID"
        val referrer = "$baseUrl/e/$Id?domain=wcostream.cc"

        data class SourcesWco (
            @JsonProperty("file" ) val file : String
        )

        data class MediaWco (
            @JsonProperty("sources" ) val sources : ArrayList<SourcesWco> = arrayListOf()
        )

        data class DataWco (
            @JsonProperty("media" ) val media : MediaWco? = MediaWco()
        )

        data class WcoResponse (
            @JsonProperty("status" ) val status : Int?  = null,
            @JsonProperty("data"   ) val data   : DataWco? = DataWco()
        )


        val mapped = app.get(apiLink, headers = mapOf("Referer" to referrer)).parsed<WcoResponse>()
        val sources = mutableListOf<ExtractorLink>()

        if (mapped.status == 200) {
            mapped.data?.media?.sources?.forEach {
                if (
                    arrayOf(
                        "https://vidstream.pro",
                        "https://vidstreamz.online",
                        "https://vizcloud2.online",
                        "https://vizcloud.xyz",
                        "https://vizcloud.live",
                        "https://vizcloud.info",
                        "https://mwvn.vizcloud.info",
                        "https://vizcloud.digital",
                        "https://vizcloud.cloud"
                    ).contains(mainUrl)
                ) {
                    if (it.file.contains("m3u8")) {
                        sources.addAll(
                            generateM3u8(
                                name,
                                it.file.replace("#.mp4", ""),
                                url,
                                headers = mapOf("Referer" to url)
                            )
                        )
                    } else {
                        sources.add(
                            ExtractorLink(
                                name,
                                name = name,
                                it.file,
                                "",
                                Qualities.P720.value,
                                false
                            )
                        )
                    }
                }
            }
        }
        return sources
    }
}