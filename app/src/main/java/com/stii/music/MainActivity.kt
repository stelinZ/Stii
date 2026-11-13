package com.stii.music

import android.Manifest
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil.compose.AsyncImage
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.ExecutionException

private val Ink = Color(0xFF090B0D)
private val Panel = Color(0xFF14181B)
private val Lime = Color(0xFFC7F36B)
private val Muted = Color(0xFF929A9D)

class MainActivity : ComponentActivity() {
    private var controllerFuture: ListenableFuture<MediaController>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        renderStarting()
    }

    private fun renderStarting() {
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Lime, onPrimary = Ink, background = Ink, surface = Panel, onSurface = Color(0xFFF4F6F2))) {
                Box(Modifier.fillMaxSize().background(Ink), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Lime)
                        Spacer(Modifier.height(14.dp))
                        Text("Starting Stii…", color = Muted)
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val future = MediaController.Builder(this, SessionToken(this, ComponentName(this, StiiPlaybackService::class.java))).buildAsync()
        controllerFuture = future
        future.addListener({
            try {
                val controller = future.get()
                if (!isFinishing && !isDestroyed) setContent {
                    MaterialTheme(colorScheme = darkColorScheme(primary = Lime, onPrimary = Ink, background = Ink, surface = Panel, onSurface = Color(0xFFF4F6F2))) {
                        StiiApp(controller)
                    }
                }
            } catch (_: ExecutionException) {
                renderStarting()
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onStop() {
        controllerFuture?.let(MediaController::releaseFuture)
        controllerFuture = null
        super.onStop()
    }
}

private data class MusicItem(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val mediaUri: Uri,
    val artworkUri: Uri? = null,
    val isOnline: Boolean = false,
    val license: String? = null,
    val normalized: String = normalizeSearch("$title $artist $album")
)

private enum class Tab { HOME, SEARCH, LIBRARY }
private enum class SearchSource { DEVICE, OPEN_WEB }

@Composable
private fun StiiApp(player: Player) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var library by remember { mutableStateOf<List<MusicItem>>(emptyList()) }
    var onlineResults by remember { mutableStateOf<List<MusicItem>>(emptyList()) }
    var localResults by remember { mutableStateOf<List<MusicItem>>(emptyList()) }
    var tab by remember { mutableStateOf(Tab.HOME) }
    var searchSource by remember { mutableStateOf(SearchSource.DEVICE) }
    var query by remember { mutableStateOf("") }
    var selectedId by remember { mutableStateOf(player.currentMediaItem?.mediaId) }
    var playing by remember { mutableStateOf(player.isPlaying) }
    var showPlayer by remember { mutableStateOf(false) }
    var position by remember { mutableFloatStateOf(0f) }
    var duration by remember { mutableFloatStateOf(1f) }
    var scanning by remember { mutableStateOf(false) }
    var onlineLoading by remember { mutableStateOf(false) }
    var onlineError by remember { mutableStateOf<String?>(null) }
    var favorites by remember { mutableStateOf<Set<String>>(emptySet()) }
    var shuffle by remember { mutableStateOf(player.shuffleModeEnabled) }
    val currentItem = remember(library, onlineResults, selectedId) {
        (library + onlineResults).firstOrNull { it.id == selectedId }
    }
    val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE

    fun scanLibrary() {
        scope.launch {
            scanning = true
            library = withContext(Dispatchers.IO) { readLocalLibrary(context) }
            scanning = false
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) scanLibrary()
    }

    LaunchedEffect(Unit) {
        favorites = context.getSharedPreferences("stii_library", Context.MODE_PRIVATE)
            .getStringSet("favorites", emptySet()).orEmpty().toSet()
        if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) scanLibrary()
    }

    LaunchedEffect(query, library) {
        val q = normalizeSearch(query)
        localResults = withContext(Dispatchers.Default) {
            if (q.isBlank()) emptyList() else library.asSequence()
                .mapNotNull { item ->
                    val rank = when {
                        normalizeSearch(item.title) == q -> 0
                        normalizeSearch(item.title).startsWith(q) -> 1
                        normalizeSearch(item.artist).startsWith(q) -> 2
                        q.split(' ').all(item.normalized::contains) -> 3
                        else -> return@mapNotNull null
                    }
                    rank to item
                }.sortedBy { it.first }.map { it.second }.toList()
        }
    }

    LaunchedEffect(query, searchSource, tab) {
        if (tab != Tab.SEARCH || searchSource != SearchSource.OPEN_WEB || query.trim().length < 2) {
            onlineResults = emptyList()
            onlineLoading = false
            onlineError = null
            return@LaunchedEffect
        }
        delay(300)
        onlineLoading = true
        onlineError = null
        val result = withContext(Dispatchers.IO) { InternetArchiveCatalog.searchOpenAudio(query.trim()) }
        onlineResults = result.getOrElse {
            onlineError = "Интернет-поиск сейчас недоступен. Проверьте соединение и попробуйте ещё раз."
            emptyList()
        }
        onlineLoading = false
    }

    LaunchedEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) { selectedId = mediaItem?.mediaId }
            override fun onPlaybackStateChanged(playbackState: Int) { duration = player.duration.takeIf { it > 0 }?.toFloat() ?: 1f }
        }
        player.addListener(listener)
        try {
            while (true) {
                position = player.currentPosition.coerceAtLeast(0L).toFloat()
                duration = player.duration.takeIf { it > 0 }?.toFloat() ?: 1f
                delay(250)
            }
        } finally { player.removeListener(listener) }
    }

    fun play(item: MusicItem) {
        // A web result is queued independently, avoiding accidental jumps into local tracks.
        val queue = if (item.isOnline) listOf(item) else library
        val index = queue.indexOfFirst { it.id == item.id }
        if (index < 0) return
        player.setMediaItems(queue.map { song ->
            MediaItem.Builder().setMediaId(song.id).setUri(song.mediaUri)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(song.title).setArtist(song.artist)
                    .setAlbumTitle(song.album).setArtworkUri(song.artworkUri).build()).build()
        }, index, 0L)
        player.shuffleModeEnabled = shuffle
        player.prepare()
        player.play()
        selectedId = item.id
        showPlayer = true
    }

    fun toggleFavorite(item: MusicItem) {
        favorites = if (item.id in favorites) favorites - item.id else favorites + item.id
        context.getSharedPreferences("stii_library", Context.MODE_PRIVATE).edit().putStringSet("favorites", favorites).apply()
    }

    Scaffold(containerColor = Ink, bottomBar = {
        Column {
            if (!showPlayer && currentItem != null) MiniPlayer(currentItem, playing,
                { showPlayer = true }, { if (player.isPlaying) player.pause() else player.play() },
                { if (player.hasNextMediaItem()) player.seekToNextMediaItem() })
            if (!showPlayer) NavigationBar(containerColor = Ink, contentColor = Lime) {
                listOf(Tab.HOME, Tab.SEARCH, Tab.LIBRARY).forEach { item ->
                    val title = when (item) { Tab.HOME -> "Главная"; Tab.SEARCH -> "Поиск"; Tab.LIBRARY -> "Избранное" }
                    NavigationBarItem(selected = tab == item, onClick = { tab = item; if (item != Tab.SEARCH) query = "" },
                        icon = { Icon(when (item) { Tab.HOME -> Icons.Default.Home; Tab.SEARCH -> Icons.Default.Search; Tab.LIBRARY -> Icons.Default.LibraryMusic }, title) },
                        label = { Text(title) })
                }
            }
        }
    }) { insets ->
        AnimatedContent(showPlayer, transitionSpec = {
            (fadeIn(tween(250)) + slideInHorizontally(tween(300, easing = FastOutSlowInEasing)) { it / 8 }) togetherWith
                (fadeOut(tween(160)) + slideOutHorizontally(tween(240)) { -it / 10 })
        }, label = "player-screen") { fullPlayer ->
            if (fullPlayer && currentItem != null) NowPlaying(
                currentItem, playing, position, duration,
                { showPlayer = false }, { if (player.isPlaying) player.pause() else player.play() },
                { if (player.hasPreviousMediaItem()) player.seekToPreviousMediaItem() },
                { if (player.hasNextMediaItem()) player.seekToNextMediaItem() }, { player.seekTo(it.toLong()) },
                { toggleFavorite(currentItem) }, { shuffle = !shuffle; player.shuffleModeEnabled = shuffle },
                { player.repeatMode = when (player.repeatMode) { Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL; Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE; else -> Player.REPEAT_MODE_OFF } },
                shuffle, player.repeatMode, Modifier.padding(insets)
            ) else Column(Modifier.fillMaxSize().padding(insets)) {
                Header(tab)
                if (tab == Tab.SEARCH) {
                    TextField(query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
                        placeholder = { Text("Песня, исполнитель или альбом", color = Muted) },
                        leadingIcon = { Icon(Icons.Default.Search, null, tint = Muted) }, singleLine = true,
                        shape = RoundedCornerShape(18.dp), colors = TextFieldDefaults.colors(
                            focusedContainerColor = Panel, unfocusedContainerColor = Panel,
                            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent))
                    Row(Modifier.padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = searchSource == SearchSource.DEVICE, onClick = { searchSource = SearchSource.DEVICE },
                            label = { Text("На устройстве") }, leadingIcon = { Icon(Icons.Default.MusicNote, null) })
                        FilterChip(selected = searchSource == SearchSource.OPEN_WEB, onClick = { searchSource = SearchSource.OPEN_WEB },
                            label = { Text("Открытый интернет") }, leadingIcon = { Icon(Icons.Default.Search, null) })
                    }
                    if (searchSource == SearchSource.OPEN_WEB) {
                        Text("Поиск по Internet Archive · показываются записи с открытой лицензией",
                            color = Muted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 22.dp, vertical = 2.dp))
                    }
                }
                if (tab == Tab.HOME) HeroCard(library.size) {
                    if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) scanLibrary()
                    else permissionLauncher.launch(permission)
                }
                val visibleItems = when (tab) {
                    Tab.HOME -> library.take(8)
                    Tab.LIBRARY -> library.filter { it.id in favorites }
                    Tab.SEARCH -> if (searchSource == SearchSource.DEVICE) localResults else onlineResults
                }
                if (tab == Tab.HOME && library.isEmpty()) EmptyLibrary(
                    ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED,
                    { if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) scanLibrary() else permissionLauncher.launch(permission) }, scanning
                ) else {
                    val heading = when (tab) {
                        Tab.HOME -> "МУЗЫКА НА УСТРОЙСТВЕ"
                        Tab.LIBRARY -> "ИЗБРАННОЕ · ${visibleItems.size}"
                        Tab.SEARCH -> if (searchSource == SearchSource.DEVICE) "РЕЗУЛЬТАТЫ · ${visibleItems.size}" else "ОТКРЫТЫЕ АУДИОЗАПИСИ · ${visibleItems.size}"
                    }
                    Text(heading, color = Muted, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 22.dp, top = 14.dp, bottom = 6.dp))
                    when {
                        tab == Tab.SEARCH && searchSource == SearchSource.OPEN_WEB && onlineLoading -> Box(Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Lime) }
                        tab == Tab.SEARCH && onlineError != null -> Text(onlineError!!, color = Muted, modifier = Modifier.padding(22.dp))
                        tab == Tab.SEARCH && searchSource == SearchSource.OPEN_WEB && query.trim().length < 2 -> Text("Введите не менее двух символов для поиска", color = Muted, modifier = Modifier.padding(22.dp))
                        else -> LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 16.dp)) {
                            items(visibleItems, key = { it.id }) { item ->
                                TrackRow(item, item.id in favorites, { play(item) }, { toggleFavorite(item) })
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun normalizeSearch(value: String): String = java.text.Normalizer.normalize(value.lowercase(Locale.ROOT), java.text.Normalizer.Form.NFD)
    .replace(Regex("\\p{Mn}+"), "").trim().replace(Regex("\\s+"), " ")

private fun readLocalLibrary(context: Context): List<MusicItem> {
    val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL) else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.ALBUM_ID, MediaStore.Audio.Media.DURATION)
    val result = mutableListOf<MusicItem>()
    context.contentResolver.query(collection, projection, "${MediaStore.Audio.Media.IS_MUSIC} != 0", null,
        "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC")?.use { cursor ->
        val id = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val title = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
        val artist = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
        val album = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
        val albumId = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
        val duration = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
        while (cursor.moveToNext()) {
            val trackId = cursor.getLong(id)
            val trackTitle = cursor.getString(title) ?: "Неизвестный трек"
            val trackArtist = cursor.getString(artist)?.takeUnless { it == "<unknown>" } ?: "Неизвестный исполнитель"
            val trackAlbum = cursor.getString(album) ?: "Неизвестный альбом"
            val artId = cursor.getLong(albumId)
            val length = cursor.getLong(duration)
            if (length > 1000) result += MusicItem("local:$trackId", trackTitle, trackArtist, trackAlbum, length,
                ContentUris.withAppendedId(collection, trackId), ContentUris.withAppendedId(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, artId))
        }
    }
    return result
}

