package com.deepwork.focustimer.util

import com.deepwork.focustimer.data.DailyStat
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class StatsCalculatorTest {

    // Wednesday 2024-01-17. Week starts Mon 2024-01-15 (3 days elapsed incl. today);
    // month starts 2024-01-01 (17 days elapsed incl. today).
    private val today = LocalDate.of(2024, 1, 17)

    private fun day(d: LocalDate, focusMillis: Long) =
        DailyStat(d.toEpochDay(), focusMillis, focusSessionCount = 1, totalBreakMillis = 0)

    @Test fun computesTotalsAndAverages() {
        val daily = listOf(
            day(today, 3_600_000),                           // today, this week
            day(LocalDate.of(2024, 1, 16), 1_800_000),       // this week
            day(LocalDate.of(2024, 1, 10), 7_200_000),       // this month, not this week
            day(LocalDate.of(2023, 12, 31), 9_999_999),      // previous month, excluded
        )

        val s = StatsCalculator.summarize(daily, today)

        assertEquals(3_600_000, s.todayFocusMillis)
        assertEquals(5_400_000, s.weekFocusMillis)            // 3.6M + 1.8M
        assertEquals(12_600_000, s.monthFocusMillis)          // 3.6M + 1.8M + 7.2M
        assertEquals(5_400_000 / 3, s.weeklyDailyAverageMillis)
        assertEquals(12_600_000 / 17, s.monthlyDailyAverageMillis)
    }

    @Test fun emptyDataYieldsZeros() {
        val s = StatsCalculator.summarize(emptyList(), today)
        assertEquals(0, s.todayFocusMillis)
        assertEquals(0, s.weekFocusMillis)
        assertEquals(0, s.monthFocusMillis)
        assertEquals(0, s.weeklyDailyAverageMillis)
        assertEquals(0, s.monthlyDailyAverageMillis)
    }

    @Test fun mondayWeekHasSingleDivisor() {
        val monday = LocalDate.of(2024, 1, 15)
        val daily = listOf(day(monday, 4_000_000))
        val s = StatsCalculator.summarize(daily, monday)
        assertEquals(4_000_000, s.weekFocusMillis)
        assertEquals(4_000_000, s.weeklyDailyAverageMillis) // divided by 1
    }
}
