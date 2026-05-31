package com.deepwork.focustimer.ui.timer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deepwork.focustimer.timer.Phase
import com.deepwork.focustimer.timer.TimerMode
import com.deepwork.focustimer.timer.TimerUiState
import com.deepwork.focustimer.ui.theme.PitchBlack
import com.deepwork.focustimer.ui.theme.Sepia
import com.deepwork.focustimer.ui.theme.SepiaDim
import com.deepwork.focustimer.util.formatClock

@Composable
fun TimerScreen(vm: TimerViewModel) {
    val state by vm.timerState.collectAsStateWithLifecycle()
    val immersive by vm.immersive.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize()) {
        if (state.phase == Phase.IDLE) {
            SetupContent(vm)
        } else {
            ActiveContent(state = state, immersive = immersive, vm = vm)
        }
    }
}

// ---------------------------------------------------------------------------
// Setup (idle) screen
// ---------------------------------------------------------------------------

@Composable
private fun SetupContent(vm: TimerViewModel) {
    val setup by vm.setup.collectAsStateWithLifecycle()
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("FOCUS TIMER", color = Sepia, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(32.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ModeChip("Timer", setup.mode == TimerMode.TIMER) { vm.setMode(TimerMode.TIMER) }
            ModeChip("Stopwatch", setup.mode == TimerMode.STOPWATCH) { vm.setMode(TimerMode.STOPWATCH) }
        }
        Spacer(Modifier.height(28.dp))

        if (setup.mode == TimerMode.TIMER) {
            Stepper("Focus (min)", setup.focusMinutes, step = 5) { vm.setFocusMinutes(it) }
            Spacer(Modifier.height(16.dp))
            Stepper("Break (min)", setup.breakMinutes, step = 1) { vm.setBreakMinutes(it) }
            Spacer(Modifier.height(16.dp))
            Stepper("Sessions", setup.rounds, step = 1) { vm.setRounds(it) }
            Spacer(Modifier.height(32.dp))
        } else {
            Text(
                "Counts up until you finish.",
                color = SepiaDim, fontSize = 14.sp, fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.height(32.dp))
        }

        Button(
            onClick = { vm.start() },
            colors = ButtonDefaults.buttonColors(containerColor = Sepia, contentColor = PitchBlack),
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) { Text("START", fontWeight = FontWeight.Bold, fontSize = 18.sp) }
    }
}

@Composable
private fun ModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(containerColor = Sepia, contentColor = PitchBlack),
        ) { Text(label, fontWeight = FontWeight.Bold) }
    } else {
        OutlinedButton(
            onClick = onClick,
            border = BorderStroke(1.dp, SepiaDim),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Sepia),
        ) { Text(label) }
    }
}

@Composable
private fun Stepper(label: String, value: Int, step: Int, onChange: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = Sepia, fontFamily = FontFamily.Monospace, fontSize = 16.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(
                onClick = { onChange(value - step) },
                border = BorderStroke(1.dp, SepiaDim),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Sepia),
            ) { Text("–", fontSize = 18.sp) }
            Text(
                value.toString().padStart(2, ' '),
                color = Sepia,
                fontFamily = FontFamily.Monospace,
                fontSize = 20.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(56.dp),
            )
            OutlinedButton(
                onClick = { onChange(value + step) },
                border = BorderStroke(1.dp, SepiaDim),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Sepia),
            ) { Text("+", fontSize = 18.sp) }
        }
    }
}

// ---------------------------------------------------------------------------
// Active (running/paused) screen
// ---------------------------------------------------------------------------

@Composable
private fun ActiveContent(state: TimerUiState, immersive: Boolean, vm: TimerViewModel) {
    var menuOpen by remember { mutableStateOf(false) }
    val noRipple = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // Tapping anywhere on the active screen opens the quick menu.
            .clickable(interactionSource = noRipple, indication = null) { menuOpen = true },
        contentAlignment = Alignment.Center,
    ) {
        // Phase/round label — hidden in immersive mode (only the timer shows).
        if (!immersive) {
            val label = when {
                state.mode == TimerMode.STOPWATCH -> "STOPWATCH"
                state.phase == Phase.FOCUS -> "FOCUS  ·  ${state.currentRound}/${state.config.rounds}"
                state.phase == Phase.BREAK -> "BREAK  ·  ${state.currentRound}/${state.config.rounds}"
                else -> ""
            }
            Column(
                modifier = Modifier.fillMaxSize().padding(top = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(label, color = SepiaDim, fontFamily = FontFamily.Monospace, fontSize = 16.sp)
                if (!state.isRunning) {
                    Text("PAUSED", color = SepiaDim, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                }
            }
        }

        BigClock(text = formatClock(state.displayMillis), immersive = immersive)

        // Fullscreen toggle only while not immersive; in immersive mode use the menu.
        if (!immersive) {
            IconButton(
                onClick = { vm.toggleImmersive() },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            ) {
                Icon(Icons.Filled.Fullscreen, contentDescription = "Full screen", tint = SepiaDim)
            }
        }
    }

    if (menuOpen) {
        QuickMenu(
            state = state,
            immersive = immersive,
            onDismiss = { menuOpen = false },
            onPauseResume = { if (state.isRunning) vm.pause() else vm.resume(); menuOpen = false },
            onSkip = { vm.skipToBreak(); menuOpen = false },
            onToggleFullscreen = { vm.toggleImmersive(); menuOpen = false },
            onFinish = { vm.finish(); menuOpen = false },
            onStop = { vm.stop(); menuOpen = false },
        )
    }
}

/**
 * The headline timer. In immersive mode the digits scale to fill almost the
 * whole screen; otherwise they're large but capped so the rest of the UI fits.
 */
@Composable
private fun BigClock(text: String, immersive: Boolean) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val chars = text.length.coerceAtLeast(4)
        // Monospace glyphs are ~0.6 em wide; size the font so the string spans
        // ~92% of the available width, then clamp by height and mode.
        val widthBased = (maxWidth.value * 0.92f) / (chars * 0.6f)
        val heightBased = maxHeight.value * (if (immersive) 0.6f else 0.32f)
        val cap = if (immersive) 1000f else 110f
        val sizeSp = minOf(widthBased, heightBased, cap).coerceAtLeast(40f)

        Text(
            text = text,
            color = Sepia,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = sizeSp.sp,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun QuickMenu(
    state: TimerUiState,
    immersive: Boolean,
    onDismiss: () -> Unit,
    onPauseResume: () -> Unit,
    onSkip: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onFinish: () -> Unit,
    onStop: () -> Unit,
) {
    val noRipple = remember { MutableInteractionSource() }
    // Opaque pitch-black scrim so the big timer digits behind it are fully
    // covered (otherwise they overlap the menu buttons).
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PitchBlack)
            .clickable(interactionSource = noRipple, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            MenuButton(if (state.isRunning) "PAUSE" else "RESUME", onPauseResume)

            // "Skip to Break" only makes sense for a running TIMER focus leg.
            if (state.mode == TimerMode.TIMER && state.phase == Phase.FOCUS) {
                MenuButton("SKIP TO BREAK", onSkip)
            }

            MenuButton(
                if (immersive) "EXIT FULL SCREEN" else "FULL SCREEN",
                onToggleFullscreen,
                icon = if (immersive) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
            )
            MenuButton("FINISH", onFinish)
            MenuButton("STOP", onStop)
        }
    }
}

@Composable
private fun MenuButton(
    text: String,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    OutlinedButton(
        onClick = onClick,
        border = BorderStroke(1.dp, Sepia),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Sepia),
        modifier = Modifier.width(240.dp).height(52.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = Sepia)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}
