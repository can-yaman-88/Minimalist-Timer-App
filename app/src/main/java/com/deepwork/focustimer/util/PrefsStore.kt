package com.deepwork.focustimer.util

import android.content.Context

/**
 * Remembers the last-used timer setup so the setup screen reopens with the
 * user's previous focus/break/rounds/mode instead of the hardcoded defaults.
 */
class PrefsStore(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences("focus_prefs", Context.MODE_PRIVATE)

    var lastMode: String?
        get() = prefs.getString(KEY_MODE, null)
        set(v) = prefs.edit().putString(KEY_MODE, v).apply()

    var lastFocusMinutes: Int
        get() = prefs.getInt(KEY_FOCUS, -1)
        set(v) = prefs.edit().putInt(KEY_FOCUS, v).apply()

    var lastBreakMinutes: Int
        get() = prefs.getInt(KEY_BREAK, -1)
        set(v) = prefs.edit().putInt(KEY_BREAK, v).apply()

    var lastRounds: Int
        get() = prefs.getInt(KEY_ROUNDS, -1)
        set(v) = prefs.edit().putInt(KEY_ROUNDS, v).apply()

    private companion object {
        const val KEY_MODE = "last_mode"
        const val KEY_FOCUS = "last_focus_minutes"
        const val KEY_BREAK = "last_break_minutes"
        const val KEY_ROUNDS = "last_rounds"
    }
}
