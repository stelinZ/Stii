package com.stii.music

import android.Manifest
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
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private val Ink = Color(0xFF090B0D)
private val Panel = Color(0xFF14181B)
private val Lime = Color(0xFFC7F36B)
private val Muted = Color(0xFF929A9D)

class MainActivity : ComponentActivity() {
    private lateinit var player: ExoPlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        player = ExoPlayer.Builder(this).build().apply {
            setAudioAttributes(AudioAttributes.DEFAULT, true)
            setHandleAudioBecomingNoisy(true)
        }
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Lime, onPrimary = Ink, background = Ink, surface = Panel,
                onSurface = Color(0xFFF4F6F2), secondary = Color(0xFF8DD9C5)
            )) { StiiApp(player) }
        }
    }

    override fun onDestroy() {
        player.release()
        super.onDestroy()
    }
}

private data class AudioTrack(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val uri: Uri,
    // Build the search document once while scanning, not once for every keypress.
    val searchDocument: String
) {
    val artwork: Uri get() = ContentUris.withAppendedId(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, albumId)
}

private enum class Tab(val title: String) { HOME("Home"), SEARCH("Search"), LIBRARY("Library") }

@Composable
private fun StiiApp(player: ExoPlayer) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tracks by remember { mutableStateOf<List<AudioTrack>>(emptyList()) }
    var results by remember { mutableStateOf<List<AudioTrack>>(emptyList()) }
    var tab by remember { mutableStateOf(Tab.HOME) }
    var query by remember { mutableStateOf("") }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var playing by remember { mutableStateOf(false) }
    var showPlayer by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var position by remember { mutableFloatStateOf(0f) }
    var duration by remember { mutableFloatStateOf(1f) }
    var libraryReady by remember { mutableStateOf(false) }
    var favorites by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var shuffle by remember { mutableStateOf(false) }

    val currentTrack = remember(tracks, selectedId) { tracks.firstOrNull { it.id.toString() == selectedId } }
    val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE

    fun loadLibrary() {
        scope.launch {
            val loaded = withContext(Dispatchers.IO) { readAudioLibrary(context) }
            tracks = loaded
            libraryReady = true
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) loadLibrary() else libraryReady = true
    }

    LaunchedEffect(Unit) {
        favorites = context.getSharedPreferences("stii_library", Context.MODE_PRIVATE)
            .getStringSet("favorites", emptySet()).orEmpty().mapNotNull(String::toLongOrNull).toSet()
        if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) loadLibrary()
    }

    // Keep the index/filter off the UI thread, cancel stale work on every edit, and do not debounce
    // local search: the result reflects the latest keystroke as soon as the worker finishes.
    LaunchedEffect(query, tracks, tab, favorites) {
        val input = query.trim().lowercase(Locale.ROOT)
        results = withContext(Dispatchers.Default) {
            val source = when (tab) {
                Tab.LIBRARY -> tracks.filter { it.id in favorites }
                else -> tracks
            }
            if (input.isEmpty()) source
            else source.asSequence()
                .mapNotNull { track ->
                    val rank = when {
                        track.title.lowercase(Locale.ROOT) == input -> 0
                        track.title.lowercase(Locale.ROOT).startsWith(input) -> 1
                        track.artist.lowercase(Locale.ROOT).startsWith(input) -> 2
                        track.searchDocument.contains(input) -> 3
                        else -> return@mapNotNull null
                    }
                    rank to track
                }
                .sortedBy { it.first }
                .map { it.second }
                .toList()
        }
    }

    LaunchedEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                selectedId = mediaItem?.mediaId
            }
        }
        player.addListener(listener)
        try {
            while (true) {
                position = player.currentPosition.coerceAtLeast(0L).toFloat()
                duration = player.duration.takeIf { it > 0 }?.toFloat() ?: 1f
                delay(250)
            }
        } finally {
            player.removeListener(listener)
        }
    }

    fun playAt(track: AudioTrack) {
        val queue = tracks.map { item ->
            MediaItem.Builder()
                .setMediaId(item.id.toString())
                .setUri(item.uri)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(item.title).setArtist(item.artist).setAlbumTitle(item.album).setArtworkUri(item.artwork).build())
                .build()
        }
        val index = tracks.indexOfFirst { it.id == track.id }
        if (index < 0) return
        player.setMediaItems(queue, index, 0L)
        player.shuffleModeEnabled = shuffle
        player.prepare()
        player.play()
        selectedId = track.id.toString()
        showPlayer = true
    }

    fun toggleFavorite(track: AudioTrack) {
        favorites = if (track.id in favorites) favorites - track.id else favorites + track.id
        context.getSharedPreferences("stii_library", Context.MODE_PRIVATE).edit()
            .putStringSet("favorites", favorites.map(Long::toString).toSet()).apply()
    }

    Scaffold(
        containerColor = Ink,
        bottomBar = {
            Column {
                if (!showPlayer && currentTrack != null) MiniPlayer(
                    track = currentTrack, playing = playing,
                    onOpen = { showPlayer = true },
                    onPlayPause = { if (playing) player.pause() else player.play() },
                    onNext = { if (player.hasNextMediaItem()) player.seekToNextMediaItem() }
                )
                if (!showPlayer) NavigationBar(containerColor = Ink, contentColor = Lime) {
                    Tab.entries.forEach { item ->
                        NavigationBarItem(
                            selected = tab == item,
                            onClick = { tab = item; if (item != Tab.SEARCH) query = "" },
                            icon = { Icon(when (item) {
                                Tab.HOME -> Icons.Default.Home
                                Tab.SEARCH -> Icons.Default.Search
                                Tab.LIBRARY -> Icons.Default.LibraryMusic
                            }, contentDescription = item.title) },
                            label = { Text(item.title) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        AnimatedContent(
            targetState = showPlayer,
            transitionSpec = {
                (fadeIn(tween(260)) + slideInHorizontally(tween(320, easing = FastOutSlowInEasing)) { it / 7 }) togetherWith
                    (fadeOut(tween(180)) + slideOutHorizontally(tween(260)) { -it / 10 })
            },
            label = "player-screen"
        ) { fullPlayer ->
            if (fullPlayer && currentTrack != null) {
                NowPlaying(
                    track = currentTrack, playing = playing, position = position, duration = duration,
                    favorite = currentTrack.id in favorites,
                    shuffle = shuffle, repeatMode = player.repeatMode,
                    onBack = { showPlayer = false },
                    onPlayPause = { if (playing) player.pause() else player.play() },
                    onPrevious = { if (player.hasPreviousMediaItem()) player.seekToPreviousMediaItem() },
                    onNext = { if (player.hasNextMediaItem()) player.seekToNextMediaItem() },
                    onSeek = { player.seekTo(it.toLong()) },
                    onFavorite = { toggleFavorite(currentTrack) },
                    onShuffle = { shuffle = !shuffle; player.shuffleModeEnabled = shuffle },
                    onRepeat = { player.repeatMode = when (player.repeatMode) {
                        Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                        Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                        else -> Player.REPEAT_MODE_OFF
                    } },
                    onQueue = { showQueue = true },
                    modifier = Modifier.padding(padding)
                )
            } else {
                Column(Modifier.fillMaxSize().padding(padding)) {
                    Header(tab)
                    AnimatedVisibility(visible = tab == Tab.SEARCH, enter = fadeIn() + slideInVertically { -it / 3 }, exit = fadeOut() + slideOutVertically { -it / 4 }) {
                        TextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                            placeholder = { Text("Songs, artists, albums", color = Muted) },
                            leadingIcon = { Icon(Icons.Default.Search, null, tint = Muted) },
                            singleLine = true,
                            shape = RoundedCornerShape(18.dp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Panel, unfocusedContainerColor = Panel,
                                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent
                            )
                        )
                    }
                    AnimatedVisibility(visible = tab == Tab.HOME, enter = fadeIn(tween(300)) + slideInVertically { it / 5 }, exit = fadeOut(tween(160))) {
                        HeroCard(trackCount = tracks.size, onOpenLibrary = { tab = Tab.LIBRARY })
                    }
                    if (!libraryReady || tracks.isEmpty()) {
                        EmptyLibrary(
                            permissionNeeded = ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED,
                            onOpen = {
                                if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) loadLibrary()
                                else permissionLauncher.launch(permission)
                            }
                        )
                    } else {
                        val list = when (tab) {
                            Tab.HOME -> tracks.take(8)
                            Tab.SEARCH -> results
                            Tab.LIBRARY -> results
                        }
                        Text(
                            text = when (tab) {
                                Tab.HOME -> "ON THIS DEVICE"
                                Tab.SEARCH -> "RESULTS · ${list.size}"
                                Tab.LIBRARY -> "LIKED SONGS · ${list.size}"
                            },
                            color = Muted, style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 22.dp, top = 18.dp, bottom = 8.dp)
                        )
                        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 16.dp)) {
                            items(list, key = { it.id }) { track ->
                                TrackRow(track, favorite = track.id in favorites,
                                    onClick = { playAt(track) }, onFavorite = { toggleFavorite(track) })
                            }
                        }
                    }
                }
            }
        }
    }

    if (showQueue) {
        AlertDialog(
            onDismissRequest = { showQueue = false },
            title = { Text("Up next") },
            text = {
                LazyColumn(Modifier.height(360.dp)) {
                    items(tracks, key = { it.id }) { track ->
                        Row(Modifier.fillMaxWidth().clickable { playAt(track); showQueue = false }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.MusicNote, null, tint = if (track.id.toString() == selectedId) Lime else Muted)
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(track.artist, color = Muted, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showQueue = false }) { Text("Close") } },
            containerColor = Panel
        )
    }
}

private fun readAudioLibrary(context: Context): List<AudioTrack> {
    val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    val projection = arrayOf(
        MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM,
        MediaStore.Audio.Media.ALBUM_ID, MediaStore.Audio.Media.DURATION
    )
    val result = mutableListOf<AudioTrack>()
    context.contentResolver.query(collection, projection, "${MediaStore.Audio.Media.IS_MUSIC} != 0", null,
        "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC")?.use { cursor ->
        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
        val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
        val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
        val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
        val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
        while (cursor.moveToNext()) {
            val id = cursor.getLong(idColumn)
            val title = cursor.getString(titleColumn) ?: "Unknown track"
            val artist = cursor.getString(artistColumn)?.takeUnless { it == "<unknown>" } ?: "Unknown artist"
            val album = cursor.getString(albumColumn) ?: "Unknown album"
            val albumId = cursor.getLong(albumIdColumn)
            val duration = cursor.getLong(durationColumn)
            if (duration > 1000) {
                val document = "$title $artist $album".lowercase(Locale.ROOT)
                result += AudioTrack(id, title, artist, album, albumId, duration,
                    ContentUris.withAppendedId(collection, id), document)
            }
        }
    }
    return result
}

@Composable
private fun Header(tab: Tab) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("STII", color = Lime, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black)
            Text(when (tab) { Tab.HOME -> "Good evening"; Tab.SEARCH -> "Find your sound"; Tab.LIBRARY -> "Your library" },
                style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
        Box(Modifier.size(42.dp).clip(CircleShape).background(Panel), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.MusicNote, null, tint = Lime)
        }
    }
}

