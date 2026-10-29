package app.pacinglab.feature.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.pacinglab.core.analytics.performance.RaceDistance
import app.pacinglab.core.analytics.recovery.Night
import app.pacinglab.core.analytics.recovery.NightlyMetric
import app.pacinglab.core.analytics.recovery.NightlyRange
import app.pacinglab.core.analytics.recovery.Readiness
import app.pacinglab.core.analytics.recovery.SleepDebtLevel
import app.pacinglab.core.analytics.recovery.SleepRating
import app.pacinglab.core.analytics.recovery.SleepScore
import app.pacinglab.core.analytics.recovery.Standing
import app.pacinglab.core.designsystem.chart.MiniBars
import app.pacinglab.core.designsystem.chart.SegmentBar
import app.pacinglab.core.designsystem.chart.Sparkline
import app.pacinglab.core.designsystem.chart.weekdayInitials
import app.pacinglab.core.designsystem.component.RangePill
import app.pacinglab.core.designsystem.component.StatusPill
import app.pacinglab.core.designsystem.format.Formats
import app.pacinglab.core.designsystem.icon.PacingLabIcons
import app.pacinglab.core.designsystem.theme.StatusColors
import app.pacinglab.core.designsystem.theme.chartColors
import app.pacinglab.core.designsystem.theme.tabularNumbers
import app.pacinglab.core.model.SleepStageType
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Two tiles side by side, equally tall; a lone tile keeps its half and leaves the other empty. */
@Composable
internal fun TileRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
internal fun RowScope.EmptyTile() {
    Spacer(Modifier.weight(1f))
}

/**
 * A small card: label, headline value, one line of context, a mini chart and, at the bottom, a status
 * pill. The pill sits at the foot of the tile so that pills line up across a row.
 */
@Composable
internal fun Tile(
    label: String,
    value: String,
    detail: String?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    status: (@Composable () -> Unit)? = null,
    chart: @Composable () -> Unit = {},
) {
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    val content: @Composable () -> Unit = {
        Column(modifier = Modifier.fillMaxHeight().padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge.tabularNumbers(), maxLines = 1)
            Text(
                text = detail ?: "",
                style = MaterialTheme.typography.bodySmall.tabularNumbers(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Box(Modifier.padding(top = 8.dp)) { chart() }
            if (status != null) {
                Spacer(Modifier.weight(1f))
                Box(Modifier.padding(top = 10.dp)) { status() }
            }
        }
    }
    if (onClick == null) {
        Card(modifier = modifier.fillMaxHeight(), colors = colors) { content() }
    } else {
        Card(onClick = onClick, modifier = modifier.fillMaxHeight(), colors = colors) { content() }
    }
}

/** One value per day of the last week, oldest first, null for days without a night. */
internal fun TodayUiState.Loaded.byNight(value: (Night) -> Double?): List<Double?> {
    val nights = nights.associateBy { it.date }
    return (TodayViewModel.TILE_DAYS - 1 downTo 0).map { back -> nights[today.minusDays(back.toLong())]?.let(value) }
}

/** Weekday initials of the days [byNight] covers. */
internal fun TodayUiState.Loaded.days(locale: Locale): List<String> =
    weekdayInitials((TodayViewModel.TILE_DAYS - 1 downTo 0).map { today.minusDays(it.toLong()) }, locale)

internal fun TodayUiState.Loaded.lastNight(): Night? = nights.lastOrNull()?.takeIf { it.date == today }

@Composable
internal fun SleepTile(state: TodayUiState.Loaded, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val night = state.lastNight()
    val score = night?.let { state.sleepScores[it.date] }
    val colors = chartColors()
    Tile(
        label = stringResource(R.string.tile_sleep),
        value = score?.score?.toString() ?: DASH,
        detail = if (night != null) {
            stringResource(R.string.tile_sleep_asleep, Formats.hoursMinutes(night.asleepMillis))
        } else {
            stringResource(R.string.tile_no_night)
        },
        modifier = modifier,
        onClick = onClick,
        status = score?.let { { SleepRatingPill(it.rating) } },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (night != null && night.stageMillis.isNotEmpty()) {
                // Deep, REM, light, awake: the order the colours were checked in.
                SegmentBar(
                    weights = listOf(SleepStageType.DEEP, SleepStageType.REM, SleepStageType.LIGHT, SleepStageType.AWAKE)
                        .map { (night.stageMillis[it] ?: 0L).toDouble() },
                    colors = listOf(colors.deepSleep, colors.remSleep, colors.lightSleep, colors.awake),
                )
            }
            Sparkline(
                values = state.byNight { state.sleepScores[it.date]?.score?.toDouble() },
                band = SleepScore.GOOD_FROM.toDouble()..100.0,
                labels = state.days(LocalConfiguration.current.locales[0]),
                height = 32.dp,
            )
        }
    }
}

