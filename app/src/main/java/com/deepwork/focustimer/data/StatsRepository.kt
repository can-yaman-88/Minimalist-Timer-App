package com.deepwork.focustimer.data

import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.ZoneId

/**
 * Single owner of all persistence. ViewModels and the timer service talk to
 * this and never to the DAO directly (Clean Architecture data boundary).
 */
class StatsRepository(private val dao: FocusSessionDao) {

    fun observeDailyStats(): Flow<List<DailyStat>> = dao.observeDailyStats()

    suspend fun getAllSessions(): List<FocusSession> = dao.getAll()

    /**
     * Persist a finished/partial interval. Returns the generated row id.
     * `durationMillis` is whatever actually elapsed — the caller computes it.
     */
    suspend fun recordSession(
        type: SessionType,
        durationMillis: Long,
        completed: Boolean,
        startedAtEpochMillis: Long,
    ): Long {
        val day = LocalDate.now(ZoneId.systemDefault()).toEpochDay()
        return dao.insert(
            FocusSession(
                type = type,
                durationMillis = durationMillis,
                completed = completed,
                dateEpochDay = day,
                startedAtEpochMillis = startedAtEpochMillis,
            )
        )
    }
}
