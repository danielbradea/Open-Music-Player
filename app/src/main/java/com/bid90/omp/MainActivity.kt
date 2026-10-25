package com.bid90.omp

import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import com.bid90.omp.ui.theme.MusicBackground
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.bid90.omp.playback.LocalMusic
import com.bid90.omp.playback.PlaybackService
import com.bid90.omp.ui.theme.OpenMusicPlayerTheme
import com.bid90.omp.download.AudioDownloadService
import com.bid90.omp.download.SharedAudioLink
import android.content.Intent
import android.Manifest
import android.os.Build
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import androidx.activity.result.IntentSenderRequest
import android.content.ContentUris
import android.provider.MediaStore

class MainActivity : ComponentActivity() {
    private var sharedIntent by mutableStateOf<Intent?>(null)

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        val pendingShare = savedInstanceState?.getParcelable<Intent>("pending_audio_share")
        sharedIntent = pendingShare
        if (savedInstanceState == null && intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY == 0) {
            if (intent.action == Intent.ACTION_SEND && intent.hasExtra(Intent.EXTRA_TEXT)) sharedIntent = Intent(intent).putExtra("share_request_id", java.util.UUID.randomUUID().toString())
        }
        clearShareLaunchIntent()
        setContent { OpenMusicPlayerTheme { MusicScreen() } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_SEND) sharedIntent = Intent(intent).putExtra("share_request_id", java.util.UUID.randomUUID().toString())
        clearShareLaunchIntent()
    }

    private fun clearShareLaunchIntent() {
        setIntent(Intent(this, MainActivity::class.java).setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER))
    }

    override fun onSaveInstanceState(outState: Bundle) {
        sharedIntent?.let { outState.putParcelable("pending_audio_share", it) }
        super.onSaveInstanceState(outState)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun MusicScreen() {
        var allowed by remember { mutableStateOf(false) }
        var refresh by remember { mutableIntStateOf(0) }
        var tracks by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
        var loading by remember { mutableStateOf(false) }
        var controller by remember { mutableStateOf<MediaController?>(null) }
        var current by remember { mutableStateOf<MediaItem?>(null) }
        var playing by remember { mutableStateOf(false) }
        var playerExpanded by rememberSaveable { mutableStateOf(false) }
        val playerTransition = remember { androidx.compose.animation.core.MutableTransitionState(false) }
        playerTransition.targetState = playerExpanded
        LaunchedEffect(playerExpanded, playerTransition.currentState) {
            requestedOrientation = if (playerExpanded || playerTransition.currentState)
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        var error by remember { mutableStateOf<String?>(null) }
        var deleteTarget by remember { mutableStateOf<MediaItem?>(null) }
        var deleting by remember { mutableStateOf(false) }
        var pendingDeleteId by rememberSaveable { mutableStateOf<String?>(null) }
        var deleteError by remember { mutableStateOf<String?>(null) }
        val scope = rememberCoroutineScope()
        fun finishDelete(mediaId: String) {
            tracks = tracks.filterNot { it.mediaId == mediaId }
            controller?.let { player ->
                for (index in player.mediaItemCount - 1 downTo 0) {
                    if (player.getMediaItemAt(index).mediaId == mediaId) player.removeMediaItem(index)
                }
                current = player.currentMediaItem
                playing = player.isPlaying
            }
            deleteTarget = null
            deleting = false
            pendingDeleteId = null
            deleteError = null
            refresh++
        }
        val deletePermission = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            val mediaId = pendingDeleteId
            if (result.resultCode == RESULT_OK && mediaId != null) finishDelete(mediaId)
            else {
                deleting = false
                pendingDeleteId = null
            }
        }
        deleteTarget?.let { item ->
            AlertDialog(
                onDismissRequest = { if (!deleting) deleteTarget = null },
                title = { Text(getString(R.string.delete_song_question)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(item.mediaMetadata.title.toString(), fontWeight = FontWeight.Bold)
                        Text(getString(R.string.delete_file_warning, LocalMusic.FOLDER_NAME))
                        if (deleting) LinearProgressIndicator(Modifier.fillMaxWidth())
                        deleteError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                },
                confirmButton = {
                    TextButton(enabled = !deleting, onClick = {
                        deleting = true
                        deleteError = null
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) { LocalMusic(this@MainActivity).deleteTrack(item.mediaId) }
                                finishDelete(item.mediaId)
                            } catch (exception: SecurityException) {
                                android.util.Log.w("DeleteTrack", "System consent required", exception)
                                try {
                                    val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, item.mediaId.toLong())
                                    val request = withContext(Dispatchers.IO) {
                                        MediaStore.createDeleteRequest(contentResolver, listOf(uri))
                                    }
                                    pendingDeleteId = item.mediaId
                                    deletePermission.launch(IntentSenderRequest.Builder(request.intentSender).build())
                                } catch (_: Exception) {
                                    deleting = false
                                    pendingDeleteId = null
                                    deleteError = getString(R.string.delete_error)
                                }
                            } catch (exception: Exception) {
                                android.util.Log.e("DeleteTrack", "Could not delete track or update playback", exception)
                                deleting = false
                                deleteError = getString(R.string.delete_error)
                            }
                        }
                    }) { Text(getString(R.string.delete), color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = { TextButton(enabled = !deleting, onClick = { deleteTarget = null }) { Text(getString(R.string.cancel)) } }
            )
        }
        var folderError by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(Unit) {
            folderError = withContext(Dispatchers.IO) {
                runCatching { LocalMusic.ensureFolder() }.exceptionOrNull()?.message
            }
        }
        val download by AudioDownloadService.state.collectAsState()
        var downloadDialog by remember { mutableStateOf(false) }
        var downloadUrl by rememberSaveable { mutableStateOf("") }
        var downloadError by remember { mutableStateOf<String?>(null) }
        fun sendDownload(urls: List<String>, playlistName: String?, source: String, titles: List<String> = urls) {
            try {
                ContextCompat.startForegroundService(this@MainActivity,
                    Intent(this@MainActivity, AudioDownloadService::class.java)
                        .putStringArrayListExtra("urls", ArrayList(urls)).putStringArrayListExtra("titles", ArrayList(titles)).putExtra("playlist_name", playlistName)
                        .putExtra("source_url", source))
                downloadError = null
            } catch (_: Exception) { downloadError = getString(R.string.download_error) }
        }
        fun startDownload(url: String = downloadUrl) = sendDownload(listOf(url), null, url)
        var pendingDownload by rememberSaveable { mutableStateOf<String?>(null) }
        val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            pendingDownload?.let { data ->
                val json = org.json.JSONObject(data)
                val urls = json.getJSONArray("urls")
                sendDownload(List(urls.length()) { index -> urls.getString(index) },
                    json.optString("name").takeIf { it.isNotBlank() }, json.getString("source"),
                    json.optJSONArray("titles")?.let { values -> List(values.length()) { index -> values.getString(index) } } ?: emptyList())
            }
            pendingDownload = null
        }
        LaunchedEffect(sharedIntent) {
            val received = sharedIntent ?: return@LaunchedEffect
            val receipt = received.getStringExtra("share_request_id") ?: java.util.UUID.randomUUID().toString()
            val sharePreferences = getSharedPreferences("handled_shares", MODE_PRIVATE)
            val handled = sharePreferences.getStringSet("receipts", emptySet()).orEmpty()
            if (receipt in handled) {
                sharedIntent = null
                clearShareLaunchIntent()
                return@LaunchedEffect
            }
            val link = SharedAudioLink.extract(received)
            // Consume the share before starting work so rotation cannot download it twice.
            sharedIntent = null
            clearShareLaunchIntent()
            // Persist the receipt before starting a download, even across process recreation.
            sharePreferences.edit().putStringSet("receipts", (handled.toList().takeLast(63) + receipt).toSet()).commit()
            downloadDialog = true
            if (link == null) downloadError = getString(R.string.invalid_share)
            else {
                downloadUrl = link
                if (com.bid90.omp.download.YouTubePlaylist.playlistUrl(link) == null) startDownload(link)
            }
        }
        var observedCompleted by rememberSaveable { mutableLongStateOf(download.completed) }
        val engineUpdate by com.bid90.omp.download.DownloadEngine.updateState.collectAsState()
        LaunchedEffect(download.completed) {
            if (download.completed > observedCompleted) {
                refresh++
                if (downloadUrl.trim() == download.finishedSource) {
                    downloadUrl = ""
                    downloadError = null
                }
            }
            observedCompleted = download.completed
        }
        if (downloadDialog) AudioDownloadDialog(downloadUrl, onUrlChange = { downloadUrl = it; downloadError = null },
            download = download, error = downloadError, onDismiss = { downloadDialog = false },
            onCancel = { startService(Intent(this@MainActivity, AudioDownloadService::class.java).setAction("cancel")) },
            onDownload = { urls, name, titles ->
                if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this@MainActivity,
                    Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    pendingDownload = org.json.JSONObject().put("urls", org.json.JSONArray(urls)).put("name", name ?: "")
                        .put("source", downloadUrl.trim()).put("titles", org.json.JSONArray(titles)).toString()
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else sendDownload(urls, name, downloadUrl.trim(), titles)
            })
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            allowed = it
            refresh++
        }
        val lifecycleOwner = this@MainActivity
        DisposableEffect(lifecycleOwner) {
            val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                    allowed = ContextCompat.checkSelfPermission(this@MainActivity, LocalMusic.permission) == PackageManager.PERMISSION_GRANTED
                    refresh++
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
        LaunchedEffect(allowed, refresh) {
            loading = true
            try {
                tracks = if (allowed) withContext(Dispatchers.IO) { LocalMusic(this@MainActivity).tracks() } else emptyList()
            } finally { loading = false }
        }
        DisposableEffect(Unit) {
            val future = MediaController.Builder(this@MainActivity,
                SessionToken(this@MainActivity, ComponentName(this@MainActivity, PlaybackService::class.java))).buildAsync()
            val listener = object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) {
                    current = player.currentMediaItem
                    playing = player.isPlaying
                }
                override fun onPlayerError(playbackError: PlaybackException) {
                    error = getString(R.string.file_error)
                }
            }
            var disposed = false
            future.addListener({
                if (!disposed) {
                    try {
                        controller = future.get().also {
                            it.addListener(listener)
                            current = it.currentMediaItem
                            playing = it.isPlaying
                        }
                    } catch (_: Exception) { error = getString(R.string.connection_error) }
                }
            }, ContextCompat.getMainExecutor(this@MainActivity))
            onDispose {
                disposed = true
                controller?.removeListener(listener)
                MediaController.releaseFuture(future)
                controller = null
            }
        }
        val playlistStore = remember { PlaylistStore(this@MainActivity) }
        var playlists by remember { mutableStateOf(playlistStore.load()) }
        DisposableEffect(playlistStore) {
            val prefs = getSharedPreferences("playlists", MODE_PRIVATE)
            val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (key == "lists") playlists = playlistStore.load()
            }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        }
        var createPlaylist by rememberSaveable { mutableStateOf(false) }
        val libraryPreferences = remember { getSharedPreferences("library_state", MODE_PRIVATE) }
        var selectedPlaylistId by rememberSaveable { mutableStateOf(libraryPreferences.getString("playlist", null)) }
        LaunchedEffect(selectedPlaylistId) {
            libraryPreferences.edit().putString("playlist", selectedPlaylistId).apply()
        }
        val selectedPlaylist = playlists.firstOrNull { it.id == selectedPlaylistId }
        val playlistTracks = remember(tracks, selectedPlaylist) {
            if (selectedPlaylist == null) tracks else {
                val byId = tracks.associateBy { it.mediaId }
                selectedPlaylist.trackIds.mapNotNull { byId[it] }
            }
        }
        var query by remember { mutableStateOf("") }
        var searchOpen by remember { mutableStateOf(false) }
        val searchFocus = androidx.compose.ui.platform.LocalFocusManager.current
        val searchKeyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
        fun closeSearch(clear: Boolean = false) {
            searchOpen = false
            if (clear) query = ""
            searchFocus.clearFocus()
            searchKeyboard?.hide()
        }
        val visibleTracks = remember(playlistTracks, query) {
            playlistTracks.filter { query.isBlank() || it.mediaMetadata.title.toString().contains(query, true) ||
                it.mediaMetadata.artist.toString().contains(query, true) }
        }
        var mainMenu by remember { mutableStateOf(false) }
        var settingsOpen by rememberSaveable { mutableStateOf(false) }
        var playlistFolderMessage by remember { mutableStateOf<String?>(null) }
        var connectingFolder by remember { mutableStateOf(false) }
        val playlistFolderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                connectingFolder = true
                scope.launch {
                    try {
                        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                        val count = withContext(Dispatchers.IO) { PlaylistBackup.connect(this@MainActivity, uri) }
                        playlists = playlistStore.load()
                        playlistFolderMessage = getString(R.string.playlist_folder_connected, count)
                    } catch (exception: Exception) {
                        android.util.Log.w("PlaylistBackup", "Folder connection failed", exception)
                        playlistFolderMessage = getString(R.string.playlist_folder_error)
                    } finally { connectingFolder = false }
                }
            }
        }
        if (settingsOpen) LanguageSettings(this@MainActivity,
            folderMessage = playlistFolderMessage, folderBusy = connectingFolder,
            onChooseFolder = {
                if (allowed) playlistFolderPicker.launch(null)
                else playlistFolderMessage = getString(R.string.folder_needs_audio)
            }, onDismiss = { settingsOpen = false }, onSelect = { code ->
            if (AppLanguage.selected(this@MainActivity) != code) {
                AppLanguage.save(this@MainActivity, code)
                recreate()
            }
        })
        Scaffold(containerColor = Color.Transparent, modifier = Modifier.background(MusicBackground)) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp)) {
                Spacer(Modifier.height(24.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(androidx.compose.ui.text.buildAnnotatedString {
                    pushStyle(androidx.compose.ui.text.SpanStyle(color = Color.White))
                    append("Open")
                    pop()
                    pushStyle(androidx.compose.ui.text.SpanStyle(color = MaterialTheme.colorScheme.primary))
                    append("MusicPlayer")
                    pop()
                }, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold,
                    maxLines = 1, modifier = Modifier.weight(1f))
                Box {
                    IconButton(onClick = { mainMenu = true }) {
                        Text("⋮", color = Color.White, style = MaterialTheme.typography.headlineMedium,
                            modifier = Modifier.semantics { contentDescription = getString(R.string.app_menu) })
                    }
                    DropdownMenu(expanded = mainMenu, onDismissRequest = { mainMenu = false }) {
                        DropdownMenuItem(text = { Text(getString(R.string.new_playlist), color = Color.White) },
                            onClick = { mainMenu = false; createPlaylist = true })
                        DropdownMenuItem(text = { Text(getString(R.string.settings), color = Color.White) }, onClick = {
                            mainMenu = false
                            settingsOpen = true
                        })
                        DropdownMenuItem(text = {
                            Text(if (download.running) getString(R.string.download_progress, download.progress) else getString(R.string.download_link), color = Color.White)
                        }, onClick = { mainMenu = false; downloadDialog = true })
                    }
                }
                }
                folderError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Spacer(Modifier.height(20.dp))
                if (!allowed) {
                    Text(getString(R.string.audio_permission_info, LocalMusic.FOLDER_NAME))
                    Button(onClick = { launcher.launch(LocalMusic.permission) }) { Text(getString(R.string.allow_access)) }
                    TextButton(onClick = {
                        startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            android.net.Uri.parse("package:$packageName")))
                    }) { Text(getString(R.string.android_settings)) }
                } else {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(selectedPlaylist?.name ?: getString(R.string.library), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(getString(R.string.song_count, playlistTracks.size), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (tracks.isNotEmpty()) IconButton(onClick = {
                            searchOpen = true
                        }, modifier = Modifier.semantics { contentDescription = getString(R.string.search_library) }) {
                            SearchIcon()
                        }
                    }
                }
                Box {
                PlaylistControls(playlists, selectedPlaylistId, tracks,
                    onSelect = { selectedPlaylistId = it },
                    onSave = { playlist -> playlistStore.upsert(playlist); playlists = playlistStore.load() },
                    onDelete = { id -> playlistStore.delete(id); playlists = playlistStore.load() },
                    createRequested = createPlaylist, onCreateHandled = { createPlaylist = false })
                }
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (allowed && searchOpen) {
                    CompactSearchField(query, onQueryChange = { query = it }, onClose = { closeSearch(true) })
                }
                if (allowed && !loading && tracks.isEmpty()) Text(getString(R.string.empty_library, LocalMusic.FOLDER_NAME))
                if (allowed && selectedPlaylist != null && playlistTracks.isEmpty()) Text(getString(R.string.empty_playlist_help))
                if (allowed && playlistTracks.isNotEmpty() && visibleTracks.isEmpty()) Text(getString(R.string.no_results))
                PullToRefreshBox(isRefreshing = loading, onRefresh = {
                    if (allowed && !loading) refresh++
                }, modifier = Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    itemsIndexed(visibleTracks, key = { _, item -> item.mediaId }) { _, item ->
                        LibraryTrackRow(item, selected = current?.mediaId == item.mediaId, playlists = playlists,
                            onPlay = {
                                error = null
                                playerExpanded = true
                                controller?.apply { setMediaItems(playlistTracks, playlistTracks.indexOf(item), 0L); prepare(); play() }
                            },
                            onDelete = { deleteError = null; deleteTarget = item },
                            onAddToPlaylist = { playlist ->
                                playlistStore.addTrack(playlist.id, item.mediaId)
                                playlists = playlistStore.load()
                            },
                            onRemoveFromPlaylist = selectedPlaylist?.let { playlist -> {
                                playlistStore.removeTrack(playlist.id, item.mediaId)
                                playlists = playlistStore.load()
                            } })
                    }
                }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                current?.let { item ->
                    Box {
                    CompactPlayer(item, controller, onExpand = { playerExpanded = true })
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
        if ((playerTransition.targetState || playerTransition.currentState) && current != null) {
            FullPlayer(current!!, controller, visibility = playerTransition, onClose = { playerExpanded = false },
                onDelete = { playerExpanded = false; deleteError = null; deleteTarget = current },
                playlists = playlists, selectedPlaylistId = selectedPlaylistId, playlistTracks = playlistTracks,
                onNewPlaylist = { playerExpanded = false; createPlaylist = true },
                onDownload = { playerExpanded = false; downloadDialog = true },
                onPlaylistSelected = { id ->
                    selectedPlaylistId = id
                    val playlist = playlists.firstOrNull { it.id == id }
                    val byId = tracks.associateBy { it.mediaId }
                    val queue = if (playlist == null) tracks else playlist.trackIds.mapNotNull { byId[it] }
                    if (queue.isEmpty()) {
                        playerExpanded = false
                    } else controller?.let { player ->
                        val index = queue.indexOfFirst { it.mediaId == player.currentMediaItem?.mediaId }
                        val resume = player.isPlaying
                        val position = if (index >= 0) player.currentPosition else 0L
                        player.setMediaItems(queue, index.coerceAtLeast(0), position)
                        player.prepare()
                        player.playWhenReady = resume
                    }
                })
        }
    }
}