/** Sleep habits over the last two weeks, with the week of daily values. */
@Composable
internal fun SleepHealthTile(state: TodayUiState.Loaded, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val health = state.sleepHealth[state.today]
    Tile(
        label = stringResource(R.string.tile_sleep_health),
        value = health?.score?.toString() ?: DASH,
        detail = stringResource(R.string.tile_sleep_health_detail),
        modifier = modifier,
        onClick = onClick,
        status = health?.let { { SleepRatingPill(it.rating) } },
    ) {
        Sparkline(
            values = (TodayViewModel.TILE_DAYS - 1 downTo 0).map {
                state.sleepHealth[state.today.minusDays(it.toLong())]?.score?.toDouble()
            },
            band = SleepScore.GOOD_FROM.toDouble()..100.0,
            labels = state.days(LocalConfiguration.current.locales[0]),
        )
    }
}

/** Sleep missed against need over two weeks, recent nights weighing more. */
@Composable
internal fun SleepDebtTile(state: TodayUiState.Loaded, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val debt = state.sleepDebts[state.today]
    Tile(
        label = stringResource(R.string.tile_sleep_debt),
        value = debt?.let { Formats.hoursMinutes(it.millis) } ?: DASH,
        detail = stringResource(R.string.tile_sleep_debt_detail),
        modifier = modifier,
        onClick = onClick,
        status = debt?.let { { SleepDebtPill(it.level) } },
    ) {
        Sparkline(
            values = (TodayViewModel.TILE_DAYS - 1 downTo 0).map {
                state.sleepDebts[state.today.minusDays(it.toLong())]?.millis?.div(HOUR)
            },
            band = 0.0..LOW_DEBT_HOURS,
            color = chartColors().series[2],
            labels = state.days(LocalConfiguration.current.locales[0]),
        )
    }
}

@Composable
private fun SleepDebtPill(level: SleepDebtLevel) {
    when (level) {
        SleepDebtLevel.NONE -> StatusPill(stringResource(R.string.debt_none), StatusColors.Good, PacingLabIcons.CheckCircle)
        SleepDebtLevel.LOW -> StatusPill(stringResource(R.string.debt_low), StatusColors.Good, PacingLabIcons.CheckCircle)
        SleepDebtLevel.MODERATE -> StatusPill(stringResource(R.string.debt_moderate), StatusColors.Warning, PacingLabIcons.Info)
        SleepDebtLevel.HIGH -> StatusPill(stringResource(R.string.debt_high), StatusColors.Serious, PacingLabIcons.Warning)
    }
}

private const val HOUR = 3_600_000.0

/** Debt up to this many hours is low: the tile's band. */
private const val LOW_DEBT_HOURS = 2.0

@Composable
private fun SleepRatingPill(rating: SleepRating) {
    when (rating) {
        SleepRating.GOOD -> StatusPill(stringResource(R.string.sleep_good), StatusColors.Good, PacingLabIcons.CheckCircle)
        SleepRating.FAIR -> StatusPill(stringResource(R.string.sleep_fair), StatusColors.Warning, PacingLabIcons.Info)
        SleepRating.POOR -> StatusPill(stringResource(R.string.sleep_poor), StatusColors.Serious, PacingLabIcons.Warning)
    }
}

internal fun SleepRating.label(): Int = when (this) {
    SleepRating.GOOD -> R.string.sleep_good
    SleepRating.FAIR -> R.string.sleep_fair
    SleepRating.POOR -> R.string.sleep_poor
}

/**
 * A nightly measure against the athlete's own range: last night's value, the range, the week as a
 * sparkline over each day's range, and whether last night fell inside it.
 */
