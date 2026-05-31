package com.deepwork.focustimer.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Type of a recorded interval. */
enum class SessionType { FOCUS, BREAK }

/**
 * Where a recorded interval came from. Lets the UI manage manually-added and
 * imported rows independently of timer-produced ones.
 */
enum class SessionOrigin { TIMER, MANUAL, IMPORTED }

/**
 * One recorded interval. `durationMillis` is the *actual* elapsed time, so a
 * focus session skipped at 32m15s is stored as 1_935_000, exactly as specced.
 *
 * `dateEpochDay` is LocalDate.toEpochDay() — a stable, timezone-light day key
 * that's trivial to GROUP BY for daily stats.
 *
 * `origin` distinguishes timer/manual/imported rows; `importBatchId` links an
 * imported row back to the [ImportBatch] it arrived with (null otherwise).
 */
@Entity(tableName = "focus_sessions")
data class FocusSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val type: SessionType,
    val durationMillis: Long,
    val completed: Boolean,          // ran to its full configured length?
    val dateEpochDay: Long,
    val startedAtEpochMillis: Long,
    val origin: SessionOrigin = SessionOrigin.TIMER,
    val importBatchId: Long? = null,
)

/** Aggregated per-day row produced directly by the DAO. */
data class DailyStat(
    val dateEpochDay: Long,
    val totalFocusMillis: Long,
    val focusSessionCount: Int,
    val totalBreakMillis: Long,
)
