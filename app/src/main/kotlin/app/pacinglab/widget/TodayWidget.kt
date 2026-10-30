package app.pacinglab.widget

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.createBitmap
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.pacinglab.MainActivity
import app.pacinglab.R
import app.pacinglab.core.analytics.recovery.NightlyMetric
import app.pacinglab.core.analytics.recovery.ReadinessLevel
import app.pacinglab.core.analytics.recovery.SleepRating
import app.pacinglab.core.data.repository.HomeScreenWidget
import app.pacinglab.core.data.repository.WidgetChart
import app.pacinglab.core.data.sync.AfterSync
import app.pacinglab.core.designsystem.format.Formats
import app.pacinglab.core.designsystem.icon.PacingLabIcons
import app.pacinglab.core.designsystem.theme.ChartColors
import app.pacinglab.core.designsystem.theme.StatusColors
import app.pacinglab.core.model.RangeStanding
import app.pacinglab.core.ui.WidgetMeasure
import app.pacinglab.core.ui.icon
import app.pacinglab.core.ui.label
import app.pacinglab.core.ui.tint
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.stateIn
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToInt

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun widgetData(): WidgetData
}

/**
 * Today at a glance on the home screen, as much as fits ([WidgetLayout]): the athlete's measures (Settings,
 * Home-screen widget) in tiles, vitals as a panel, the training status on a line underneath and a chart of
 * the last seven days. It follows the data while its session runs and is redrawn after every sync and every
 * change in Settings; a tap opens the app.
 */
class TodayWidget : GlanceAppWidget() {
    // Laid out for the size the launcher reports, so that tiles fit the cells exactly.
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java).widgetData()
        coroutineScope {
            val snapshot = data.observe().stateIn(this)
            provideContent {
                val current by snapshot.collectAsState()
                GlanceTheme { WidgetContent(current) }
            }
        }
    }
}

/** The widget's contents for [snapshot] at the current size. */
@Composable
internal fun WidgetContent(snapshot: WidgetSnapshot) {
    val context = LocalContext.current
    val size = LocalSize.current
    val values = snapshot.values
    val layout = WidgetLayout.of(
        width = size.width.value,
        height = size.height.value,
        fontScale = context.resources.configuration.fontScale,
        measures = snapshot.measures,
        line = WidgetMeasure.STATUS in snapshot.measures && values.status != null,
        chart = values.charted,
    )
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(16.dp)
            .padding(layout.padding.dp)
            .clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = if (layout.chart) Alignment.Top else Alignment.CenterVertically,
    ) {
        Grid(layout, values)
        if (layout.line) {
            Spacer(GlanceModifier.height(WidgetLayout.LINE_GAP.dp))
            StatusLine(layout, values)
        }
        if (layout.chart) {
            Spacer(GlanceModifier.defaultWeight())
            ChartRow(layout, values)
        }
    }
}

/**
 * The tiles row by row. The vitals panel spans two rows, so the rows beside it are drawn as two columns of
 * half rows on either side of it. Gaps are padding (a widget row or column takes at most ten children).
 */
@Composable
private fun Grid(layout: WidgetLayout, values: WidgetValues) {
    val panel = layout.tiles.firstOrNull { it.panel }
    val used = layout.tiles.maxOfOrNull { it.row + if (it.panel) 2 else 1 } ?: 0
    val starts = generateSequence(0) { row -> if (panel?.row == row) row + 2 else row + 1 }.takeWhile { it < used }.toList()
    Column {
        for (row in starts) {
            Box(GlanceModifier.padding(top = if (row > 0) WidgetLayout.GAP.dp else 0.dp)) {
                if (panel?.row == row) {
                    Row {
                        if (panel.column > 0) HalfRows(layout, values, row, 0 until panel.column)
                        Box(GlanceModifier.padding(start = if (panel.column > 0) WidgetLayout.GAP.dp else 0.dp)) {
                            VitalsPanel(
                                values,
                                width = 2 * layout.tileWidth + WidgetLayout.GAP,
                                height =
                                2 * layout.tileHeight + WidgetLayout.GAP,
                            )
                        }
                        if (panel.column + 2 < layout.columns) {
                            Box(GlanceModifier.padding(start = WidgetLayout.GAP.dp)) {
                                HalfRows(layout, values, row, panel.column + 2 until layout.columns)
                            }
                        }
                    }
                } else {
                    TileRow(layout, values, row, 0 until layout.columns)
                }
            }
        }
    }
}

/** Two rows of tiles beside the vitals panel. */
@Composable
private fun HalfRows(layout: WidgetLayout, values: WidgetValues, row: Int, columns: IntRange) {
    Column {
        TileRow(layout, values, row, columns)
        Box(GlanceModifier.padding(top = WidgetLayout.GAP.dp)) { TileRow(layout, values, row + 1, columns) }
    }
}

