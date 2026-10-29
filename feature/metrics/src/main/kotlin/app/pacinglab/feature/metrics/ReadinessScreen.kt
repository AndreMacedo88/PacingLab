package app.pacinglab.feature.metrics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.pacinglab.core.analytics.load.FormDay
import app.pacinglab.core.analytics.recovery.Night
import app.pacinglab.core.analytics.recovery.Readiness
import app.pacinglab.core.analytics.recovery.ReadinessLevel
import app.pacinglab.core.analytics.recovery.SleepScore
import app.pacinglab.core.analytics.recovery.Standing
import app.pacinglab.core.data.repository.NightRepository
import app.pacinglab.core.data.repository.TrainingLoadRepository
import app.pacinglab.core.designsystem.chart.ChartLevel
import app.pacinglab.core.designsystem.chart.HistoryChart
import app.pacinglab.core.designsystem.component.RangePill
import app.pacinglab.core.designsystem.component.StatusPill
import app.pacinglab.core.designsystem.icon.PacingLabIcons
import app.pacinglab.core.designsystem.theme.StatusColors
import app.pacinglab.core.designsystem.theme.tabularNumbers
import app.pacinglab.core.model.RangeStanding
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalDate
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToInt

/** Everything readiness is computed from; the page works out each day of the period itself. */
data class ReadinessState(
    val nights: List<Night>,
    val scores: Map<LocalDate, SleepScore>,
    val form: Map<LocalDate, FormDay>,
    val today: LocalDate,
) {
    fun on(date: LocalDate): Readiness = Readiness.of(nights, form[date], date, scores)
}