@Composable private fun Header(tab: Tab) {
    val title = when (tab) { Tab.HOME -> "С возвращением"; Tab.SEARCH -> "Найди свою музыку"; Tab.LIBRARY -> "Избранное" }
    Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text("STII", color = Lime, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black); Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
        Box(Modifier.size(42.dp).clip(CircleShape).background(Panel), contentAlignment = Alignment.Center) { Icon(Icons.Default.MusicNote, null, tint = Lime) }
    }
}

@Composable private fun HeroCard(trackCount: Int, onOpen: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp).clip(RoundedCornerShape(28.dp))
        .background(Brush.linearGradient(listOf(Color(0xFF25352D), Color(0xFF11191A), Color(0xFF17272A))))
        .clickable(onClick = onOpen).padding(22.dp)) {
        Text("ТВОЯ МУЗЫКА.\nТВОЁ ПРОСТРАНСТВО.", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(10.dp)); Text("Ищи открытую музыку или слушай свою библиотеку.", color = Color(0xFFCAD3CE))
        Spacer(Modifier.height(18.dp)); Text("$trackCount треков на устройстве", color = Lime, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable private fun EmptyLibrary(permissionNeeded: Boolean, onOpen: () -> Unit, scanning: Boolean) {
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(76.dp).clip(RoundedCornerShape(26.dp)).background(Panel), contentAlignment = Alignment.Center) { Icon(Icons.Default.Album, null, tint = Lime, modifier = Modifier.size(36.dp)) }
        Spacer(Modifier.height(16.dp)); Text(if (permissionNeeded) "Разрешите доступ к аудио" else "Библиотека пока пуста", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp)); Text("Интернет-поиск доступен во вкладке «Поиск» независимо от музыки на устройстве.", color = Muted)
        Spacer(Modifier.height(14.dp)); Button(onClick = onOpen, enabled = !scanning) { Text(if (scanning) "Сканирую…" else if (permissionNeeded) "Разрешить доступ" else "Сканировать снова") }
    }
}

