package com.bid90.omp

import com.bid90.omp.download.*
import org.junit.Assert.*
import org.junit.Test

class DownloadProgressTest {
    private fun items() = listOf(
        DownloadItem("1", "url1", "First", "playlist"),
        DownloadItem("2", "url2", "Second", "playlist"),
        DownloadItem("3", "url3", "Third", "playlist"))

    @Test fun reportsDownloadedAndRemainingIncludingActiveSong() {
        val state = items().mark("1", DownloadStatus.DONE, 100).mark("2", DownloadStatus.DOWNLOADING, 75)
        assertEquals(DownloadCounts(3, 1, 0, 0, 2), state.counts())
        assertEquals("Second", state.first { it.status == DownloadStatus.DOWNLOADING }.title)
    }
    @Test fun failedSongsAreNotReportedAsDownloadedOrRemaining() {
        val state = items().mark("1", DownloadStatus.DONE, 100).mark("2", DownloadStatus.FAILED, error = "Unavailable")
        assertEquals(DownloadCounts(3, 1, 1, 0, 1), state.counts())
        assertEquals("Unavailable", state[1].error)
    }
    @Test fun cancellationPreservesAlreadySavedSongs() {
        val state = items().mark("1", DownloadStatus.DONE, 100).mark("2", DownloadStatus.DOWNLOADING, 50).cancelOutstanding()
        assertEquals(DownloadCounts(3, 1, 0, 2, 0), state.counts())
        assertEquals(DownloadStatus.DONE, state[0].status)
    }
    @Test fun retryDoesNotDuplicateTheSongOrIncreaseTotals() {
        val state = items().mark("1", DownloadStatus.DOWNLOADING, 80).mark("1", DownloadStatus.DOWNLOADING)
            .mark("1", DownloadStatus.DONE, 100)
        assertEquals(DownloadCounts(3, 1, 0, 0, 2), state.counts())
    }
    @Test fun hundredPercentIsNotDoneUntilTheFileIsSaved() {
        val state = items().mark("1", DownloadStatus.DOWNLOADING, 100)
        assertEquals(0, state.counts().done)
        assertEquals(3, state.counts().remaining)
    }
}
