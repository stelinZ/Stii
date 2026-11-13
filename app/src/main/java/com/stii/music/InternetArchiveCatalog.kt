package com.stii.music

import android.net.Uri
import com.stii.music.data.MusicItem
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Search adapter for Internet Archive's openly licensed audio catalog. */
internal object InternetArchiveCatalog {
    private const val SEARCH_ENDPOINT = "https://archive.org/advancedsearch.php"
    private const val MAX_RESULTS = 8
    private val allowedExtensions = setOf("mp3", "ogg", "opus", "flac", "m4a", "wav")
    private val metadataWorkers = Executors.newFixedThreadPool(4)

    fun searchOpenAudio(query: String): Result<List<MusicItem>> = runCatching {
        val terms = query.trim().take(120)
        require(terms.isNotBlank()) { "Search query is empty" }
        val encodedQuery = URLEncoder.encode("($terms AND mediatype:audio)", "UTF-8")
        val address = "$SEARCH_ENDPOINT?q=$encodedQuery&fl[]=identifier&fl[]=title&fl[]=creator&rows=$MAX_RESULTS&page=1&output=json"
        val docs = requestJson(address).optJSONObject("response")?.optJSONArray("docs") ?: JSONArray()
        val futures = (0 until docs.length()).mapNotNull { index ->
            val doc = docs.optJSONObject(index) ?: return@mapNotNull null
            val identifier = doc.optString("identifier").takeIf(String::isNotBlank) ?: return@mapNotNull null
            CompletableFuture.supplyAsync({ resolveItem(doc, identifier) }, metadataWorkers)
        }
        futures.mapNotNull { future -> runCatching { future.get(12, TimeUnit.SECONDS) }.getOrNull() }.distinctBy { it.id }
    }

    private fun resolveItem(doc: JSONObject, identifier: String): MusicItem? {
        val metadata = runCatching { requestJson("https://archive.org/metadata/${pathSegment(identifier)}") }.getOrNull() ?: return null
        val meta = metadata.optJSONObject("metadata") ?: return null
        val licenseUrl = meta.optString("licenseurl").ifBlank { meta.optString("license_url").ifBlank { meta.optString("license") } }
        val license = allowedLicense(licenseUrl) ?: return null
        val files = metadata.optJSONArray("files") ?: return null
        val file = (0 until files.length()).mapNotNull(files::optJSONObject).firstOrNull(::isPlayableAudio) ?: return null
        val fileName = file.optString("name").ifBlank { return null }
        val encodedFilePath = fileName.split('/').joinToString("/") { pathSegment(it) }
        val title = doc.optString("title").ifBlank { meta.optString("title") }.ifBlank { identifier }
        val artist = creatorText(doc.opt("creator")).ifBlank { creatorText(meta.opt("creator")).ifBlank { "Internet Archive contributor" } }
        val duration = parseDurationMs(file.optString("length"))
        return MusicItem(
            id = "ia:$identifier:$fileName", title = title, artist = artist, album = "Internet Archive",
            durationMs = duration,
            mediaUri = Uri.parse("https://archive.org/download/${pathSegment(identifier)}/$encodedFilePath"),
            artworkUri = Uri.parse("https://archive.org/services/img/${pathSegment(identifier)}"),
            isOnline = true, license = license,
        )
    }

    private fun isPlayableAudio(file: JSONObject): Boolean {
        if (file.optBoolean("private", false)) return false
        if (file.optString("name").substringAfterLast('.', "").lowercase() !in allowedExtensions) return false
        return file.optString("size").toLongOrNull()?.let { it > 0L } ?: true
    }

    private fun creatorText(value: Any?): String = when (value) {
        is JSONArray -> (0 until value.length()).map { value.optString(it) }.filter(String::isNotBlank).joinToString(", ")
        is String -> value
        else -> ""
    }

    private fun parseDurationMs(value: String): Long {
        value.toDoubleOrNull()?.let { return (it * 1000).toLong().coerceAtLeast(0L) }
        val parts = value.split(':').mapNotNull(String::toLongOrNull)
        if (parts.isEmpty()) return 0L
        return parts.fold(0L) { total, part -> total * 60L + part } * 1000L
    }

    private fun allowedLicense(url: String): String? {
        val normalized = url.lowercase()
        return when {
            "creativecommons.org/publicdomain" in normalized || "creativecommons.org/licenses/zero/" in normalized -> "Public domain / CC0"
            "creativecommons.org/licenses/by-nc-sa/" in normalized -> "CC BY-NC-SA"
            "creativecommons.org/licenses/by-nc-nd/" in normalized -> "CC BY-NC-ND"
            "creativecommons.org/licenses/by-sa/" in normalized -> "CC BY-SA"
            "creativecommons.org/licenses/by-nc/" in normalized -> "CC BY-NC"
            "creativecommons.org/licenses/by-nd/" in normalized -> "CC BY-ND"
            "creativecommons.org/licenses/by/" in normalized -> "CC BY"
            else -> null
        }
    }

    private fun requestJson(address: String): JSONObject {
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 5000
            readTimeout = 7000
            instanceFollowRedirects = true
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