@Composable private fun TrackRow(item: MusicItem, favorite: Boolean, onClick: () -> Unit, onFavorite: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (item.artworkUri != null) AsyncImage(item.artworkUri, "Обложка ${item.album}", Modifier.size(54.dp).clip(RoundedCornerShape(15.dp)).background(Panel), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
        else Box(Modifier.size(54.dp).clip(RoundedCornerShape(15.dp)).background(Brush.linearGradient(listOf(Color(0xFF30423A), Color(0xFF20262B)))), contentAlignment = Alignment.Center) { Icon(Icons.Default.MusicNote, null, tint = Lime) }
        Column(Modifier.weight(1f).padding(horizontal = 13.dp)) {
            Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
            Text(listOf(item.artist, item.album).filter(String::isNotBlank).joinToString(" · "), maxLines = 1, overflow = TextOverflow.Ellipsis, color = Muted, style = MaterialTheme.typography.bodySmall)
            if (item.isOnline) Text(item.license ?: "Открытая лицензия", color = Lime, style = MaterialTheme.typography.labelSmall)
        }
        IconButton(onClick = onFavorite) { Icon(if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "В избранное", tint = if (favorite) Lime else Muted) }
    }
}

@Composable private fun MiniPlayer(item: MusicItem, playing: Boolean, onOpen: () -> Unit, onToggle: () -> Unit, onNext: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp).clip(RoundedCornerShape(18.dp)).background(Panel).clickable(onClick = onOpen).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (item.artworkUri != null) AsyncImage(item.artworkUri, null, Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
        else Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF26352E)), contentAlignment = Alignment.Center) { Icon(Icons.Default.MusicNote, null, tint = Lime) }
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) { Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold); Text(item.artist, color = Muted, style = MaterialTheme.typography.labelSmall) }
        IconButton(onClick = onToggle) { Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, if (playing) "Пауза" else "Играть", tint = Lime) }
        IconButton(onClick = onNext) { Icon(Icons.Default.SkipNext, "Следующий") }
    }
}

