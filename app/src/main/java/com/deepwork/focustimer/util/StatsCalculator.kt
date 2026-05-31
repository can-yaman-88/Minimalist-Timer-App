package com.deepwork.focustimer.util

import com.deepwork.focustimer.data.DailyStat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** Headline focus totals + the two calendar-based daily averages. */
data class StatsSummary(
    val todayFocusMillis: Long = 0L,
    val weekFocusMillis: Long = 0L,
    val monthFocusMillis: Long = 0L,
    /** This week's focus total ÷ days elapsed this week (Mon→today). */
    val weeklyDailyAverageMillis: Long = 0L,
    /** This month's focus total ÷ days elapsed this month (1st→today). */
    val monthlyDailyAverageMillis: Long = 0L,
)

/**
 * Pure aggregation over the per-day rows the DAO already produces, so it needs
 * no extra query and is trivial to unit-test. Averages use calendar divisors:
 * the number of days from the start of the week/month up to and including
 * `today`, so empty days correctly drag the average down.
 */
object StatsCalculator {

    fun summarize(daily: List<DailyStat>, today: LocalDate): StatsSummary {
        val byDay = daily.associateBy { it.dateEpochDay }
        val todayEpoch = today.toEpochDay()

        val startOfWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val startOfMonth = today.withDayOfMonth(1)

        val weekTotal = sumFocusBetween(byDay, startOfWeek.toEpochDay(), todayEpoch)
        val monthTotal = sumFocusBetween(byDay, startOfMonth.toEpochDay(), todayEpoch)

        // Days elapsed, inclusive of today.
        val weekDays = (todayEpoch - startOfWeek.toEpochDay() + 1).coerceAtLeast(1)
        val monthDays = (todayEpoch - startOfMonth.toEpochDay() + 1).coerceAtLeast(1)

        return StatsSummary(
            todayFocusMillis = byDay[todayEpoch]?.totalFocusMillis ?: 0L,
            weekFocusMillis = weekTotal,
            monthFocusMillis = monthTotal,
            weeklyDailyAverageMillis = weekTotal / weekDays,
            monthlyDailyAverageMillis = monthTotal / monthDays,
        )
    }

    private fun sumFocusBetween(
        byDay: Map<Long, DailyStat>,
        fromEpochDay: Long,
        toEpochDay: Long,
    ): Long {
        var sum = 0L
        var day = fromEpochDay
        while (day <= toEpochDay) {
            sum += byDay[day]?.totalFocusMillis ?: 0L
            day++
        }
        return sum
    }
}
