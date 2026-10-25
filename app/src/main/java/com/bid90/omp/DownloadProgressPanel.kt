package com.bid90.omp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bid90.omp.download.DownloadItem
import com.bid90.omp.download.DownloadStatus
import com.bid90.omp.download.counts

@Composable
internal fun DownloadProgressPanel(downloads: List<DownloadItem>) {
    val counts = downloads.counts()
    val current = downloads.firstOrNull { it.status == DownloadStatus.DOWNLOADING }
    val finished = counts.done + counts.failed + counts.cancelled
    val progress = ((finished + (current?.progress ?: 0) / 100f) / counts.total.coerceAtLeast(1)).coerceIn(0f, 1f)
    Column(Modifier.fillMaxWidth().background(Color.White.copy(alpha = .05f), RoundedCornerShape(18.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.batch_download_title, counts.total), style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold)
        Text(stringResource(R.string.batch_download_summary, counts.done, counts.remaining),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        if (counts.failed > 0 || counts.cancelled > 0) Text(
            stringResource(R.string.batch_download_failures, counts.failed, counts.cancelled),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        current?.let {
            Text(stringResource(R.string.batch_current, it.title, it.progress), style = MaterialTheme.typography.bodySmall,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 260.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(downloads, key = { it.id }) { item ->
                val color = when (item.status) {
                    DownloadStatus.DONE -> Color(0xFFA3E4B0)
                    DownloadStatus.FAILED -> MaterialTheme.colorScheme.error
                    DownloadStatus.DOWNLOADING -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(when (item.status) {
                        DownloadStatus.DONE -> "✓"
                        DownloadStatus.FAILED -> "!"
                        DownloadStatus.CANCELLED -> "×"
                        DownloadStatus.DOWNLOADING -> "↓"
                        DownloadStatus.WAITING -> "·"
                    }, modifier = Modifier.width(24.dp), color = color, style = MaterialTheme.typography.titleMedium)
                    Column(Modifier.weight(1f)) {
                        Text(item.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2,
                            overflow = TextOverflow.Ellipsis)
                        Text(when (item.status) {
                            DownloadStatus.DONE -> stringResource(R.string.song_downloaded)
                            DownloadStatus.FAILED -> stringResource(R.string.song_download_failed)
                            DownloadStatus.CANCELLED -> stringResource(R.string.song_download_cancelled)
                            DownloadStatus.DOWNLOADING -> stringResource(R.string.song_downloading, item.progress)
                            DownloadStatus.WAITING -> stringResource(R.string.song_download_waiting)
                        }, style = MaterialTheme.typography.labelSmall, color = color)
                        if (item.error.isNotBlank()) Text(item.error, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