@Composable
internal fun NightlyTile(state: TodayUiState.Loaded, metric: NightlyMetric, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    val night = state.lastNight()
    val value = night?.let(metric.value)
    val range = state.rangeTonight(metric)
    val colors = chartColors()
    Tile(
        label = stringResource(metric.label()),
        value = value?.let { metric.format(it, locale) } ?: DASH,
        detail = when {
            range != null -> stringResource(
                R.string.tile_range,
                metric.format(range.low, locale, unit = false),
                metric.format(range.high, locale),
            )

            else -> pluralStringResource(
                R.plurals.tile_range_learning,
                NightlyRange.MIN_NIGHTS,
                state.rangeNights[metric] ?: 0,
                NightlyRange.MIN_NIGHTS,
            )
        },
        modifier = modifier,
        onClick = onClick,
        status = when {
            value != null -> ({ RangePill(range?.standing(value)) })

            metric == NightlyMetric.HRV && night?.hrvPartial == true -> (
                {
                    StatusPill(stringResource(R.string.tile_hrv_partial), MaterialTheme.colorScheme.outline, PacingLabIcons.Info)
                }
                )

            else -> null
        },
    ) {
        Sparkline(
            values = state.byNight { night -> metric.value(night)?.let(metric::rounded) },
            color = when (metric) {
                NightlyMetric.HRV -> colors.series[0]
                NightlyMetric.RESTING_HR -> colors.restingHr
                NightlyMetric.BREATHING_RATE -> colors.series[2]
                NightlyMetric.SLEEPING_HR -> colors.restingHr
                NightlyMetric.OXYGEN_SATURATION -> colors.oxygenSaturation
                NightlyMetric.SKIN_TEMPERATURE -> colors.skinTemperature
            },
            bands = state.ranges[metric].orEmpty().map { it?.let { r -> r.low..r.high } },
            labels = state.days(locale),
        )
    }
}

internal fun NightlyMetric.label(): Int = when (this) {
    NightlyMetric.HRV -> R.string.tile_hrv
    NightlyMetric.RESTING_HR -> R.string.tile_resting_hr
    NightlyMetric.BREATHING_RATE -> R.string.tile_breathing
    NightlyMetric.SLEEPING_HR -> R.string.tile_sleeping_hr
    NightlyMetric.OXYGEN_SATURATION -> R.string.tile_oxygen
    NightlyMetric.SKIN_TEMPERATURE -> R.string.tile_skin_temperature
}

/** "62 ms", "57 bpm", "13.4 /min"; without the unit for the lower end of a range. */
@Composable
internal fun NightlyMetric.format(value: Double, locale: Locale, unit: Boolean = true): String {
    val number = when (this) {
        NightlyMetric.BREATHING_RATE, NightlyMetric.OXYGEN_SATURATION -> "%.1f".format(locale, value)
        NightlyMetric.SKIN_TEMPERATURE -> "%+.1f".format(locale, value)
        else -> value.roundToInt().toString()
    }
    if (!unit) return number
    return when (this) {
        NightlyMetric.HRV -> stringResource(R.string.unit_ms_text, number)
        NightlyMetric.RESTING_HR, NightlyMetric.SLEEPING_HR -> stringResource(R.string.unit_bpm_text, number)
        NightlyMetric.BREATHING_RATE -> stringResource(R.string.unit_breaths, number)
        NightlyMetric.OXYGEN_SATURATION -> stringResource(R.string.unit_percent, number)
        NightlyMetric.SKIN_TEMPERATURE -> stringResource(R.string.unit_celsius, number)
    }
}

@Composable
internal fun FormTile(state: TodayUiState.Loaded, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val today = state.form.lastOrNull()
    Tile(
        label = stringResource(R.string.tile_form),
        value = today?.let { signed(it.form) } ?: DASH,
        detail = today?.let { stringResource(R.string.tile_form_detail, it.fitness.roundToInt(), it.fatigue.roundToInt()) },
        modifier = modifier,
        onClick = onClick,
        status = state.readiness.training?.let { training -> { FormPill(training.standing) } },
    ) {
        Sparkline(
            values = state.form.map { it.form },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            bands = state.form.map { Readiness.formBand(it.fitness).let { band -> -band..band } },
            baseline = 0.0,
        )
    }
}

