package com.deepwork.focustimer.ui.timer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deepwork.focustimer.data.SessionType
import com.deepwork.focustimer.data.TimerPreset
import com.deepwork.focustimer.timer.Phase
import com.deepwork.focustimer.timer.TimerMode
import com.deepwork.focustimer.timer.TimerUiState
import com.deepwork.focustimer.ui.theme.PitchBlack
import com.deepwork.focustimer.ui.theme.Sepia
import com.deepwork.focustimer.ui.theme.SepiaDim
import com.deepwork.focustimer.ui.theme.SepiaFaint
import com.deepwork.focustimer.util.formatClock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

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
    val presets by vm.presets.collectAsStateWithLifecycle()

    var editing by remember { mutableStateOf<EditField?>(null) }
    var savingPreset by remember { mutableStateOf(false) }
    var addingManual by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Text("FOCUS TIMER", color = Sepia, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(28.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ModeChip("Timer", setup.mode == TimerMode.TIMER) { vm.setMode(TimerMode.TIMER) }
            ModeChip("Stopwatch", setup.mode == TimerMode.STOPWATCH) { vm.setMode(TimerMode.STOPWATCH) }
        }
        Spacer(Modifier.height(24.dp))

        if (setup.mode == TimerMode.TIMER) {
            // Tap the value to type an exact number; +/- nudge by one step.
            Stepper("Focus (min)", setup.focusMinutes, step = 1,
                onChange = { vm.setFocusMinutes(it) }, onTapValue = { editing = EditField.FOCUS })
            Spacer(Modifier.height(14.dp))
            Stepper("Break (min)", setup.breakMinutes, step = 1,
                onChange = { vm.setBreakMinutes(it) }, onTapValue = { editing = EditField.BREAK })
            Spacer(Modifier.height(14.dp))
            Stepper("Sessions", setup.rounds, step = 1,
                onChange = { vm.setRounds(it) }, onTapValue = { editing = EditField.ROUNDS })
            Spacer(Modifier.height(20.dp))

            PresetsSection(
                presets = presets,
                onApply = { vm.applyPreset(it) },
                onDelete = { vm.deletePreset(it.id) },
                onSaveCurrent = { savingPreset = true },
            )
            Spacer(Modifier.height(24.dp))
        } else {
            Text(
                "Counts up until you finish.",
                color = SepiaDim, fontSize = 14.sp, fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.height(24.dp))
        }

        Button(
            onClick = { vm.start() },
            colors = ButtonDefaults.buttonColors(containerColor = Sepia, contentColor = PitchBlack),
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) { Text("START", fontWeight = FontWeight.Bold, fontSize = 18.sp) }

        Spacer(Modifier.height(16.dp))
        OutlinedButton(
            onClick = { addingManual = true },
            border = BorderStroke(1.dp, SepiaDim),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Sepia),
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) { Text("ADD MANUAL SESSION", fontWeight = FontWeight.Bold) }
    }

    // ---- dialogs ----
    editing?.let { field ->
        val (label, value, range) = when (field) {
            EditField.FOCUS -> Triple("Focus (min)", setup.focusMinutes, 1..600)
            EditField.BREAK -> Triple("Break (min)", setup.breakMinutes, 1..180)
            EditField.ROUNDS -> Triple("Sessions", setup.rounds, 1..20)
        }
        NumberInputDialog(
            title = label,
            initial = value,
            range = range,
            onConfirm = {
                when (field) {
                    EditField.FOCUS -> vm.setFocusMinutes(it)
                    EditField.BREAK -> vm.setBreakMinutes(it)
                    EditField.ROUNDS -> vm.setRounds(it)
                }
                editing = null
            },
            onDismiss = { editing = null },
        )
    }

    if (savingPreset) {
        SavePresetDialog(
            onConfirm = { vm.saveCurrentAsPreset(it); savingPreset = false },
            onDismiss = { savingPreset = false },
        )
    }

    if (addingManual) {
        ManualEntryDialog(
            onConfirm = { type, duration, day, startedAt ->
                vm.addManualSession(type, duration, day, startedAt)
                addingManual = false
            },
            onDismiss = { addingManual = false },
        )
    }
}

private enum class EditField { FOCUS, BREAK, ROUNDS }

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
private fun Stepper(
    label: String,
    value: Int,
    step: Int,
    onChange: (Int) -> Unit,
    onTapValue: () -> Unit,
) {
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
                modifier = Modifier
                    .width(56.dp)
                    .clickable(onClick = onTapValue),
            )
            OutlinedButton(
                onClick = { onChange(value + step) },
                border = BorderStroke(1.dp, SepiaDim),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Sepia),
            ) { Text("+", fontSize = 18.sp) }
        }
    }
}

