package com.deepwork.focustimer.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ImportBatchDao {

    @Insert
    suspend fun insert(batch: ImportBatch): Long

    @Query("SELECT * FROM import_batches ORDER BY importedAtEpochMillis DESC")
    fun observeAll(): Flow<List<ImportBatch>>

    @Query("DELETE FROM import_batches WHERE id = :id")
    suspend fun deleteById(id: Long)
}
