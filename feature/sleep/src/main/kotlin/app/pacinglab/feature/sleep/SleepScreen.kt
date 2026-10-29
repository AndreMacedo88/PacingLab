package app.pacinglab.feature.sleep

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pacinglab.core.analytics.recovery.Night
import app.pacinglab.core.analytics.recovery.NightlyMetric
import app.pacinglab.core.analytics.recovery.NightlyRange
import app.pacinglab.core.analytics.recovery.Nights
import app.pacinglab.core.analytics.recovery.Readiness
import app.pacinglab.core.analytics.recovery.SleepMeasureKind
import app.pacinglab.core.analytics.recovery.SleepRating
import app.pacinglab.core.analytics.recovery.SleepScore
import app.pacinglab.core.data.repository.PlacedCard
import app.pacinglab.core.designsystem.chart.Sparkline
import app.pacinglab.core.designsystem.chart.weekdayInitials
import app.pacinglab.core.designsystem.component.CardHeader
import app.pacinglab.core.designsystem.component.DashboardEditAction
import app.pacinglab.core.designsystem.component.DashboardEditor
import app.pacinglab.core.designsystem.component.DashboardEntry
import app.pacinglab.core.designsystem.component.RangePill
import app.pacinglab.core.designsystem.component.SettingsAction
import app.pacinglab.core.designsystem.format.Formats
import app.pacinglab.core.designsystem.icon.PacingLabIcons
import app.pacinglab.core.designsystem.theme.StatusColors
import app.pacinglab.core.designsystem.theme.chartColors
import app.pacinglab.core.designsystem.theme.tabularNumbers
import app.pacinglab.core.model.SleepStageType
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun SleepRoute(
    initialDate: String?,
    onOpenSettings: () -> Unit,
    onOpenMetric: (NightlyMetric) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SleepViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    SleepScreen(
        state = state,
        initialDate = initialDate?.let(LocalDate::parse),
        onOpenSettings = onOpenSettings,
        modifier = modifier,
        refreshing = refreshing,
        onRefresh = viewModel::refresh,
        onOpenMetric = onOpenMetric,
        onArrange = viewModel::arrange,
        onResetArrangement = viewModel::resetArrangement,
    )
}

