package com.bid90.omp.playback

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import java.io.File
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

class LocalMusic(private val context: Context) {
    companion object {
        const val ROOT = "local_music"
        const val FOLDER_NAME = "Music/OpenMusic"
        const val FOLDER_PATH = "$FOLDER_NAME/"
        fun ensureFolder(): File = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "OpenMusic"
        ).also { check(it.isDirectory || it.mkdirs()) { "Nu pot crea folderul $FOLDER_NAME." } }
        val permission: String get() = if (Build.VERSION.SDK_INT >= 33)
            Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    }

    fun deleteTrack(mediaId: String) {
        val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, mediaId.toLong())
        context.contentResolver.query(uri, arrayOf(MediaStore.Audio.Media.RELATIVE_PATH), null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return // Already removed outside the app.
            check(cursor.getString(0) == FOLDER_PATH) { "Poți șterge doar melodii din folderul OpenMusic." }
        } ?: error("Nu pot verifica fișierul selectat.")
        check(context.contentResolver.delete(uri, null, null) > 0) { "Fișierul nu a putut fi șters." }
    }

    fun tracks(): List<MediaItem> {
        if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) return emptyList()
        val result = mutableListOf<MediaItem>()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        try {
            context.contentResolver.query(collection, arrayOf(
                MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM
            ), "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.RELATIVE_PATH} = ?",
                arrayOf(FOLDER_PATH),
                "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC")?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    result += MediaItem.Builder().setMediaId(id.toString())
                        .setUri(ContentUris.withAppendedId(collection, id))
                        .setMediaMetadata(MediaMetadata.Builder()
                            .setTitle(cursor.getString(1) ?: "Fără titlu")
                            .setArtist(cursor.getString(2)?.takeUnless { it == "<unknown>" } ?: "Artist necunoscut")
                            .setAlbumTitle(cursor.getString(3))
                            .setIsBrowsable(false).setIsPlayable(true)
                            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC).build()).build()
                }
            }
        } catch (_: SecurityException) { return emptyList() }
        return result
    }

    fun root(): MediaItem = MediaItem.Builder().setMediaId(ROOT)
        .setMediaMetadata(MediaMetadata.Builder().setTitle("OpenMusic")
            .setIsBrowsable(true).setIsPlayable(false)
            .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED).build()).build()
}
