package com.deepwork.focustimer.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.ZoneId

/**
 * Single owner of all persistence. ViewModels and the timer service talk to
 * this and never to the DAOs directly (Clean Architecture data boundary).
 */
class StatsRepository(
    private val db: AppDatabase,
) {
    private val dao: FocusSessionDao = db.focusSessionDao()
    private val importDao: ImportBatchDao = db.importBatchDao()
    private val presetDao: TimerPresetDao = db.timerPresetDao()

    // ---- stats ----
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
                origin = SessionOrigin.TIMER,
            )
        )
    }

    // ---- manual entries ----
    suspend fun addManualSession(
        type: SessionType,
        durationMillis: Long,
        dateEpochDay: Long,
        startedAtEpochMillis: Long,
    ): Long = dao.insert(
        FocusSession(
            type = type,
            durationMillis = durationMillis,
            completed = true,
            dateEpochDay = dateEpochDay,
            startedAtEpochMillis = startedAtEpochMillis,
            origin = SessionOrigin.MANUAL,
        )
    )

    fun observeManualSessions(): Flow<List<FocusSession>> =
        dao.observeByOrigin(SessionOrigin.MANUAL)

    suspend fun deleteSession(id: Long) = dao.deleteById(id)

    // ---- import / batches ----
    /**
     * Insert a batch row, then attach the parsed sessions to it as IMPORTED.
     * Ids on the parsed rows are discarded (set to 0) so Room assigns fresh
     * ones and imports never collide with existing primary keys. Returns the
     * number of sessions added.
     */
    suspend fun importSessions(
        fileName: String,
        format: String,
        parsed: List<FocusSession>,
    ): Int = db.withTransaction {
        val batchId = importDao.insert(
            ImportBatch(
                fileName = fileName,
                format = format,
                importedAtEpochMillis = System.currentTimeMillis(),
                sessionCount = parsed.size,
            )
        )
        dao.insertAll(
            parsed.map {
                it.copy(id = 0L, origin = SessionOrigin.IMPORTED, importBatchId = batchId)
            }
        )
        parsed.size
    }

    fun observeImportBatches(): Flow<List<ImportBatch>> = importDao.observeAll()

    suspend fun deleteImportBatch(id: Long) = db.withTransaction {
        dao.deleteByImportBatch(id)
        importDao.deleteById(id)
    }

    // ---- presets ----
    fun observePresets(): Flow<List<TimerPreset>> = presetDao.observeAll()

    suspend fun savePreset(preset: TimerPreset): Long = presetDao.insert(preset)

    suspend fun deletePreset(id: Long) = presetDao.deleteById(id)
}
