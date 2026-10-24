package com.bid90.omp

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class MusicPlaylist(val id: String, val name: String, val trackIds: List<String>)

internal class PlaylistStore(context: Context) {
    private val preferences = context.getSharedPreferences("playlists", Context.MODE_PRIVATE)
    fun load(): List<MusicPlaylist> = runCatching {
        val array = JSONArray(preferences.getString("lists", "[]"))
        List(array.length()) { index ->
            val entry = array.getJSONObject(index)
            val ids = entry.getJSONArray("tracks")
            MusicPlaylist(entry.getString("id"), entry.getString("name"),
                List(ids.length()) { ids.getString(it) })
        }
    }.getOrDefault(emptyList())

    fun save(playlists: List<MusicPlaylist>) {
        val array = JSONArray()
        playlists.forEach { playlist ->
            array.put(JSONObject().put("id", playlist.id).put("name", playlist.name)
                .put("tracks", JSONArray(playlist.trackIds)))
        }
        preferences.edit().putString("lists", array.toString()).apply()
    }
}

@Composable
internal fun PlaylistControls(playlists: List<MusicPlaylist>, selectedId: String?, tracks: List<MediaItem>,
                              onSelect: (String?) -> Unit, onSave: (MusicPlaylist) -> Unit,
                              onDelete: (String) -> Unit, createRequested: Boolean, onCreateHandled: () -> Unit) {
    val resources = androidx.compose.ui.platform.LocalResources.current
    var editing by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var selectedTracks by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    var search by rememberSaveable { mutableStateOf("") }
    var deleteId by rememberSaveable { mutableStateOf<String?>(null) }
    val deleteTarget = playlists.firstOrNull { it.id == deleteId }
    fun openEditor(playlist: MusicPlaylist?) {
        editingId = playlist?.id
        name = playlist?.name ?: ""
        selectedTracks = ArrayList(playlist?.trackIds ?: emptyList())
        search = ""
        editing = true
    }
    LaunchedEffect(createRequested) {
        if (createRequested) {
            openEditor(null)
            onCreateHandled()
        }
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        item { FilterChip(selected = selectedId == null, onClick = { onSelect(null) }, label = { Text(resources.getString(R.string.all_tracks)) }) }
        items(playlists, key = { it.id }) { playlist ->
            var menuOpen by remember { mutableStateOf(false) }
            val active = selectedId == playlist.id
            Box {
                Surface(shape = RoundedCornerShape(8.dp),
                    color = if (active) MaterialTheme.colorScheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent,
                    border = androidx.compose.foundation.BorderStroke(1.dp,
                        if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                    modifier = Modifier.padding(vertical = 8.dp).semantics { selected = active }
                        .combinedClickable(onClick = { onSelect(playlist.id) },
                            onLongClickLabel = resources.getString(R.string.playlist_options),
                            onLongClick = { menuOpen = true })) {
                    Text(playlist.name, style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text(resources.getString(R.string.edit_playlist)) },
                        onClick = { menuOpen = false; openEditor(playlist) })
                    DropdownMenuItem(text = { Text(resources.getString(R.string.delete_playlist)) },
                        onClick = { menuOpen = false; deleteId = playlist.id })
                }
            }
        }
    }
    if (editing) {
        val duplicate = playlists.any { it.id != editingId && it.name.equals(name.trim(), ignoreCase = true) }
        AlertDialog(onDismissRequest = { editing = false }, title = { Text(if (editingId == null) resources.getString(R.string.new_playlist) else resources.getString(R.string.edit_playlist)) },
            text = {
                Column {
                    OutlinedTextField(name, { name = it }, label = { Text(resources.getString(R.string.playlist_name)) }, singleLine = true,
                        isError = duplicate, supportingText = { if (duplicate) Text(resources.getString(R.string.duplicate_playlist)) })
                    Text(resources.getString(R.string.selected_song_count, selectedTracks.size), modifier = Modifier.padding(vertical = 8.dp))
                    OutlinedTextField(search, { search = it }, label = { Text(resources.getString(R.string.search_songs)) }, singleLine = true)
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 280.dp)) {
                        val filtered = tracks.filter { search.isBlank() || it.mediaMetadata.title.toString().contains(search, true) || it.mediaMetadata.artist.toString().contains(search, true) }
                        if (tracks.isEmpty()) item { Text(resources.getString(R.string.add_library_first)) }
                        else if (filtered.isEmpty()) item { Text(resources.getString(R.string.no_songs_found)) }
                        items(filtered, key = { it.mediaId }) { track ->
                            val checked = track.mediaId in selectedTracks
                            Row(Modifier.fillMaxWidth().toggleable(checked, role = Role.Checkbox, onValueChange = { check ->
                                selectedTracks = ArrayList(if (check) selectedTracks + track.mediaId else selectedTracks - track.mediaId)
                            }).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked, onCheckedChange = null)
                                Column(Modifier.weight(1f)) {
                                    Text(track.mediaMetadata.title.toString(), maxLines = 2)
                                    Text(track.mediaMetadata.artist.toString(), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }, confirmButton = {
                TextButton(enabled = name.isNotBlank() && !duplicate, onClick = {
                    val id = editingId ?: UUID.randomUUID().toString()
                    onSave(MusicPlaylist(id, name.trim(), selectedTracks.toList()))
                    onSelect(id)
                    editing = false
                }) { Text(resources.getString(R.string.save)) }
            }, dismissButton = { TextButton(onClick = { editing = false }) { Text(resources.getString(R.string.cancel)) } })
    }
    if (deleteTarget != null) AlertDialog(onDismissRequest = { deleteId = null },
        title = { Text(resources.getString(R.string.delete_playlist_question, deleteTarget.name)) },
        text = { Text(resources.getString(R.string.keep_songs)) },
        confirmButton = { TextButton(onClick = {
            onDelete(deleteTarget.id)
            if (selectedId == deleteTarget.id) onSelect(null)
            deleteId = null
        }) { Text(resources.getString(R.string.delete)) } },
        dismissButton = { TextButton(onClick = { deleteId = null }) { Text(resources.getString(R.string.cancel)) } })
}