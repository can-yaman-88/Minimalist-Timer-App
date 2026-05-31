package com.deepwork.focustimer.ui.stats

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.deepwork.focustimer.FocusTimerApp
import com.deepwork.focustimer.data.DailyStat
import com.deepwork.focustimer.util.ExportFormat
import com.deepwork.focustimer.util.StatsExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class StatsViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = FocusTimerApp.from(app).repository

    val dailyStats: StateFlow<List<DailyStat>> =
        repository.observeDailyStats()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Build the export payload and stream it into the SAF-provided Uri.
     * The Composable only supplies the destination Uri (chosen by the system
     * file picker) — all data assembly and I/O happens here, off the main thread.
     */
    fun export(uri: Uri, format: ExportFormat, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val sessions = repository.getAllSessions()
                    val content = when (format) {
                        ExportFormat.CSV -> StatsExporter.toCsv(sessions)
                        ExportFormat.JSON -> StatsExporter.toJson(sessions)
                    }
                    getApplication<Application>().contentResolver
                        .openOutputStream(uri)?.use { out ->
                            out.write(content.toByteArray(Charsets.UTF_8))
                        } ?: error("Could not open output stream")
                }.isSuccess
            }
            onDone(ok)
        }
    }
}
