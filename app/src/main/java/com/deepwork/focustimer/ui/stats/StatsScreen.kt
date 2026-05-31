package com.deepwork.focustimer.ui.stats

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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
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
import com.deepwork.focustimer.data.DailyStat
import com.deepwork.focustimer.ui.theme.Sepia
import com.deepwork.focustimer.ui.theme.SepiaDim
import com.deepwork.focustimer.ui.theme.SepiaFaint
import com.deepwork.focustimer.util.ExportFormat
import com.deepwork.focustimer.util.formatDuration
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun StatsScreen(vm: StatsViewModel = viewModel()) {
    val stats by vm.dailyStats.collectAsStateWithLifecycle()
    val context = LocalContext.current

    fun toast(ok: Boolean) = Toast.makeText(
        context, if (ok) "Exported" else "Export failed", Toast.LENGTH_SHORT
    ).show()

    // One SAF "create document" launcher per format. The picker returns a Uri
    // the user chose; the ViewModel does all assembly + writing off-thread.
    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ExportFormat.CSV.mime)
    ) { uri -> uri?.let { vm.export(it, ExportFormat.CSV, ::toast) } }

    val jsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ExportFormat.JSON.mime)
    ) { uri -> uri?.let { vm.export(it, ExportFormat.JSON, ::toast) } }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("DAILY STATS", color = Sepia, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            ExportButton("EXPORT CSV", Modifier.weight(1f)) {
                csvLauncher.launch("focus_stats.${ExportFormat.CSV.extension}")
            }
            ExportButton("EXPORT JSON", Modifier.weight(1f)) {
                jsonLauncher.launch("focus_stats.${ExportFormat.JSON.extension}")
            }
        }
        Spacer(Modifier.height(20.dp))

        if (stats.isEmpty()) {
            Text(
                "No sessions recorded yet.",
                color = SepiaDim, fontFamily = FontFamily.Monospace, fontSize = 14.sp,
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                items(stats, key = { it.dateEpochDay }) { stat ->
                    DailyRow(stat)
                    HorizontalDivider(color = SepiaFaint)
                }
            }
        }
    }
}

@Composable
private fun ExportButton(label: String, modifier: Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        border = BorderStroke(1.dp, SepiaDim),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Sepia),
        modifier = modifier.height(48.dp),
    ) { Text(label, fontWeight = FontWeight.Bold) }
}

private val dateFmt = DateTimeFormatter.ofPattern("EEE, MMM d yyyy")

@Composable
private fun DailyRow(stat: DailyStat) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(
                LocalDate.ofEpochDay(stat.dateEpochDay).format(dateFmt),
                color = Sepia, fontFamily = FontFamily.Monospace, fontSize = 15.sp,
            )
            Text(
                "${stat.focusSessionCount} session(s) · break ${formatDuration(stat.totalBreakMillis)}",
                color = SepiaDim, fontFamily = FontFamily.Monospace, fontSize = 12.sp,
            )
        }
        Text(
            formatDuration(stat.totalFocusMillis),
            color = Sepia, fontFamily = FontFamily.Monospace,
            fontSize = 20.sp, fontWeight = FontWeight.Bold,
        )
    }
}