/** Tiles side by side; a widget's padding lies inside its width, so each later tile is wider by the gap before it. */
@Composable
private fun TileRow(layout: WidgetLayout, values: WidgetValues, row: Int, columns: IntRange) {
    val byColumn = layout.tiles.filter { it.row == row && !it.panel }.associateBy { it.column }
    Row {
        for (column in columns) {
            val gap = if (column == columns.first) 0f else WidgetLayout.GAP
            Box(GlanceModifier.width((layout.tileWidth + gap).dp).height(layout.tileHeight.dp).padding(start = gap.dp)) {
                byColumn[column]?.let { Figure(it.measure, values) }
            }
        }
    }
}

/** A measure as a label, its value and, where it has one, a word under it. */
@Composable
private fun Figure(measure: WidgetMeasure, values: WidgetValues) {
    val context = LocalContext.current
    val (value, unit, word) = figure(context, measure, values)
    Column {
        Text(context.getString(measure.label), style = small(GlanceTheme.colors.onSurfaceVariant), maxLines = 1)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                value ?: DASH,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = WidgetLayout.VALUE_SP.sp,
                    fontWeight = FontWeight.Medium,
                ),
                maxLines = 1,
            )
            // The unit sits at the value's baseline, which the bottom of its smaller line misses by about this much.
            if (value != null && unit != null) {
                Text(
                    " $unit",
                    style = small(GlanceTheme.colors.onSurfaceVariant),
                    maxLines = 1,
                    modifier = GlanceModifier.padding(bottom = 3.dp),
                )
            }
        }
        if (word != null) Text(word, style = small(GlanceTheme.colors.onSurfaceVariant), maxLines = 1)
    }
}

/** A figure's value, its unit and the word under it. */
private data class FigureText(val value: String?, val unit: String? = null, val word: String? = null)

private fun figure(context: Context, measure: WidgetMeasure, v: WidgetValues): FigureText = when (measure) {
    WidgetMeasure.READINESS -> FigureText(v.readiness?.toString(), word = v.readinessLevel?.let { context.getString(it.word()) })

    WidgetMeasure.SLEEP_SCORE -> FigureText(v.sleepScore?.toString(), word = v.sleepRating?.let { context.getString(it.word()) })

    WidgetMeasure.FORM -> FigureText(v.form?.let { if (it > 0) "+$it" else it.toString() })

    WidgetMeasure.HRV -> FigureText(
        v.hrv?.roundToInt()?.toString(),
        context.getString(R.string.widget_unit_ms),
        v.hrvStanding?.word(context),
    )

    WidgetMeasure.RESTING_HR -> FigureText(
        v.restingHr?.roundToInt()?.toString(),
        context.getString(R.string.widget_unit_bpm),
        v.restingStanding?.word(context),
    )

    WidgetMeasure.TIME_ASLEEP -> FigureText(v.asleepMillis?.let(Formats::hoursMinutes))

    WidgetMeasure.LOAD_WEEK -> FigureText(v.loadWeek?.roundToInt()?.toString(), word = context.getString(R.string.widget_this_week))

    WidgetMeasure.VO2MAX -> FigureText(v.vo2max?.let { "%.1f".format(Locale.getDefault(), it) })

    // Vitals in a single tile, when there is no room for the panel: how many of those judged are in range.
    WidgetMeasure.VITALS -> if (v.vitalsJudged > 0) {
        FigureText(
            context.getString(R.string.widget_vitals_ratio, v.vitalsInRange, v.vitalsJudged),
            word = context.getString(R.string.widget_in_range),
        )
    } else {
        FigureText(null)
    }

    WidgetMeasure.STATUS -> FigureText(v.status?.let { context.getString(it.label()) })
}

/** Last night's vitals as rows of name, value and a mark for where each stands, like the Today card. */
@Composable
private fun VitalsPanel(values: WidgetValues, width: Float, height: Float) {
    val context = LocalContext.current
    Column(GlanceModifier.width(width.dp).height(height.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(context.getString(WidgetMeasure.VITALS.label), style = small(GlanceTheme.colors.onSurfaceVariant), maxLines = 1)
            if (values.vitalsAlert) {
                Spacer(GlanceModifier.width(4.dp))
                Mark(PacingLabIcons.Warning, StatusColors.Warning, StatusColors.Warning, context.getString(R.string.widget_vitals_alert))
            }
        }
        if (values.vitals.isEmpty()) {
            Text(DASH, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = WidgetLayout.VALUE_SP.sp), maxLines = 1)
        }
        for (vital in values.vitals) {
            Row(
                modifier = GlanceModifier.fillMaxWidth().padding(top = VITAL_SPACING.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    context.getString(vital.metric.shortName()),
                    style = small(GlanceTheme.colors.onSurfaceVariant),
                    maxLines = 1,
                    modifier = GlanceModifier.defaultWeight(),
                )
                Text(
                    vital.metric.widgetValue(vital.value),
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = VITAL_SP.sp, fontWeight = FontWeight.Medium),
                    maxLines = 1,
                )
                Spacer(GlanceModifier.width(4.dp))
                StandingMark(vital.standing)
            }
        }
    }
}

