package dev.reflux.sources.plex

import dev.reflux.core.net.HttpFetcher
import dev.reflux.core.net.HttpRequest
import dev.reflux.core.net.HttpResult

/**
 * A Plex Media Server and plex.tv as far as Reflux talks to them, answering as the real ones do (JSON, as asked
 * for with `Accept: application/json`). Shapes follow what a Plex Media Server 1.40 answers.
 */
internal class FakePlex {
    var online = true
    var plexTvOnline = true

    /** The token the server accepts; null lets anyone in (a server that trusts this network). */
    var serverToken: String? = "srv-token"

    /** What plex.tv signs in, and the token it gives. */
    var accountPassword = "secret"
    var accountToken = "srv-token"

    /** For a server shared with the account rather than owned: the token plex.tv lists for it. */
    var sharedToken: String? = null

    val requests = mutableListOf<HttpRequest>()
    val calls get() = requests.map { it.url.substringAfter("://").substringAfter('/') }

    private fun movie(key: String, title: String, year: Int, part: Int, extra: String = "") = """{
        "ratingKey":"$key","key":"/library/metadata/$key","type":"movie","title":"$title","year":$year,
        "thumb":"/library/metadata/$key/thumb/1700000000","art":"/library/metadata/$key/art/1700000000",
        "addedAt":1690000000,"updatedAt":1700000000,"duration":6000000,$extra
        "Guid":[{"id":"imdb://tt$key"},{"id":"tmdb://$key"}],
        "Media":[{"id":$part,"duration":6000000,"bitrate":8000,"width":1920,"height":1080,"container":"mkv","videoCodec":"h264","audioCodec":"ac3","audioChannels":6,
          "Part":[{"id":$part,"key":"/library/parts/$part/1700000000/file.mkv","size":4000000000,"container":"mkv","duration":6000000,
            "Stream":[
              {"streamType":1,"codec":"h264","index":0,"width":1920,"height":1080,"bitDepth":8,"profile":"high","level":41,"frameRate":23.976},
              {"streamType":2,"codec":"ac3","index":1,"channels":6,"languageCode":"eng","default":true,"samplingRate":48000,"displayTitle":"English (AC3 5.1)"},
              {"streamType":2,"codec":"aac","index":2,"channels":2,"languageCode":"fra","samplingRate":48000},
              {"streamType":3,"codec":"srt","index":3,"languageCode":"eng","forced":true},
              {"streamType":3,"codec":"srt","id":91,"key":"/library/streams/91","languageCode":"spa"}
            ]}]}]}"""

    private fun episode(key: String, show: String, showKey: String, season: Int, number: Int, part: Int, extra: String = "") = """{
        "ratingKey":"$key","type":"episode","title":"Episode $number","grandparentTitle":"$show","grandparentRatingKey":"$showKey",
        "parentIndex":$season,"index":$number,"thumb":"/library/metadata/$key/thumb/1","addedAt":1690000000,"updatedAt":1700000000,$extra
        "Media":[{"id":$part,"duration":2700000,"bitrate":12000,"width":3840,"height":2160,"container":"mkv","videoCodec":"hevc","audioCodec":"eac3","audioChannels":6,
          "Part":[{"id":$part,"key":"/library/parts/$part/1700000000/file.mkv","size":2000000000,"container":"mkv",
            "Stream":[
              {"streamType":1,"codec":"hevc","index":0,"width":3840,"height":2160,"bitDepth":10,"colorTrc":"smpte2084","profile":"main 10"},
              {"streamType":2,"codec":"eac3","index":1,"channels":6,"languageCode":"eng","default":true,"displayTitle":"English (EAC3 5.1 Atmos)"}
            ]}]}]}"""

    private val items = mapOf(
        "101" to movie("101", "Heat", 1995, 501, """"viewOffset":1200000,"viewCount":0,"lastViewedAt":1700001000,"""),
        "102" to movie("102", "Arrival", 2016, 502, """"viewCount":2,"lastViewedAt":1700002000,"""),
        "201" to episode("201", "Severance", "200", 1, 1, 601, """"viewCount":1,"lastViewedAt":1700003000,"""),
        "202" to episode("202", "Severance", "200", 1, 2, 602),
        // A film in two parts is not one version yet: it is left out.
        "103" to """{"ratingKey":"103","type":"movie","title":"Two Parts","year":2001,"Media":[{"id":503,"Part":[{"id":5031,"key":"/library/parts/5031/1/a.mkv"},{"id":5032,"key":"/library/parts/5032/1/b.mkv"}]}]}""",
    )