/**
 * One night in depth, laid out like the sleep screen of Google Health: score and duration at the
 * top, the night's stages, the score's parts, sleep quality against published ranges, heart rate
 * through the night, the night's recovery measurements and key metrics over recent nights. Arrows
 * step from night to night. A top-level screen: settings sit at the right of the app bar, beside the
 * pencil that switches to arranging the cards.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepScreen(
    state: SleepUiState,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    initialDate: LocalDate? = null,
    refreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    onOpenMetric: (NightlyMetric) -> Unit = {},
    onArrange: (List<PlacedCard>) -> Unit = {},
    onResetArrangement: () -> Unit = {},
    initiallyEditing: Boolean = false,
) {
    val locale = LocalConfiguration.current.locales[0]
    var editing by rememberSaveable { mutableStateOf(initiallyEditing) }
    BackHandler(enabled = editing) { editing = false }
    val loaded = state as? SleepUiState.Loaded
    val nights = loaded?.nights.orEmpty()
    var selectedDay by rememberSaveable { mutableLongStateOf(initialDate?.toEpochDay() ?: Long.MAX_VALUE) }
    val index = nights.indexOfLast { it.date.toEpochDay() <= selectedDay }.takeIf { it >= 0 } ?: nights.lastIndex
    val night = nights.getOrNull(index)
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (editing) R.string.sleep_edit_title else R.string.sleep_title)) },
                actions = {
                    if (loaded != null) DashboardEditAction(editing, onEditingChange = { editing = it })
                    if (!editing) SettingsAction(onOpenSettings)
                },
            )
        },
    ) { scaffoldPadding ->
        if (editing && loaded != null) {
            DashboardEditor(
                entries = loaded.cards.mapNotNull { placed ->
                    SleepCardKind.of(placed.id)?.let {
                        DashboardEntry(it.id, stringResource(it.title), stringResource(it.description), placed.visible)
                    }
                },
                onChange = { changed -> onArrange(changed.map { PlacedCard(it.id, it.visible) }) },
                onReset = onResetArrangement,
                modifier = Modifier.fillMaxSize().padding(scaffoldPadding),
            )
            return@Scaffold
        }
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize().padding(scaffoldPadding),
        ) {
            when {
                state is SleepUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                loaded == null || night == null -> Text(
                    text = stringResource(R.string.sleep_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(24.dp),
                )

                else -> {
                    val score = loaded.scores.getValue(night.date)
                    val need = loaded.needs.getValue(night.date)
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
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
                        for (card in loaded.cards.filter { it.visible }.mapNotNull { SleepCardKind.of(it.id) }) {
                            when (card) {
                                SleepCardKind.TILES -> item(key = card.id) {
                                    TopTiles(night, score, need, nights, loaded.scores, loaded.needs, locale)
                                }

                                SleepCardKind.NIGHT -> item(key = card.id) {
                                    NightCard(night, score, locale, rested = loaded.rested[night.date])
                                }

                                SleepCardKind.SCORE -> item(key = card.id) { ScoreCard(night, score, need) }

                                SleepCardKind.QUALITY -> if (night.continuity != null) item(key = card.id) { QualityCard(night, score) }

                                SleepCardKind.HEART_RATE -> if (night.sleepingHeartRate != null) {
                                    item(key = card.id) {
                                        HeartRateCard(night, locale, onClick = { onOpenMetric(NightlyMetric.SLEEPING_HR) })
                                    }
                                }

                                SleepCardKind.RECOVERY -> item(key = card.id) { BodyCard(nights, night, onOpenMetric) }

                                SleepCardKind.KEY_METRICS -> item(key = card.id) {
                                    KeyMetricsCard(nights, night.date, loaded.scores, loaded.needs, locale)
                                }

                                SleepCardKind.FEELING -> item(key = card.id) { FeelingCard(loaded.feeling) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NightSelector(night: Night, locale: Locale, onPrevious: (() -> Unit)?, onNext: (() -> Unit)?, modifier: Modifier = Modifier) {
    val date = remember(locale) { DateTimeFormatter.ofPattern("EEEE d MMMM", locale) }
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onPrevious?.invoke() }, enabled = onPrevious != null) {
            Icon(painterResource(PacingLabIcons.ChevronLeft), stringResource(R.string.sleep_previous))
        }
        Text(
            text = date.format(night.date).replaceFirstChar { it.titlecase(locale) },
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { onNext?.invoke() }, enabled = onNext != null) {
            Icon(painterResource(PacingLabIcons.ChevronRight), stringResource(R.string.sleep_next))
        }
    }
}

/** Score and time asleep side by side, each with the last week and a status. */
@Composable
private fun TopTiles(
    night: Night,
    score: SleepScore,
    need: Long,
    nights: List<Night>,
    scores: Map<LocalDate, SleepScore>,
    needs: Map<LocalDate, Long>,
    locale: Locale,
    modifier: Modifier = Modifier,
) {
    val days = (WEEK - 1 downTo 0).map { night.date.minusDays(it) }
    val labels = weekdayInitials(days, locale)
    val byDate = nights.associateBy { it.date }
    Row(modifier = modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Tile(
            label = stringResource(R.string.sleep_score_title),
            value = score.score.toString(),
            status = stringResource(score.rating.label()),
            good = score.rating == SleepRating.GOOD,
            modifier = Modifier.weight(1f),
        ) {
            Sparkline(values = days.map { scores[it]?.score?.toDouble() }, band = SleepScore.GOOD_FROM.toDouble()..100.0, labels = labels)
        }
        val met = night.asleepMillis >= need
        Tile(
            label = stringResource(R.string.sleep_time_asleep),
            value = Formats.hoursMinutes(night.asleepMillis),
            status = stringResource(if (met) R.string.sleep_need_met else R.string.sleep_need_missed),
            good = met,
            modifier = Modifier.weight(1f),
        ) {
            Sparkline(
                values = days.map { byDate[it]?.asleepMillis?.div(HOUR) },
                bands = days.map { day -> needs[day]?.let { it / HOUR..Double.POSITIVE_INFINITY } },
                color = chartColors().series[2],
                labels = labels,
            )
        }
    }
}

