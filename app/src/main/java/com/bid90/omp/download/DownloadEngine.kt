package com.bid90.omp.download

import kotlinx.coroutines.launch
import android.content.Context
import android.net.Uri
import com.yausername.youtubedl_android.YoutubeDL

/** Keep extractors current independently of the native runtime packaged in the APK. */
internal object DownloadEngine {
    enum class UpdateState { IDLE, RUNNING, SUCCESS, FAILED }
    private val mutableUpdateState = kotlinx.coroutines.flow.MutableStateFlow(UpdateState.IDLE)
    val updateState: kotlinx.coroutines.flow.StateFlow<UpdateState> = mutableUpdateState
    private val updateScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)

    @Synchronized
    fun requestUpdate(context: Context) {
        if (mutableUpdateState.value == UpdateState.RUNNING) return
        mutableUpdateState.value = UpdateState.RUNNING
        updateScope.launch {
            try {
                YoutubeDL.init(context.applicationContext)
                updateIfNeeded(context.applicationContext, force = true)
                mutableUpdateState.value = UpdateState.SUCCESS
            } catch (exception: Exception) {
                android.util.Log.w("DownloadEngine", "Manual update failed", exception)
                mutableUpdateState.value = UpdateState.FAILED
            }
        }
    }
    fun isYouTube(url: String): Boolean {
        val host = Uri.parse(url).host.orEmpty().lowercase()
        return host == "youtu.be" || host == "youtube.com" || host.endsWith(".youtube.com")
    }

    @Synchronized
    fun updateIfNeeded(context: Context, force: Boolean = false) {
        val preferences = context.getSharedPreferences("download_engine", Context.MODE_PRIVATE)
        val lastUpdate = preferences.getLong("last_update", 0)
        if (!force && System.currentTimeMillis() - lastUpdate in 0 until 24 * 60 * 60 * 1000L) return
        YoutubeDL.updateYoutubeDL(context, YoutubeDL.UpdateChannel.STABLE)
        preferences.edit().putLong("last_update", System.currentTimeMillis()).apply()
    }

    fun errorDetails(error: Exception): String = error.message.orEmpty().lineSequence()
        .map { it.trim() }.filter { it.isNotBlank() }
        .filter { it.startsWith("ERROR:") || it.startsWith("WARNING:") }
        .joinToString("\n").ifBlank { error.message ?: error.javaClass.simpleName }.takeLast(1800)

    fun errorMessage(details: String): String = when {
        "403" in details -> "YouTube a refuzat descărcarea (403). Încearcă din nou; dacă persistă, sursa poate restricționa accesul."
        "Sign in" in details || "sign in" in details || "login" in details ->
            "Acest link solicită autentificare. Alege o piesă disponibilă fără cont."
        "unavailable" in details || "Private video" in details -> "Videoclipul nu este disponibil pentru descărcare."
        "timed out" in details || "Unable to download" in details -> "Nu pot accesa sursa. Verifică internetul și încearcă din nou."
        else -> "Descărcarea a eșuat. Vezi detaliile erorii de mai jos."
    }
}
