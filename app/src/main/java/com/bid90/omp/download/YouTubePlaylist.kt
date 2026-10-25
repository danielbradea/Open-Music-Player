package com.bid90.omp.download

import android.content.Context
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.coroutines.resumeWithException

internal data class OnlineTrack(val id: String, val title: String, val artist: String,
                                val url: String, val duration: Long?, val available: Boolean)
internal data class OnlinePlaylist(val title: String, val author: String, val tracks: List<OnlineTrack>)

internal object YouTubePlaylist {
    private val executor = Executors.newCachedThreadPool()
    fun playlistUrl(url: String): String? = runCatching {
        val uri = URI(url.trim())
        if (uri.scheme?.lowercase() !in setOf("https", "http")) return null
        val host = uri.host.orEmpty().lowercase()
        if (host != "youtu.be" && host != "youtube.com" && !host.endsWith(".youtube.com")) return null
        val id = uri.rawQuery.orEmpty().split('&').firstOrNull { it.substringBefore('=') == "list" }
            ?.substringAfter('=', "")?.let { URLDecoder.decode(it, "UTF-8") }
            ?.takeIf { it.matches(Regex("[A-Za-z0-9_-]+")) } ?: return null
        "https://www.youtube.com/playlist?list=$id"
    }.getOrNull()

    suspend fun inspect(context: Context, url: String): OnlinePlaylist = suspendCancellableCoroutine { continuation ->
        val process = "playlist-preview-${UUID.randomUUID()}"
        continuation.invokeOnCancellation { executor.execute { runCatching { YoutubeDL.destroyProcessById(process) } } }
        executor.execute {
            try {
                YoutubeDL.init(context.applicationContext)
                if (!continuation.isActive) return@execute
                val request = YoutubeDLRequest(playlistUrl(url) ?: error("Not a YouTube playlist")).apply {
                    addOption("--flat-playlist")
                    addOption("--dump-single-json")
                    addOption("--yes-playlist")
                    addOption("--skip-download")
                    addOption("--ignore-errors")
                    addOption("--socket-timeout", "20")
                    addOption("--retries", "1")
                }
                val json = JSONObject(YoutubeDL.execute(request, process).out)
                val entries = json.optJSONArray("entries")
                val tracks = (0 until (entries?.length() ?: 0)).mapNotNull { index ->
                    val entry = entries?.optJSONObject(index) ?: return@mapNotNull null
                    val id = entry.optString("id").takeIf { it.matches(Regex("[A-Za-z0-9_-]+")) }
                        ?: return@mapNotNull null
                    val title = entry.optString("title").takeUnless { it.isBlank() || it == "null" } ?: id
                    val availability = entry.optString("availability")
                    OnlineTrack(id, title, entry.optString("uploader").takeUnless { it == "null" }.orEmpty(),
                        "https://www.youtube.com/watch?v=$id",
                        if (entry.isNull("duration")) null else entry.optDouble("duration").toLong().takeIf { it > 0 },
                        availability !in setOf("private", "premium_only", "subscriber_only", "needs_auth") &&
                            title !in setOf("[Private video]", "[Deleted video]"))
                }.distinctBy { it.id }
                check(tracks.isNotEmpty()) { "Empty or inaccessible playlist" }
                val playlist = OnlinePlaylist(json.optString("title").takeUnless { it.isBlank() || it == "null" } ?: "YouTube playlist",
                    json.optString("uploader").takeUnless { it == "null" }.orEmpty(), tracks)
                if (continuation.isActive) continuation.resumeWith(Result.success(playlist))
            } catch (exception: Exception) {
                if (continuation.isActive) continuation.resumeWithException(exception)
            }
        }
    }
}
