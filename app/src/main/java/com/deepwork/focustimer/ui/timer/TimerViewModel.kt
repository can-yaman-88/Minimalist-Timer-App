package com.deepwork.focustimer.ui.timer

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import com.deepwork.focustimer.timer.TimerConfig
import com.deepwork.focustimer.timer.TimerMode
import com.deepwork.focustimer.timer.TimerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Editable, UI-only setup form state (before a plan starts). */
data class SetupState(
    val mode: TimerMode = TimerMode.TIMER,
    val focusMinutes: Int = 50,
    val breakMinutes: Int = 10,
    val rounds: Int = 4,
)

/**
 * Mediator between the UI and the timer engine. Holds transient UI state that
 * must survive rotation (the setup form + the immersive toggle), exposes the
 * authoritative engine state for observation, and forwards user intents to the
 * service. Contains no timekeeping logic itself.
 */
class TimerViewModel(app: Application) : AndroidViewModel(app) {

    /** Authoritative engine state, observed by the UI through the ViewModel. */
    val timerState: StateFlow<com.deepwork.focustimer.timer.TimerUiState> = TimerService.state

    private val _setup = MutableStateFlow(SetupState())
    val setup: StateFlow<SetupState> = _setup.asStateFlow()

    private val _immersive = MutableStateFlow(false)
    val immersive: StateFlow<Boolean> = _immersive.asStateFlow()

    // ---- setup form mutations ----
    fun setMode(mode: TimerMode) { _setup.value = _setup.value.copy(mode = mode) }
    fun setFocusMinutes(v: Int) { _setup.value = _setup.value.copy(focusMinutes = v.coerceIn(1, 240)) }
    fun setBreakMinutes(v: Int) { _setup.value = _setup.value.copy(breakMinutes = v.coerceIn(1, 120)) }
    fun setRounds(v: Int) { _setup.value = _setup.value.copy(rounds = v.coerceIn(1, 12)) }

    fun toggleImmersive() { _immersive.value = !_immersive.value }
    fun setImmersive(value: Boolean) { _immersive.value = value }

    // ---- engine commands (forwarded as service intents) ----
    fun start() {
        val s = _setup.value
        // Only the initial start promotes the service to the foreground.
        send(TimerService.ACTION_START, foreground = true) {
            putExtra(TimerService.EXTRA_MODE, s.mode.name)
            putExtra(TimerService.EXTRA_FOCUS, s.focusMinutes * 60_000L)
            putExtra(TimerService.EXTRA_BREAK, s.breakMinutes * 60_000L)
            putExtra(TimerService.EXTRA_ROUNDS, s.rounds)
        }
    }

    fun pause() = send(TimerService.ACTION_PAUSE)
    fun resume() = send(TimerService.ACTION_RESUME)
    fun skipToBreak() = send(TimerService.ACTION_SKIP)
    fun finish() = send(TimerService.ACTION_FINISH)
    fun stop() {
        setImmersive(false)
        send(TimerService.ACTION_STOP)
    }

    private inline fun send(action: String, foreground: Boolean = false, block: Intent.() -> Unit = {}) {
        val ctx = getApplication<Application>()
        val intent = Intent(ctx, TimerService::class.java).apply {
            this.action = action
            block()
        }
        // Subsequent commands are sent while the UI is visible, so a plain
        // startService is allowed and does not re-arm the startForeground deadline.
        if (foreground) ContextCompat.startForegroundService(ctx, intent)
        else ctx.startService(intent)
    }
}
