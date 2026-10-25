package com.bid90.omp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bid90.omp.download.AudioDownloadService
import com.bid90.omp.download.AudioDownloadState
import com.bid90.omp.download.DownloadEngine
import com.bid90.omp.download.OnlinePlaylist
import com.bid90.omp.download.YouTubePlaylist
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

@Composable
internal fun AudioDownloadDialog(url: String, onUrlChange: (String) -> Unit, download: AudioDownloadState,
                                 error: String?, onDismiss: () -> Unit, onCancel: () -> Unit,
                                 onDownload: (List<String>, String?) -> Unit) {
    val context = LocalContext.current
    val engine by DownloadEngine.updateState.collectAsState()
    val playlistLink = YouTubePlaylist.playlistUrl(url)
    var preview by remember(url) { mutableStateOf<OnlinePlaylist?>(null) }
    var loading by remember(url) { mutableStateOf(playlistLink != null) }
    var failed by remember(url) { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    var selectionInitialized by rememberSaveable(url) { mutableStateOf(false) }
    var selected by rememberSaveable(url) { mutableStateOf(arrayListOf<String>()) }
    var createLocal by rememberSaveable(url) { mutableStateOf(true) }
    var name by rememberSaveable(url) { mutableStateOf("") }
    LaunchedEffect(url, retry) {
        if (playlistLink == null || download.running) { loading = false; return@LaunchedEffect }
        loading = true
        failed = false
        delay(650)
        try {
            val result = YouTubePlaylist.inspect(context.applicationContext, url)
            preview = result
            if (!selectionInitialized) {
                selected = ArrayList(result.tracks.filter { it.available }.map { it.id })
                selectionInitialized = true
            }
            if (name.isBlank()) name = result.title
        } catch (exception: CancellationException) { throw exception }
        catch (_: Exception) { failed = true }
        finally { loading = false }
    }
    val selection = preview?.tracks?.filter { it.available && it.id in selected }.orEmpty()
    val ready = AudioDownloadService.validUrl(url) && !download.running && !loading &&
        engine != DownloadEngine.UpdateState.RUNNING &&
        (playlistLink == null || (preview != null && selection.isNotEmpty() && (!createLocal || name.isNotBlank())))
    val maxHeight = LocalConfiguration.current.screenHeightDp.dp * 0.9f
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.widthIn(max = 600.dp).fillMaxWidth(0.94f).heightIn(max = maxHeight),
            shape = RoundedCornerShape(28.dp), color = Color(0xFF28203D)) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("♫", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.download_audio), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.download_subtitle), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
                }
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(url, onUrlChange, enabled = !download.running, singleLine = true,
                        label = { Text(stringResource(R.string.link_label)) }, shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth())
                    if (loading) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text(stringResource(R.string.playlist_loading), style = MaterialTheme.typography.bodySmall)
                    }
                    if (failed) {
                        Text(stringResource(R.string.playlist_load_error), color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = { retry++ }) { Text(stringResource(R.string.retry)) }
                    }
                    preview?.let { playlist ->
                        Column(Modifier.fillMaxWidth().background(Color.White.copy(alpha = .05f), RoundedCornerShape(16.dp)).padding(14.dp)) {
                            Text(playlist.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            if (playlist.author.isNotBlank()) Text(playlist.author, style = MaterialTheme.typography.bodySmall)
                            Text(stringResource(R.string.playlist_selection_count, selection.size, playlist.tracks.size),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                        Row(Modifier.fillMaxWidth().toggleable(createLocal, enabled = !download.running, role = Role.Checkbox,
                            onValueChange = { createLocal = it }), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(createLocal, onCheckedChange = null, enabled = !download.running)
                            Text(stringResource(R.string.create_local_playlist), style = MaterialTheme.typography.bodyMedium)
                        }
                        if (createLocal) OutlinedTextField(name, { name = it }, enabled = !download.running,
                            label = { Text(stringResource(R.string.playlist_name)) }, supportingText = { Text(stringResource(R.string.original_or_custom_name)) },
                            singleLine = true, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth())
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            TextButton(enabled = !download.running, onClick = { selected = ArrayList(playlist.tracks.filter { it.available }.map { it.id }) }) {
                                Text(stringResource(R.string.select_all))
                            }
                            TextButton(enabled = !download.running, onClick = { selected = arrayListOf() }) { Text(stringResource(R.string.select_none)) }
                        }
                        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 230.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(playlist.tracks, key = { it.id }) { track ->
                                Row(Modifier.fillMaxWidth().background(Color.White.copy(alpha = .04f), RoundedCornerShape(12.dp))
                                    .toggleable(track.id in selected, enabled = track.available && !download.running, role = Role.Checkbox,
                                        onValueChange = { check -> selected = ArrayList(if (check) selected + track.id else selected - track.id) })
                                    .padding(vertical = 6.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(track.id in selected, onCheckedChange = null, enabled = track.available && !download.running)
                                    Column(Modifier.weight(1f)) {
                                        Text(track.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                                        Text(if (!track.available) stringResource(R.string.unavailable_song) else
                                            listOfNotNull(track.artist.takeIf { it.isNotBlank() }, track.duration?.let { "%d:%02d".format(it / 60, it % 60) }).joinToString(" · "),
                                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                    if (download.running) LinearProgressIndicator(progress = { download.progress / 100f }, modifier = Modifier.fillMaxWidth())
                    if (download.message.isNotBlank()) Text(download.message, style = MaterialTheme.typography.bodySmall)
                    if (download.queued > 0) Text(stringResource(R.string.queued_links, download.queued), style = MaterialTheme.typography.bodySmall)
                    if (download.details.isNotBlank()) SelectionContainer {
                        Text(download.details, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
                Spacer(Modifier.height(12.dp))
                if (download.running) OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.cancel_download))
                } else Button(enabled = ready, onClick = {
                    onDownload(if (playlistLink == null) listOf(url.trim()) else selection.map { it.url },
                        if (playlistLink != null && createLocal) name.trim() else null)
                }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                    Text(if (playlistLink != null) stringResource(R.string.download_selected, selection.size) else stringResource(R.string.download_mp3))
                }
            }
        }
    }
}
