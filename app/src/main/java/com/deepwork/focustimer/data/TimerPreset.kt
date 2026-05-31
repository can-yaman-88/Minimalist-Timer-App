package com.deepwork.focustimer.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A saved, named timer configuration the user can re-apply on the setup screen
 * (e.g. "Pomodoro 25/5"). `mode` stores [com.deepwork.focustimer.timer.TimerMode].name.
 */
@Entity(tableName = "timer_presets")
data class TimerPreset(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val mode: String,
    val focusMinutes: Int,
    val breakMinutes: Int,
    val rounds: Int,
)
