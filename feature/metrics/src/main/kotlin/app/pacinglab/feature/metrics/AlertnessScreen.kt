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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.pacinglab.core.analytics.alertness.Alertness
import app.pacinglab.core.analytics.alertness.AlertnessCalibration
import app.pacinglab.core.analytics.alertness.AlertnessHistory
import app.pacinglab.core.analytics.alertness.ScoredTest
import app.pacinglab.core.data.repository.AlertnessRepository
import app.pacinglab.core.designsystem.chart.ChartLevel
import app.pacinglab.core.designsystem.chart.HistoryChart
import app.pacinglab.core.designsystem.component.AdvancedInfo
import app.pacinglab.core.designsystem.component.InfoText
import app.pacinglab.core.designsystem.icon.PacingLabIcons
import app.pacinglab.core.designsystem.theme.StatusColors
import app.pacinglab.core.designsystem.theme.chartColors
import app.pacinglab.core.designsystem.theme.tabularNumbers
import app.pacinglab.core.ui.AlertnessSummary
import app.pacinglab.core.ui.formatAlertness
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToInt
import app.pacinglab.core.ui.R as UiR

@HiltViewModel
class AlertnessViewModel @Inject constructor(private val alertness: AlertnessRepository) : ViewModel() {
    val state: StateFlow<AlertnessHistory?> =
        alertness.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    fun delete(start: Instant) {
        viewModelScope.launch { alertness.delete(start) }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

@Composable
fun AlertnessRoute(onBack: () -> Unit, onTest: () -> Unit, modifier: Modifier = Modifier, viewModel: AlertnessViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AlertnessScreen(state, onBack, onTest, onDelete = viewModel::delete, modifier = modifier)
}

/**
 * Alertness in depth: the selected day (today at first) with its curve, the scores of the last
 * [Alertness.DAYS] days on the bands of the three levels (tap one to show it), what the reaction tests
 * have taught the model, every test with what the model expected of it, and how it all works.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertnessScreen(
    state: AlertnessHistory?,
    onBack: () -> Unit,
    onTest: () -> Unit,
    onDelete: (Instant) -> Unit,
    modifier: Modifier = Modifier,
    zoneId: ZoneId = ZoneId.systemDefault(),
) {
    val locale = LocalConfiguration.current.locales[0]
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(UiR.string.alertness_title)) },
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
        val period = Period(Span.MONTH, state.today)
        var selected by rememberSaveable { mutableStateOf<Int?>(null) }
        val date = selected?.let { period.days[it] } ?: state.today
        var deleting by remember { mutableStateOf<ScoredTest?>(null) }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "day") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(dayLabel(date, state.today, locale), style = MaterialTheme.typography.titleMedium)
                    AlertnessSummary(state, date, zoneId)
                }
            }
            item(key = "history") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.alertness_page_history), style = MaterialTheme.typography.titleMedium)
                    HistoryChart(
                        values = period.days.map { state.days[it]?.score },
                        color = chartColors().series[0],
                        description = stringResource(R.string.alertness_page_history_chart),
                        levels = levels(),
                        scale = SCALE,
                        labels = period.axisLabels(locale),
                        selected = selected ?: period.days.indexOfLast { it in state.days }.takeIf { it >= 0 },
                        onSelect = { selected = it },
                        format = { it.roundToInt().toString() },
                        height = 160.dp,
                    )
                }
            }
            item(key = "calibration") { Calibration(state, onTest) }
            items(state.tests.reversed(), key = { it.test.start.toEpochMilli() }) { test ->
                TestRow(test, zoneId, locale, onDelete = { deleting = test })
            }
            item(key = "about") {
                Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.metric_about_title, stringResource(UiR.string.alertness_title)),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    InfoText(stringResource(UiR.string.alertness_info))
                    AdvancedInfo(stringResource(UiR.string.alertness_advanced))
                }
            }
        }
        deleting?.let { test ->
            AlertDialog(
                onDismissRequest = { deleting = null },
                title = { Text(stringResource(R.string.alertness_page_delete_title)) },
                text = { Text(stringResource(R.string.alertness_page_delete_text)) },
                confirmButton = {
                    TextButton(onClick = {
                        onDelete(test.test.start)
                        deleting = null
                    }) { Text(stringResource(R.string.alertness_page_delete_confirm)) }
                },
                dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.alertness_page_cancel)) } },
            )
        }
    }
}

/** What the tests have taught the model so far, and the button to take one, always offered here. */
@Composable
private fun Calibration(state: AlertnessHistory, onTest: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val calibration = state.calibration
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.alertness_page_tests), style = MaterialTheme.typography.titleMedium)
        val lines = buildList {
            if (calibration.tests == 0) {
                add(stringResource(R.string.alertness_page_no_tests))
            } else if (calibration.learning) {
                add(
                    pluralStringResource(
                        UiR.plurals.alertness_learning,
                        AlertnessCalibration.MIN_TESTS,
                        calibration.tests,
                        AlertnessCalibration.MIN_TESTS,
                    ),
                )
                add(stringResource(R.string.alertness_page_sensitivity_learning))
            } else {
                add(pluralStringResource(UiR.plurals.alertness_tuned, calibration.tests, calibration.tests))
                add(stringResource(sensitivity(calibration)))
            }
            val error = calibration.error
            val average = calibration.averageError
            if (error != null && average != null) {
                val text = if (calibration.follows == true) R.string.alertness_page_follows else R.string.alertness_page_not_following
                add(stringResource(text, percent(error, locale), percent(average, locale)))
            }
        }
        for (line in lines) Text(line, style = MaterialTheme.typography.bodyMedium)
        Button(onClick = onTest) { Text(stringResource(UiR.string.alertness_take_test)) }
    }
}

