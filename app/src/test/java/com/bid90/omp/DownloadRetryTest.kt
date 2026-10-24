package com.bid90.omp

import com.bid90.omp.download.downloadWithEngineRetry
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CancellationException

class DownloadRetryTest {
    @Test fun successfulDownloadDoesNotUpdateEngine() {
        var updates = 0
        assertEquals("audio", downloadWithEngineRetry({ false }, { updates++ }) { "audio" })
        assertEquals(0, updates)
    }

    @Test fun failedDownloadUpdatesThenRetriesOnce() {
        val order = mutableListOf<String>()
        var attempts = 0
        val result = downloadWithEngineRetry({ false }, { order += "update" }) {
            order += "download"
            if (++attempts == 1) error("outdated engine")
            "audio"
        }
        assertEquals("audio", result)
        assertEquals(listOf("download", "update", "download"), order)
    }

    @Test fun secondFailureDoesNotCauseMoreUpdatesOrRetries() {
        var attempts = 0
        var updates = 0
        val failure = runCatching {
            downloadWithEngineRetry({ false }, { updates++ }) { attempts++; error("failed") }
        }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
        assertEquals(2, attempts)
        assertEquals(1, updates)
    }

    @Test fun cancellationDoesNotUpdateOrRetry() {
        var updates = 0
        var attempts = 0
        val failure = runCatching {
            downloadWithEngineRetry({ false }, { updates++ }) { attempts++; throw CancellationException() }
        }.exceptionOrNull()
        assertTrue(failure is CancellationException)
        assertEquals(1, attempts)
        assertEquals(0, updates)
    }

    @Test fun cancellationDuringUpdatePreventsRetry() {
        var cancelled = false
        var attempts = 0
        val failure = runCatching {
            downloadWithEngineRetry({ cancelled }, { cancelled = true }) { attempts++; error("failed") }
        }.exceptionOrNull()
        assertTrue(failure is CancellationException)
        assertEquals(1, attempts)
    }
}
