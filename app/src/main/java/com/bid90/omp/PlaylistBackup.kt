package com.bid90.omp

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.bid90.omp.playback.LocalMusic
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors

/** A user-selected shared folder survives uninstall; preferences are only a working cache. */
internal object PlaylistBackup {
    private const val FILE_NAME = "OMPlayer-playlists.json"
    private val executor = Executors.newSingleThreadExecutor()
    @Volatile var lastError: String? = null
        private set

    fun configured(context: Context): Boolean = context.getSharedPreferences("playlist_backup", Context.MODE_PRIVATE)
        .contains("tree")

    fun schedule(context: Context) {
        if (!configured(context)) return
        executor.execute {
            try { export(context); lastError = null }
            catch (exception: Exception) {
                lastError = exception.message
                android.util.Log.w("PlaylistBackup", "Could not export playlists", exception)
            }
        }
    }

    private fun audioFiles(context: Context): Map<String, String> {
        val files = mutableMapOf<String, String>()
        context.contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DISPLAY_NAME),
            "${MediaStore.Audio.Media.RELATIVE_PATH} = ?", arrayOf(LocalMusic.FOLDER_PATH), null)?.use { cursor ->
            while (cursor.moveToNext()) files[cursor.getString(0)] = cursor.getString(1)
        }
        return files
    }

    private fun document(context: Context, tree: Uri, create: Boolean): Uri? {
        val root = DocumentsContract.getTreeDocumentId(tree)
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, root)
        context.contentResolver.query(children,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) if (cursor.getString(1) == FILE_NAME)
                return DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(0))
        }
        return if (create) DocumentsContract.createDocument(context.contentResolver,
            DocumentsContract.buildDocumentUriUsingTree(tree, root), "application/json", FILE_NAME) else null
    }

    @Synchronized
    private fun export(context: Context) {
        val tree = context.getSharedPreferences("playlist_backup", Context.MODE_PRIVATE).getString("tree", null) ?: return
        val names = audioFiles(context)
        val lists = JSONArray()
        PlaylistStore(context).load().forEach { playlist ->
            val entries = JSONArray()
            playlist.trackIds.forEach { id -> entries.put(JSONObject().put("id", id).put("file", names[id] ?: playlist.trackFiles[id] ?: "")) }
            lists.put(JSONObject().put("id", playlist.id).put("name", playlist.name).put("tracks", entries))
        }
        val data = JSONObject().put("version", 1).put("playlists", lists).toString(2)
        val file = document(context, Uri.parse(tree), true) ?: error("Cannot create playlist backup")
        context.contentResolver.openOutputStream(file, "wt")?.bufferedWriter()?.use { it.write(data) }
            ?: error("Cannot write playlist backup")
    }

    /** Called on IO after Android has granted persistent access to the selected tree. */
    @Synchronized
    fun connect(context: Context, tree: Uri): Int {
        val file = document(context, tree, false)
        var imported = 0
        if (file != null) {
            val text = context.contentResolver.openInputStream(file)?.bufferedReader()?.use { it.readText() }
                ?: error("Cannot read playlist backup")
            val root = JSONObject(text)
            check(root.optInt("version") == 1) { "Unsupported playlist backup version" }
            val lists = root.getJSONArray("playlists")
            val files = audioFiles(context)

            val store = PlaylistStore(context)
            for (i in 0 until lists.length()) {
                val list = lists.getJSONObject(i)
                val entries = list.getJSONArray("tracks")
                val refs = (0 until entries.length()).map { index ->
                    val entry = entries.getJSONObject(index)
                    PlaylistFileRef(entry.optString("id"), entry.optString("file"))
                }
                val (ids, names) = restorePlaylistRefs(refs, files)
                store.upsert(MusicPlaylist(list.getString("id"), list.getString("name"), ids, names))
                imported++
            }
        }
        context.getSharedPreferences("playlist_backup", Context.MODE_PRIVATE).edit().putString("tree", tree.toString()).commit()
        export(context)
        lastError = null
        return imported
    }
}
