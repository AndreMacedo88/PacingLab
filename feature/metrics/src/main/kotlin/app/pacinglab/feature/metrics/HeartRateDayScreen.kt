package app.pacinglab.feature.metrics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import app.pacinglab.core.analytics.zones.TimeInZones
import app.pacinglab.core.analytics.zones.Zones
import app.pacinglab.core.data.repository.DayHeartRate
import app.pacinglab.core.data.repository.DayHeartRateRepository
import app.pacinglab.core.data.repository.NightRepository
import app.pacinglab.core.data.repository.TrainingLoadRepository
import app.pacinglab.core.designsystem.chart.HeartRateChart
import app.pacinglab.core.designsystem.component.ZoneSwatch
import app.pacinglab.core.designsystem.component.zoneName
import app.pacinglab.core.designsystem.component.zoneNumber
import app.pacinglab.core.designsystem.format.Formats
import app.pacinglab.core.designsystem.icon.PacingLabIcons
import app.pacinglab.core.designsystem.theme.tabularNumbers
import app.pacinglab.core.designsystem.theme.zoneColors
import app.pacinglab.core.model.ZoneModel
import app.pacinglab.core.model.ZoneSet
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * One day's heart rate.
 *
 * @property restingHr the watch's resting heart rate for the night that ended on that day.
 */
data class HeartRateDayState(val day: DayHeartRate, val zones: ZoneSet?, val restingHr: Double?, val today: LocalDate)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HeartRateDayViewModel @Inject constructor(
    private val repository: DayHeartRateRepository,
    trainingLoad: TrainingLoadRepository,
    nights: NightRepository,
    private val clock: Clock,
) : ViewModel() {
    private val date = MutableStateFlow(LocalDate.now(clock))

    val state: StateFlow<HeartRateDayState?> = combine(date, trainingLoad.anchors, nights.observeSleep()) { day, anchors, sleep ->
        Triple(day, anchors.at(day), sleep.nights.firstOrNull { it.date == day }?.restingHr)
    }.mapLatest { (day, anchors, resting) ->
        HeartRateDayState(repository.read(day), anchors?.let { Zones.of(ZoneModel.LTHR_FIVE, it) }, resting, LocalDate.now(clock))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    fun show(day: LocalDate) {
        date.value = minOf(day, LocalDate.now(clock))
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

@Composable
fun HeartRateDayRoute(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: HeartRateDayViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HeartRateDayScreen(state, onBack, onDay = viewModel::show, modifier = modifier)
}

/**
 * A day of heart rate: its range, average and the night's resting heart rate, the day coloured by zone
 * with a touch readout, and the time spent in each zone. The arrows step through days.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeartRateDayScreen(state: HeartRateDayState?, onBack: () -> Unit, onDay: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.heart_rate_title)) },
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
        val day = state.day
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "day") {
                PeriodStepper(
                    label = if (day.date == state.today) {
                        stringResource(R.string.heart_rate_today)
                    } else {
                        DateTimeFormatter.ofPattern("EEE d MMM", locale).format(day.date)
                    },
                    onPrevious = { onDay(day.date.minusDays(1)) },
                    onNext = if (day.date < state.today) ({ onDay(day.date.plusDays(1)) }) else null,
                )
            }
            if (day.series.isEmpty()) {
                item(key = "none") { Text(stringResource(R.string.heart_rate_none), style = MaterialTheme.typography.bodyLarge) }
            } else {
                item(key = "stats") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Stat(
                            stringResource(R.string.metric_lowest),
                            stringResource(R.string.unit_bpm, day.lowest.toString()),
                            Modifier.weight(1f),
                        )
                        Stat(
                            stringResource(R.string.metric_average),
                            stringResource(R.string.unit_bpm, day.average.toString()),
                            Modifier.weight(1f),
                        )
                        Stat(
                            stringResource(R.string.metric_highest),
                            stringResource(R.string.unit_bpm, day.highest.toString()),
                            Modifier.weight(1f),
                        )
                        state.restingHr?.let {
                            Stat(
                                stringResource(R.string.heart_rate_resting),
                                stringResource(R.string.unit_bpm, it.toInt().toString()),
                                Modifier.weight(1f),
                            )
                        }
                    }
                }
                state.zones?.let { zones ->
                    item(key = "chart") {
                        HeartRateChart(
                            heartRate = day.series,
                            zones = zones,
                            zoneId = day.zone,
                            description = stringResource(R.string.heart_rate_chart, day.lowest ?: 0, day.highest ?: 0),
                            height = 220.dp,
                        )
                    }
                    item(key = "zones") { ZoneTimes(day, zones) }
                }
            }
            item(key = "about") {
                About(
                    stringResource(R.string.about_title, stringResource(R.string.heart_rate_title)),
                    stringResource(R.string.about_heart_rate),
                )
            }
        }
    }
}

@Composable
private fun ZoneTimes(day: DayHeartRate, zones: ZoneSet, modifier: Modifier = Modifier) {
    val times = TimeInZones.compute(day.series, zones)
    val fills = zoneColors(zones.size)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.heart_rate_zones), style = MaterialTheme.typography.titleSmall)
        for (i in zones.size - 1 downTo 0) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ZoneSwatch(fills[i])
                Text(zoneNumber(i), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(28.dp))
                Text(stringResource(zones.model.zoneName(i)), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(Formats.hoursMinutes(times.millis[i]), style = MaterialTheme.typography.bodyMedium.tabularNumbers())
            }
        }
    }
}