@HiltViewModel
class ReadinessViewModel @Inject constructor(nights: NightRepository, trainingLoad: TrainingLoadRepository, clock: Clock) : ViewModel() {
    val state: StateFlow<ReadinessState?> = combine(nights.observeSleep(), trainingLoad.observeFitness()) { sleep, form ->
        ReadinessState(sleep.nights, sleep.scores, form.associateBy { it.date }, LocalDate.now(clock))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

@Composable
fun ReadinessRoute(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: ReadinessViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ReadinessScreen(state, onBack, modifier)
}

/**
 * Readiness over time: each day's score on the bands of the three levels, the selected day's score and
 * what it rested on, and how the score is worked out.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadinessScreen(state: ReadinessState?, onBack: () -> Unit, modifier: Modifier = Modifier, initialSpan: Span = Span.WEEK) {
    val locale = LocalConfiguration.current.locales[0]
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.readiness_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(painterResource(PacingLabIcons.ArrowBack), stringResource(R.string.navigate_back)) }
                },
            )
        },
    ) { padding ->
        if (state == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        var span by rememberSaveable { mutableIntStateOf(initialSpan.ordinal) }
        var end by rememberSaveable { mutableLongStateOf(state.today.toEpochDay()) }
        val period = Period(Span.entries[span], LocalDate.ofEpochDay(end))
        var selected by rememberSaveable(span, end) { mutableStateOf<Int?>(null) }
        val days = remember(state, period) { period.days.map(state::on) }
        val scores = days.map { it.score?.toDouble() }
        val shown = selected ?: scores.indexOfLast { it != null }.takeIf { it >= 0 }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "period") {
                PeriodControls(period, state.today, onPeriod = {
                    span = it.span.ordinal
                    end = it.end.toEpochDay()
                }, locale = locale)
            }
            item(key = "readout") { ReadinessReadout(shown?.let { days[it] }, state.today, locale) }
            item(key = "chart") {
                HistoryChart(
                    values = scores,
                    color = MaterialTheme.colorScheme.primary,
                    description = stringResource(R.string.readiness_chart_description, period.label(locale)),
                    levels = levels(),
                    scale = 0.0..100.0,
                    labels = period.axisLabels(locale),
                    selected = shown,
                    onSelect = { selected = it },
                )
            }
            shown?.let { index -> item(key = "contributors") { Contributors(days[index]) } }
            item(key = "about") {
                About(
                    stringResource(R.string.metric_about_title, stringResource(R.string.readiness_title)),
                    stringResource(R.string.about_readiness),
                )
            }
        }
    }
}

@Composable
private fun levels() = listOf(
    ChartLevel(0.0..Readiness.MODERATE_FROM.toDouble(), StatusColors.Serious, stringResource(R.string.readiness_detail_level_low)),
    ChartLevel(
        Readiness.MODERATE_FROM.toDouble()..Readiness.READY_FROM.toDouble(),
        StatusColors.Warning,
        stringResource(R.string.readiness_detail_level_moderate),
    ),
    ChartLevel(Readiness.READY_FROM.toDouble()..100.0, StatusColors.Good, stringResource(R.string.readiness_level_ready)),
)

@Composable
private fun ReadinessReadout(readiness: Readiness?, today: LocalDate, locale: Locale, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val score = readiness?.score
        val level = readiness?.level
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(score?.toString() ?: DASH, style = MaterialTheme.typography.displaySmall.tabularNumbers())
            if (level != null) LevelPill(level)
        }
        if (readiness != null) {
            Text(
                nightLabel(readiness.date, today, locale),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        when {
            readiness?.score == null -> Text(stringResource(R.string.readiness_none), style = MaterialTheme.typography.bodySmall)

            readiness.provisional -> Text(
                stringResource(R.string.readiness_provisional),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LevelPill(level: ReadinessLevel) {
    when (level) {
        ReadinessLevel.READY -> StatusPill(stringResource(R.string.readiness_level_ready), StatusColors.Good, PacingLabIcons.CheckCircle)

        ReadinessLevel.MODERATE -> StatusPill(
            stringResource(R.string.readiness_detail_level_moderate),
            StatusColors.Warning,
            PacingLabIcons.Info,
        )

        ReadinessLevel.LOW -> StatusPill(stringResource(R.string.readiness_detail_level_low), StatusColors.Serious, PacingLabIcons.Warning)
    }
}

/** Each contributor's own score on the selected day, with its share of the total and where it stood. */
@Composable
private fun Contributors(readiness: Readiness, modifier: Modifier = Modifier) {
    val rows = listOfNotNull(
        readiness.hrv?.let { Triple(R.string.readiness_detail_hrv, Readiness.HRV_WEIGHT, it.score to it.standing) },
        readiness.sleep?.let { Triple(R.string.readiness_sleep, Readiness.SLEEP_WEIGHT, it.score to it.standing) },
        readiness.restingHr?.let { Triple(R.string.readiness_resting, Readiness.RESTING_HR_WEIGHT, it.score to it.standing) },
        readiness.training?.let { Triple(R.string.readiness_detail_training, Readiness.TRAINING_WEIGHT, it.score to it.standing) },
    )
    if (rows.isEmpty()) return
    Column(modifier) {
        Text(
            stringResource(R.string.readiness_contributors),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        for ((name, weight, scored) in rows) {
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(name), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.readiness_weight, (weight * 100).roundToInt()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StandingPill(scored.second, Modifier.padding(end = 12.dp))
                Text(scored.first.toString(), style = MaterialTheme.typography.titleMedium.tabularNumbers())
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        if (readiness.breathingElevated) {
            StatusPill(
                stringResource(R.string.readiness_detail_breathing_raised),
                StatusColors.Serious,
                PacingLabIcons.Warning,
                Modifier.padding(top = 12.dp),
            )
        }
    }
}

@Composable
private fun StandingPill(standing: Standing, modifier: Modifier = Modifier) {
    RangePill(
        when (standing) {
            Standing.BELOW -> RangeStanding.BELOW
            Standing.NORMAL -> RangeStanding.IN_RANGE
            Standing.ABOVE -> RangeStanding.ABOVE
        },
        modifier,
    )
}
