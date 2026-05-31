package com.deepwork.focustimer.timer

/** Top-level mode the user picks on the setup screen. */
enum class TimerMode { TIMER, STOPWATCH }

/** Which leg of a multi-session workflow we're in. */
enum class Phase { IDLE, FOCUS, BREAK, DONE }

/** User-configured plan for TIMER mode. */
data class TimerConfig(
    val focusMillis: Long = 50 * 60_000L,
    val breakMillis: Long = 10 * 60_000L,
    val rounds: Int = 4,                 // number of focus sessions in the plan
)

/**
 * The single immutable snapshot the UI observes. Produced authoritatively by
 * TimerService and mirrored through TimerViewModel.
 */
data class TimerUiState(
    val mode: TimerMode = TimerMode.TIMER,
    val phase: Phase = Phase.IDLE,
    val isRunning: Boolean = false,
    val config: TimerConfig = TimerConfig(),
    val currentRound: Int = 0,           // 1-based once running
    val elapsedInPhaseMillis: Long = 0L, // counts up
    val remainingMillis: Long = 0L,      // counts down (TIMER only)
    val phaseTotalMillis: Long = 0L,     // configured length of current phase
) {
    /** The number actually shown on the big display. */
    val displayMillis: Long
        get() = if (mode == TimerMode.STOPWATCH) elapsedInPhaseMillis else remainingMillis

    val isActive: Boolean get() = phase == Phase.FOCUS || phase == Phase.BREAK
}