/** The pill of the Today card reduced to its icon: a check in range, an arrow outside it, a clock while learning. */
@Composable
private fun StandingMark(standing: RangeStanding?) {
    val context = LocalContext.current
    when (standing) {
        RangeStanding.IN_RANGE -> Mark(
            PacingLabIcons.CheckCircle,
            StatusColors.Good,
            StatusColors.Good,
            context.getString(R.string.widget_in_range),
        )

        RangeStanding.ABOVE ->
            Mark(
                PacingLabIcons.ArrowUp,
                ChartColors.Light.outOfRange,
                ChartColors.Dark.outOfRange,
                context.getString(R.string.widget_above),
            )

        RangeStanding.BELOW ->
            Mark(
                PacingLabIcons.ArrowDown,
                ChartColors.Light.outOfRange,
                ChartColors.Dark.outOfRange,
                context.getString(R.string.widget_below),
            )

        null -> Mark(PacingLabIcons.Schedule, ChartColors.Light.muted, ChartColors.Dark.muted, context.getString(R.string.widget_learning))
    }
}

@Composable
private fun Mark(@DrawableRes icon: Int, day: Color, night: Color, description: String) {
    Image(
        provider = ImageProvider(icon),
        contentDescription = description,
        colorFilter = ColorFilter.tint(ColorProvider(day = day, night = night)),
        modifier = GlanceModifier.size(MARK_SIZE.dp),
    )
}

/** The training status in a word, after its icon; named in full when there is room. */
@Composable
private fun StatusLine(layout: WidgetLayout, values: WidgetValues) {
    val context = LocalContext.current
    val status = values.status ?: return
    val word = context.getString(status.label())
    Row(verticalAlignment = Alignment.CenterVertically) {
        Mark(status.icon(), status.tint(), status.tint(), "")
        Spacer(GlanceModifier.width(4.dp))
        Text(
            if (layout.columns >= 3) context.getString(R.string.widget_status_line, word) else word,
            style = small(GlanceTheme.colors.onSurfaceVariant),
            maxLines = 1,
        )
    }
}

/** The chart's name beside or above it, and the last seven days as a line. */
@Composable
private fun ChartRow(layout: WidgetLayout, values: WidgetValues) {
    val context = LocalContext.current
    val name = context.getString(values.chart.label())
    val description = context.getString(R.string.widget_chart_description, name)
    if (layout.chartLabelBeside) {
        val chartWidth = layout.innerWidth - WidgetLayout.CHART_LABEL_WIDTH - WidgetLayout.GAP
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(GlanceModifier.width(WidgetLayout.CHART_LABEL_WIDTH.dp)) {
                Text(name, style = small(GlanceTheme.colors.onSurfaceVariant), maxLines = 1)
                Text(context.getString(R.string.widget_chart_days), style = small(GlanceTheme.colors.onSurfaceVariant), maxLines = 1)
            }
            Spacer(GlanceModifier.width(WidgetLayout.GAP.dp))
            Image(
                provider = ImageProvider(sparkline(context, values.history, chartWidth, layout.chartHeight)),
                contentDescription = description,
                modifier = GlanceModifier.width(chartWidth.dp).height(layout.chartHeight.dp),
            )
        }
    } else {
        Column {
            Text(context.getString(R.string.widget_chart_label, name), style = small(GlanceTheme.colors.onSurfaceVariant), maxLines = 1)
            Image(
                provider = ImageProvider(sparkline(context, values.history, layout.innerWidth, layout.chartHeight)),
                contentDescription = description,
                modifier = GlanceModifier.width(layout.innerWidth.dp).height(layout.chartHeight.dp),
            )
        }
    }
}

private fun small(color: ColorProvider) = TextStyle(color = color, fontSize = WidgetLayout.SMALL_SP.sp)

