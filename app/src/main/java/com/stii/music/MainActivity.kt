package com.stii.music

import android.Manifest
import android.content.ContentUris
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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
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
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
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
        player = ExoPlayer.Builder(this).build()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Lime,
                onPrimary = Ink,
                background = Ink,
                surface = Panel,
                onSurface = Color(0xFFF4F6F2),
                secondary = Color(0xFF8DD9C5)
            )) {
                StiiApp(player)
            }
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
    val durationMs: Long,
    val uri: Uri
)

private enum class Tab(val title: String) { HOME("Home"), SEARCH("Search"), LIBRARY("Library") }

@Composable
private fun StiiApp(player: ExoPlayer) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tracks = remember { mutableStateListOf<AudioTrack>() }
    var tab by remember { mutableStateOf(Tab.HOME) }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableIntStateOf(-1) }
    var playing by remember { mutableStateOf(false) }
    var showPlayer by remember { mutableStateOf(false) }
    var position by remember { mutableFloatStateOf(0f) }
    var duration by remember { mutableFloatStateOf(1f) }
    var libraryReady by remember { mutableStateOf(false) }

    fun loadLibrary() {
        scope.launch {
            val result = withContext(Dispatchers.IO) { readAudioLibrary(context) }
            tracks.clear()
            tracks.addAll(result)
            libraryReady = true
        }
    }

    val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) loadLibrary() else libraryReady = true
    }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) loadLibrary()
    }
    LaunchedEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                selected = mediaItem?.mediaId?.toIntOrNull() ?: selected
            }
        }
        player.addListener(listener)
        while (true) {
            position = player.currentPosition.coerceAtLeast(0L).toFloat()
            duration = player.duration.takeIf { it > 0 }?.toFloat() ?: 1f
            delay(400)
        }
    }

    val filtered = remember(tracks, query) {
        if (query.isBlank()) tracks.toList() else tracks.filter {
            it.title.contains(query, true) || it.artist.contains(query, true) || it.album.contains(query, true)
        }
    }

    fun playAt(index: Int) {
        if (index !in tracks.indices) return
        selected = index
        player.setMediaItems(tracks.map { track ->
            MediaItem.Builder().setMediaId(tracks.indexOf(track).toString()).setUri(track.uri).build()
        }, index, 0L)
        player.prepare()
        player.play()
        showPlayer = true
    }

    Scaffold(
        containerColor = Ink,
        bottomBar = {
            Column {
                if (!showPlayer && selected in tracks.indices) MiniPlayer(
                    track = tracks[selected], playing = playing,
                    onOpen = { showPlayer = true },
                    onPlayPause = { if (playing) player.pause() else player.play() },
                    onNext = { if (player.hasNextMediaItem()) player.seekToNextMediaItem() }
                )
                if (!showPlayer) NavigationBar(containerColor = Ink, contentColor = Lime) {
                    Tab.entries.forEach { item ->
                        NavigationBarItem(
                            selected = tab == item,
                            onClick = { tab = item },
                            icon = {
                                Icon(when (item) {
                                    Tab.HOME -> Icons.Default.Home
                                    Tab.SEARCH -> Icons.Default.Search
                                    Tab.LIBRARY -> Icons.Default.LibraryMusic
                                }, contentDescription = item.title)
                            },
                            label = { Text(item.title) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        AnimatedContent(targetState = showPlayer, label = "player-screen") { fullPlayer ->
            if (fullPlayer && selected in tracks.indices) {
                NowPlaying(
                    track = tracks[selected], playing = playing,
                    position = position, duration = duration,
                    onBack = { showPlayer = false },
                    onPlayPause = { if (playing) player.pause() else player.play() },
                    onPrevious = { if (player.hasPreviousMediaItem()) player.seekToPreviousMediaItem() },
                    onNext = { if (player.hasNextMediaItem()) player.seekToNextMediaItem() },
                    onSeek = { player.seekTo(it.toLong()) },
                    modifier = Modifier.padding(padding)
                )
            } else {
                Column(Modifier.fillMaxSize().padding(padding)) {
                    Header(tab)
                    if (tab == Tab.SEARCH) {
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
                    if (tab == Tab.HOME) HeroCard(
                        trackCount = tracks.size,
                        onOpenLibrary = { tab = Tab.LIBRARY }
                    )
                    if (!libraryReady || tracks.isEmpty()) {
                        EmptyLibrary(
                            permissionNeeded = ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED,
                            onOpen = {
                                if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) loadLibrary()
                                else permissionLauncher.launch(permission)
                            }
                        )
                    } else {
                        val list = if (tab == Tab.HOME) tracks.take(8) else filtered
                        Text(
                            text = when (tab) {
                                Tab.HOME -> "ON THIS DEVICE"
                                Tab.SEARCH -> "RESULTS · ${list.size}"
                                Tab.LIBRARY -> "YOUR SONGS · ${list.size}"
                            },
                            color = Muted, style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 22.dp, top = 18.dp, bottom = 8.dp)
                        )
                        LazyColumn(Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp)) {
                            itemsIndexed(list, key = { _, track -> track.id }) { _, track ->
                                val realIndex = tracks.indexOfFirst { it.id == track.id }
                                TrackRow(track, onClick = { playAt(realIndex) })
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun readAudioLibrary(context: android.content.Context): List<AudioTrack> {
    val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    val projection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.ALBUM,
        MediaStore.Audio.Media.DURATION
    )
    val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
    val result = mutableListOf<AudioTrack>()
    context.contentResolver.query(collection, projection, selection, null, "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC")?.use { cursor ->
        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
        val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
        val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
        val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
        while (cursor.moveToNext()) {
            val id = cursor.getLong(idColumn)
            val title = cursor.getString(titleColumn) ?: "Unknown track"
            val artist = cursor.getString(artistColumn)?.takeUnless { it == "<unknown>" } ?: "Unknown artist"
            val album = cursor.getString(albumColumn) ?: "Unknown album"
            val duration = cursor.getLong(durationColumn)
            if (duration > 1000) result += AudioTrack(
                id, title, artist, album, duration,
                ContentUris.withAppendedId(collection, id)
            )
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
            .clickable(onClick = onOpenLibrary).padding(22.dp)
    ) {
        Text("YOUR MUSIC,\nIN YOUR ELEMENT.", color = Color.White, style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(10.dp))
        Text("A private listening space. No account needed.", color = Color(0xFFCAD3CE), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(color = Lime, shape = CircleShape) {
                Text("  LISTEN NOW  ", color = Ink, modifier = Modifier.padding(vertical = 9.dp), fontWeight = FontWeight.Bold)
            }
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
        Text(
            if (permissionNeeded) "Allow access to audio files to scan music stored on this device."
            else "Stii will show music files available in your device library.",
            color = Muted, style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(18.dp))
        Button(onClick = onOpen) { Text(if (permissionNeeded) "Allow music access" else "Scan again") }
    }
}

@Composable
private fun TrackRow(track: AudioTrack, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(
            Brush.linearGradient(listOf(Color(0xFF30423A), Color(0xFF20262B)))
        ), contentAlignment = Alignment.Center) { Icon(Icons.Default.MusicNote, null, tint = Lime) }
        Column(Modifier.weight(1f).padding(horizontal = 13.dp)) {
            Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
            Text("${track.artist} · ${track.album}", maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = Muted, style = MaterialTheme.typography.bodySmall)
        }
        Icon(Icons.Default.PlayArrow, contentDescription = "Play ${track.title}", tint = Lime)
    }
}

@Composable
private fun MiniPlayer(track: AudioTrack, playing: Boolean, onOpen: () -> Unit, onPlayPause: () -> Unit, onNext: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp).clip(RoundedCornerShape(18.dp))
            .background(Panel).clickable(onClick = onOpen).padding(start = 12.dp, end = 5.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF26352E)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.MusicNote, null, tint = Lime)
        }
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
            Text(track.artist, color = Muted, style = MaterialTheme.typography.labelSmall)
        }
        IconButton(onClick = onPlayPause) { Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Lime) }
        IconButton(onClick = onNext) { Icon(Icons.Default.SkipNext, null) }
    }
}

@Composable
private fun NowPlaying(
    track: AudioTrack, playing: Boolean, position: Float, duration: Float,
    onBack: () -> Unit, onPlayPause: () -> Unit, onPrevious: () -> Unit,
    onNext: () -> Unit, onSeek: (Float) -> Unit, modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxSize().padding(horizontal = 22.dp).padding(
        top = 12.dp, bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp
    ), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text("NOW PLAYING", modifier = Modifier.weight(1f), color = Muted,
                style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            IconButton(onClick = {}) { Icon(Icons.Default.QueueMusic, "Queue", tint = Lime) }
        }
        Spacer(Modifier.weight(0.7f))
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(32.dp)).background(
                Brush.linearGradient(listOf(Color(0xFF496C52), Color(0xFF1E3230), Color(0xFF161A21)))
            ), contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Album, null, tint = Color(0x99C7F36B), modifier = Modifier.size(112.dp))
        }
        Spacer(Modifier.weight(0.7f))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(track.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(track.artist, color = Muted, style = MaterialTheme.typography.titleMedium)
            }
            IconButton(onClick = {}) { Icon(Icons.Default.FavoriteBorder, "Like", tint = Lime) }
        }
        Slider(value = position.coerceIn(0f, duration), onValueChange = onSeek, valueRange = 0f..duration, modifier = Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(position.toLong()), color = Muted, style = MaterialTheme.typography.labelSmall)
            Text(formatTime(track.durationMs), color = Muted, style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrevious, modifier = Modifier.size(58.dp)) { Icon(Icons.Default.SkipPrevious, "Previous", modifier = Modifier.size(34.dp)) }
            Surface(onClick = onPlayPause, color = Lime, shape = CircleShape, modifier = Modifier.size(72.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, "Play or pause", tint = Ink, modifier = Modifier.size(38.dp)) }
            }
            IconButton(onClick = onNext, modifier = Modifier.size(58.dp)) { Icon(Icons.Default.SkipNext, "Next", modifier = Modifier.size(34.dp)) }
        }
        Spacer(Modifier.weight(0.5f))
    }
}

private fun formatTime(milliseconds: Long): String {
    val totalSeconds = (milliseconds / 1000).coerceAtLeast(0)
    return String.format(Locale.getDefault(), "%d:%02d", totalSeconds / 60, totalSeconds % 60)
}
