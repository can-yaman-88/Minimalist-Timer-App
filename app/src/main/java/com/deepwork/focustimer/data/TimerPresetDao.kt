package com.deepwork.focustimer.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TimerPresetDao {

    @Insert
    suspend fun insert(preset: TimerPreset): Long

    @Query("SELECT * FROM timer_presets ORDER BY id ASC")
    fun observeAll(): Flow<List<TimerPreset>>

    @Query("DELETE FROM timer_presets WHERE id = :id")
    suspend fun deleteById(id: Long)
}
