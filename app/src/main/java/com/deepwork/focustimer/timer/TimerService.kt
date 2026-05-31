package com.deepwork.focustimer.timer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.deepwork.focustimer.FocusTimerApp
import com.deepwork.focustimer.MainActivity
import com.deepwork.focustimer.R
import com.deepwork.focustimer.data.SessionType
import com.deepwork.focustimer.data.StatsRepository
import com.deepwork.focustimer.util.formatClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The authoritative timer engine.
 *
 * Why the engine lives here and not in the ViewModel: the timer must keep
 * accurate time while the Activity is destroyed (screen locked, app swiped
 * away, Doze). A ViewModel is tied to the UI process scope and gets cleared.
 * A started foreground service keeps the process alive and is exempt from the
 * background execution limits that would otherwise pause our coroutine.
 *
 * Clean Architecture is preserved by direction of dependencies:
 *   UI  ->  TimerViewModel  ->  (intents)  ->  TimerService  ->  StatsRepository
 *   UI  <-  TimerViewModel  <-  (StateFlow) <- TimerService
 * The UI never touches the service or the repository directly.
 *
 * Timekeeping uses [SystemClock.elapsedRealtime] (monotonic, unaffected by
 * wall-clock changes) as the base, and derives elapsed time by subtraction on
 * every tick. Individual ticks may be delayed, but the displayed value is
 * always recomputed from the clock, so the timer never drifts.
 */
class TimerService : Service() {

    private val scope = CoroutineScope(SupervisorJob())
    private var ticker: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private val repository: StatsRepository
        get() = FocusTimerApp.from(application).repository

