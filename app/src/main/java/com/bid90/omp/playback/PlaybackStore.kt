package com.bid90.omp.playback

import android.content.Context
import androidx.media3.common.Player
import org.json.JSONArray

internal class PlaybackStore(context: Context) {
    private val preferences = context.getSharedPreferences("playback_state", Context.MODE_PRIVATE)
    fun load(): PlaybackSnapshot = runCatching {
        val ids = JSONArray(preferences.getString("queue", "[]"))
        PlaybackSnapshot(List(ids.length()) { ids.getString(it) }, preferences.getString("current", null),
            preferences.getLong("position", 0), preferences.getBoolean("shuffle", false), preferences.getInt("repeat", 0))
    }.getOrDefault(PlaybackSnapshot())

    fun save(player: Player) {
        val queue = List(player.mediaItemCount) { player.getMediaItemAt(it).mediaId }
        preferences.edit().putString("queue", JSONArray(queue).toString())
            .putString("current", player.currentMediaItem?.mediaId)
            .putLong("position", player.currentPosition.coerceAtLeast(0))
            .putBoolean("shuffle", player.shuffleModeEnabled).putInt("repeat", player.repeatMode).apply()
    }
}