/** Form against its band: fresh above it, fatigued below, balanced inside. */
@Composable
private fun FormPill(standing: Standing) {
    when (standing) {
        Standing.ABOVE -> StatusPill(stringResource(R.string.form_fresh), StatusColors.Good, PacingLabIcons.ArrowUp)
        Standing.NORMAL -> StatusPill(stringResource(R.string.form_balanced), StatusColors.Good, PacingLabIcons.CheckCircle)
        Standing.BELOW -> StatusPill(stringResource(R.string.form_fatigued), StatusColors.Warning, PacingLabIcons.ArrowDown)
    }
}

@Composable
internal fun WeekTile(state: TodayUiState.Loaded, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val week = state.week
    val today = state.today.dayOfWeek.value - 1
    Tile(
        label = stringResource(R.string.tile_week),
        value = week?.load?.roundToInt()?.toString() ?: "0",
        detail = week?.let {
            stringResource(R.string.tile_week_detail, Formats.hoursMinutes(it.durationMillis)) + " · " +
                pluralStringResource(R.plurals.sessions, state.weekSessions, state.weekSessions)
        },
        modifier = modifier,
        onClick = onClick,
    ) {
        // Days still to come stay empty rather than showing zero.
        MiniBars(
            values = state.weekLoads.mapIndexed { i, load -> if (i > today) null else load },
            color = chartColors().series[0],
            labels = weekdayInitials(
                (0 until 7).map {
                    state.today.minusDays((today - it).toLong())
                },
                LocalConfiguration.current.locales[0],
            ),
            today = today,
        )
    }
}

@Composable
internal fun Vo2MaxTile(state: TodayUiState.Loaded, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    val trend = state.extras.vo2max?.trend
    val recent = trend?.days?.takeLast(VO2MAX_DAYS)
    Tile(
        label = stringResource(R.string.tile_vo2max),
        value = trend?.current?.value?.let { "%.1f".format(locale, it) } ?: DASH,
        detail = trend?.change(30)?.let { stringResource(R.string.tile_vo2max_change, signedOne(it, locale)) },
        modifier = modifier,
        onClick = onClick,
    ) {
        if (recent != null && recent.size > 1) {
            Sparkline(
                values = recent.map { it.value },
                bands = recent.map { it.value - it.sd..it.value + it.sd },
                color = chartColors().series[0],
            )
        }
    }
}

@Composable
internal fun RaceTile(state: TodayUiState.Loaded, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val prediction = state.extras.running?.predictions?.firstOrNull { it.distance == RaceDistance.K5 }
    val seconds = prediction?.fromVo2Max ?: prediction?.fromCriticalSpeed
    Tile(
        label = stringResource(R.string.tile_race_5k),
        value = seconds?.let { Formats.duration((it * 1000).roundToLong()) } ?: DASH,
        detail = seconds?.let { Formats.pacePerKm((it * 1000).roundToLong(), RaceDistance.K5.meters) }
            ?.let { stringResource(R.string.unit_pace, it) },
        modifier = modifier,
        onClick = onClick,
    )
}

@Composable
internal fun EfficiencyTile(state: TodayUiState.Loaded, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    val history = state.extras.efficiency
    val latest = history?.trend?.lastOrNull()
    val date = DateTimeFormatter.ofPattern("d MMM", locale)
    Tile(
        label = stringResource(R.string.tile_efficiency),
        value =
        latest?.let { stringResource(R.string.unit_pace, Formats.pacePerKm(1_000_000L, it.speedAtReference * 1000) ?: DASH) } ?: DASH,
        detail = history?.points?.lastOrNull()?.let { point ->
            history.referenceHr?.let { stringResource(R.string.tile_efficiency_detail, it, date.format(point.date)) }
        },
        modifier = modifier,
        onClick = onClick,
    ) {
        val trend = history?.trend.orEmpty().takeLast(EFFICIENCY_DAYS)
        if (trend.size > 1) Sparkline(values = trend.map { it.speedAtReference }, color = chartColors().series[0])
    }
}

private fun signedOne(value: Double, locale: Locale): String {
    val text = "%.1f".format(locale, abs(value))
    return if (value < 0) "−$text" else "+$text"
}

internal const val DASH = "–"
private const val VO2MAX_DAYS = 90
private const val EFFICIENCY_DAYS = 90
