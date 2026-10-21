package app.pacinglab.feature.sleep

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pacinglab.core.analytics.recovery.Night
import app.pacinglab.core.analytics.recovery.Nights
import app.pacinglab.core.analytics.recovery.Readiness
import app.pacinglab.core.analytics.recovery.SleepRating
import app.pacinglab.core.analytics.recovery.SleepScore
import app.pacinglab.core.designsystem.chart.MiniBars
import app.pacinglab.core.designsystem.chart.Sparkline
import app.pacinglab.core.designsystem.component.CardHeader
import app.pacinglab.core.designsystem.format.Formats
import app.pacinglab.core.designsystem.icon.PacingLabIcons
import app.pacinglab.core.designsystem.theme.chartColors
import app.pacinglab.core.designsystem.theme.tabularNumbers
import app.pacinglab.core.model.SleepStageType
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun SleepRoute(initialDate: String?, onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: SleepViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SleepScreen(state = state, initialDate = initialDate?.let(LocalDate::parse), onBack = onBack, modifier = modifier)
}

/**
 * One night in depth: the score and what makes it up, the stages over the night, the night's
 * measurements against the athlete's normal, the recent schedule and longer trends. Arrows step from
 * night to night.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepScreen(state: SleepUiState, onBack: () -> Unit, modifier: Modifier = Modifier, initialDate: LocalDate? = null) {
    val locale = LocalConfiguration.current.locales[0]
    val loaded = state as? SleepUiState.Loaded
    val nights = loaded?.nights.orEmpty()
    var selectedDay by rememberSaveable { mutableLongStateOf(initialDate?.toEpochDay() ?: Long.MAX_VALUE) }
    val index = nights.indexOfLast { it.date.toEpochDay() <= selectedDay }.takeIf { it >= 0 } ?: nights.lastIndex
    val night = nights.getOrNull(index)
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sleep_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(painterResource(PacingLabIcons.ArrowBack), stringResource(R.string.navigate_back)) }
                },
            )
        },
    ) { padding ->
        when {
            state is SleepUiState.Loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            loaded == null || night == null -> Text(
                text = stringResource(R.string.sleep_empty),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(padding).padding(24.dp),
            )

            else -> {
                val score = loaded.scores.getValue(night.date)
                val need = loaded.needs.getValue(night.date)
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item(key = "night") {
                        NightSelector(
                            night = night,
                            locale = locale,
                            onPrevious = nights.getOrNull(index - 1)?.let { { selectedDay = it.date.toEpochDay() } },
                            onNext = nights.getOrNull(index + 1)?.let { { selectedDay = it.date.toEpochDay() } },
                        )
                    }
                    item(key = "score") { ScoreCard(night, score, need) }
                    if (night.stages.isNotEmpty()) item(key = "stages") { StagesCard(night, locale) }
                    item(key = "body") { BodyCard(nights, night) }
                    item(key = "schedule") {
                        val recent = nights.filter { it.date > night.date.minusDays(SCHEDULE_NIGHTS) && it.date <= night.date }
                        ScheduleCard(recent, night.date, onSelect = { selectedDay = it.toEpochDay() }, locale = locale)
                    }
                    item(key = "trends") { TrendsCard(nights, loaded.scores, loaded.needs, night.date) }
                }
            }
        }
    }
}

@Composable
private fun NightSelector(night: Night, locale: Locale, onPrevious: (() -> Unit)?, onNext: (() -> Unit)?, modifier: Modifier = Modifier) {
    val date = remember(locale) { DateTimeFormatter.ofPattern("EEEE d MMMM", locale) }
    val time = remember(locale) { DateTimeFormatter.ofPattern("HH:mm", locale) }
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onPrevious?.invoke() }, enabled = onPrevious != null) {
            Icon(painterResource(PacingLabIcons.ChevronLeft), stringResource(R.string.sleep_previous))
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(date.format(night.date).replaceFirstChar { it.titlecase(locale) }, style = MaterialTheme.typography.titleMedium)
            Text(
                text = stringResource(
                    R.string.sleep_in_bed,
                    time.format(night.start.atZone(night.zone)),
                    time.format(night.end.atZone(night.zone)),
                ),
                style = MaterialTheme.typography.bodyMedium.tabularNumbers(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = { onNext?.invoke() }, enabled = onNext != null) {
            Icon(painterResource(PacingLabIcons.ChevronRight), stringResource(R.string.sleep_next))
        }
    }
}

@Composable
private fun ScoreCard(night: Night, score: SleepScore, needMillis: Long, modifier: Modifier = Modifier) {
    val c = night.continuity
    SleepCard(stringResource(R.string.sleep_score_title), stringResource(R.string.sleep_score_info), modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(score.score.toString(), style = MaterialTheme.typography.displaySmall.tabularNumbers())
            Column {
                Text(stringResource(score.rating.label()), style = MaterialTheme.typography.titleMedium)
                Text(
                    text = stringResource(
                        R.string.sleep_asleep_of_need,
                        Formats.hoursMinutes(night.asleepMillis),
                        Formats.hoursMinutes(needMillis),
                    ),
                    style = MaterialTheme.typography.bodyMedium.tabularNumbers(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Part(
            name = stringResource(R.string.sleep_part_duration),
            fraction = score.duration,
            points = SleepScore.DURATION_POINTS,
            detail = stringResource(
                R.string.sleep_detail_duration,
                Formats.hoursMinutes(night.asleepMillis),
                Formats.hoursMinutes(needMillis),
            ),
        )
        if (score.continuity != null && c != null) {
            Part(
                name = stringResource(R.string.sleep_part_continuity),
                fraction = score.continuity!!,
                points = SleepScore.CONTINUITY_POINTS,
                detail = stringResource(
                    R.string.sleep_detail_continuity,
                    (100.0 * night.asleepMillis / night.inBedMillis).roundToInt(),
                    (c.wakeAfterOnsetMillis / MINUTE).toInt(),
                    (c.latencyMillis / MINUTE).toInt(),
                    c.longAwakenings,
                ),
            )
        }
        score.regularity?.let { regularity ->
            Part(
                name = stringResource(R.string.sleep_part_regularity),
                fraction = regularity,
                points = SleepScore.REGULARITY_POINTS,
                detail = stringResource(R.string.sleep_detail_regularity, score.midpointShiftMinutes ?: 0),
            )
        }
        Part(
            name = stringResource(R.string.sleep_part_timing),
            fraction = score.timing,
            points = SleepScore.TIMING_POINTS,
            detail = stringResource(R.string.sleep_detail_timing, clock(score.midpointMinuteOfDay)),
        )
    }
}

/** One part of the score: its share of points as a bar, the points and what was measured. */
@Composable
private fun Part(name: String, fraction: Double, points: Double, detail: String, modifier: Modifier = Modifier) {
    val earned = (fraction * points).roundToInt()
    Column(
        modifier = modifier.clearAndSetSemantics { contentDescription = "$name: $earned of ${points.toInt()}. $detail" },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(96.dp))
            LinearProgressIndicator(
                progress = { fraction.toFloat() },
                color = chartColors().series[0],
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                drawStopIndicator = {},
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.sleep_points, earned, points.toInt()),
                style = MaterialTheme.typography.bodyMedium.tabularNumbers(),
                textAlign = TextAlign.End,
                modifier = Modifier.width(56.dp),
            )
        }
        Text(detail, style = MaterialTheme.typography.bodySmall.tabularNumbers(), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StagesCard(night: Night, locale: Locale, modifier: Modifier = Modifier) {
    val colors = chartColors()
    val names = HYPNOGRAM_ROWS.map { stringResource(it.label()) }
    SleepCard(stringResource(R.string.sleep_stages_title), stringResource(R.string.sleep_stages_info), modifier) {
        Hypnogram(night, names, stringResource(R.string.sleep_stages_description), locale)
        // Deep, REM, light, awake: the order the colours were checked in as neighbours.
        for (type in listOf(SleepStageType.DEEP, SleepStageType.REM, SleepStageType.LIGHT, SleepStageType.AWAKE)) {
            val millis = night.stageMillis[type] ?: 0L
            val share = if (type == SleepStageType.AWAKE) null else (100.0 * millis / night.asleepMillis).roundToInt()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).background(colors.stage(type), RoundedCornerShape(3.dp)))
                Text(
                    stringResource(type.label()),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 10.dp).width(72.dp),
                )
                Text(
                    Formats.hoursMinutes(millis),
                    style = MaterialTheme.typography.bodyMedium.tabularNumbers(),
                    modifier = Modifier.width(48.dp),
                )
                Text(
                    text = share?.let { stringResource(R.string.sleep_share, it) } ?: "",
                    style = MaterialTheme.typography.bodyMedium.tabularNumbers(),
                    modifier = Modifier.width(48.dp),
                )
                Text(
                    text = type.typical()?.let { stringResource(R.string.sleep_typical, it.first, it.last) } ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

/** HRV, resting heart rate and breathing rate of the night against the athlete's usual, with the last two weeks. */
@Composable
private fun BodyCard(nights: List<Night>, night: Night, modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    val colors = chartColors()
    val readiness = remember(nights, night) { Readiness.of(nights, null, night.date) }
    val recent = nights.filter { it.date > night.date.minusDays(BODY_NIGHTS) && it.date <= night.date }
    val days = (BODY_NIGHTS - 1 downTo 0).map { night.date.minusDays(it) }
    fun series(value: (Night) -> Double?) = recent.associateBy { it.date }.let { byDate -> days.map { byDate[it]?.let(value) } }
    val earlierBreathing = nights.filter { it.date < night.date && it.date > night.date.minusDays(30) }.mapNotNull { it.respiratoryRate }
    SleepCard(stringResource(R.string.sleep_body_title), stringResource(R.string.sleep_body_info), modifier) {
        BodyRow(
            name = stringResource(R.string.sleep_hrv),
            value = night.hrvMs?.let { stringResource(R.string.unit_ms, it.roundToInt()) },
            usual = readiness.hrv?.let {
                stringResource(R.string.sleep_hrv_normal, it.normalLowMs.roundToInt(), it.normalHighMs.roundToInt())
            },
            values = series { it.hrvMs },
            band = readiness.hrv?.let { it.normalLowMs..it.normalHighMs },
            color = colors.series[0],
        )
        BodyRow(
            name = stringResource(R.string.sleep_resting_hr),
            value = night.restingHr?.let { stringResource(R.string.unit_bpm, it.roundToInt()) },
            usual = readiness.restingHr?.let { stringResource(R.string.sleep_usual_bpm, it.usual.roundToInt()) },
            values = series { it.restingHr },
            band = null,
            color = colors.series[1],
        )
        BodyRow(
            name = stringResource(R.string.sleep_breathing),
            value = night.respiratoryRate?.let { stringResource(R.string.unit_breaths, "%.1f".format(locale, it)) },
            usual = Nights.median(earlierBreathing)?.let { stringResource(R.string.sleep_usual_breaths, "%.1f".format(locale, it)) },
            values = series { it.respiratoryRate },
            band = null,
            color = colors.series[2],
        )
    }
}

@Composable
private fun BodyRow(
    name: String,
    value: String?,
    usual: String?,
    values: List<Double?>,
    band: ClosedFloatingPointRange<Double>?,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value ?: DASH, style = MaterialTheme.typography.titleLarge.tabularNumbers())
            usual?.let {
                Text(it, style = MaterialTheme.typography.bodySmall.tabularNumbers(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Box(Modifier.weight(1f)) { Sparkline(values = values, color = color, band = band) }
    }
}

@Composable
private fun ScheduleCard(
    nights: List<Night>,
    selected: LocalDate,
    onSelect: (LocalDate) -> Unit,
    locale: Locale,
    modifier: Modifier = Modifier,
) {
    SleepCard(stringResource(R.string.sleep_schedule_title), stringResource(R.string.sleep_schedule_info), modifier) {
        SleepSchedule(nights, selected, onSelect, stringResource(R.string.sleep_schedule_description), locale)
    }
}

@Composable
private fun TrendsCard(
    nights: List<Night>,
    scores: Map<LocalDate, SleepScore>,
    needs: Map<LocalDate, Long>,
    until: LocalDate,
    modifier: Modifier = Modifier,
) {
    val days = (TREND_NIGHTS - 1 downTo 0).map { until.minusDays(it) }
    val byDate = nights.associateBy { it.date }
    val asleep = days.map { day -> byDate[day]?.let { it.asleepMillis / HOUR } }
    val recentScores = days.mapNotNull { scores[it]?.score }
    SleepCard(stringResource(R.string.sleep_trends_title), stringResource(R.string.sleep_trends_info), modifier) {
        Text(
            text = stringResource(
                R.string.sleep_trends_asleep,
                Formats.hoursMinutes(
                    (
                        asleep.filterNotNull().average().takeIf {
                            !it.isNaN()
                        }
                            ?: 0.0
                        ).times(HOUR).toLong(),
                ),
            ),
            style = MaterialTheme.typography.bodyMedium.tabularNumbers(),
        )
        MiniBars(values = asleep, reference = needs[until]?.let { it / HOUR }, height = 56.dp)
        if (recentScores.isNotEmpty()) {
            Text(
                text = stringResource(R.string.sleep_trends_score, recentScores.average().roundToInt()),
                style = MaterialTheme.typography.bodyMedium.tabularNumbers(),
            )
            Sparkline(values = days.map { scores[it]?.score?.toDouble() }, height = 48.dp)
        }
    }
}

@Composable
private fun SleepCard(title: String, info: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CardHeader(title = title, info = info)
            content()
        }
    }
}

internal fun SleepRating.label(): Int = when (this) {
    SleepRating.GOOD -> R.string.sleep_good
    SleepRating.FAIR -> R.string.sleep_fair
    SleepRating.POOR -> R.string.sleep_poor
}

private fun SleepStageType.label(): Int = when (this) {
    SleepStageType.DEEP -> R.string.stage_deep
    SleepStageType.REM -> R.string.stage_rem
    SleepStageType.AWAKE, SleepStageType.OUT_OF_BED -> R.string.stage_awake
    else -> R.string.stage_light
}

/** Typical share of sleep for healthy adults, in percent; awake has none. */
private fun SleepStageType.typical(): IntRange? = when (this) {
    SleepStageType.DEEP -> 13..23
    SleepStageType.REM -> 20..25
    SleepStageType.LIGHT -> 45..55
    else -> null
}

private fun clock(minuteOfDay: Int): String = "%02d:%02d".format(Locale.ROOT, minuteOfDay / 60, minuteOfDay % 60)

private const val MINUTE = 60_000.0
private const val HOUR = 3_600_000.0
private const val DASH = "–"
private const val SCHEDULE_NIGHTS = 14L
private const val BODY_NIGHTS = 14L
private const val TREND_NIGHTS = 30L