/** How strongly sleep loss slows the athlete against the population, from the middle 80 % of the estimate. */
private fun sensitivity(calibration: AlertnessCalibration): Int {
    val margin = Z80 * calibration.sensitivitySd
    return when {
        calibration.sensitivity - margin > AlertnessCalibration.PRIOR_SENSITIVITY -> R.string.alertness_page_sensitivity_more
        calibration.sensitivity + margin < AlertnessCalibration.PRIOR_SENSITIVITY -> R.string.alertness_page_sensitivity_less
        else -> R.string.alertness_page_sensitivity_average
    }
}

@Composable
private fun TestRow(test: ScoredTest, zone: ZoneId, locale: Locale, onDelete: () -> Unit) {
    val time = remember(zone, locale) { DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", locale).withZone(zone) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(time.format(test.test.start), style = MaterialTheme.typography.bodyMedium)
            Text(
                listOf(
                    stringResource(R.string.reaction_ms, test.result.millis.roundToInt()),
                    pluralStringResource(R.plurals.reaction_lapses, test.result.lapses, test.result.lapses),
                    pluralStringResource(R.plurals.reaction_false_starts, test.result.falseStarts, test.result.falseStarts),
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall.tabularNumbers(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val measured = test.measured
            val expected = test.expected
            if (measured != null && expected != null) {
                Text(
                    stringResource(
                        R.string.alertness_page_test_measured,
                        formatAlertness(measured, locale),
                        formatAlertness(expected, locale),
                    ),
                    style = MaterialTheme.typography.bodySmall.tabularNumbers(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onDelete) { Icon(painterResource(PacingLabIcons.Delete), stringResource(R.string.alertness_page_delete)) }
    }
}

@Composable
private fun levels() = listOf(
    ChartLevel(SCALE.start..Alertness.REDUCED_FROM, StatusColors.Serious, stringResource(UiR.string.alertness_level_low)),
    ChartLevel(Alertness.REDUCED_FROM..Alertness.ALERT_FROM, StatusColors.Warning, stringResource(UiR.string.alertness_level_reduced)),
    ChartLevel(Alertness.ALERT_FROM..SCALE.endInclusive, StatusColors.Good, stringResource(UiR.string.alertness_level_alert)),
)

@Composable
private fun dayLabel(date: LocalDate, today: LocalDate, locale: Locale): String = when (date) {
    today -> stringResource(R.string.alertness_page_today)
    today.minusDays(1) -> stringResource(R.string.alertness_page_yesterday)
    else -> DateTimeFormatter.ofPattern("EEEE d MMMM", locale).format(date).replaceFirstChar { it.titlecase(locale) }
}

/** An error in log speed as the share of speed it is about, to one decimal. */
private fun percent(value: Double, locale: Locale): String = String.format(locale, "%.1f %%", 100 * value)

private val SCALE = 85.0..105.0

/** The 90th percentile of the normal distribution: the edge of the middle 80 %. */
private const val Z80 = 1.2816