@Composable
private fun Tile(
    label: String,
    value: String,
    status: String,
    good: Boolean,
    modifier: Modifier = Modifier,
    chart: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxHeight(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineSmall.tabularNumbers())
            chart()
            StatusChip(status, good)
        }
    }
}

/** A short status with an icon, so that the state never depends on colour alone. */
@Composable
private fun StatusChip(text: String, good: Boolean, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(
            painter = painterResource(if (good) PacingLabIcons.CheckCircle else PacingLabIcons.Info),
            contentDescription = null,
            tint = if (good) StatusColors.Good else StatusColors.Warning,
            modifier = Modifier.size(16.dp),
        )
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** What kind of night it was, what held it back, and the stages over the night. */
@Composable
private fun NightCard(night: Night, score: SleepScore, locale: Locale, modifier: Modifier = Modifier, rested: Int? = null) {
    val time = remember(locale) { DateTimeFormatter.ofPattern("HH:mm", locale) }
    val labels = STAGE_LANES.map { type ->
        stringResource(R.string.sleep_lane, stringResource(type.label()), Formats.hoursMinutes(night.stageMillis[type] ?: 0))
    }
    SleepCard(stringResource(score.rating.headline()), stringResource(R.string.sleep_stages_info), modifier) {
        Text(summary(score), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (night.stages.isNotEmpty()) StageLanes(night, labels, stringResource(R.string.sleep_stages_description))
        Row(Modifier.fillMaxWidth()) {
            TimeLabel(
                time.format(night.start.atZone(night.zone)),
                stringResource(R.string.sleep_went_to_bed),
                TextAlign.Start,
                Modifier.weight(1f),
            )
            TimeLabel(clock(score.midpointMinuteOfDay), stringResource(R.string.sleep_middle), TextAlign.Center, Modifier.weight(1f))
            TimeLabel(time.format(night.end.atZone(night.zone)), stringResource(R.string.sleep_got_up), TextAlign.End, Modifier.weight(1f))
        }
        if (rested != null) {
            Text(
                text = stringResource(R.string.sleep_felt, rested, stringArrayResource(R.array.sleep_rested_words)[rested - 1]),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TimeLabel(time: String, caption: String, align: TextAlign, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(time, style = MaterialTheme.typography.titleMedium.tabularNumbers(), textAlign = align, modifier = Modifier.fillMaxWidth())
        Text(
            caption,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = align,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** One sentence on what held the night back, from the measures below their threshold. */
@Composable
private fun summary(score: SleepScore): String {
    val weak = score.measures.filter { !it.withinThreshold }.sortedBy { it.fraction }.map { stringResource(it.kind.shortfall()) }
    val fair = score.measures.filter {
        it.withinThreshold && it.fraction < NEARLY_FULL
    }.sortedBy { it.fraction }.map { stringResource(it.kind.shortfall()) }
    val and = stringResource(R.string.sleep_summary_and)
    return when {
        weak.isNotEmpty() -> stringResource(R.string.sleep_summary_held_back, weak.take(2).joinToString(" $and "))
        fair.isNotEmpty() -> stringResource(R.string.sleep_summary_could_be_better, fair.take(2).joinToString(" $and "))
        else -> stringResource(R.string.sleep_summary_excellent)
    }
}

@Composable
private fun ScoreCard(night: Night, score: SleepScore, needMillis: Long, modifier: Modifier = Modifier) {
    SleepCard(stringResource(R.string.sleep_score_parts), stringResource(R.string.sleep_score_info), modifier) {
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
        score.continuity?.let { continuity ->
            Part(
                name = stringResource(R.string.sleep_part_continuity),
                fraction = continuity,
                points = SleepScore.CONTINUITY_POINTS,
                detail = stringResource(R.string.sleep_detail_continuity),
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
    val bar = chartColors().series[0]
    Column(
        modifier = modifier.clearAndSetSemantics { contentDescription = "$name: $earned of ${points.toInt()}. $detail" },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(96.dp))
            Box(Modifier.weight(1f).height(8.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))) {
                Box(Modifier.fillMaxWidth(fraction.toFloat()).height(8.dp).background(bar, RoundedCornerShape(4.dp)))
            }
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

/** Continuity and stage measures against their published ranges. */
@Composable
private fun QualityCard(night: Night, score: SleepScore, modifier: Modifier = Modifier) {
    val c = night.continuity ?: return
    SleepCard(stringResource(R.string.sleep_quality_title), stringResource(R.string.sleep_quality_info), modifier) {
        val latency = c.latencyMillis / MINUTE
        QualityRow(
            stringResource(R.string.sleep_quality_time_to_sleep),
            stringResource(R.string.sleep_minutes, latency.roundToInt()),
            latency,
            0.0..30.0,
            0.0..90.0,
        )
        val awake = c.wakeAfterOnsetMillis / MINUTE
        QualityRow(
            stringResource(R.string.sleep_quality_awake),
            stringResource(R.string.sleep_minutes, awake.roundToInt()),
            awake,
            0.0..20.0,
            0.0..90.0,
        )
        QualityRow(
            stringResource(R.string.sleep_quality_awakenings),
            pluralStringResource(R.plurals.sleep_awakenings, c.longAwakenings, c.longAwakenings),
            c.longAwakenings.toDouble(),
            0.0..1.0,
            0.0..6.0,
        )
        score.measure(SleepMeasureKind.EFFICIENCY)?.let {
            val percent = it.value * 100
            QualityRow(
                stringResource(R.string.sleep_quality_efficiency),
                stringResource(R.string.sleep_percent, percent.roundToInt()),
                percent,
                85.0..100.0,
                60.0..100.0,
            )
        }
        for (type in listOf(SleepStageType.DEEP, SleepStageType.REM)) {
            val typical = type.typical() ?: continue
            val millis = night.stageMillis[type] ?: 0L
            val share = 100.0 * millis / night.asleepMillis
            QualityRow(
                stringResource(R.string.sleep_quality_stage, stringResource(type.label())),
                stringResource(R.string.sleep_stage_value, Formats.hoursMinutes(millis), share.roundToInt()),
                share,
                typical.first.toDouble()..typical.last.toDouble(),
                0.0..40.0,
            )
        }
    }
}

@Composable
private fun QualityRow(
    name: String,
    value: String,
    measured: Double,
    range: ClosedFloatingPointRange<Double>,
    track: ClosedFloatingPointRange<Double>,
    modifier: Modifier = Modifier,
) {
    val inRange = measured in range
    val status = stringResource(
        when {
            inRange -> R.string.sleep_in_range
            measured > range.endInclusive -> R.string.sleep_above_range
            else -> R.string.sleep_below_range
        },
    )
    Column(
        modifier = modifier.clearAndSetSemantics {
            contentDescription = "$name $value, $status"
        },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$name · $value", style = MaterialTheme.typography.bodyMedium.tabularNumbers(), modifier = Modifier.weight(1f))
            StatusChip(status, inRange)
        }
        RangeBar(value = measured, range = range, track = track)
    }
}

/** Heart rate through the night with its average, its lowest stretch and the night's resting value. */
@Composable
private fun HeartRateCard(night: Night, locale: Locale, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val sleeping = night.sleepingHeartRate ?: return
    SleepCard(stringResource(R.string.sleep_heart_rate_title), stringResource(R.string.sleep_heart_rate_info), modifier, onClick) {
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Value(stringResource(R.string.sleep_heart_rate_average), stringResource(R.string.unit_bpm, sleeping.average.roundToInt()))
            Value(stringResource(R.string.sleep_heart_rate_lowest), stringResource(R.string.unit_bpm, sleeping.lowest.roundToInt()))
            night.restingHr?.let {
                Value(stringResource(R.string.sleep_resting_hr_short), stringResource(R.string.unit_bpm, it.roundToInt()))
            }
        }
        SleepingHeartRateChart(
            night = night,
            sleeping = sleeping,
            restingLabel = night.restingHr?.let { stringResource(R.string.sleep_heart_rate_resting_line, it.roundToInt()) },
            description = stringResource(
                R.string.sleep_heart_rate_description,
                sleeping.average.roundToInt(),
                sleeping.lowest.roundToInt(),
            ),
            locale = locale,
        )
    }
}

@Composable
private fun Value(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleLarge.tabularNumbers())
    }
}

/**
 * HRV, resting heart rate and breathing rate of the night against the athlete's own range, with the
 * week before as sparklines over each night's range. Each row opens the measure's detail page.
 */
@Composable
private fun BodyCard(nights: List<Night>, night: Night, onOpenMetric: (NightlyMetric) -> Unit, modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    val colors = chartColors()
    val days = (WEEK - 1 downTo 0).map { night.date.minusDays(it) }
    val labels = weekdayInitials(days, locale)
    val byDate = nights.associateBy { it.date }
    SleepCard(stringResource(R.string.sleep_body_title), stringResource(R.string.sleep_body_info), modifier) {
        val rows = listOf(
            NightlyMetric.HRV to colors.series[0],
            NightlyMetric.RESTING_HR to colors.restingHr,
            NightlyMetric.BREATHING_RATE to colors.series[2],
            NightlyMetric.OXYGEN_SATURATION to colors.oxygenSaturation,
            NightlyMetric.SKIN_TEMPERATURE to colors.skinTemperature,
        ).filter { (metric, _) -> metric !in SECONDARY_ONLY || days.any { day -> byDate[day]?.let(metric.value) != null } }
        for ((metric, color) in rows) {
            val ranges = remember(nights, night, metric) { days.map { NightlyRange.of(nights, metric, it) } }
            val value = metric.value(night)
            val range = ranges.last()
            val partial = metric == NightlyMetric.HRV && night.hrvPartial
            BodyRow(
                name = stringResource(metric.label()),
                value = value?.let { metric.formatted(it, locale) },
                usual = when {
                    partial -> stringResource(R.string.sleep_hrv_partial)

                    range != null -> stringResource(
                        R.string.sleep_range,
                        metric.number(range.low, locale),
                        metric.formatted(range.high, locale),
                    )

                    else -> null
                },
                values = days.map { day -> byDate[day]?.let(metric.value)?.let(metric::rounded) },
                bands = ranges.map { it?.let { r -> r.low..r.high } },
                color = color,
                labels = labels,
                status = value?.let { { RangePill(range?.standing(it)) } },
                onClick = { onOpenMetric(metric) },
            )
        }
    }
}

@Composable
private fun BodyRow(
    name: String,
    value: String?,
    usual: String?,
    values: List<Double?>,
    bands: List<ClosedFloatingPointRange<Double>?>,
    color: Color,
    labels: List<String>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    status: (@Composable () -> Unit)? = null,
) {
    Row(modifier = modifier.fillMaxWidth().clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value ?: DASH, style = MaterialTheme.typography.titleLarge.tabularNumbers())
            usual?.let {
                Text(it, style = MaterialTheme.typography.bodySmall.tabularNumbers(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            status?.let { Box(Modifier.padding(top = 4.dp)) { it() } }
        }
        Box(Modifier.weight(1f)) { Sparkline(values = values, color = color, bands = bands, labels = labels) }
    }
}

/** Measures only the Google Health API supplies; their rows show once a recent night has a value. */
private val SECONDARY_ONLY = setOf(NightlyMetric.OXYGEN_SATURATION, NightlyMetric.SKIN_TEMPERATURE)

private fun NightlyMetric.label(): Int = when (this) {
    NightlyMetric.HRV -> R.string.sleep_hrv
    NightlyMetric.RESTING_HR -> R.string.sleep_resting_hr
    NightlyMetric.BREATHING_RATE -> R.string.sleep_breathing
    NightlyMetric.SLEEPING_HR -> R.string.sleep_heart_rate_lowest
    NightlyMetric.OXYGEN_SATURATION -> R.string.sleep_oxygen
    NightlyMetric.SKIN_TEMPERATURE -> R.string.sleep_skin_temperature
}

private fun NightlyMetric.number(value: Double, locale: Locale): String = when (this) {
    NightlyMetric.BREATHING_RATE, NightlyMetric.OXYGEN_SATURATION -> "%.1f".format(locale, value)
    NightlyMetric.SKIN_TEMPERATURE -> "%+.1f".format(locale, value)
    else -> value.roundToInt().toString()
}

@Composable
private fun NightlyMetric.formatted(value: Double, locale: Locale): String = when (this) {
    NightlyMetric.HRV -> stringResource(R.string.unit_ms, value.roundToInt())
    NightlyMetric.RESTING_HR, NightlyMetric.SLEEPING_HR -> stringResource(R.string.unit_bpm, value.roundToInt())
    NightlyMetric.BREATHING_RATE -> stringResource(R.string.unit_breaths, number(value, locale))
    NightlyMetric.OXYGEN_SATURATION -> stringResource(R.string.unit_percent, number(value, locale))
    NightlyMetric.SKIN_TEMPERATURE -> stringResource(R.string.unit_celsius, number(value, locale))
}

/** Key measures over the last week or month, ending with the selected night. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KeyMetricsCard(
    nights: List<Night>,
    until: LocalDate,
    scores: Map<LocalDate, SleepScore>,
    needs: Map<LocalDate, Long>,
    locale: Locale,
    modifier: Modifier = Modifier,
) {
    var span by rememberSaveable { mutableIntStateOf(0) }
    val count = if (span == 0) WEEK else MONTH
    val days = (count - 1 downTo 0).map { until.minusDays(it) }
    val byDate = nights.associateBy { it.date }
    val colors = chartColors()
    fun series(value: (Night) -> Double?) = days.map { byDate[it]?.let(value) }
    SleepCard(stringResource(R.string.sleep_metrics_title), stringResource(R.string.sleep_metrics_info), modifier) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf(R.string.sleep_metrics_week, R.string.sleep_metrics_month).forEachIndexed { i, label ->
                SegmentedButton(selected = span == i, onClick = {
                    span = i
                }, shape = SegmentedButtonDefaults.itemShape(i, 2)) { Text(stringResource(label)) }
            }
        }
        // Weekday initials fit under a week; a month is read as a shape.
        val labels = if (count == WEEK) weekdayInitials(days, locale) else null
        val scoreValues = days.map { scores[it]?.score?.toDouble() }
        MetricRow(stringResource(R.string.sleep_score_title), average(scoreValues)?.roundToInt()?.toString()) {
            Sparkline(values = scoreValues, band = SleepScore.GOOD_FROM.toDouble()..100.0, labels = labels)
        }
        val asleep = series { it.asleepMillis / HOUR }
        MetricRow(stringResource(R.string.sleep_time_asleep), average(asleep)?.let { Formats.hoursMinutes((it * HOUR).toLong()) }) {
            Sparkline(
                values = asleep,
                bands = days.map { day -> needs[day]?.let { it / HOUR..Double.POSITIVE_INFINITY } },
                color = colors.series[2],
                labels = labels,
            )
        }
        Text(stringResource(R.string.sleep_metrics_windows), style = MaterialTheme.typography.labelLarge)
        SleepWindows(days.mapNotNull { byDate[it] }, until, stringResource(R.string.sleep_schedule_description), locale)
        val efficiency = series { 100.0 * it.asleepMillis / it.inBedMillis }
        MetricRow(
            stringResource(R.string.sleep_quality_efficiency),
            average(efficiency)?.let {
                stringResource(R.string.sleep_percent, it.roundToInt())
            },
        ) {
            Sparkline(values = efficiency, band = 85.0..100.0, labels = labels)
        }
        for (type in listOf(SleepStageType.DEEP, SleepStageType.REM)) {
            val typical = type.typical() ?: continue
            val shares = series { n -> (n.stageMillis[type] ?: 0L).takeIf { it > 0 }?.let { 100.0 * it / n.asleepMillis } }
            MetricRow(
                stringResource(R.string.sleep_quality_stage, stringResource(type.label())),
                average(shares)?.let {
                    stringResource(R.string.sleep_percent, it.roundToInt())
                },
            ) {
                // Published ranges, not the athlete's own: a habit of short deep sleep should not look normal.
                Sparkline(
                    values = shares,
                    band = typical.first.toDouble()..typical.last.toDouble(),
                    color = colors.stage(type),
                    labels = labels,
                )
            }
        }
    }
}

@Composable
private fun MetricRow(name: String, average: String?, modifier: Modifier = Modifier, chart: @Composable () -> Unit) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.labelLarge)
            Text(
                text = average?.let { stringResource(R.string.sleep_metrics_average, it) } ?: DASH,
                style = MaterialTheme.typography.bodyMedium.tabularNumbers(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(Modifier.weight(1.2f)) { chart() }
    }
}

private fun average(values: List<Double?>): Double? = values.filterNotNull().takeIf { it.isNotEmpty() }?.average()

@Composable
internal fun SleepCard(
    title: String,
    info: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    val body: @Composable () -> Unit = {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CardHeader(title = title, info = info)
            content()
        }
    }
    if (onClick == null) {
        Card(modifier = modifier.fillMaxWidth(), colors = colors) { body() }
    } else {
        Card(onClick = onClick, modifier = modifier.fillMaxWidth(), colors = colors) { body() }
    }
}

internal fun SleepRating.label(): Int = when (this) {
    SleepRating.GOOD -> R.string.sleep_good
    SleepRating.FAIR -> R.string.sleep_fair
    SleepRating.POOR -> R.string.sleep_poor
}

private fun SleepRating.headline(): Int = when (this) {
    SleepRating.GOOD -> R.string.sleep_headline_good
    SleepRating.FAIR -> R.string.sleep_headline_fair
    SleepRating.POOR -> R.string.sleep_headline_poor
}

private fun SleepMeasureKind.shortfall(): Int = when (this) {
    SleepMeasureKind.DURATION -> R.string.sleep_shortfall_duration
    SleepMeasureKind.EFFICIENCY -> R.string.sleep_shortfall_efficiency
    SleepMeasureKind.AWAKE_AFTER_ONSET -> R.string.sleep_shortfall_awake
    SleepMeasureKind.TIME_TO_SLEEP -> R.string.sleep_shortfall_time_to_sleep
    SleepMeasureKind.LONG_AWAKENINGS -> R.string.sleep_shortfall_awakenings
    SleepMeasureKind.MIDPOINT_SHIFT -> R.string.sleep_shortfall_regularity
    SleepMeasureKind.TIMING -> R.string.sleep_shortfall_timing
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
private const val WEEK = 7L
private const val MONTH = 30L

/** A measure this close to full points counts as excellent in the summary. */
private const val NEARLY_FULL = 0.85
