package com.bid90.omp.download

import java.util.concurrent.CancellationException

/** Retry the extraction once after refreshing the engine; never retry cancellation or file publishing. */
internal fun <T> downloadWithEngineRetry(
    isCancelled: () -> Boolean,
    update: () -> Unit,
    download: () -> T
): T {
    if (isCancelled()) throw CancellationException()
    return try {
        download()
    } catch (first: Exception) {
        if (first is CancellationException || isCancelled()) throw CancellationException().apply { initCause(first) }
        update()
        if (isCancelled()) throw CancellationException()
        download()
    }
}
