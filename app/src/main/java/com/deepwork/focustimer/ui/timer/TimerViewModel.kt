package com.deepwork.focustimer.ui.timer

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.deepwork.focustimer.FocusTimerApp
import com.deepwork.focustimer.data.SessionType
import com.deepwork.focustimer.data.TimerPreset
import com.deepwork.focustimer.timer.TimerMode
import com.deepwork.focustimer.timer.TimerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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

    private val repository = FocusTimerApp.from(app).repository
    private val prefs = FocusTimerApp.from(app).prefs

    /** Authoritative engine state, observed by the UI through the ViewModel. */
    val timerState: StateFlow<com.deepwork.focustimer.timer.TimerUiState> = TimerService.state

    // Seed the form from the last-used config so reopening the app restores it.
    private val _setup = MutableStateFlow(loadLastSetup())
    val setup: StateFlow<SetupState> = _setup.asStateFlow()

    private val _immersive = MutableStateFlow(false)
    val immersive: StateFlow<Boolean> = _immersive.asStateFlow()

    val presets: StateFlow<List<TimerPreset>> =
        repository.observePresets()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun loadLastSetup(): SetupState {
        val mode = prefs.lastMode?.let { runCatching { TimerMode.valueOf(it) }.getOrNull() }
            ?: TimerMode.TIMER
        val default = SetupState()
        return SetupState(
            mode = mode,
            focusMinutes = prefs.lastFocusMinutes.takeIf { it > 0 } ?: default.focusMinutes,
            breakMinutes = prefs.lastBreakMinutes.takeIf { it > 0 } ?: default.breakMinutes,
            rounds = prefs.lastRounds.takeIf { it > 0 } ?: default.rounds,
        )
    }

    // ---- setup form mutations ----
    fun setMode(mode: TimerMode) { _setup.value = _setup.value.copy(mode = mode) }
    fun setFocusMinutes(v: Int) { _setup.value = _setup.value.copy(focusMinutes = v.coerceIn(1, 600)) }
    fun setBreakMinutes(v: Int) { _setup.value = _setup.value.copy(breakMinutes = v.coerceIn(1, 180)) }
    fun setRounds(v: Int) { _setup.value = _setup.value.copy(rounds = v.coerceIn(1, 20)) }

    fun toggleImmersive() { _immersive.value = !_immersive.value }
    fun setImmersive(value: Boolean) { _immersive.value = value }

    // ---- presets ----
    fun applyPreset(preset: TimerPreset) {
        val mode = runCatching { TimerMode.valueOf(preset.mode) }.getOrNull() ?: TimerMode.TIMER
        _setup.value = SetupState(
            mode = mode,
            focusMinutes = preset.focusMinutes.coerceIn(1, 600),
            breakMinutes = preset.breakMinutes.coerceIn(1, 180),
            rounds = preset.rounds.coerceIn(1, 20),
        )
    }

    fun saveCurrentAsPreset(name: String) {
        val s = _setup.value
        viewModelScope.launch {
            repository.savePreset(
                TimerPreset(
                    name = name.trim().ifBlank { "Preset" },
                    mode = s.mode.name,
                    focusMinutes = s.focusMinutes,
                    breakMinutes = s.breakMinutes,
                    rounds = s.rounds,
                )
            )
        }
    }

    fun deletePreset(id: Long) {
        viewModelScope.launch { repository.deletePreset(id) }
    }

    // ---- manual session entry ----
    fun addManualSession(
        type: SessionType,
        durationMillis: Long,
        dateEpochDay: Long,
        startedAtEpochMillis: Long,
    ) {
        viewModelScope.launch {
            repository.addManualSession(type, durationMillis, dateEpochDay, startedAtEpochMillis)
        }
    }

    // ---- engine commands (forwarded as service intents) ----
    fun start() {
        val s = _setup.value
        persistLastSetup(s)
        // Only the initial start promotes the service to the foreground.
        send(TimerService.ACTION_START, foreground = true) {
            putExtra(TimerService.EXTRA_MODE, s.mode.name)
            putExtra(TimerService.EXTRA_FOCUS, s.focusMinutes * 60_000L)
            putExtra(TimerService.EXTRA_BREAK, s.breakMinutes * 60_000L)
            putExtra(TimerService.EXTRA_ROUNDS, s.rounds)
        }
    }

    private fun persistLastSetup(s: SetupState) {
        prefs.lastMode = s.mode.name
        prefs.lastFocusMinutes = s.focusMinutes
        prefs.lastBreakMinutes = s.breakMinutes
        prefs.lastRounds = s.rounds
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
