package com.deepwork.focustimer

import android.app.Application
import com.deepwork.focustimer.data.AppDatabase
import com.deepwork.focustimer.data.StatsRepository
import com.deepwork.focustimer.util.PrefsStore

/**
 * Tiny manual DI container. Keeps the sample free of Hilt/kapt while still
 * giving the ViewModels and the service a single shared repository instance.
 * (Swap this for Hilt in a larger codebase — the boundaries already fit.)
 */
class FocusTimerApp : Application() {

    val repository: StatsRepository by lazy {
        StatsRepository(AppDatabase.get(this))
    }

    /** Lightweight key-value store for the last-used timer configuration. */
    val prefs: PrefsStore by lazy { PrefsStore(this) }

    companion object {
        fun from(app: Application): FocusTimerApp = app as FocusTimerApp
    }
}
