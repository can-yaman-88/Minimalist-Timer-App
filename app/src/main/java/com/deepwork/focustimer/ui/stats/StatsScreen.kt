package com.deepwork.focustimer.ui.stats

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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.deepwork.focustimer.util.StatsSummary
import com.deepwork.focustimer.util.formatDuration
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun StatsScreen(vm: StatsViewModel = viewModel()) {
    val stats by vm.dailyStats.collectAsStateWithLifecycle()
    val summary by vm.summary.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("DAILY STATS", color = Sepia, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        SummaryCard(summary)
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
private fun SummaryCard(summary: StatsSummary) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SummaryTile("TODAY", summary.todayFocusMillis, Modifier.weight(1f))
            SummaryTile("THIS WEEK", summary.weekFocusMillis, Modifier.weight(1f))
            SummaryTile("THIS MONTH", summary.monthFocusMillis, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SummaryTile("WEEKLY AVG / DAY", summary.weeklyDailyAverageMillis, Modifier.weight(1f))
            SummaryTile("MONTHLY AVG / DAY", summary.monthlyDailyAverageMillis, Modifier.weight(1f))
        }
    }
}

@Composable
private fun SummaryTile(label: String, millis: Long, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, color = SepiaDim, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            formatDuration(millis),
            color = Sepia, fontFamily = FontFamily.Monospace,
            fontSize = 18.sp, fontWeight = FontWeight.Bold,
        )
    }
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