private fun WidgetChart.label(): Int = when (this) {
    WidgetChart.READINESS -> WidgetMeasure.READINESS.label
    WidgetChart.SLEEP_SCORE -> WidgetMeasure.SLEEP_SCORE.label
    WidgetChart.HRV -> WidgetMeasure.HRV.label
    WidgetChart.RESTING_HR -> WidgetMeasure.RESTING_HR.label
    WidgetChart.FORM -> WidgetMeasure.FORM.label
    WidgetChart.TIME_ASLEEP, WidgetChart.NONE -> WidgetMeasure.TIME_ASLEEP.label
}

private fun NightlyMetric.shortName(): Int = when (this) {
    NightlyMetric.HRV -> R.string.widget_vital_hrv
    NightlyMetric.RESTING_HR, NightlyMetric.SLEEPING_HR -> R.string.widget_vital_resting_hr
    NightlyMetric.BREATHING_RATE -> R.string.widget_vital_breathing
    NightlyMetric.OXYGEN_SATURATION -> R.string.widget_vital_oxygen
    NightlyMetric.SKIN_TEMPERATURE -> R.string.widget_vital_skin
}

/** A vital without its unit, which the panel has no room for; the name makes it plain. */
private fun NightlyMetric.widgetValue(value: Double): String = when (this) {
    NightlyMetric.BREATHING_RATE -> "%.1f".format(Locale.getDefault(), value)
    NightlyMetric.OXYGEN_SATURATION -> "%.1f%%".format(Locale.getDefault(), value)
    NightlyMetric.SKIN_TEMPERATURE -> "%+.1f°".format(Locale.getDefault(), value)
    else -> value.roundToInt().toString()
}

private fun RangeStanding.word(context: Context): String = when (this) {
    RangeStanding.IN_RANGE -> context.getString(R.string.widget_in_range)
    RangeStanding.ABOVE -> context.getString(R.string.widget_above)
    RangeStanding.BELOW -> context.getString(R.string.widget_below)
}

private fun ReadinessLevel.word(): Int = when (this) {
    ReadinessLevel.READY -> R.string.widget_ready
    ReadinessLevel.MODERATE -> R.string.widget_moderate
    ReadinessLevel.LOW -> R.string.widget_low
}

private fun SleepRating.word(): Int = when (this) {
    SleepRating.GOOD -> R.string.widget_good
    SleepRating.FAIR -> R.string.widget_fair
    SleepRating.POOR -> R.string.widget_poor
}

/**
 * The last seven days as a line with a dot per day, the newest larger, drawn into a bitmap (a widget
 * cannot draw itself). Days without a value break the line.
 */
private fun sparkline(context: Context, values: List<Double?>, widthDp: Float, heightDp: Float): Bitmap {
    val density = context.resources.displayMetrics.density
    val width = (widthDp * density).toInt().coerceAtLeast(1)
    val height = (heightDp * density).toInt().coerceAtLeast(1)
    val bitmap = createBitmap(width, height)
    val canvas = Canvas(bitmap)
    val dark = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    val colors = if (dark) ChartColors.Dark else ChartColors.Light
    val known = values.filterNotNull()
    if (known.isEmpty()) return bitmap
    val low = known.min()
    val high = known.max().takeIf { it > low } ?: (low + 1)
    val inset = 6 * density
    fun x(i: Int) = inset + i * (width - 2 * inset) / (values.size - 1).coerceAtLeast(1)
    fun y(v: Double) = (height - inset - (v - low) / (high - low) * (height - 2 * inset)).toFloat()
    val baseline = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colors.baseline.toArgb() }
    canvas.drawRect(0f, height - 1 * density, width.toFloat(), height.toFloat(), baseline)
    val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colors.series[0].toArgb()
        style = Paint.Style.STROKE
        strokeWidth = 2 * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    val path = Path()
    var drawing = false
    values.forEachIndexed { i, v ->
        if (v == null) {
            drawing = false
        } else if (drawing) {
            path.lineTo(x(i), y(v))
        } else {
            path.moveTo(x(i), y(v))
            drawing = true
        }
    }
    canvas.drawPath(path, line)
    val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colors.series[0].toArgb() }
    values.forEachIndexed { i, v ->
        if (v != null) canvas.drawCircle(x(i), y(v), (if (i == values.lastIndex) 4f else 2.5f) * density, dot)
    }
    return bitmap
}

private const val DASH = "–"
private const val VITAL_SP = 12f
private const val VITAL_SPACING = 2f
private const val MARK_SIZE = 12f

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}

/** Redraws the widget after every sync with the new values, and when its measures change. */
class WidgetRefresher @Inject constructor(@param:ApplicationContext private val context: Context) :
    AfterSync,
    HomeScreenWidget {
    override suspend fun run() = update()

    override suspend fun update() = TodayWidget().updateAll(context)
}
