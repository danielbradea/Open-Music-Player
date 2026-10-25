package com.bid90.omp.playback

internal data class PlaybackSnapshot(
    val queueIds: List<String> = emptyList(),
    val currentId: String? = null,
    val positionMs: Long = 0L,
    val shuffle: Boolean = false,
    val repeat: Int = 0
) {
    fun resolve(availableIds: Set<String>): PlaybackSnapshot {
        val queue = queueIds.filter { it in availableIds }.distinct()
        val current = currentId?.takeIf { it in queue } ?: queue.firstOrNull()
        return copy(queueIds = queue, currentId = current,
            positionMs = if (current != null && current == currentId) positionMs.coerceAtLeast(0) else 0,
            repeat = repeat.takeIf { it in 0..2 } ?: 0)
    }
}
