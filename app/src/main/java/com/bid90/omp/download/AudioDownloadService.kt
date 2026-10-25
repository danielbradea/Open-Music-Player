package com.bid90.omp.download

import android.app.*
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import android.provider.MediaStore
import com.bid90.omp.MainActivity
import com.bid90.omp.playback.LocalMusic
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.ffmpeg.FFmpeg
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue

internal data class AudioDownloadState(
    val running: Boolean = false,
    val progress: Int = 0,
    val message: String = "",
    val details: String = "",
    val queued: Int = 0,
    val completed: Long = 0,
    val completedUrl: String = "",
    val finishedSource: String = "",
    val items: List<DownloadItem> = emptyList()
)

class AudioDownloadService : Service() {
    companion object {
        private const val CHANNEL = "audio_downloads"
        private const val NOTIFICATION = 42
        private const val CANCEL = "cancel"
        private val mutableState = MutableStateFlow(AudioDownloadState())
        internal val state = mutableState.asStateFlow()
        fun validUrl(value: String): Boolean = runCatching {
            val uri = Uri.parse(value.trim())
            uri.scheme in listOf("http", "https") && !uri.host.isNullOrBlank() && uri.userInfo == null
        }.getOrDefault(false)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var cancelled = false
    private var task: Job? = null
    private var processId = ""
    private data class DownloadJob(val url: String, val playlistId: String? = null, val sourceUrl: String = url,
                                   val title: String = url, val id: String = UUID.randomUUID().toString())
    private val pending = ConcurrentLinkedQueue<DownloadJob>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Descărcări audio", NotificationManager.IMPORTANCE_LOW))
    }

