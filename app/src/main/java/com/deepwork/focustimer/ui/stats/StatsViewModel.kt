package com.deepwork.focustimer.ui.stats

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.deepwork.focustimer.FocusTimerApp
import com.deepwork.focustimer.data.DailyStat
import com.deepwork.focustimer.util.StatsCalculator
import com.deepwork.focustimer.util.StatsSummary
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

class StatsViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = FocusTimerApp.from(app).repository

    val dailyStats: StateFlow<List<DailyStat>> =
        repository.observeDailyStats()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Today / week / month totals plus the weekly & monthly daily averages. */
    val summary: StateFlow<StatsSummary> =
        repository.observeDailyStats()
            .map { StatsCalculator.summarize(it, LocalDate.now()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsSummary())
}
