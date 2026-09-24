package com.lowertraining.app

import android.app.Application
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.TrendingDown
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import com.lowertraining.app.ui.theme.Accent
import com.lowertraining.app.ui.theme.Accent2
import com.lowertraining.app.ui.theme.Background
import com.lowertraining.app.ui.theme.Cyan
import com.lowertraining.app.ui.theme.Ink
import com.lowertraining.app.ui.theme.Line
import com.lowertraining.app.ui.theme.LowerTrainingTheme
import com.lowertraining.app.ui.theme.Muted
import com.lowertraining.app.ui.theme.Success
import com.lowertraining.app.ui.theme.Surface
import com.lowertraining.app.ui.theme.SurfaceSoft
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

enum class Screen { HOME, SET, REST, ANALYTICS, SUMMARY, SETTINGS }

class TrainingViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = TrainingRepository(app)
    private val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 55)
    private val vibrator: Vibrator? = app.getSystemService(Vibrator::class.java)
    private var timerJob: Job? = null

    var screen by mutableStateOf(Screen.HOME)
        private set

    var selectedPlan by mutableStateOf<WorkoutPlan?>(null)
        private set

    var targets by mutableStateOf(emptyList<SetTarget>())
        private set

    var targetIndex by mutableIntStateOf(0)
        private set

    var weightText by mutableStateOf("")
    var repsText by mutableStateOf("")

    var ghost by mutableStateOf<PreviousSet?>(null)
        private set

    var settings by mutableStateOf(repository.loadSettings())
        private set

    var restRemaining by mutableIntStateOf(0)
        private set

    var restTotal by mutableIntStateOf(0)
        private set

    var summary by mutableStateOf<SessionSummary?>(null)
        private set

    var analytics by mutableStateOf(repository.analytics())
        private set

    var validationMessage by mutableStateOf<String?>(null)
        private set

    var lastPrMessage by mutableStateOf<String?>(null)
        private set

    private var sessionId by mutableLongStateOf(0L)
    private var sessionStartedAt by mutableLongStateOf(0L)

    val currentTarget: SetTarget?
        get() = targets.getOrNull(targetIndex)

    val nextTarget: SetTarget?
        get() = targets.getOrNull(targetIndex + 1)

    val progress: Float
        get() = if (targets.isEmpty()) 0f else targetIndex.toFloat() / targets.size.toFloat()

    fun startWorkout(plan: WorkoutPlan) {
        timerJob?.cancel()
        selectedPlan = plan
        targets = WorkoutDefinitions.targets(plan)
        targetIndex = 0
        sessionStartedAt = System.currentTimeMillis()
        sessionId = repository.startSession(plan, sessionStartedAt)
        summary = null
        validationMessage = null
        lastPrMessage = null
        loadTarget()
        screen = Screen.SET
    }

    private fun loadTarget() {
        val target = currentTarget ?: return
        weightText = ""
        repsText = ""
        ghost = repository.lastSet(target.exercise.id)
        validationMessage = null
    }

    fun logCurrentSet(): Boolean {
        val target = currentTarget ?: return false
        val weight = weightText.toDoubleOrNull()
        val reps = repsText.toIntOrNull()

        if (weight == null || weight <= 0) {
            validationMessage = "Enter a valid weight."
            return false
        }
        if (reps == null || reps !in 1..100) {
            validationMessage = "Enter reps from 1 to 100."
            return false
        }

        val logged = repository.saveSet(
            sessionId = sessionId,
            target = target,
            weight = weight,
            reps = reps,
        )

        lastPrMessage = if (logged.isPr) "New personal best" else null
        if (logged.isPr) {
            cue(ToneGenerator.TONE_PROP_ACK, 130)
            haptic(70)
        }

        val finalSet = targetIndex >= targets.lastIndex
        if (finalSet) {
            finishWorkout()
            return true
        }

        if (target.restAfter) {
            beginRest(restSecondsFor(target.exercise.restClass))
        } else {
            targetIndex += 1
            loadTarget()
            screen = Screen.SET
            cue(ToneGenerator.TONE_PROP_BEEP2, 80)
        }
        return true
    }

    private fun restSecondsFor(restClass: RestClass): Int = when (restClass) {
        RestClass.COMPOUND -> settings.compoundRestSec
        RestClass.ACCESSORY -> settings.accessoryRestSec
        RestClass.SUPERSET -> settings.supersetRestSec
    }

    private fun beginRest(seconds: Int) {
        restTotal = seconds
        restRemaining = seconds
        screen = Screen.REST
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (restRemaining > 0 && screen == Screen.REST) {
                delay(1000)
                restRemaining = max(0, restRemaining - 1)
                if (restRemaining in 1..3) {
                    cue(ToneGenerator.TONE_PROP_BEEP, 90)
                    haptic(28)
                }
            }
            if (screen == Screen.REST && restRemaining <= 0) {
                cue(ToneGenerator.TONE_PROP_ACK, 180)
                haptic(90)
                advanceAfterRest()
            }
        }
    }

    fun adjustRest(delta: Int) {
        restRemaining = max(0, restRemaining + delta)
        restTotal = max(restTotal, restRemaining)
        if (restRemaining == 0) advanceAfterRest()
    }

    fun skipRest() = advanceAfterRest()

    private fun advanceAfterRest() {
        timerJob?.cancel()
        if (targetIndex < targets.lastIndex) {
            targetIndex += 1
            loadTarget()
            screen = Screen.SET
        } else {
            finishWorkout()
        }
    }

    private fun finishWorkout() {
        timerJob?.cancel()
        val plan = selectedPlan ?: return
        summary = repository.completeSession(
            sessionId = sessionId,
            planName = plan.name,
            startedAt = sessionStartedAt,
        )
        analytics = repository.analytics()
        cue(ToneGenerator.TONE_PROP_ACK, 220)
        haptic(120)
        screen = Screen.SUMMARY
    }

    fun abandonWorkout() {
        timerJob?.cancel()
        if (sessionId > 0) repository.abandonSession(sessionId)
        sessionId = 0
        selectedPlan = null
        targets = emptyList()
        targetIndex = 0
        screen = Screen.HOME
    }

    fun goHome() {
        analytics = repository.analytics()
        screen = Screen.HOME
    }

    fun openAnalytics() {
        analytics = repository.analytics()
        screen = Screen.ANALYTICS
    }

    fun openSettings() {
        screen = Screen.SETTINGS
    }

    fun updateSettings(newSettings: AppSettings) {
        settings = newSettings
        repository.saveSettings(newSettings)
    }

    private fun cue(toneId: Int, durationMs: Int) {
        if (settings.soundEnabled) tone.startTone(toneId, durationMs)
    }

    private fun haptic(durationMs: Long) {
        if (!settings.hapticsEnabled) return
        vibrator?.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    override fun onCleared() {
        timerJob?.cancel()
        tone.release()
        super.onCleared()
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LowerTrainingTheme {
                val vm: TrainingViewModel = viewModel()
                LowerTrainingApp(vm)
            }
        }
    }
}