    private fun notification(message: String, progress: Int): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val cancel = PendingIntent.getService(this, 1,
            Intent(this, AudioDownloadService::class.java).setAction(CANCEL), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("OpenMusic · Descărcare audio")
            .setSubText(mutableState.value.items.counts().let { "${it.done}/${it.total}" })
            .setStyle(Notification.BigTextStyle().bigText(
                listOfNotNull(mutableState.value.items.firstOrNull { it.status == DownloadStatus.DOWNLOADING }?.title, message).joinToString("\n")))
            .setContentText(message).setContentIntent(open).setOnlyAlertOnce(true).setOngoing(true)
            .setProgress(100, progress, progress == 0)
            .addAction(Notification.Action.Builder(null, "Anulează", cancel).build()).build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == CANCEL) {
            cancelled = true
            pending.clear()
            mutableState.update { it.copy(queued = 0, items = it.items.cancelOutstanding()) }
            scope.launch { YoutubeDL.destroyProcessById(processId) }
            if (task == null) stopSelf()
            return START_NOT_STICKY
        }
        val urls = intent?.getStringArrayListExtra("urls")?.filter(::validUrl)
            ?: listOfNotNull(intent?.getStringExtra("url")?.trim()?.takeIf(::validUrl))
        if (urls.isEmpty()) { if (task == null) stopSelf(); return START_NOT_STICKY }
        val playlistName = intent?.getStringExtra("playlist_name")?.trim()?.takeIf { it.isNotBlank() }
        val playlistId = playlistName?.let { com.bid90.omp.PlaylistStore(this).create(it) }
        val source = intent?.getStringExtra("source_url") ?: urls.first()
        val titles = intent?.getStringArrayListExtra("titles")
        val jobs = urls.mapIndexed { index, url -> DownloadJob(url, playlistId, source, titles?.getOrNull(index)?.takeIf { it.isNotBlank() } ?: url) }
        mutableState.update { state ->
            val previous = if (task == null) emptyList() else state.items
            state.copy(items = previous + jobs.map { DownloadItem(it.id, it.url, it.title, it.sourceUrl) })
        }
        if (task != null) {
            pending.addAll(jobs)
            mutableState.update { state -> state.copy(queued = pending.size) }
        } else {
            pending.addAll(jobs.drop(1))
            startDownload(jobs.first())
        }
        return START_NOT_STICKY
    }
    private fun startDownload(job: DownloadJob) {
        val url = job.url
        cancelled = false
        mutableState.update { it.copy(items = it.items.mark(job.id, DownloadStatus.DOWNLOADING)) }
        processId = UUID.randomUUID().toString()
        mutableState.update { state -> state.copy(running = true, progress = 0, queued = pending.size,
            details = "", finishedSource = "", message = "Pregătesc descărcarea…") }
        startForeground(NOTIFICATION, notification("Pregătesc descărcarea…", 0))
        task = scope.launch {
            val directory = File(cacheDir, "audio-$processId")
            try {
                LocalMusic.ensureFolder()
                check(directory.mkdirs()) { "Nu pot crea directorul temporar." }
                YoutubeDL.init(applicationContext)
                FFmpeg.init(applicationContext)
                if (cancelled) throw CancellationException()
                mutableState.update { state -> state.copy(message = "Citesc linkul și caut sursa audio…") }
                val request = YoutubeDLRequest(url).apply {
                    addOption("--remote-components", "ejs:github")
                    addOption("--no-playlist")
                    addOption("--no-mtime")
                    addOption("--socket-timeout", "30")
                    addOption("--retries", "3")
                    addOption("-f", "bestaudio/best")
                    addOption("--extract-audio")
                    addOption("--audio-format", "mp3")
                    addOption("--audio-quality", "0")
                    addOption("--embed-metadata")
                    addOption("--embed-thumbnail")
                    addOption("--convert-thumbnails", "jpg")
                    addOption("-o", "${directory.absolutePath}/%(title).120B [%(id)s].%(ext)s")
                }
                val file = downloadWithEngineRetry(isCancelled = { cancelled }, update = {
                    val updating = if (com.bid90.omp.AppLanguage.selected(this@AudioDownloadService) == "en")
                        "Download failed. Updating engine before one retry…"
                    else "Descărcarea a eșuat. Actualizez motorul și reîncerc o dată…"
                    mutableState.update { state -> state.copy(progress = 0, message = updating) }
                    getSystemService(NotificationManager::class.java).notify(NOTIFICATION, notification(updating, 0))
                    try {
                        DownloadEngine.updateIfNeeded(applicationContext, force = true)
                    } catch (exception: CancellationException) {
                        throw exception
                    } catch (exception: Exception) {
                        android.util.Log.w("AudioDownload", "Engine update failed; retrying installed engine", exception)
                    }
                    if (cancelled) throw CancellationException()
                    // Discard partial extraction output before the single fresh attempt.
                    directory.listFiles()?.forEach { it.deleteRecursively() }
                    mutableState.update { state -> state.copy(progress = 0, details = "",
                        message = if (com.bid90.omp.AppLanguage.selected(this@AudioDownloadService) == "en")
                            "Retrying download…" else "Reîncerc descărcarea…") }
                }) {
                var lastProgress = -1
                YoutubeDL.execute(request, processId) { percent, _, _ ->
                    if (cancelled) YoutubeDL.destroyProcessById(processId)
                    val progress = percent.toInt().coerceIn(0, 100)
                    if (!cancelled && progress != lastProgress) {
                        lastProgress = progress
                        val message = if (progress >= 100) "Procesez fișierul audio…" else "Descarc audio: $progress%"
                        mutableState.update { state -> state.copy(progress = progress, message = message,
                            items = state.items.mark(job.id, DownloadStatus.DOWNLOADING, progress)) }
                        getSystemService(NotificationManager::class.java).notify(NOTIFICATION, notification(message, progress))
                    }
                }
                if (cancelled) throw CancellationException()
                directory.listFiles()?.singleOrNull { it.extension == "mp3" && it.length() > 0 }
                    ?: error("Nu s-a obținut un fișier audio.")
                }
                val mediaId = publishAudio(file)
                job.playlistId?.let { com.bid90.omp.PlaylistStore(this@AudioDownloadService).addTrack(it, mediaId) }
                mutableState.update { state -> state.copy(items = state.items.mark(job.id, DownloadStatus.DONE, 100),
                    running = pending.isNotEmpty(), message = "Salvat în ${LocalMusic.FOLDER_NAME}. Piesa este în bibliotecă.",
                    queued = pending.size,
                    completed = state.completed + 1, completedUrl = job.sourceUrl,
                    finishedSource = if (pending.isEmpty()) job.sourceUrl else "") }
            } catch (e: Exception) {
                android.util.Log.e("AudioDownload", "Download failed", e)
                val details = DownloadEngine.errorDetails(e)
                mutableState.update { state -> state.copy(running = pending.isNotEmpty() && !cancelled, progress = 0,
                    message = if (cancelled || e is CancellationException) "Descărcare anulată."
                    else DownloadEngine.errorMessage(details),
                    details = if (cancelled || e is CancellationException) "" else details,
                    items = state.items.mark(job.id,
                        if (cancelled || e is CancellationException) DownloadStatus.CANCELLED else DownloadStatus.FAILED,
                        error = if (cancelled || e is CancellationException) "" else DownloadEngine.errorMessage(details))) }
            } finally {
                directory.deleteRecursively()
                withContext(NonCancellable + Dispatchers.Main) {
                    task = null
                    val next = pending.poll()
                    if (next != null) startDownload(next)
                    else {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
            }
        }
    }

    private fun publishAudio(file: File): String {
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
            put(MediaStore.Audio.Media.RELATIVE_PATH, LocalMusic.FOLDER_PATH)
            put(MediaStore.Audio.Media.IS_MUSIC, 1)
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val uri = contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("Nu pot salva fișierul audio.")
        try {
            contentResolver.openOutputStream(uri)?.use { output -> file.inputStream().use { it.copyTo(output) } }
                ?: error("Nu pot scrie fișierul audio.")
            if (cancelled) throw CancellationException()
            contentResolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
            return android.content.ContentUris.parseId(uri).toString()
        } catch (e: Exception) {
            contentResolver.delete(uri, null, null)
            throw e
        }
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        cancelled = true
        scope.launch { YoutubeDL.destroyProcessById(processId) }
        stopSelf()
    }

    override fun onDestroy() {
        cancelled = true
        pending.clear()
        // Blocking native work must be terminated explicitly as coroutine cancellation alone cannot stop it.
        Thread { YoutubeDL.destroyProcessById(processId) }.start()
        scope.cancel()
        if (mutableState.value.running) mutableState.update { state -> state.copy(
            running = false, items = state.items.cancelOutstanding(),
            message = "Descărcarea a fost întreruptă. Poți încerca din nou.") }
        super.onDestroy()
    }
}
