package com.bid90.omp

import com.bid90.omp.download.YouTubePlaylist
import org.junit.Assert.*
import org.junit.Test

class PlaylistImportTest {
    @Test fun detectsPlaylistWithoutDownloadingTheFirstVideo() {
        assertEquals("https://www.youtube.com/playlist?list=PL_test-123",
            YouTubePlaylist.playlistUrl("https://music.youtube.com/watch?v=video123&list=PL_test-123&index=4"))
    }
    @Test fun individualVideoIsNotTreatedAsAPlaylist() {
        assertNull(YouTubePlaylist.playlistUrl("https://youtu.be/video123"))
        assertNull(YouTubePlaylist.playlistUrl("https://www.youtube.com/watch?v=video123"))
    }
    @Test fun rejectsUntrustedHostsEmptyAndInvalidPlaylistIds() {
        assertNull(YouTubePlaylist.playlistUrl("https://youtube.com.evil.test/playlist?list=PL123"))
        assertNull(YouTubePlaylist.playlistUrl("file://youtube.com/playlist?list=PL123"))
        assertNull(YouTubePlaylist.playlistUrl("https://www.youtube.com/playlist?list="))
        assertNull(YouTubePlaylist.playlistUrl("https://www.youtube.com/playlist?list=PL%26bad"))
    }
    @Test fun restoresFilesAfterMediaStoreIdsHaveChanged() {
        val (ids, files) = restorePlaylistRefs(listOf(PlaylistFileRef("10", "song.mp3")), mapOf("77" to "song.mp3"))
        assertEquals(listOf("77"), ids)
        assertEquals(mapOf("77" to "song.mp3"), files)
    }
    @Test fun missingFilesKeepReferencesForLaterRestoration() {
        val (ids, files) = restorePlaylistRefs(listOf(PlaylistFileRef("10", "song.mp3")), emptyMap())
        assertEquals(listOf("missing:song.mp3"), ids)
        assertEquals("song.mp3", files[ids.first()])
        val restored = restorePlaylistRefs(listOf(PlaylistFileRef(ids.first(), files.getValue(ids.first()))), mapOf("99" to "song.mp3"))
        assertEquals(listOf("99"), restored.first)
    }
    @Test fun doesNotAttachAnUnrelatedSongWithTheOldMediaId() {
        val (ids, _) = restorePlaylistRefs(listOf(PlaylistFileRef("10", "original.mp3")), mapOf("10" to "different.mp3"))
        assertEquals(listOf("missing:original.mp3"), ids)
    }
}
