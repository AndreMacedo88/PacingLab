package app.pacinglab.feature.metrics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.pacinglab.core.analytics.alertness.AlertnessHistory
import app.pacinglab.core.analytics.alertness.ReactionResult
import app.pacinglab.core.analytics.alertness.ReactionTestRun
import app.pacinglab.core.analytics.alertness.ReactionTestRun.State
import app.pacinglab.core.data.repository.AlertnessRepository
import app.pacinglab.core.designsystem.icon.PacingLabIcons
import app.pacinglab.core.designsystem.theme.tabularNumbers
import app.pacinglab.core.model.ReactionTest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.roundToInt

@HiltViewModel
class ReactionTestViewModel @Inject constructor(private val alertness: AlertnessRepository, private val clock: Clock) : ViewModel() {
    val history: StateFlow<AlertnessHistory?> = alertness.observe().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        null,
    )

    /** Starts a test now: its start time and the clock's offset then. */
    fun begin(): Pair<Instant, ZoneOffset> = clock.instant().let { it to clock.zone.rules.getOffset(it) }

    fun save(test: ReactionTest) {
        viewModelScope.launch { alertness.save(test) }
    }

    fun discard(start: Instant) {
        viewModelScope.launch { alertness.delete(start) }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

@Composable
fun ReactionTestRoute(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: ReactionTestViewModel = hiltViewModel()) {
    val history by viewModel.history.collectAsStateWithLifecycle()
    ReactionTestScreen(
        history = history,
        onBack = onBack,
        onBegin = viewModel::begin,
        onFinish = viewModel::save,
        onDiscard = {
            viewModel.discard(it)
            onBack()
        },
        modifier = modifier,
    )
}

/** Where the screen is: the instructions, the test running, stopped part way, or done. */
sealed interface TestPhase {
    data object Intro : TestPhase

    data class Running(val attempt: Int) : TestPhase

    data object Stopped : TestPhase

    data class Done(val test: ReactionTest) : TestPhase
}

/**
 * The 3-minute reaction test ([ReactionTestRun]): instructions, then three minutes of tapping as soon as the
 * counter in the box starts, anywhere on the screen. Leaving the screen stops it, since only a full test
 * counts. A finished test is saved straight away and its result shown against what the model expected.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReactionTestScreen(
    history: AlertnessHistory?,
    onBack: () -> Unit,
    onBegin: () -> Pair<Instant, ZoneOffset?>,
    onFinish: (ReactionTest) -> Unit,
    onDiscard: (Instant) -> Unit,
    modifier: Modifier = Modifier,
    initialPhase: TestPhase = TestPhase.Intro,
) {
    var phase by remember { mutableStateOf(initialPhase) }
    var attempts by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) {
        if (phase is TestPhase.Running) phase = TestPhase.Stopped
    }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.reaction_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(painterResource(PacingLabIcons.ArrowBack), stringResource(R.string.navigate_back)) }
                },
            )
        },
    ) { padding ->
        val start = {
            attempts++
            phase = TestPhase.Running(attempts)
        }
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val current = phase) {
                TestPhase.Intro -> Message(stringResource(R.string.reaction_intro), stringResource(R.string.reaction_start), start)

                TestPhase.Stopped -> Message(
                    stringResource(R.string.reaction_stopped),
                    stringResource(R.string.reaction_start_again),
                    start,
                )

                is TestPhase.Running -> LiveTest(current.attempt, onBegin) { test ->
                    phase = TestPhase.Done(test)
                    if (ReactionResult.of(test) != null) onFinish(test)
                }

                is TestPhase.Done -> Result(current.test, history, onDone = onBack, onDiscard = { onDiscard(current.test.start) })
            }
        }
    }
}

@Composable
private fun Message(text: String, action: String, onAction: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
        Button(onClick = onAction) { Text(action) }
    }
}

/** Runs one test from the frame clock: a counter starts with the first frame that draws it, and taps carry their own time. */
@Composable
private fun LiveTest(attempt: Int, onBegin: () -> Pair<Instant, ZoneOffset?>, onFinish: (ReactionTest) -> Unit) {
    val run = remember(attempt) { ReactionTestRun() }
    var state by remember(attempt) { mutableStateOf<State?>(null) }
    var counter by remember(attempt) { mutableLongStateOf(0L) }
    var progress by remember(attempt) { mutableFloatStateOf(0f) }
    val finished by rememberUpdatedState(onFinish)
    val begin by rememberUpdatedState(onBegin)
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    LaunchedEffect(run) {
        val (start, offset) = begin()
        withFrameMillis { run.start(it) }
        while (true) {
            val shown = withFrameMillis { now ->
                run.frame(now).also {
                    state = it
                    progress = run.progress(now)
                    if (it is State.Stimulus) counter = now - it.onset
                }
            }
            if (shown == State.Finished) {
                run.result(start, offset)?.let(finished)
                break
            }
        }
    }
    TestArea(
        state = state,
        counter = counter,
        progress = progress,
        modifier = Modifier.pointerInput(run) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                run.tap(down.uptimeMillis)
                state = run.state
            }
        },
    )
}

