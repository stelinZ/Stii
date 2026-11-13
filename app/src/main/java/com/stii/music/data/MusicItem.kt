package com.stii.music.data

import android.net.Uri
import java.text.Normalizer
import java.util.Locale

/** Shared model for local and catalog-backed audio. */
data class MusicItem(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val mediaUri: Uri,
    val artworkUri: Uri? = null,
    val isOnline: Boolean = false,
    val license: String? = null,
    val normalized: String = normalizeSearch("$title $artist $album"),
)

fun normalizeSearch(value: String): String = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
    .replace(Regex("\\p{Mn}+"), "")
    .trim()
    .replace(Regex("\\s+"), " ")
