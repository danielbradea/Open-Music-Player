package com.bid90.omp.download

import android.content.Intent

object SharedAudioLink {
    fun extract(intent: Intent): String? {
        if (intent.action != Intent.ACTION_SEND || intent.type != "text/plain") return null
        val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty()
        val links = Regex("https?://[^\\s<>\\\"]+", RegexOption.IGNORE_CASE).findAll(text)
            .map { it.value.trimEnd('.', ',', ';', ')', ']', '}') }
            .filter(AudioDownloadService::validUrl).toList()
        return links.firstOrNull(DownloadEngine::isYouTube) ?: links.firstOrNull()
    }
}