@Composable
private fun LowerTrainingApp(vm: TrainingViewModel) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        AmbientBackdrop()

        Crossfade(
            targetState = vm.screen,
            animationSpec = tween(260),
            label = "screen"
        ) { screen ->
            when (screen) {
                Screen.HOME -> HomeScreen(vm)
                Screen.SET -> SetScreen(vm)
                Screen.REST -> RestScreen(vm)
                Screen.ANALYTICS -> AnalyticsScreen(vm)
                Screen.SUMMARY -> SummaryScreen(vm)
                Screen.SETTINGS -> SettingsScreen(vm)
            }
        }
    }
}

@Composable
private fun AmbientBackdrop() {
    val transition = rememberInfiniteTransition(label = "ambient")
    val shift by transition.animateFloat(
        initialValue = -30f,
        targetValue = 30f,
        animationSpec = infiniteRepeatable(
            tween(5200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shift"
    )

    Canvas(Modifier.fillMaxSize()) {
        drawCircle(
            color = Accent.copy(alpha = 0.055f),
            radius = size.minDimension * 0.42f,
            center = Offset(size.width * 0.92f + shift, size.height * 0.08f)
        )
        drawCircle(
            color = Cyan.copy(alpha = 0.04f),
            radius = size.minDimension * 0.35f,
            center = Offset(size.width * 0.05f - shift, size.height * 0.86f)
        )
    }
}

@Composable
private fun HomeScreen(vm: TrainingViewModel) {
    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0)
    ) { inner ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "LOWER",
                            style = MaterialTheme.typography.headlineLarge,
                            color = Ink
                        )
                        Text(
                            "TRAINING CONSOLE",
                            style = MaterialTheme.typography.labelLarge,
                            color = Accent
                        )
                    }
                    IconButton(onClick = vm::openSettings) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Settings", tint = Ink)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Two focused lower-body sessions. No clutter, no account, no network.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted
                )
            }

            items(WorkoutDefinitions.plans) { plan ->
                WorkoutCard(plan = plan, onClick = { vm.startWorkout(plan) })
            }

            item {
                AnalyticsPreview(vm.analytics, onClick = vm::openAnalytics)
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = vm::openAnalytics,
                        modifier = Modifier.weight(1f).height(54.dp),
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Icon(Icons.Rounded.Analytics, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Analytics")
                    }
                    Button(
                        onClick = { vm.startWorkout(WorkoutDefinitions.lower1) },
                        modifier = Modifier.weight(1f).height(54.dp),
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Icon(Icons.Rounded.Bolt, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Start Lower 1")
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkoutCard(plan: WorkoutPlan, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(
                            Brush.linearGradient(listOf(Accent, Accent2)),
                            RoundedCornerShape(18.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.FitnessCenter,
                        contentDescription = null,
                        tint = Color.White
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(plan.name, style = MaterialTheme.typography.titleLarge)
                    Text(plan.subtitle, color = Muted, style = MaterialTheme.typography.bodyMedium)
                }
                Text("16 sets", color = Accent, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(18.dp))
            HorizontalDivider(color = Line)
            Spacer(Modifier.height(14.dp))
            Text(
                if (plan.id == "lower_1")
                    "4 Squat · 4 RDL · 4 Leg Press/Calf Press rounds"
                else
                    "4 Deadlift · 4 Extension · 4 Curl · 4 Calf Press",
                color = Muted,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun AnalyticsPreview(data: AnalyticsSnapshot, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Ink)
    ) {
        Column(Modifier.padding(22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Analytics, null, tint = Color.White)
                Spacer(Modifier.width(10.dp))
                Text("Training analytics", color = Color.White, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DarkMetric("Sessions", data.sessionCount.toString(), Modifier.weight(1f))
                DarkMetric("Volume", formatKg(data.totalVolume), Modifier.weight(1f))
                DarkMetric("PRs", data.totalPrs.toString(), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun DarkMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        Text(value, color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp)
        Text(label, color = Color.White.copy(alpha = 0.65f), fontSize = 12.sp)
    }
}

@Composable
private fun SetScreen(vm: TrainingViewModel) {
    val target = vm.currentTarget ?: return
    var showExit by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val repsFocus = FocusRequester()

    BackHandler { showExit = true }

    if (showExit) {
        AlertDialog(
            onDismissRequest = { showExit = false },
            title = { Text("End workout?") },
            text = { Text("The current workout will be discarded. Completed historical sessions are not affected.") },
            confirmButton = {
                TextButton(onClick = vm::abandonWorkout) { Text("End workout") }
            },
            dismissButton = {
                TextButton(onClick = { showExit = false }) { Text("Continue") }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { showExit = true }) {
                Icon(Icons.Rounded.Close, "End workout")
            }
            Spacer(Modifier.weight(1f))
            Text(
                vm.selectedPlan?.name ?: "",
                color = Muted,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
            Text(
                (vm.targetIndex + 1).toString() + "/" + vm.targets.size.toString(),
                color = Accent,
                fontWeight = FontWeight.Black
            )
        }

        LinearProgressIndicator(
            progress = { (vm.targetIndex + 1f) / vm.targets.size.toFloat() },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .height(5.dp),
            color = Accent,
            trackColor = SurfaceSoft,
            strokeCap = StrokeCap.Round
        )

        Spacer(Modifier.weight(0.35f))

        target.supersetLabel?.let {
            Text(
                it.uppercase(),
                color = Cyan,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.2.sp,
                modifier = Modifier
                    .background(Cyan.copy(alpha = 0.09f), RoundedCornerShape(999.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
            Spacer(Modifier.height(14.dp))
        }

        Text(
            "SET " + target.displaySet.toString() + " OF " + target.totalSets.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = Accent
        )
        Spacer(Modifier.height(8.dp))
        Text(
            target.exercise.name,
            style = MaterialTheme.typography.headlineLarge,
            color = Ink
        )

        vm.ghost?.let { previous ->
            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceSoft, RoundedCornerShape(18.dp))
                    .border(1.dp, Line, RoundedCornerShape(18.dp))
                    .padding(15.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("LAST", color = Muted, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Spacer(Modifier.width(12.dp))
                Text(
                    trimNumber(previous.weight) + " kg × " + previous.reps.toString(),
                    color = Ink,
                    fontWeight = FontWeight.Black
                )
                Spacer(Modifier.weight(1f))
                Text("ghost", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        vm.lastPrMessage?.let {
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .background(Success.copy(alpha = 0.09f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 13.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.EmojiEvents, null, tint = Success, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(7.dp))
                Text(it, color = Success, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(24.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = vm.weightText,
                onValueChange = {
                    vm.weightText = it.filter { ch -> ch.isDigit() || ch == '.' }.take(7)
                },
                modifier = Modifier.weight(1f),
                label = { Text("Weight") },
                placeholder = { Text(vm.ghost?.let { trimNumber(it.weight) } ?: "kg") },
                suffix = { Text("kg") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(onNext = { repsFocus.requestFocus() }),
                shape = RoundedCornerShape(18.dp)
            )

            OutlinedTextField(
                value = vm.repsText,
                onValueChange = {
                    vm.repsText = it.filter(Char::isDigit).take(3)

                },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(repsFocus),
                label = { Text("Reps") },
                placeholder = { Text(vm.ghost?.reps?.toString() ?: "reps") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (vm.logCurrentSet()) focusManager.clearFocus(force = true)
                    }
                ),
                shape = RoundedCornerShape(18.dp)
            )
        }

        vm.validationMessage?.let {
            Spacer(Modifier.height(9.dp))
            Text(it, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(10.dp))
        Text(
            "Enter weight and reps, then press Done. Rest starts automatically after completed sets.",
            color = Muted,
            fontSize = 12.sp
        )

        Spacer(Modifier.weight(1f))

        Button(
            onClick = {
                if (vm.logCurrentSet()) focusManager.clearFocus(force = true)
            },
            modifier = Modifier.fillMaxWidth().height(64.dp),
            shape = RoundedCornerShape(22.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Ink)
        ) {
            Text("LOG SET", fontWeight = FontWeight.Black, letterSpacing = 0.8.sp)
            Spacer(Modifier.width(10.dp))
            Icon(Icons.Rounded.Check, null)
        }
    }
}

@Composable
private fun RestScreen(vm: TrainingViewModel) {
    var showExit by remember { mutableStateOf(false) }
    BackHandler { showExit = true }

    if (showExit) {
        AlertDialog(
            onDismissRequest = { showExit = false },
            title = { Text("End workout?") },
            text = { Text("Your current in-progress workout will be discarded.") },
            confirmButton = {
                TextButton(onClick = vm::abandonWorkout) { Text("End workout") }
            },
            dismissButton = {
                TextButton(onClick = { showExit = false }) { Text("Keep training") }
            }
        )
    }

    val progress = if (vm.restTotal <= 0) 0f
    else vm.restRemaining.toFloat() / vm.restTotal.toFloat()

    val pulseTransition = rememberInfiniteTransition(label = "restPulse")
    val pulse by pulseTransition.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(950, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { showExit = true }) {
                Icon(Icons.Rounded.Close, "End workout")
            }
            Spacer(Modifier.weight(1f))
            Text("REST", color = Accent, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.size(48.dp))
        }

        Spacer(Modifier.weight(0.55f))

        Box(
            modifier = Modifier
                .size(280.dp)
                .scale(pulse),
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 13.dp.toPx()
                drawArc(
                    color = SurfaceSoft,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(stroke, cap = StrokeCap.Round)
                )
                drawArc(
                    brush = Brush.sweepGradient(listOf(Accent, Accent2, Cyan, Accent)),
                    startAngle = -90f,
                    sweepAngle = 360f * progress.coerceIn(0f, 1f),
                    useCenter = false,
                    style = Stroke(stroke, cap = StrokeCap.Round)
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    formatSeconds(vm.restRemaining),
                    fontSize = 66.sp,
                    fontWeight = FontWeight.Black,
                    color = Ink
                )
                Text("RECOVER", color = Muted, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
            }
        }

        Spacer(Modifier.height(28.dp))
        Text("NEXT", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text(
            vm.nextTarget?.exercise?.name ?: "Workout complete",
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )
        vm.nextTarget?.let {
            Text(
                "Set " + it.displaySet.toString() + " of " + it.totalSets.toString(),
                color = Accent,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.weight(1f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            RestButton("-15", Modifier.weight(1f)) { vm.adjustRest(-15) }
            Button(
                onClick = vm::skipRest,
                modifier = Modifier.weight(1.35f).height(60.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Ink)
            ) {
                Icon(Icons.Rounded.SkipNext, null)
                Spacer(Modifier.width(6.dp))
                Text("SKIP", fontWeight = FontWeight.Black)
            }
            RestButton("+15", Modifier.weight(1f)) { vm.adjustRest(15) }
        }
    }
}

@Composable
private fun RestButton(label: String, modifier: Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(60.dp),
        shape = RoundedCornerShape(20.dp)
    ) {
        Text(label, fontWeight = FontWeight.Black, fontSize = 16.sp)
    }
}

@Composable
private fun SummaryScreen(vm: TrainingViewModel) {
    val summary = vm.summary ?: return
    BackHandler { vm.goHome() }

    val transition = rememberInfiniteTransition(label = "summary")
    val pulse by transition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            tween(850, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "summaryPulse"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(0.7f))
        Box(
            modifier = Modifier
                .size(112.dp)
                .scale(pulse)
                .background(
                    Brush.linearGradient(listOf(Accent, Accent2)),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(58.dp))
        }
        Spacer(Modifier.height(28.dp))
        Text("SESSION COMPLETE", color = Accent, fontWeight = FontWeight.Black, letterSpacing = 1.1.sp)
        Text(summary.planName, style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(28.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Surface)
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricTile("Sets", summary.totalSets.toString(), Modifier.weight(1f))
                    MetricTile("Reps", summary.totalReps.toString(), Modifier.weight(1f))
                    MetricTile("PRs", summary.prCount.toString(), Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricTile("Volume", formatKg(summary.totalVolume), Modifier.weight(1f))
                    MetricTile("Time", formatDuration(summary.durationMillis), Modifier.weight(1f))
                }
            }
        }

        Spacer(Modifier.weight(1f))

        Button(
            onClick = vm::openAnalytics,
            modifier = Modifier.fillMaxWidth().height(58.dp),
            shape = RoundedCornerShape(20.dp)
        ) {
            Icon(Icons.Rounded.Analytics, null)
            Spacer(Modifier.width(8.dp))
            Text("VIEW ANALYTICS", fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = vm::goHome,
            modifier = Modifier.fillMaxWidth().height(58.dp),
            shape = RoundedCornerShape(20.dp)
        ) {
            Text("DONE", fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun AnalyticsScreen(vm: TrainingViewModel) {
    BackHandler { vm.goHome() }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = vm::goHome) {
                    Icon(Icons.Rounded.ArrowBack, "Back")
                }
                Spacer(Modifier.width(4.dp))
                Column {
                    Text("Analytics", style = MaterialTheme.typography.headlineMedium)
                    Text("Training history & progression", color = Muted)
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricTile("Sessions", vm.analytics.sessionCount.toString(), Modifier.weight(1f))
                MetricTile("Sets", vm.analytics.totalSets.toString(), Modifier.weight(1f))
                MetricTile("PRs", vm.analytics.totalPrs.toString(), Modifier.weight(1f))
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricTile("Volume", formatKg(vm.analytics.totalVolume), Modifier.weight(1f))
                MetricTile("Best e1RM", formatKg(vm.analytics.bestE1rm), Modifier.weight(1f))
            }
        }

        item {
            SectionCard(title = "8-week volume") {
                WeeklyVolumeChart(vm.analytics.weeklyVolume)
            }
        }

        item {
            Text("Exercise progression", style = MaterialTheme.typography.titleLarge)
        }

        if (vm.analytics.exercises.isEmpty()) {
            item {
                EmptyState("Complete a workout to populate exercise analytics.")
            }
        } else {
            items(vm.analytics.exercises) { exercise ->
                ExerciseAnalyticsCard(exercise)
            }
        }

        item {
            Text("Recent sessions", style = MaterialTheme.typography.titleLarge)
        }

        if (vm.analytics.recentSessions.isEmpty()) {
            item { EmptyState("No completed sessions yet.") }
        } else {
            items(vm.analytics.recentSessions) { session ->
                RecentSessionCard(session)
            }
        }

        item { Spacer(Modifier.height(22.dp)) }
    }
}

@Composable
private fun MetricTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .background(Surface, RoundedCornerShape(20.dp))
            .border(1.dp, Line, RoundedCornerShape(20.dp))
            .padding(15.dp)
    ) {
        Text(value, color = Ink, fontWeight = FontWeight.Black, fontSize = 19.sp)
        Text(label, color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = Surface)
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(title, fontWeight = FontWeight.Black, fontSize = 17.sp)
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
private fun WeeklyVolumeChart(weeks: List<WeeklyVolume>) {
    val maxVolume = weeks.maxOfOrNull { it.volume }?.coerceAtLeast(1.0) ?: 1.0
    Column {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(155.dp)
        ) {
            if (weeks.isEmpty()) return@Canvas
            val gap = 8.dp.toPx()
            val barWidth = (size.width - gap * (weeks.size - 1)) / weeks.size
            weeks.forEachIndexed { index, item ->
                val ratio = (item.volume / maxVolume).toFloat().coerceIn(0f, 1f)
                val barHeight = max(5f, size.height * ratio)
                drawRoundRect(
                    brush = Brush.verticalGradient(listOf(Accent2, Accent)),
                    topLeft = Offset(index * (barWidth + gap), size.height - barHeight),
                    size = Size(barWidth, barHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx())
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            weeks.forEach {
                Text(it.label, color = Muted, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun ExerciseAnalyticsCard(exercise: ExerciseAnalytics) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Surface)
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(exercise.exerciseName, fontWeight = FontWeight.Black, fontSize = 17.sp)
                    val last = if (exercise.lastWeight != null && exercise.lastReps != null)
                        "Last " + trimNumber(exercise.lastWeight) + " kg × " + exercise.lastReps
                    else "No recent set"
                    Text(last, color = Muted, fontSize = 12.sp)
                }
                exercise.recentTrendPercent?.let { trend ->
                    val up = trend >= 0
                    Row(
                        modifier = Modifier
                            .background(
                                (if (up) Success else MaterialTheme.colorScheme.error).copy(alpha = 0.08f),
                                RoundedCornerShape(999.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (up) Icons.Rounded.TrendingUp else Icons.Rounded.TrendingDown,
                            null,
                            tint = if (up) Success else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            (if (trend >= 0) "+" else "") + String.format(Locale.UK, "%.1f%%", trend),
                            color = if (up) Success else MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallStat("Max", formatKg(exercise.maxWeight), Modifier.weight(1f))
                SmallStat("e1RM", formatKg(exercise.bestE1rm), Modifier.weight(1f))
                SmallStat("Sets", exercise.totalSets.toString(), Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Lifetime volume " + formatKg(exercise.totalVolume),
                color = Muted,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun SmallStat(label: String, value: String, modifier: Modifier) {
    Column(
        modifier
            .background(SurfaceSoft, RoundedCornerShape(14.dp))
            .padding(10.dp)
    ) {
        Text(value, fontWeight = FontWeight.Black, fontSize = 14.sp)
        Text(label, color = Muted, fontSize = 10.sp)
    }
}

@Composable
private fun RecentSessionCard(session: RecentSession) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Surface)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(Accent.copy(alpha = 0.09f), RoundedCornerShape(15.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.FitnessCenter, null, tint = Accent)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(session.planName, fontWeight = FontWeight.Black)
                Text(
                    formatDate(session.completedAt) + " · " + formatDuration(session.durationMillis),
                    color = Muted,
                    fontSize = 12.sp
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(formatKg(session.totalVolume), fontWeight = FontWeight.Black)
                Text(
                    session.totalSets.toString() + " sets · " + session.prCount.toString() + " PR",
                    color = Muted,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun EmptyState(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Surface, RoundedCornerShape(22.dp))
            .border(1.dp, Line, RoundedCornerShape(22.dp))
            .padding(20.dp)
    ) {
        Text(text, color = Muted)
    }
}

@Composable
private fun SettingsScreen(vm: TrainingViewModel) {
    BackHandler { vm.goHome() }
    var local by remember { mutableStateOf(vm.settings) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = vm::goHome) {
                Icon(Icons.Rounded.ArrowBack, "Back")
            }
            Spacer(Modifier.width(4.dp))
            Column {
                Text("Settings", style = MaterialTheme.typography.headlineMedium)
                Text("Rest, sound and feedback", color = Muted)
            }
        }

        Spacer(Modifier.height(24.dp))

        SettingSlider(
            title = "Compound rest",
            seconds = local.compoundRestSec,
            range = 60f..300f,
            onChange = {
                local = local.copy(compoundRestSec = it)
                vm.updateSettings(local)
            }
        )
        SettingSlider(
            title = "Accessory rest",
            seconds = local.accessoryRestSec,
            range = 30f..180f,
            onChange = {
                local = local.copy(accessoryRestSec = it)
                vm.updateSettings(local)
            }
        )
        SettingSlider(
            title = "Superset rest",
            seconds = local.supersetRestSec,
            range = 30f..240f,
            onChange = {
                local = local.copy(supersetRestSec = it)
                vm.updateSettings(local)
            }
        )

        Spacer(Modifier.height(16.dp))

        ToggleRow(
            title = "Sound cues",
            subtitle = "Countdown, PR and completion tones",
            checked = local.soundEnabled,
            onChecked = {
                local = local.copy(soundEnabled = it)
                vm.updateSettings(local)
            }
        )
        Spacer(Modifier.height(10.dp))
        ToggleRow(
            title = "Haptics",
            subtitle = "Stored preference for tactile feedback",
            checked = local.hapticsEnabled,
            onChecked = {
                local = local.copy(hapticsEnabled = it)
                vm.updateSettings(local)
            }
        )

        Spacer(Modifier.weight(1f))
        Text(
            "Lower Training 1.1.0 · Local SQLite storage · Offline",
            color = Muted,
            fontSize = 12.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}

@Composable
private fun SettingSlider(
    title: String,
    seconds: Int,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Int) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Surface)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Timer, null, tint = Accent)
                Spacer(Modifier.width(10.dp))
                Text(title, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                Text(formatSeconds(seconds), color = Accent, fontWeight = FontWeight.Black)
            }
            Slider(
                value = seconds.toFloat(),
                onValueChange = { onChange((it / 15f).roundToInt() * 15) },
                valueRange = range,
            )
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Surface, RoundedCornerShape(22.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Black)
            Text(subtitle, color = Muted, fontSize = 12.sp)
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

private fun formatSeconds(seconds: Int): String {
    val safe = max(0, seconds)
    return String.format(Locale.UK, "%d:%02d", safe / 60, safe % 60)
}

private fun trimNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString()
    else String.format(Locale.UK, "%.1f", value)

private fun formatKg(value: Double): String {
    if (value >= 1000) return NumberFormat.getIntegerInstance(Locale.UK).format(value.roundToInt()) + " kg"
    return trimNumber(value) + " kg"
}

private fun formatDuration(ms: Long): String {
    val minutes = ms / 60000L
    return if (minutes < 60) minutes.toString() + " min"
    else (minutes / 60).toString() + "h " + (minutes % 60).toString() + "m"
}

private fun formatDate(timestamp: Long): String =
    SimpleDateFormat("dd MMM", Locale.UK).format(Date(timestamp))
