package com.bid90.omp

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.bid90.omp.download.DownloadEngine
import com.bid90.omp.download.AudioDownloadService
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import java.util.Locale

internal object AppLanguage {
    fun selected(context: Context): String = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        .getString("language", "ro") ?: "ro"

    fun wrap(context: Context): Context {
        val config = Configuration(context.resources.configuration)
        config.setLocale(Locale.forLanguageTag(selected(context)))
        return context.createConfigurationContext(config)
    }

    fun save(context: Context, language: String) {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putString("language", language).commit()
    }
}

@Composable
internal fun LanguageSettings(context: Context, folderMessage: String?, folderBusy: Boolean,
                             onChooseFolder: () -> Unit, onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    val update by DownloadEngine.updateState.collectAsState()
    val download by AudioDownloadService.state.collectAsState()
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(context.getString(R.string.settings), color = Color.White) },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                Text(context.getString(R.string.language), color = Color.White, modifier = Modifier.padding(bottom = 12.dp))
                listOf("ro" to "Română", "en" to "English").forEach { (code, label) ->
                    val chosen = AppLanguage.selected(context) == code
                    Row(Modifier.fillMaxWidth().selectable(selected = chosen, role = Role.RadioButton,
                        onClick = { onSelect(code) }).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = chosen, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(label, color = Color.White)
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                Text(context.getString(R.string.playlist_storage_title), color = Color.White)
                Text(context.getString(R.string.playlist_storage_info), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(enabled = !folderBusy, onClick = onChooseFolder) {
                    Text(context.getString(R.string.choose_playlist_folder), color = Color.White)
                }
                if (folderBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
                folderMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Color.White) }
                if (PlaylistBackup.lastError != null) Text(context.getString(R.string.playlist_folder_error), color = MaterialTheme.colorScheme.error)
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                TextButton(onClick = { DownloadEngine.requestUpdate(context.applicationContext) },
                    enabled = update != DownloadEngine.UpdateState.RUNNING && !download.running) {
                    Text(context.getString(if (update == DownloadEngine.UpdateState.RUNNING) R.string.engine_updating else R.string.update_engine),
                        color = if (update == DownloadEngine.UpdateState.RUNNING || download.running) Color.White.copy(alpha = 0.4f) else Color.White)
                }
                if (update == DownloadEngine.UpdateState.RUNNING) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (update == DownloadEngine.UpdateState.SUCCESS) Text(context.getString(R.string.engine_updated), color = Color.White)
                if (update == DownloadEngine.UpdateState.FAILED) Text(context.getString(R.string.engine_update_failed), color = MaterialTheme.colorScheme.error)
                if (download.running) Text(context.getString(R.string.engine_wait_download), color = Color.White)
            }
        }, confirmButton = { TextButton(onClick = onDismiss) { Text(context.getString(R.string.close), color = Color.White) } })
}
