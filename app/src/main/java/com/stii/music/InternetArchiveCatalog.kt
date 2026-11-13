package com.stii.music

import android.net.Uri
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Internet catalog adapter for freely reusable audio indexed by Internet Archive.
 * Only items whose metadata declares a Creative Commons or public-domain license are playable.
 */
internal object InternetArchiveCatalog {
    private const val SEARCH_ENDPOINT = "https://archive.org/advancedsearch.php"
    private const val MAX_RESULTS = 10
    private val allowedExtensions = setOf("mp3", "ogg", "opus", "flac", "m4a", "wav")

    fun searchOpenAudio(query: String): Result<List<MusicItem>> = runCatching {
        val encodedQuery = URLEncoder.encode("(${query.trim()} AND mediatype:audio)", "UTF-8")
        val searchUrl = "$SEARCH_ENDPOINT?q=$encodedQuery&fl[]=identifier&fl[]=title&fl[]=creator&rows=$MAX_RESULTS&page=1&output=json"
        val searchJson = requestJson(searchUrl)
        val docs = searchJson.getJSONObject("response").getJSONArray("docs")
        val items = mutableListOf<MusicItem>()
        for (i in 0 until docs.length()) {
            val doc = docs.optJSONObject(i) ?: continue
            val identifier = doc.optString("identifier").takeIf { it.isNotBlank() } ?: continue
            val metadata = runCatching { requestJson("https://archive.org/metadata/${pathSegment(identifier)}") }.getOrNull() ?: continue
            val meta = metadata.optJSONObject("metadata") ?: continue
            val licenseUrl = meta.optString("licenseurl").ifBlank { meta.optString("license_url") }
            val license = allowedLicense(licenseUrl) ?: continue
            val files = metadata.optJSONArray("files") ?: continue
            val audioFile = (0 until files.length()).mapNotNull { files.optJSONObject(it) }
                .firstOrNull { file ->
                    val name = file.optString("name")
                    val ext = name.substringAfterLast('.', "").lowercase()
                    ext in allowedExtensions && !file.optBoolean("private", false) && file.optString("source") != "original"
                } ?: continue
            val filename = audioFile.optString("name").split('/').joinToString("/") { pathSegment(it) }
            val rawTitle = doc.optString("title").ifBlank { meta.optString("title") }.ifBlank { identifier }
            val creator = when (val c = doc.opt("creator")) {
                is org.json.JSONArray -> (0 until c.length()).joinToString(", ") { c.optString(it) }
                is String -> c
                else -> meta.optString("creator")
            }.ifBlank { "Internet Archive contributor" }
            val lengthSeconds = audioFile.optString("length").toDoubleOrNull()?.toLong() ?: 0L
            items += MusicItem(
                id = "ia:$identifier:${audioFile.optString("name")}",
                title = rawTitle,
                artist = creator,
                album = "Internet Archive",
                durationMs = lengthSeconds * 1000,
                mediaUri = Uri.parse("https://archive.org/download/${pathSegment(identifier)}/$filename"),
                artworkUri = Uri.parse("https://archive.org/services/img/${pathSegment(identifier)}"),
                isOnline = true,
                license = license,
            )
        }
        items
    }

    private fun allowedLicense(url: String): String? {
        val normalized = url.lowercase()
        return when {
            normalized.contains("creativecommons.org/publicdomain") -> "Public domain"
            normalized.contains("creativecommons.org/licenses/by/") -> "CC BY"
            normalized.contains("creativecommons.org/licenses/by-sa/") -> "CC BY-SA"
            normalized.contains("creativecommons.org/licenses/by-nc/") -> "CC BY-NC"
            normalized.contains("creativecommons.org/licenses/by-nd/") -> "CC BY-ND"
            else -> null
        }
    }

    private fun requestJson(address: String): JSONObject {
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 7000
            readTimeout = 9000
            setRequestProperty("User-Agent", "StiiAndroidMusic/0.4 (open-licensed audio search)")
            setRequestProperty("Accept", "application/json")
        }
        try {
            val status = connection.responseCode
            if (status !in 200..299) throw IllegalStateException("Catalog request failed: HTTP $status")
            return JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } finally { connection.disconnect() }
    }

    private fun pathSegment(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
