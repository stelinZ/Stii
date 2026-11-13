package com.stii.music

import android.Manifest
import android.content.ComponentName
import android.os.Build
import android.os.Bundle
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil.compose.AsyncImage
import com.google.common.util.concurrent.ListenableFuture
import com.stii.music.data.MusicItem
import com.stii.music.ui.MusicSearchSource
import com.stii.music.ui.MusicUiState
import com.stii.music.ui.StiiViewModel
import java.util.Locale
import java.util.concurrent.ExecutionException

private val Ink = Color(0xFF090B0D)
private val Panel = Color(0xFF14181B)
private val Lime = Color(0xFFC7F36B)
private val Muted = Color(0xFF929A9D)

class MainActivity : ComponentActivity() {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val viewModel by lazy { ViewModelProvider(this)[StiiViewModel::class.java] }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showStartup()
    }

    private fun showStartup() {
        setContent {
            StiiTheme {
                Box(Modifier.fillMaxSize().background(Ink), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Lime)
                        Spacer(Modifier.height(14.dp))
                        Text("Запускаем Stii…", color = Muted)
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val future = MediaController.Builder(
            this,
            SessionToken(this, ComponentName(this, StiiPlaybackService::class.java)),
        ).buildAsync()
        controllerFuture = future
        future.addListener({
            try {
                val controller = future.get()
                if (!isFinishing && !isDestroyed) setContent {
                    StiiTheme { StiiApp(viewModel, controller) }
                }
            } catch (_: ExecutionException) {
                showStartup()
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

@Composable
private fun StiiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Lime,
            onPrimary = Ink,
            background = Ink,
            surface = Panel,
            onSurface = Color(0xFFF4F6F2),
            secondary = Color(0xFF8DD9C5),
        ),
        content = content,
    )
}

private enum class MainTab { HOME, SEARCH, LIBRARY }

@Composable
private fun StiiApp(viewModel: StiiViewModel, player: Player) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf(MainTab.HOME) }
    var showPlayer by remember { mutableStateOf(false) }
    var selectedId by remember { mutableStateOf(player.currentMediaItem?.mediaId) }
    var playing by remember { mutableStateOf(player.isPlaying) }
    var position by remember { mutableFloatStateOf(0f) }
    var duration by remember { mutableFloatStateOf(1f) }
    var shuffle by remember { mutableStateOf(player.shuffleModeEnabled) }
    val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.scanLibrary()
    }
    val allItems = remember(state.library, state.localResults, state.onlineResults) {
        (state.library + state.localResults + state.onlineResults).distinctBy { it.id }
    }
    val currentItem = remember(allItems, selectedId) { allItems.firstOrNull { it.id == selectedId } }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, permission) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            viewModel.scanLibrary()
        }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) { selectedId = mediaItem?.mediaId }
            override fun onPlaybackStateChanged(playbackState: Int) { duration = player.duration.takeIf { it > 0 }?.toFloat() ?: 1f }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    LaunchedEffect(player) {
        while (true) {
            position = player.currentPosition.coerceAtLeast(0L).toFloat()
            duration = player.duration.takeIf { it > 0 }?.toFloat() ?: 1f
            delay(250)
        }
    }

    fun play(item: MusicItem) {
        val queue = if (item.isOnline) listOf(item) else state.library
        val index = queue.indexOfFirst { it.id == item.id }
        if (index < 0) return
        val mediaItems = queue.map { song ->
            MediaItem.Builder()
                .setMediaId(song.id)
                .setUri(song.mediaUri)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(song.title).setArtist(song.artist)
                    .setAlbumTitle(song.album).setArtworkUri(song.artworkUri).build())
                .build()
        }
        player.setMediaItems(mediaItems, index, 0L)
        player.shuffleModeEnabled = shuffle
        player.prepare()
        player.play()
        selectedId = item.id
        showPlayer = true
    }

    Scaffold(
        containerColor = Ink,
        bottomBar = {
            Column {
                if (!showPlayer && currentItem != null) MiniPlayer(
                    item = currentItem,
                    isPlaying = playing,
                    onOpen = { showPlayer = true },
                    onPlayPause = { if (player.isPlaying) player.pause() else player.play() },
                    onNext = { if (player.hasNextMediaItem()) player.seekToNextMediaItem() },
                )
                if (!showPlayer) NavigationBar(containerColor = Ink, contentColor = Lime) {
                    listOf(MainTab.HOME, MainTab.SEARCH, MainTab.LIBRARY).forEach { item ->
                        val label = when (item) { MainTab.HOME -> "Главная"; MainTab.SEARCH -> "Поиск"; MainTab.LIBRARY -> "Избранное" }
                        NavigationBarItem(
                            selected = tab == item,
                            onClick = { tab = item; if (item != MainTab.SEARCH) viewModel.search("") },
                            icon = { Icon(when (item) { MainTab.HOME -> Icons.Default.Home; MainTab.SEARCH -> Icons.Default.Search; MainTab.LIBRARY -> Icons.Default.LibraryMusic }, label) },
                            label = { Text(label) },
                        )
                    }
                }
            }
        },
    ) { contentPadding ->
        AnimatedContent(
            targetState = showPlayer,
            transitionSpec = {
                (fadeIn(tween(250)) + slideInHorizontally(tween(300, easing = FastOutSlowInEasing)) { it / 8 }) togetherWith
                    (fadeOut(tween(160)) + slideOutHorizontally(tween(240)) { -it / 10 })
            },
            label = "player-screen",
        ) { fullPlayer ->
            if (fullPlayer && currentItem != null) {
                NowPlaying(
                    item = currentItem,
                    playing = playing,
                    position = position,
                    duration = duration,
                    shuffle = shuffle,
                    repeatMode = player.repeatMode,
                    onBack = { showPlayer = false },
                    onToggle = { if (player.isPlaying) player.pause() else player.play() },
                    onPrevious = { if (player.hasPreviousMediaItem()) player.seekToPreviousMediaItem() },
                    onNext = { if (player.hasNextMediaItem()) player.seekToNextMediaItem() },
                    onSeek = { player.seekTo(it.toLong()) },
                    onShuffle = { shuffle = !shuffle; player.shuffleModeEnabled = shuffle },
                    onRepeat = {
                        player.repeatMode = when (player.repeatMode) {
                            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                            else -> Player.REPEAT_MODE_OFF
                        }
                    },
                    onFavorite = { viewModel.toggleFavorite(currentItem) },
                    isFavorite = currentItem.id in state.favorites,
                    modifier = Modifier.padding(contentPadding),
                )
            } else {
                MainContent(
                    state = state,
                    tab = tab,
                    onQuery = viewModel::search,
                    onSource = viewModel::setSearchSource,
                    onPlay = ::play,
                    onFavorite = viewModel::toggleFavorite,
                    onScan = {
                        if (ContextCompat.checkSelfPermission(context, permission) == android.content.pm.PackageManager.PERMISSION_GRANTED) viewModel.scanLibrary()
                        else permissionLauncher.launch(permission)
                    },
                    modifier = Modifier.padding(contentPadding),
                )
            }
        }
    }
}