    private fun page(keys: List<String>, start: Int, size: Int): String {
        val slice = keys.drop(start).take(size)
        return """{"MediaContainer":{"size":${slice.size},"totalSize":${keys.size},"offset":$start,"Metadata":[${slice.joinToString(",") { """{"ratingKey":"$it"}""" }}]}}"""
    }

    private fun answer(status: Int, body: String = "") = HttpResult(status, body.encodeToByteArray())

    val http = HttpFetcher { request ->
        requests += request
        val url = request.url
        if (url.startsWith("https://plex.tv/")) return@HttpFetcher plexTv(request)
        if (!online) error("connection refused")
        val path = url.substringAfter("://").substringAfter('/').substringBefore('?')
        val query = url.substringAfter('?', "").split('&').associate { decode(it.substringBefore('=')) to decode(it.substringAfter('=', "")) }
        if (path == "identity") return@HttpFetcher answer(200, """{"MediaContainer":{"size":0,"claimed":true,"machineIdentifier":"machine-1","version":"1.40.0"}}""")
        val token = request.headers["X-Plex-Token"]
        if (serverToken != null && token != serverToken) return@HttpFetcher answer(401, "<html>Unauthorized</html>")
        val body = when {
            path == "" -> """{"MediaContainer":{"friendlyName":"Living Room","machineIdentifier":"machine-1"}}"""
            path == "library/sections" -> """{"MediaContainer":{"size":3,"Directory":[
                {"key":"1","type":"movie","title":"Films"},
                {"key":"3","type":"artist","title":"Music"},
                {"key":"2","type":"show","title":"TV Shows"}]}}"""
            path == "library/sections/1/all" -> page(listOf("101", "102", "103"), query["X-Plex-Container-Start"]!!.toInt(), query["X-Plex-Container-Size"]!!.toInt().coerceAtMost(2))
            path == "library/sections/2/all" && query["type"] == "2" -> """{"MediaContainer":{"Metadata":[
                {"ratingKey":"200","type":"show","title":"Severance","year":2022,"thumb":"/library/metadata/200/thumb/1","art":"/library/metadata/200/art/1",
                 "Guid":[{"id":"tvdb://371980"},{"id":"tmdb://95396"}]}]}}"""
            path == "library/sections/2/all" -> page(listOf("201", "202"), query["X-Plex-Container-Start"]!!.toInt(), query["X-Plex-Container-Size"]!!.toInt())
            path.startsWith("library/metadata/") -> {
                val keys = path.removePrefix("library/metadata/").split(',')
                """{"MediaContainer":{"Metadata":[${keys.mapNotNull(items::get).joinToString(",")}]}}"""
            }
            path.startsWith(":/") -> ""
            else -> return@HttpFetcher answer(404)
        }
        answer(200, body)
    }

    private fun plexTv(request: HttpRequest): HttpResult {
        if (!plexTvOnline) error("plex.tv unreachable")
        val path = request.url.removePrefix("https://plex.tv/").substringBefore('?')
        return when (path) {
            "api/v2/users/signin" -> {
                val form = request.body!!.decodeToString().split('&').associate { decode(it.substringBefore('=')) to decode(it.substringAfter('=')) }
                if (form["login"] == "alex@example.com" && form["password"] == accountPassword) {
                    answer(201, """{"id":7,"uuid":"u7","username":"alex","email":"alex@example.com","authToken":"$accountToken"}""")
                } else {
                    answer(401, """{"errors":[{"code":1001,"message":"User could not be authenticated"}]}""")
                }
            }
            "api/v2/resources" -> answer(
                200,
                """[{"name":"Living Room","clientIdentifier":"machine-1","provides":"server","owned":${sharedToken == null},"accessToken":"${sharedToken ?: accountToken}"},
                    {"name":"Phone","clientIdentifier":"phone-1","provides":"player","owned":true}]""",
            )
            else -> answer(404)
        }
    }
}

/** Percent-decoding as a form or a query is encoded. */
internal fun decode(value: String): String {
    val bytes = ArrayList<Byte>()
    var i = 0
    while (i < value.length) {
        val c = value[i]
        when {
            c == '%' && i + 2 < value.length + 0 && i + 2 <= value.lastIndex -> {
                bytes += value.substring(i + 1, i + 3).toInt(16).toByte()
                i += 3
            }
            c == '+' -> { bytes += ' '.code.toByte(); i++ }
            else -> { c.toString().encodeToByteArray().forEach { bytes += it }; i++ }
        }
    }
    return bytes.toByteArray().decodeToString()
}
