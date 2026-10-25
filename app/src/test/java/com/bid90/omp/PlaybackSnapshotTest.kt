package com.bid90.omp

import com.bid90.omp.playback.PlaybackSnapshot
import org.junit.Assert.*
import org.junit.Test

class PlaybackSnapshotTest {
    @Test fun restoresQueuePositionAndModes() {
        val saved = PlaybackSnapshot(listOf("a", "b", "c"), "b", 45000, true, 2)
        assertEquals(saved, saved.resolve(setOf("a", "b", "c")))
    }

    @Test fun missingFilesAreRemovedWithoutLosingCurrentPosition() {
        val restored = PlaybackSnapshot(listOf("missing", "b", "c"), "b", 45000, true, 1).resolve(setOf("b", "c"))
        assertEquals(listOf("b", "c"), restored.queueIds)
        assertEquals("b", restored.currentId)
        assertEquals(45000L, restored.positionMs)
        assertTrue(restored.shuffle)
        assertEquals(1, restored.repeat)
    }

    @Test fun deletedCurrentSongFallsBackToFirstAvailableSongAtStart() {
        val restored = PlaybackSnapshot(listOf("a", "b"), "a", 45000).resolve(setOf("b"))
        assertEquals("b", restored.currentId)
        assertEquals(0L, restored.positionMs)
    }

    @Test fun emptyLibraryKeepsPlaybackModesWithoutInvalidSong() {
        val restored = PlaybackSnapshot(listOf("a"), "a", 45000, true, 2).resolve(emptySet())
        assertTrue(restored.queueIds.isEmpty())
        assertNull(restored.currentId)
        assertEquals(0L, restored.positionMs)
        assertTrue(restored.shuffle)
        assertEquals(2, restored.repeat)
    }

    @Test fun invalidStoredValuesAreNormalized() {
        val restored = PlaybackSnapshot(listOf("a", "a"), "a", -5, false, 99).resolve(setOf("a"))
        assertEquals(listOf("a"), restored.queueIds)
        assertEquals(0L, restored.positionMs)
        assertEquals(0, restored.repeat)
    }
}