@Composable
private fun MainContent(
    state: MusicUiState,
    tab: MainTab,
    onQuery: (String) -> Unit,
    onSource: (MusicSearchSource) -> Unit,
    onPlay: (MusicItem) -> Unit,
    onFavorite: (MusicItem) -> Unit,
    onScan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = when (tab) {
        MainTab.HOME -> state.library.take(8)
        MainTab.SEARCH -> when (state.source) {
            MusicSearchSource.DEVICE -> state.localResults
            MusicSearchSource.OPEN_WEB -> state.onlineResults
        }
        MainTab.LIBRARY -> state.library.filter { it.id in state.favorites }
    }
    Column(modifier.fillMaxSize()) {
        Header(tab)
        AnimatedVisibility(visible = tab == MainTab.SEARCH, enter = fadeIn() + slideInVertically { -it / 3 }, exit = fadeOut() + slideOutVertically { -it / 4 }) {
            Column {
                TextField(
                    value = state.query,
                    onValueChange = onQuery,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
                    placeholder = { Text("Песня, исполнитель или альбом", color = Muted) },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = Muted) },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    colors = TextFieldDefaults.colors(focusedContainerColor = Panel, unfocusedContainerColor = Panel,
                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                )
                Row(Modifier.padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(state.source == MusicSearchSource.DEVICE, { onSource(MusicSearchSource.DEVICE) }, label = { Text("На устройстве") })
                    FilterChip(state.source == MusicSearchSource.OPEN_WEB, { onSource(MusicSearchSource.OPEN_WEB) }, label = { Text("Открытый интернет") })
                }
                if (state.source == MusicSearchSource.OPEN_WEB) Text(
                    "Internet Archive · аудио с распознанной открытой лицензией",
                    color = Muted, style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 4.dp),
                )
            }
        }
        AnimatedVisibility(visible = tab == MainTab.HOME, enter = fadeIn(tween(300)) + slideInVertically { it / 5 }, exit = fadeOut(tween(160))) {
            HeroCard(state.library.size, onScan)
        }
        if (state.error != null) Text(state.error, color = Color(0xFFFF9A8B), modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        val heading = when (tab) {
            MainTab.HOME -> "МУЗЫКА НА УСТРОЙСТВЕ"
            MainTab.SEARCH -> if (state.source == MusicSearchSource.DEVICE) "ЛОКАЛЬНЫЕ РЕЗУЛЬТАТЫ · ${items.size}" else "ОНЛАЙН-РЕЗУЛЬТАТЫ · ${items.size}"
            MainTab.LIBRARY -> "ИЗБРАННОЕ · ${items.size}"
        }
        Text(heading, color = Muted, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 22.dp, top = 14.dp, bottom = 6.dp))
        when {
            state.isScanning -> CenterMessage("Сканирую библиотеку…", true)
            tab == MainTab.SEARCH && state.source == MusicSearchSource.OPEN_WEB && state.query.trim().length < 2 -> CenterMessage("Введите минимум два символа для поиска", false)
            tab == MainTab.SEARCH && state.source == MusicSearchSource.OPEN_WEB && state.isSearchingOnline -> CenterMessage("Ищу в открытом аудиокаталоге…", true)
            tab == MainTab.SEARCH && state.source == MusicSearchSource.DEVICE && state.query.isBlank() -> CenterMessage("Введите запрос для поиска по устройству", false)
            items.isEmpty() -> CenterMessage(if (tab == MainTab.HOME) "Музыка на устройстве не найдена" else "Ничего не найдено", false)
            else -> LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 16.dp)) {
                items(items, key = { it.id }) { item ->
                    TrackRow(item, item.id in state.favorites, { onPlay(item) }, { onFavorite(item) })
                }
            }
        }
    }
}