@Composable private fun NowPlaying(item: MusicItem, playing: Boolean, position: Float, duration: Float,
    onBack: () -> Unit, onToggle: () -> Unit, onPrevious: () -> Unit, onNext: () -> Unit, onSeek: (Float) -> Unit,
    onFavorite: () -> Unit, onShuffle: () -> Unit, onRepeat: () -> Unit, shuffle: Boolean, repeatMode: Int, modifier: Modifier = Modifier) {
    var scrubbing by remember(item.id) { mutableStateOf(false) }
    var scrubPosition by remember(item.id) { mutableFloatStateOf(position) }
    LaunchedEffect(position, scrubbing) { if (!scrubbing) scrubPosition = position }
    Column(modifier.fillMaxSize().padding(horizontal = 22.dp).padding(top = 12.dp, bottom = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
            Text("СЕЙЧАС ИГРАЕТ", Modifier.weight(1f), color = Muted, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            if (item.isOnline) Text("WEB", color = Lime, style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.weight(.5f))
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(30.dp)).background(Brush.linearGradient(listOf(Color(0xFF496C52), Color(0xFF1E3230), Color(0xFF161A21)))), contentAlignment = Alignment.Center) {
            if (item.artworkUri != null) AsyncImage(item.artworkUri, null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
            else Icon(Icons.Default.Album, null, tint = Lime.copy(alpha = .8f), modifier = Modifier.size(100.dp))
        }
        Spacer(Modifier.weight(.5f))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(item.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(item.artist, color = Muted, style = MaterialTheme.typography.titleMedium) }
            IconButton(onClick = onFavorite) { Icon(Icons.Default.FavoriteBorder, "Избранное", tint = Lime) }
        }
        Slider(scrubPosition.coerceIn(0f, duration), { scrubPosition = it; scrubbing = true }, onValueChangeFinished = { onSeek(scrubPosition); scrubbing = false }, valueRange = 0f..duration)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(timeText(scrubPosition.toLong()), color = Muted); Text(timeText(item.durationMs), color = Muted) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onShuffle) { Text(if (shuffle) "🔀" else "⇄", color = if (shuffle) Lime else Muted) }
            IconButton(onClick = onPrevious) { Icon(Icons.Default.SkipPrevious, "Предыдущий", Modifier.size(34.dp)) }
            Surface(onClick = onToggle, color = Lime, shape = CircleShape, modifier = Modifier.size(70.dp)) { Box(contentAlignment = Alignment.Center) { Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Ink, modifier = Modifier.size(38.dp)) } }
            IconButton(onClick = onNext) { Icon(Icons.Default.SkipNext, "Следующий", Modifier.size(34.dp)) }
            IconButton(onClick = onRepeat) { Text(if (repeatMode == Player.REPEAT_MODE_ONE) "1↻" else "↻", color = if (repeatMode == Player.REPEAT_MODE_OFF) Muted else Lime) }
        }
        Spacer(Modifier.weight(.3f))
    }
}

private fun timeText(ms: Long): String { val seconds = (ms / 1000).coerceAtLeast(0); return String.format(Locale.getDefault(), "%d:%02d", seconds / 60, seconds % 60) }
