package com.bid90.omp.download

internal enum class DownloadStatus { WAITING, DOWNLOADING, DONE, FAILED, CANCELLED }
internal data class DownloadItem(
    val id: String, val url: String, val title: String, val sourceUrl: String,
    val status: DownloadStatus = DownloadStatus.WAITING, val progress: Int = 0, val error: String = ""
)
internal data class DownloadCounts(val total: Int, val done: Int, val failed: Int, val cancelled: Int, val remaining: Int)
internal fun List<DownloadItem>.counts(): DownloadCounts = DownloadCounts(size,
    count { it.status == DownloadStatus.DONE }, count { it.status == DownloadStatus.FAILED },
    count { it.status == DownloadStatus.CANCELLED },
    count { it.status == DownloadStatus.WAITING || it.status == DownloadStatus.DOWNLOADING })
internal fun List<DownloadItem>.mark(id: String, status: DownloadStatus, progress: Int = 0, error: String = ""): List<DownloadItem> =
    map { if (it.id == id) it.copy(status = status, progress = progress.coerceIn(0, 100), error = error) else it }
internal fun List<DownloadItem>.cancelOutstanding(): List<DownloadItem> = map {
    if (it.status == DownloadStatus.WAITING || it.status == DownloadStatus.DOWNLOADING) it.copy(status = DownloadStatus.CANCELLED) else it
}