@Composable
private fun CenterMessage(message: String, loading: Boolean) {
    Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (loading) CircularProgressIndicator(color = Lime)
        Spacer(Modifier.height(12.dp))
        Text(message, color = Muted)
    }
}

@Composable
private fun Header(tab: MainTab) {
    val title = when (tab) { MainTab.HOME -> "С возвращением"; MainTab.SEARCH -> "Найди свою музыку"; MainTab.LIBRARY -> "Избранное" }
    Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("STII", color = Lime, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black)
            Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
        Box(Modifier.size(42.dp).clip(CircleShape).background(Panel), contentAlignment = Alignment.Center) { Icon(Icons.Default.MusicNote, null, tint = Lime) }
    }
}

@Composable
private fun HeroCard(trackCount: Int, onScan: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp).clip(RoundedCornerShape(28.dp))
        .background(Brush.linearGradient(listOf(Color(0xFF25352D), Color(0xFF11191A), Color(0xFF17272A))))
        .clickable(onClick = onScan).padding(22.dp)) {
        Text("ТВОЯ МУЗЫКА.\nТВОЁ ПРОСТРАНСТВО.", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(10.dp))
        Text("Ищи локальную музыку и открытые аудиозаписи.", color = Color(0xFFCAD3CE))
        Spacer(Modifier.height(18.dp))
        Text("$trackCount треков на устройстве · нажми, чтобы обновить", color = Lime, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun TrackRow(item: MusicItem, favorite: Boolean, onClick: () -> Unit, onFavorite: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (item.artworkUri != null) AsyncImage(item.artworkUri, "Обложка ${item.album}", Modifier.size(54.dp).clip(RoundedCornerShape(15.dp)).background(Panel), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
        else Box(Modifier.size(54.dp).clip(RoundedCornerShape(15.dp)).background(Brush.linearGradient(listOf(Color(0xFF30423A), Color(0xFF20262B)))), contentAlignment = Alignment.Center) { Icon(Icons.Default.MusicNote, null, tint = Lime) }
        Column(Modifier.weight(1f).padding(horizontal = 13.dp)) {
            Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
            Text(listOf(item.artist, item.album).filter(String::isNotBlank).joinToString(" · "), maxLines = 1, overflow = TextOverflow.Ellipsis, color = Muted, style = MaterialTheme.typography.bodySmall)
            if (item.isOnline) Text(item.license ?: "Открытая лицензия", color = Lime, style = MaterialTheme.typography.labelSmall)
        }
        IconButton(onClick = onFavorite) { Icon(if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Избранное", tint = if (favorite) Lime else Muted) }
    }
}

@Composable
private fun MiniPlayer(item: MusicItem, isPlaying: Boolean, onOpen: () -> Unit, onPlayPause: () -> Unit, onNext: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp).clip(RoundedCornerShape(18.dp)).background(Panel).clickable(onClick = onOpen).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (item.artworkUri != null) AsyncImage(item.artworkUri, null, Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
        else Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF26352E)), contentAlignment = Alignment.Center) { Icon(Icons.Default.MusicNote, null, tint = Lime) }
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
            Text(item.artist, color = Muted, style = MaterialTheme.typography.labelSmall)
        }
        IconButton(onClick = onPlayPause) { Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, if (isPlaying) "Пауза" else "Играть", tint = Lime) }
        IconButton(onClick = onNext) { Icon(Icons.Default.SkipNext, "Следующий") }
    }
}

