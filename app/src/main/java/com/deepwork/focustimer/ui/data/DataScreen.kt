package com.deepwork.focustimer.ui.data

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.deepwork.focustimer.data.FocusSession
import com.deepwork.focustimer.data.ImportBatch
import com.deepwork.focustimer.ui.theme.Sepia
import com.deepwork.focustimer.ui.theme.SepiaDim
import com.deepwork.focustimer.ui.theme.SepiaFaint
import com.deepwork.focustimer.util.ExportFormat
import com.deepwork.focustimer.util.formatDuration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun DataScreen(vm: DataViewModel = viewModel()) {
    val context = LocalContext.current
    val batches by vm.importBatches.collectAsStateWithLifecycle()
    val manual by vm.manualSessions.collectAsStateWithLifecycle()

    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ExportFormat.CSV.mime)
    ) { uri ->
        uri?.let {
            vm.export(it, ExportFormat.CSV) { ok ->
                toast(context, if (ok) "Exported" else "Export failed")
            }
        }
    }
    val jsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ExportFormat.JSON.mime)
    ) { uri ->
        uri?.let {
            vm.export(it, ExportFormat.JSON) { ok ->
                toast(context, if (ok) "Exported" else "Export failed")
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { vm.import(it) { _, msg -> toast(context, msg) } }
    }

    LazyColumn(Modifier.fillMaxSize().padding(20.dp)) {
        item {
            Text("DATA", color = Sepia, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(20.dp))
        }

        // ---- Export ----
        item {
            SectionTitle("EXPORT")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                ActionButton("EXPORT CSV", Modifier.weight(1f)) {
                    csvLauncher.launch("focus_stats.${ExportFormat.CSV.extension}")
                }
                ActionButton("EXPORT JSON", Modifier.weight(1f)) {
                    jsonLauncher.launch("focus_stats.${ExportFormat.JSON.extension}")
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        // ---- Import ----
        item {
            SectionTitle("IMPORT")
            ActionButton("IMPORT CSV / JSON FILE", Modifier.fillMaxWidth()) {
                importLauncher.launch(arrayOf("*/*"))
            }
            Spacer(Modifier.height(24.dp))
        }

        // ---- Imported files ----
        item { SectionTitle("IMPORTED FILES") }
        if (batches.isEmpty()) {
            item { EmptyHint("No imported files.") }
        } else {
            items(batches, key = { "batch-${it.id}" }) { batch ->
                ImportBatchRow(batch) { vm.deleteImportBatch(batch.id) }
                HorizontalDivider(color = SepiaFaint)
            }
        }
        item { Spacer(Modifier.height(24.dp)) }

        // ---- Manual entries ----
        item { SectionTitle("MANUAL ENTRIES") }
        if (manual.isEmpty()) {
            item { EmptyHint("No manual sessions added.") }
        } else {
            items(manual, key = { "manual-${it.id}" }) { session ->
                ManualRow(session) { vm.deleteManualSession(session.id) }
                HorizontalDivider(color = SepiaFaint)
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, color = SepiaDim, fontFamily = FontFamily.Monospace, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun EmptyHint(text: String) {
    Text(text, color = SepiaDim, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
}

@Composable
private fun ActionButton(label: String, modifier: Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        border = BorderStroke(1.dp, SepiaDim),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Sepia),
        modifier = modifier.height(48.dp),
    ) { Text(label, fontWeight = FontWeight.Bold) }
}

private val dateFmt = DateTimeFormatter.ofPattern("MMM d yyyy")
private val dateTimeFmt = DateTimeFormatter.ofPattern("MMM d yyyy, HH:mm")

@Composable
private fun ImportBatchRow(batch: ImportBatch, onDelete: () -> Unit) {
    val imported = Instant.ofEpochMilli(batch.importedAtEpochMillis)
        .atZone(ZoneId.systemDefault()).toLocalDateTime().format(dateTimeFmt)
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text(batch.fileName, color = Sepia, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
            Text(
                "${batch.format} · ${batch.sessionCount} session(s) · $imported",
                color = SepiaDim, fontFamily = FontFamily.Monospace, fontSize = 11.sp,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "Delete imported file", tint = SepiaDim)
        }
    }
}

@Composable
private fun ManualRow(session: FocusSession, onDelete: () -> Unit) {
    val date = LocalDate.ofEpochDay(session.dateEpochDay).format(dateFmt)
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "$date · ${session.type.name}",
                color = Sepia, fontFamily = FontFamily.Monospace, fontSize = 14.sp,
            )
            Text(
                formatDuration(session.durationMillis),
                color = SepiaDim, fontFamily = FontFamily.Monospace, fontSize = 11.sp,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "Delete manual session", tint = SepiaDim)
        }
    }
}

private fun toast(context: android.content.Context, msg: String) =
    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