@Composable
private fun PresetsSection(
    presets: List<TimerPreset>,
    onApply: (TimerPreset) -> Unit,
    onDelete: (TimerPreset) -> Unit,
    onSaveCurrent: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("PRESETS", color = SepiaDim, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
            TextButton(onClick = onSaveCurrent) {
                Text("Save current", color = Sepia, fontWeight = FontWeight.Bold)
            }
        }
        if (presets.isEmpty()) {
            Text(
                "No saved presets yet.",
                color = SepiaDim, fontFamily = FontFamily.Monospace, fontSize = 12.sp,
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                presets.forEach { preset ->
                    AssistChip(
                        onClick = { onApply(preset) },
                        label = { Text(preset.name, color = Sepia) },
                        trailingIcon = {
                            IconButton(onClick = { onDelete(preset) }, modifier = Modifier.width(28.dp)) {
                                Icon(Icons.Filled.Close, contentDescription = "Delete preset", tint = SepiaDim)
                            }
                        },
                        border = AssistChipDefaults.assistChipBorder(enabled = true, borderColor = SepiaDim),
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Setup dialogs
// ---------------------------------------------------------------------------

@Composable
private fun NumberInputDialog(
    title: String,
    initial: Int,
    range: IntRange,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial.toString()) }
    val parsed = text.toIntOrNull()
    val valid = parsed != null && parsed in range

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = Sepia) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.filter(Char::isDigit).take(4) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Allowed: ${range.first}–${range.last}",
                    color = SepiaDim, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { parsed?.let(onConfirm) }, enabled = valid) {
                Text("OK", color = if (valid) Sepia else SepiaDim)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = SepiaDim) } },
        containerColor = PitchBlack,
    )
}

@Composable
private fun SavePresetDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save preset", color = Sepia) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(30) },
                singleLine = true,
                placeholder = { Text("Name", color = SepiaDim) },
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) {
                Text("Save", color = if (name.isNotBlank()) Sepia else SepiaDim)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = SepiaDim) } },
        containerColor = PitchBlack,
    )
}

private val manualDateFmt = DateTimeFormatter.ofPattern("EEE, MMM d yyyy")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManualEntryDialog(
    onConfirm: (SessionType, Long, Long, Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val today = remember { LocalDate.now() }
    var date by remember { mutableStateOf(today) }
    var type by remember { mutableStateOf(SessionType.FOCUS) }
    var startMin by remember { mutableIntStateOf(9 * 60) }   // 09:00
    var endMin by remember { mutableIntStateOf(10 * 60) }    // 10:00

    var showDatePicker by remember { mutableStateOf(false) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    val durationMin = endMin - startMin
    val valid = durationMin > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add manual session", color = Sepia) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModeChip("Focus", type == SessionType.FOCUS) { type = SessionType.FOCUS }
                    ModeChip("Break", type == SessionType.BREAK) { type = SessionType.BREAK }
                }
                FieldRow("Date", date.format(manualDateFmt)) { showDatePicker = true }
                FieldRow("Start", formatHm(startMin)) { showStartPicker = true }
                FieldRow("End", formatHm(endMin)) { showEndPicker = true }
                Text(
                    if (valid) "Duration: ${durationMin / 60}h ${durationMin % 60}m"
                    else "End must be after start.",
                    color = if (valid) Sepia else SepiaDim,
                    fontFamily = FontFamily.Monospace, fontSize = 13.sp,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    val zone = ZoneId.systemDefault()
                    val startedAt = date.atTime(startMin / 60, startMin % 60)
                        .atZone(zone).toInstant().toEpochMilli()
                    onConfirm(type, durationMin * 60_000L, date.toEpochDay(), startedAt)
                },
            ) { Text("Add", color = if (valid) Sepia else SepiaDim) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = SepiaDim) } },
        containerColor = PitchBlack,
    )

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text("OK", color = Sepia) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel", color = SepiaDim) }
            },
        ) { DatePicker(state = pickerState) }
    }

    if (showStartPicker) {
        TimePickerDialog(
            initialMinuteOfDay = startMin,
            onConfirm = { startMin = it; showStartPicker = false },
            onDismiss = { showStartPicker = false },
        )
    }
    if (showEndPicker) {
        TimePickerDialog(
            initialMinuteOfDay = endMin,
            onConfirm = { endMin = it; showEndPicker = false },
            onDismiss = { showEndPicker = false },
        )
    }
}

@Composable
private fun FieldRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = SepiaDim, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
        Text(value, color = Sepia, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    initialMinuteOfDay: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initialMinuteOfDay / 60,
        initialMinute = initialMinuteOfDay % 60,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) {
                Text("OK", color = Sepia)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = SepiaDim) } },
        text = { TimePicker(state = state) },
        containerColor = PitchBlack,
    )
}

private fun formatHm(minuteOfDay: Int): String =
    "%02d:%02d".format(minuteOfDay / 60, minuteOfDay % 60)

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
 * Immersive mode shows nothing but these digits — no labels, no chrome.
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