@Composable
private fun HeroCard(trackCount: Int, onOpenLibrary: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp).clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF25352D), Color(0xFF11191A), Color(0xFF17272A))))
            .clickable(onClick = onOpenLibrary).animateContentSize(spring()).padding(22.dp)
    ) {
        Text("YOUR MUSIC,\nIN YOUR ELEMENT.", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(10.dp))
        Text("A private listening space. No account needed.", color = Color(0xFFCAD3CE), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(color = Lime, shape = CircleShape) { Text("  LISTEN NOW  ", color = Ink, modifier = Modifier.padding(vertical = 9.dp), fontWeight = FontWeight.Bold) }
            Spacer(Modifier.width(12.dp))
            Text("$trackCount tracks on device", color = Color(0xFFCAD3CE), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun EmptyLibrary(permissionNeeded: Boolean, onOpen: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(76.dp).clip(RoundedCornerShape(26.dp)).background(Panel), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Album, null, tint = Lime, modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text(if (permissionNeeded) "Your music stays yours" else "Nothing here yet", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(if (permissionNeeded) "Allow access to audio files to scan music stored on this device." else "Stii will show music files available in your device library.", color = Muted, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(18.dp))
        Button(onClick = onOpen) { Text(if (permissionNeeded) "Allow music access" else "Scan again") }
    }
}

@Composable
private fun TrackRow(track: AudioTrack, favorite: Boolean, onClick: () -> Unit, onFavorite: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(model = track.artwork, contentDescription = "Album art for ${track.album}",
            modifier = Modifier.size(54.dp).clip(RoundedCornerShape(15.dp)).background(Panel), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
        Column(Modifier.weight(1f).padding(horizontal = 13.dp)) {
            Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
            Text("${track.artist} · ${track.album}", maxLines = 1, overflow = TextOverflow.Ellipsis, color = Muted, style = MaterialTheme.typography.bodySmall)
        }
        IconButton(onClick = onFavorite) { Icon(if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Like ${track.title}", tint = if (favorite) Lime else Muted) }
    }
}

@Composable
private fun MiniPlayer(track: AudioTrack, playing: Boolean, onOpen: () -> Unit, onPlayPause: () -> Unit, onNext: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp).clip(RoundedCornerShape(18.dp))
            .background(Panel).clickable(onClick = onOpen).padding(start = 9.dp, end = 5.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(model = track.artwork, contentDescription = null, modifier = Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
            Text(track.artist, color = Muted, style = MaterialTheme.typography.labelSmall)
        }
        IconButton(onClick = onPlayPause) { PlayPauseIcon(playing, Lime) }
        IconButton(onClick = onNext) { Icon(Icons.Default.SkipNext, null) }
    }
}

@Composable
private fun PlayPauseIcon(playing: Boolean, tint: Color, modifier: Modifier = Modifier) {
    AnimatedContent(targetState = playing, transitionSpec = {
        (fadeIn(tween(120)) + scaleIn(initialScale = .72f, animationSpec = spring())) togetherWith
            (fadeOut(tween(90)) + scaleOut(targetScale = .78f, animationSpec = spring()))
    }, label = "play-pause") { active ->
        Icon(if (active) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = if (active) "Pause" else "Play", tint = tint, modifier = modifier)
    }
}

@Composable
private fun NowPlaying(
    track: AudioTrack, playing: Boolean, position: Float, duration: Float, favorite: Boolean,
    shuffle: Boolean, repeatMode: Int, onBack: () -> Unit, onPlayPause: () -> Unit,
    onPrevious: () -> Unit, onNext: () -> Unit, onSeek: (Float) -> Unit,
    onFavorite: () -> Unit, onShuffle: () -> Unit, onRepeat: () -> Unit,
    onQueue: () -> Unit, modifier: Modifier = Modifier
) {
    var scrubbing by remember(track.id) { mutableStateOf(false) }
    var scrubPosition by remember(track.id) { mutableFloatStateOf(position) }
    LaunchedEffect(position, scrubbing) { if (!scrubbing) scrubPosition = position }
    val transition = updateTransition(targetState = playing, label = "artwork-motion")
    val artworkScale by transition.animateFloat(transitionSpec = { spring(dampingRatio = .78f, stiffness = 180f) }, label = "cover-scale") { if (it) 1.015f else 1f }
    Column(modifier.fillMaxSize().padding(horizontal = 22.dp).padding(
        top = 12.dp, bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp
    ), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text("NOW PLAYING", modifier = Modifier.weight(1f), color = Muted, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            IconButton(onClick = onQueue) { Icon(Icons.Default.QueueMusic, "Queue", tint = Lime) }
        }
        Spacer(Modifier.weight(.55f))
        Box(Modifier.fillMaxWidth().aspectRatio(1f).graphicsLayer { scaleX = artworkScale; scaleY = artworkScale }
            .clip(RoundedCornerShape(32.dp)).background(Brush.linearGradient(listOf(Color(0xFF496C52), Color(0xFF1E3230), Color(0xFF161A21))),
                contentAlignment = Alignment.Center) {
            AsyncImage(model = track.artwork, contentDescription = "Album art for ${track.album}", modifier = Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .14f)))
            Icon(Icons.Default.Album, null, tint = Color.White.copy(alpha = .74f), modifier = Modifier.size(64.dp))
        }
        Spacer(Modifier.weight(.55f))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                AnimatedContent(targetState = track.title, transitionSpec = {
                    (fadeIn(tween(180)) + slideInVertically { it / 4 }) togetherWith (fadeOut(tween(120)) + slideOutVertically { -it / 4 })
                }, label = "track-title") { title ->
                    Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(track.artist, color = Muted, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onFavorite) { Icon(if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Like", tint = if (favorite) Lime else Muted) }
        }
        Slider(
            value = scrubPosition.coerceIn(0f, duration),
            onValueChange = { scrubPosition = it; scrubbing = true },
            onValueChangeFinished = { onSeek(scrubPosition); scrubbing = false },
            valueRange = 0f..duration, modifier = Modifier.fillMaxWidth()
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(scrubPosition.toLong()), color = Muted, style = MaterialTheme.typography.labelSmall)
            Text(formatTime(track.durationMs), color = Muted, style = MaterialTheme.typography.labelSmall)
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onShuffle) { Icon(Icons.Default.Shuffle, "Shuffle", tint = if (shuffle) Lime else Muted) }
            IconButton(onClick = onPrevious, modifier = Modifier.size(54.dp)) { Icon(Icons.Default.SkipPrevious, "Previous", modifier = Modifier.size(32.dp)) }
            Surface(onClick = onPlayPause, color = Lime, shape = CircleShape, modifier = Modifier.size(70.dp)) {
                Box(contentAlignment = Alignment.Center) { PlayPauseIcon(playing, Ink, Modifier.size(38.dp)) }
            }
            IconButton(onClick = onNext, modifier = Modifier.size(54.dp)) { Icon(Icons.Default.SkipNext, "Next", modifier = Modifier.size(32.dp)) }
            IconButton(onClick = onRepeat) {
                Icon(if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                    "Repeat mode", tint = if (repeatMode != Player.REPEAT_MODE_OFF) Lime else Muted)
            }
        }
        Spacer(Modifier.weight(.35f))
    }
}

private fun formatTime(milliseconds: Long): String {
    val totalSeconds = (milliseconds / 1000).coerceAtLeast(0)
    return String.format(Locale.getDefault(), "%d:%02d", totalSeconds / 60, totalSeconds % 60)
}
