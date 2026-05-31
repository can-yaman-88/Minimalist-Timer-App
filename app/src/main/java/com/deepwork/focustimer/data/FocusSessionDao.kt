package com.deepwork.focustimer.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FocusSessionDao {

    @Insert
    suspend fun insert(session: FocusSession): Long

    @Query("SELECT * FROM focus_sessions ORDER BY startedAtEpochMillis DESC")
    suspend fun getAll(): List<FocusSession>

    @Query(
        """
        SELECT
            dateEpochDay AS dateEpochDay,
            SUM(CASE WHEN type = 'FOCUS' THEN durationMillis ELSE 0 END) AS totalFocusMillis,
            SUM(CASE WHEN type = 'FOCUS' THEN 1 ELSE 0 END) AS focusSessionCount,
            SUM(CASE WHEN type = 'BREAK' THEN durationMillis ELSE 0 END) AS totalBreakMillis
        FROM focus_sessions
        GROUP BY dateEpochDay
        ORDER BY dateEpochDay DESC
        """
    )
    fun observeDailyStats(): Flow<List<DailyStat>>
}