/** The test as drawn: the time gone, and the box with the counter, the last response time or a warning. */
@Composable
internal fun TestArea(state: State?, counter: Long, progress: Float, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(), drawStopIndicator = {})
        Text(
            stringResource(R.string.reaction_tap_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            val shape = RoundedCornerShape(16.dp)
            val running = state is State.Stimulus
            val description = stringResource(R.string.reaction_box)
            Box(
                modifier = Modifier
                    .size(width = 240.dp, height = 120.dp)
                    .background(if (running) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface, shape)
                    .border(BorderStroke(2.dp, MaterialTheme.colorScheme.outline), shape)
                    .semantics { contentDescription = description },
                contentAlignment = Alignment.Center,
            ) {
                val text = when (state) {
                    is State.Stimulus -> counter.toString()

                    is State.Feedback -> if (state.millis >=
                        ReactionTest.TIMEOUT_MILLIS
                    ) {
                        stringResource(R.string.reaction_missed)
                    } else {
                        state.millis.toString()
                    }

                    is State.FalseStart -> stringResource(R.string.reaction_too_soon)

                    else -> ""
                }
                Text(
                    text,
                    style = MaterialTheme.typography.displayMedium.tabularNumbers(),
                    color = if (running) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun Result(test: ReactionTest, history: AlertnessHistory?, onDone: () -> Unit, onDiscard: () -> Unit) {
    val result = ReactionResult.of(test)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (result == null) {
            Text(stringResource(R.string.reaction_too_few), style = MaterialTheme.typography.bodyLarge)
            Button(onClick = onDone) { Text(stringResource(R.string.reaction_done)) }
            return@Column
        }
        Text(stringResource(R.string.reaction_result_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.reaction_ms, result.millis.roundToInt()),
            style = MaterialTheme.typography.displaySmall.tabularNumbers(),
        )
        Text(
            listOf(
                pluralStringResource(R.plurals.reaction_lapses, result.lapses, result.lapses),
                pluralStringResource(R.plurals.reaction_false_starts, result.falseStarts, result.falseStarts),
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium.tabularNumbers(),
        )
        val scored = history?.tests?.firstOrNull { it.test.start == test.start }
        val calibration = history?.calibration
        val rested = calibration?.restedSpeed
        val expected = scored?.expected
        val comparison = if (calibration != null && rested != null && expected != null && calibration.tests >= MIN_COMPARED) {
            val expectedSpeed = rested * expected / 100.0
            val millis = (1000.0 / expectedSpeed).roundToInt()
            val difference = ln(result.speed / expectedSpeed)
            when {
                abs(difference) <= calibration.noise -> stringResource(R.string.reaction_expected_same, millis)
                difference > 0 -> stringResource(R.string.reaction_expected_faster, millis)
                else -> stringResource(R.string.reaction_expected_slower, millis)
            }
        } else {
            stringResource(R.string.reaction_first)
        }
        Text(comparison, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            stringResource(R.string.reaction_lapse_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onDone) { Text(stringResource(R.string.reaction_done)) }
            OutlinedButton(onClick = onDiscard) { Text(stringResource(R.string.reaction_discard)) }
        }
        Box(Modifier.height(24.dp))
    }
}

/** Comparisons with the model start once a few tests have set the athlete's own speed. */
private const val MIN_COMPARED = 3
