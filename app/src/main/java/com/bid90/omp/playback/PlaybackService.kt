package com.bid90.omp.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.bid90.omp.MainActivity
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.ListeningExecutorService
import com.google.common.util.concurrent.MoreExecutors
import java.util.concurrent.Executors

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlaybackService : MediaLibraryService() {
    private lateinit var session: MediaLibrarySession
    private lateinit var library: LocalMusic
    private val executor: ListeningExecutorService = MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor())

    override fun onCreate() {
        super.onCreate()
        library = LocalMusic(this)
        val player = ExoPlayer.Builder(this).build().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            setHandleAudioBecomingNoisy(true)
            setWakeMode(C.WAKE_MODE_LOCAL)
        }
        session = MediaLibrarySession.Builder(this, player, Callback())
            .setSessionActivity(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = session

    override fun onDestroy() {
        session.player.release()
        session.release()
        executor.shutdownNow()
        super.onDestroy()
    }

    private inner class Callback : MediaLibrarySession.Callback {
        override fun onGetLibraryRoot(session: MediaLibrarySession, browser: MediaSession.ControllerInfo,
            params: LibraryParams?): ListenableFuture<LibraryResult<MediaItem>> =
            Futures.immediateFuture(LibraryResult.ofItem(library.root(), params))

        override fun onGetChildren(session: MediaLibrarySession, browser: MediaSession.ControllerInfo,
            parentId: String, page: Int, pageSize: Int, params: LibraryParams?): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = executor.submit<LibraryResult<ImmutableList<MediaItem>>> {
            if (parentId != LocalMusic.ROOT || page < 0 || pageSize < 1) {
                LibraryResult.ofError(androidx.media3.session.SessionError.ERROR_BAD_VALUE)
            } else {
                val tracks = library.tracks()
                val start = (page.toLong() * pageSize).coerceAtMost(tracks.size.toLong()).toInt()
                val end = (start.toLong() + pageSize).coerceAtMost(tracks.size.toLong()).toInt()
                LibraryResult.ofItemList(tracks.subList(start, end), params)
            }
        }

        override fun onGetItem(session: MediaLibrarySession, browser: MediaSession.ControllerInfo,
            mediaId: String): ListenableFuture<LibraryResult<MediaItem>> = executor.submit<LibraryResult<MediaItem>> {
            val item = if (mediaId == LocalMusic.ROOT) library.root() else library.tracks().find { it.mediaId == mediaId }
            if (item == null) LibraryResult.ofError(androidx.media3.session.SessionError.ERROR_BAD_VALUE)
            else LibraryResult.ofItem(item, null)
        }

        override fun onAddMediaItems(mediaSession: MediaSession, controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>): ListenableFuture<List<MediaItem>> = executor.submit<List<MediaItem>> {
            val byId = library.tracks().associateBy { it.mediaId }
            mediaItems.mapNotNull { request ->
                val query = request.requestMetadata.searchQuery
                if (query != null) byId.values.firstOrNull { item ->
                    query.isBlank() || item.mediaMetadata.title.toString().contains(query, ignoreCase = true) ||
                        item.mediaMetadata.artist.toString().contains(query, ignoreCase = true)
                } else byId[request.mediaId]
            }
        }
    }
}