@Composable
private fun NowPlaying(
    item: MusicItem, playing: Boolean, position: Float, duration: Float, shuffle: Boolean, repeatMode: Int,
    onBack: () -> Unit, onToggle: () -> Unit, onPrevious: () -> Unit, onNext: () -> Unit, onSeek: (Float) -> Unit,
    onShuffle: () -> Unit, onRepeat: () -> Unit, onFavorite: () -> Unit, isFavorite: Boolean, modifier: Modifier = Modifier,
) {
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
        Box(Modifier.fillMaxWidth().aspectRatio(1f).graphicsLayer { scaleX = if (playing) 1.01f else 1f; scaleY = if (playing) 1.01f else 1f }
            .clip(RoundedCornerShape(30.dp)).background(Brush.linearGradient(listOf(Color(0xFF496C52), Color(0xFF1E3230), Color(0xFF161A21)))), contentAlignment = Alignment.Center) {
            if (item.artworkUri != null) AsyncImage(item.artworkUri, null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
            else Icon(Icons.Default.Album, null, tint = Lime.copy(alpha = .8f), modifier = Modifier.size(100.dp))
        }
        Spacer(Modifier.weight(.5f))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(item.artist, color = Muted, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            }
            IconButton(onClick = onFavorite) { Icon(if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Избранное", tint = if (isFavorite) Lime else Muted) }
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

private fun timeText(ms: Long): String {
    val seconds = (ms / 1000).coerceAtLeast(0)
    return String.format(Locale.getDefault(), "%d:%02d", seconds / 60, seconds % 60)
}
