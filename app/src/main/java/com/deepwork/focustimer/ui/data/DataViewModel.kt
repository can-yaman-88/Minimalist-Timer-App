package com.deepwork.focustimer.ui.data

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.deepwork.focustimer.FocusTimerApp
import com.deepwork.focustimer.data.FocusSession
import com.deepwork.focustimer.data.ImportBatch
import com.deepwork.focustimer.util.ExportFormat
import com.deepwork.focustimer.util.ImportException
import com.deepwork.focustimer.util.StatsExporter
import com.deepwork.focustimer.util.StatsImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DataViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = FocusTimerApp.from(app).repository

    val importBatches: StateFlow<List<ImportBatch>> =
        repository.observeImportBatches()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val manualSessions: StateFlow<List<FocusSession>> =
        repository.observeManualSessions()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Assemble the export payload and stream it into the SAF-provided Uri. */
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
                        .openOutputStream(uri)?.use { it.write(content.toByteArray(Charsets.UTF_8)) }
                        ?: error("Could not open output stream")
                }.isSuccess
            }
            onDone(ok)
        }
    }

    /**
     * Read the chosen file, auto-detect CSV vs JSON, validate it through
     * [StatsImporter], and persist the rows as an import batch. The result
     * message is surfaced as a toast so format mismatches are explained.
     */
    fun import(uri: Uri, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val resolver = getApplication<Application>().contentResolver
                    val name = displayName(uri)
                    val text = resolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: throw ImportException("Could not read the file.")

                    val isJson = name.endsWith(".json", true) || text.trimStart().startsWith("[")
                    val format = if (isJson) ExportFormat.JSON else ExportFormat.CSV
                    val parsed = if (isJson) StatsImporter.parseJson(text) else StatsImporter.parseCsv(text)

                    val count = repository.importSessions(name, format.name, parsed)
                    count
                }
            }
            result.fold(
                onSuccess = { onResult(true, "Imported $it session(s).") },
                onFailure = { onResult(false, it.message ?: "Import failed.") },
            )
        }
    }

    fun deleteImportBatch(id: Long) {
        viewModelScope.launch { repository.deleteImportBatch(id) }
    }

    fun deleteManualSession(id: Long) {
        viewModelScope.launch { repository.deleteSession(id) }
    }

    private fun displayName(uri: Uri): String {
        val resolver = getApplication<Application>().contentResolver
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) c.getString(idx)?.let { return it }
            }
        }
        return uri.lastPathSegment ?: "imported"
    }
}