    // --- timing accumulators (only the service mutates these) ---
    private var accumulatedInPhaseMillis = 0L  // time banked across pauses
    private var runStartElapsed = 0L           // elapsedRealtime when current run leg began
    private var phaseStartedWallMillis = 0L    // wall-clock start, stored with the session row

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> handleStart(intent)
            ACTION_PAUSE -> handlePause()
            ACTION_RESUME -> handleResume()
            ACTION_SKIP -> handleSkipToBreak()
            ACTION_FINISH -> handleFinish()
            ACTION_STOP -> handleStop()
        }
        // If the process is killed we don't want a stale empty restart.
        return START_NOT_STICKY
    }

    // ---------------------------------------------------------------------
    // Commands
    // ---------------------------------------------------------------------

    private fun handleStart(intent: Intent) {
        val mode = TimerMode.valueOf(intent.getStringExtra(EXTRA_MODE) ?: TimerMode.TIMER.name)
        val config = TimerConfig(
            focusMillis = intent.getLongExtra(EXTRA_FOCUS, TimerConfig().focusMillis),
            breakMillis = intent.getLongExtra(EXTRA_BREAK, TimerConfig().breakMillis),
            rounds = intent.getIntExtra(EXTRA_ROUNDS, TimerConfig().rounds),
        )

        goForeground()
        acquireWakeLock()

        _state.value = TimerUiState(
            mode = mode,
            phase = Phase.FOCUS,
            config = config,
            currentRound = 1,
        )
        // Stopwatch counts up with no target; Timer counts down from focusMillis.
        startPhase(
            phase = Phase.FOCUS,
            phaseTotal = if (mode == TimerMode.STOPWATCH) 0L else config.focusMillis,
        )
    }

    private fun handlePause() {
        val s = _state.value
        if (!s.isRunning || !s.isActive) return
        bankElapsed()
        ticker?.cancel()
        _state.value = s.copy(isRunning = false)
        updateNotification()
    }

    private fun handleResume() {
        val s = _state.value
        if (s.isRunning || !s.isActive) return
        runStartElapsed = SystemClock.elapsedRealtime()
        _state.value = s.copy(isRunning = true)
        launchTicker()
    }

    /**
     * Skip-to-break, exactly per spec:
     *  1. compute the precise elapsed time of the current FOCUS leg
     *  2. persist it as a FocusSession (completed = false, it was cut short)
     *  3. immediately switch the UI to the break timer
     *  4. auto-queue the next focus session (handled in onPhaseComplete for BREAK)
     */
    private fun handleSkipToBreak() {
        val s = _state.value
        if (s.mode != TimerMode.TIMER || s.phase != Phase.FOCUS) return

        val elapsed = currentElapsedInPhase()
        persist(SessionType.FOCUS, elapsed, completed = false)

        startPhase(Phase.BREAK, s.config.breakMillis)
    }

    /** Finish the whole plan now, recording whatever the current leg has logged. */
    private fun handleFinish() {
        val s = _state.value
        if (s.isActive) {
            val elapsed = currentElapsedInPhase()
            val type = if (s.phase == Phase.FOCUS) SessionType.FOCUS else SessionType.BREAK
            persist(type, elapsed, completed = false)
        }
        finishPlan()
    }

    /** Hard stop: discard the current leg, no DB write. */
    private fun handleStop() {
        finishPlan()
    }

    // ---------------------------------------------------------------------
    // Phase machinery
    // ---------------------------------------------------------------------

    private fun startPhase(phase: Phase, phaseTotal: Long) {
        accumulatedInPhaseMillis = 0L
        runStartElapsed = SystemClock.elapsedRealtime()
        phaseStartedWallMillis = System.currentTimeMillis()

        val s = _state.value
        _state.value = s.copy(
            phase = phase,
            isRunning = true,
            phaseTotalMillis = phaseTotal,
            elapsedInPhaseMillis = 0L,
            remainingMillis = phaseTotal,
        )
        updateNotification()
        launchTicker()
    }

    /** Called when a TIMER leg reaches zero. Drives the multi-session workflow. */
    private fun onPhaseComplete() {
        val s = _state.value
        when (s.phase) {
            Phase.FOCUS -> {
                persist(SessionType.FOCUS, s.config.focusMillis, completed = true)
                if (s.currentRound < s.config.rounds) {
                    // focus done -> take the configured break
                    startPhase(Phase.BREAK, s.config.breakMillis)
                } else {
                    finishPlan()
                }
            }
            Phase.BREAK -> {
                persist(SessionType.BREAK, s.config.breakMillis, completed = true)
                if (s.currentRound < s.config.rounds) {
                    // break done -> auto-queue the next focus session
                    _state.value = s.copy(currentRound = s.currentRound + 1)
                    startPhase(Phase.FOCUS, s.config.focusMillis)
                } else {
                    // break that followed the final focus (e.g. via skip) -> done
                    finishPlan()
                }
            }
            else -> Unit
        }
    }

    private fun finishPlan() {
        ticker?.cancel()
        releaseWakeLock()
        _state.value = TimerUiState(
            mode = _state.value.mode,
            phase = Phase.IDLE,
            config = _state.value.config,
        )
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ---------------------------------------------------------------------
    // Ticking
    // ---------------------------------------------------------------------

    private fun launchTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            var lastNotifSecond = -1L
            while (isActive) {
                val elapsed = currentElapsedInPhase()
                val s = _state.value
                if (s.mode == TimerMode.STOPWATCH) {
                    _state.value = s.copy(elapsedInPhaseMillis = elapsed)
                } else {
                    val remaining = (s.phaseTotalMillis - elapsed).coerceAtLeast(0L)
                    _state.value = s.copy(
                        elapsedInPhaseMillis = elapsed,
                        remainingMillis = remaining,
                    )
                    if (remaining <= 0L) {
                        onPhaseComplete()
                        return@launch
                    }
                }
                // Throttle the notification to once per displayed second.
                val shownSecond = _state.value.displayMillis / 1000
                if (shownSecond != lastNotifSecond) {
                    lastNotifSecond = shownSecond
                    updateNotification()
                }
                delay(TICK_INTERVAL_MS)
            }
        }
    }

    private fun currentElapsedInPhase(): Long {
        val running = if (_state.value.isRunning) {
            SystemClock.elapsedRealtime() - runStartElapsed
        } else 0L
        return accumulatedInPhaseMillis + running
    }

    private fun bankElapsed() {
        accumulatedInPhaseMillis += SystemClock.elapsedRealtime() - runStartElapsed
    }

    private fun persist(type: SessionType, durationMillis: Long, completed: Boolean) {
        val started = phaseStartedWallMillis
        scope.launch {
            repository.recordSession(
                type = type,
                durationMillis = durationMillis,
                completed = completed,
                startedAtEpochMillis = started,
            )
        }
    }

    // ---------------------------------------------------------------------
    // Foreground + notification
    // ---------------------------------------------------------------------

    private fun goForeground() {
        val notification = buildNotification(_state.value)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun updateNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID, buildNotification(_state.value))
    }

    private fun buildNotification(s: TimerUiState): Notification {
        val tapIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val title = when {
            s.mode == TimerMode.STOPWATCH -> "Stopwatch"
            s.phase == Phase.FOCUS -> "Focus · round ${s.currentRound}/${s.config.rounds}"
            s.phase == Phase.BREAK -> "Break · round ${s.currentRound}/${s.config.rounds}"
            else -> "Focus Timer"
        }
        val body = formatClock(s.displayMillis) + if (!s.isRunning) "  (paused)" else ""

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(tapIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notification_channel_desc)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    // ---------------------------------------------------------------------
    // Wake lock (keeps the CPU ticking with the screen off)
    // ---------------------------------------------------------------------

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "FocusTimer::tick").apply {
            setReferenceCounted(false)
            acquire(MAX_WAKELOCK_MS)
        }
    }

    private fun releaseWakeLock() {
        if (wakeLock?.isHeld == true) wakeLock?.release()
        wakeLock = null
    }

    override fun onDestroy() {
        ticker?.cancel()
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "focus_timer_channel"
        private const val NOTIF_ID = 1001
        private const val TICK_INTERVAL_MS = 200L
        private const val MAX_WAKELOCK_MS = 12L * 60 * 60 * 1000 // safety ceiling: 12h

        const val ACTION_START = "com.deepwork.focustimer.START"
        const val ACTION_PAUSE = "com.deepwork.focustimer.PAUSE"
        const val ACTION_RESUME = "com.deepwork.focustimer.RESUME"
        const val ACTION_SKIP = "com.deepwork.focustimer.SKIP"
        const val ACTION_FINISH = "com.deepwork.focustimer.FINISH"
        const val ACTION_STOP = "com.deepwork.focustimer.STOP"

        const val EXTRA_MODE = "extra_mode"
        const val EXTRA_FOCUS = "extra_focus"
        const val EXTRA_BREAK = "extra_break"
        const val EXTRA_ROUNDS = "extra_rounds"

        /**
         * Process-global, authoritative timer state. It lives here (not in the
         * ViewModel) so it transparently survives configuration changes and
         * Activity recreation — there is exactly one timer per process.
         */
        private val _state = MutableStateFlow(TimerUiState())
        val state: StateFlow<TimerUiState> = _state.asStateFlow()
    }
}
