package com.bid90.omp

internal data class PlaylistFileRef(val oldId: String, val fileName: String)

/** File names are stable across reinstalls, while MediaStore IDs can change. */
internal fun restorePlaylistRefs(entries: List<PlaylistFileRef>, audioFiles: Map<String, String>): Pair<List<String>, Map<String, String>> {
    val byName = audioFiles.entries.associate { it.value to it.key }
    val files = linkedMapOf<String, String>()
    val ids = entries.mapNotNull { entry ->
        val id = if (entry.fileName.isNotBlank()) byName[entry.fileName] ?: "missing:${entry.fileName}"
            else entry.oldId.takeIf { it in audioFiles }
        if (id != null && entry.fileName.isNotBlank()) files[id] = entry.fileName
        id
    }.distinct()
    return ids to files
}
