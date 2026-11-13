package com.stii.music.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.stii.music.data.MusicItem
import com.stii.music.data.MusicRepository
import com.stii.music.data.normalizeSearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class MusicSearchSource { DEVICE, OPEN_WEB }

data class MusicUiState(
    val library: List<MusicItem> = emptyList(),
    val localResults: List<MusicItem> = emptyList(),
    val onlineResults: List<MusicItem> = emptyList(),
    val query: String = "",
    val source: MusicSearchSource = MusicSearchSource.DEVICE,
    val isScanning: Boolean = false,
    val isSearchingOnline: Boolean = false,
    val error: String? = null,
    val favorites: Set<String> = emptySet(),
)

/** Owns app state and keeps content/provider work out of Compose screens. */
class StiiViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = MusicRepository(application.applicationContext)
    private val preferences = application.getSharedPreferences("stii_library", Context.MODE_PRIVATE)
    private val mutableState = MutableStateFlow(
        MusicUiState(favorites = preferences.getStringSet("favorites", emptySet()).orEmpty().toSet()),
    )
    val state: StateFlow<MusicUiState> = mutableState.asStateFlow()
    private var onlineSearchJob: Job? = null

    fun scanLibrary() {
        if (mutableState.value.isScanning) return
        mutableState.update { it.copy(isScanning = true, error = null) }
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { repository.scanLocalLibrary() } }
                .onSuccess { library ->
                    mutableState.update { current -> current.copy(library = library, isScanning = false) }
                    updateLocalResults(mutableState.value.query)
                }
                .onFailure { error -> mutableState.update { it.copy(isScanning = false, error = error.localizedMessage ?: "Could not scan music") } }
        }
    }

    fun setSearchSource(source: MusicSearchSource) {
        mutableState.update { it.copy(source = source, error = null) }
        search(mutableState.value.query)
    }

    fun search(query: String) {
        mutableState.update { it.copy(query = query, error = null) }
        updateLocalResults(query)
        onlineSearchJob?.cancel()
        if (mutableState.value.source != MusicSearchSource.OPEN_WEB || query.trim().length < 2) {
            mutableState.update { it.copy(isSearchingOnline = false, onlineResults = emptyList()) }
            return
        }
        onlineSearchJob = viewModelScope.launch {
            delay(300)
            mutableState.update { it.copy(isSearchingOnline = true, error = null) }
            val result = withContext(Dispatchers.IO) { repository.searchOpenAudio(query.trim()) }
            result.onSuccess { tracks ->
                mutableState.update { it.copy(onlineResults = tracks, isSearchingOnline = false, error = null) }
            }.onFailure { error ->
                mutableState.update {
                    it.copy(onlineResults = emptyList(), isSearchingOnline = false,
                        error = error.localizedMessage ?: "Internet search is temporarily unavailable")
                }
            }
        }
    }

    fun toggleFavorite(item: MusicItem) {
        val updated = mutableState.value.favorites.let { current ->
            if (item.id in current) current - item.id else current + item.id
        }
        mutableState.update { it.copy(favorites = updated) }
        preferences.edit().putStringSet("favorites", updated).apply()
    }

    private fun updateLocalResults(query: String) {
        val normalized = normalizeSearch(query)
        val tracks = mutableState.value.library
        viewModelScope.launch(Dispatchers.Default) {
            val matches = if (normalized.isBlank()) emptyList() else tracks.asSequence()
                .mapNotNull { item ->
                    val title = normalizeSearch(item.title)
                    val artist = normalizeSearch(item.artist)
                    val rank = when {
                        title == normalized -> 0
                        title.startsWith(normalized) -> 1
                        artist.startsWith(normalized) -> 2
                        normalized.split(' ').all(item.normalized::contains) -> 3
                        else -> return@mapNotNull null
                    }
                    rank to item
                }
                .sortedBy { it.first }
                .map { it.second }
                .toList()
            mutableState.update { current ->
                if (current.query == query) current.copy(localResults = matches) else current
            }
        }
    }
}
