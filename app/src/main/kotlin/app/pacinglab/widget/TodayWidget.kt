package app.pacinglab.widget

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.createBitmap
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
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import app.pacinglab.MainActivity
import app.pacinglab.R
import app.pacinglab.core.analytics.load.FitnessModel
import app.pacinglab.core.analytics.load.TrainingLoads
import app.pacinglab.core.analytics.load.TrainingStatusKind
import app.pacinglab.core.analytics.recovery.NightlyMetric
import app.pacinglab.core.analytics.recovery.NightlyRange
import app.pacinglab.core.analytics.recovery.Readiness
import app.pacinglab.core.analytics.recovery.ReadinessLevel
import app.pacinglab.core.analytics.recovery.SleepRating
import app.pacinglab.core.analytics.recovery.Vitals
import app.pacinglab.core.data.repository.CardSpec
import app.pacinglab.core.data.repository.Dashboard
import app.pacinglab.core.data.repository.DashboardRepository
import app.pacinglab.core.data.repository.HomeScreenWidget
import app.pacinglab.core.data.repository.NightRepository
import app.pacinglab.core.data.repository.PerformanceRepository
import app.pacinglab.core.data.repository.TrainingLoadRepository
import app.pacinglab.core.data.repository.TrainingStatusRepository
import app.pacinglab.core.data.sync.AfterSync
import app.pacinglab.core.designsystem.format.Formats
import app.pacinglab.core.designsystem.theme.ChartColors
import app.pacinglab.core.model.RangeStanding
import app.pacinglab.core.ui.WidgetMeasure
import app.pacinglab.core.ui.label
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.LocalDate
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * What the widget can show; each value is null when it is not known (shown as a dash) or not asked for.
 *
 * @property history the last seven days of [charted], oldest first, for the chart of a large widget.
 */
data class WidgetValues(
    val readiness: Int? = null,
    val readinessLevel: ReadinessLevel? = null,
    val sleepScore: Int? = null,
    val sleepRating: SleepRating? = null,
    val form: Int? = null,
    val hrv: Double? = null,
    val hrvStanding: RangeStanding? = null,
    val restingHr: Double? = null,
    val restingStanding: RangeStanding? = null,
    val asleepMillis: Long? = null,
    val loadWeek: Double? = null,
    val vo2max: Double? = null,
    val status: TrainingStatusKind? = null,
    val vitalsOutside: Int? = null,
    val charted: WidgetMeasure? = null,
    val history: List<Double?> = emptyList(),
)

/** The measures the athlete chose, in order, with their values. */
data class WidgetSnapshot(val measures: List<WidgetMeasure>, val values: WidgetValues)

/** Gathers the widget's values from the same repositories the screens use, only those it shows. */
class WidgetData @Inject constructor(
    private val nights: NightRepository,
    private val trainingLoad: TrainingLoadRepository,
    private val training: TrainingStatusRepository,
    private val performance: PerformanceRepository,
    private val dashboards: DashboardRepository,
    private val clock: Clock,
) {
    suspend fun load(): WidgetSnapshot {
        val measures = dashboards.layout(Dashboard.WIDGET).first()
            .arrange(WidgetMeasure.entries.map { CardSpec(it.id, it.hiddenByDefault) })
            .filter { it.visible }
            .mapNotNull { WidgetMeasure.of(it.id) }
        val shown = measures.toSet()
        val today = LocalDate.now(clock)
        val sleep = nights.observeSleep().first()
        val loads = trainingLoad.observeWorkoutLoads().first()
        val form = FitnessModel.series(TrainingLoads.daily(loads), until = today)
        val formByDate = form.associateBy { it.date }
        val readiness = Readiness.of(sleep.nights, formByDate[today], today, sleep.scores)
        val night = sleep.nights.lastOrNull { it.date == today }
        val score = sleep.scores[today]
        val vitals = if (WidgetMeasure.VITALS in shown) Vitals.of(sleep.nights, today) else null
        fun standing(metric: NightlyMetric, value: Double?) = value?.let { NightlyRange.of(sleep.nights, metric, today)?.standing(it) }
        val charted = measures.firstOrNull { it in CHARTED }
        val byDate = sleep.nights.associateBy { it.date }
        val history = (DAYS - 1 downTo 0).map { back ->
            val day = today.minusDays(back.toLong())
            when (charted) {
                WidgetMeasure.READINESS -> Readiness.of(sleep.nights, formByDate[day], day, sleep.scores).score?.toDouble()
                WidgetMeasure.SLEEP_SCORE -> sleep.scores[day]?.score?.toDouble()
                WidgetMeasure.HRV -> byDate[day]?.hrvMs
                WidgetMeasure.RESTING_HR -> byDate[day]?.restingHr
                WidgetMeasure.FORM -> formByDate[day]?.form
                else -> null
            }
        }
        return WidgetSnapshot(
            measures = measures,
            values = WidgetValues(
                readiness = readiness.score,
                readinessLevel = readiness.level,
                sleepScore = score?.score,
                sleepRating = score?.rating,
                form = form.lastOrNull()?.form?.roundToInt(),
                hrv = night?.hrvMs,
                hrvStanding = standing(NightlyMetric.HRV, night?.hrvMs),
                restingHr = night?.restingHr,
                restingStanding = standing(NightlyMetric.RESTING_HR, night?.restingHr),
                asleepMillis = night?.asleepMillis,
                loadWeek = if (WidgetMeasure.LOAD_WEEK in shown) TrainingLoads.weeks(loads, today, 1).first().load else null,
                vo2max = if (WidgetMeasure.VO2MAX in shown) performance.observeVo2Max().first().trend?.current?.value else null,
                status = if (WidgetMeasure.STATUS in shown) training.observeStatus().first()?.kind else null,
                vitalsOutside = vitals?.takeIf { it.judged > 0 }?.outside?.size,
                charted = charted,
                history = if (charted != null) history else emptyList(),
            ),
        )
    }

    private companion object {
        const val DAYS = 7
        val CHARTED =
            setOf(WidgetMeasure.READINESS, WidgetMeasure.SLEEP_SCORE, WidgetMeasure.HRV, WidgetMeasure.RESTING_HR, WidgetMeasure.FORM)
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun widgetData(): WidgetData
}

/**
 * Today at a glance on the home screen, as much as fits: the athlete's measures (Settings, Home-screen
 * widget) as figures, as many per row as the width allows and a second row when it is tall enough, the
 * worded ones (training status, vitals) on a line underneath, and on a large widget the last seven days
 * of the first figure that has a history. It is redrawn after every sync; a tap opens the app.
 */
class TodayWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(
        setOf(
            DpSize(110.dp, 60.dp),
            DpSize(180.dp, 60.dp),
            DpSize(250.dp, 60.dp),
            DpSize(320.dp, 60.dp),
            DpSize(180.dp, 130.dp),
            DpSize(250.dp, 130.dp),
            DpSize(320.dp, 130.dp),
            DpSize(250.dp, 200.dp),
            DpSize(320.dp, 200.dp),
        ),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java).widgetData().load()
        provideContent { GlanceTheme { Content(snapshot) } }
    }

    @Composable
    private fun Content(snapshot: WidgetSnapshot) {
        val context = LocalContext.current
        val size = LocalSize.current
        val columns = (size.width.value / FIGURE_WIDTH).toInt().coerceIn(1, MAX_COLUMNS)
        val rows = if (size.height >= TWO_ROWS) 2 else 1
        val figures = snapshot.measures.filterNot { it.line }.take(columns * rows)
        val line = snapshot.measures.filter { it.line }.mapNotNull { lineText(context, it, snapshot.values) }
        val chart = size.height >= WITH_CHART && snapshot.values.charted != null && snapshot.values.history.any { it != null }
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .cornerRadius(16.dp)
                .padding(12.dp)
                .clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = if (chart) Alignment.Top else Alignment.CenterVertically,
        ) {
            figures.chunked(columns).forEachIndexed { index, row ->
                if (index > 0) Spacer(GlanceModifier.height(8.dp))
                Row(modifier = GlanceModifier.fillMaxWidth()) {
                    for (measure in row) Figure(context, measure, snapshot.values, GlanceModifier.defaultWeight())
                    // Keeps the columns of a short last row as wide as those above.
                    repeat(columns - row.size) { Spacer(GlanceModifier.defaultWeight()) }
                }
            }
            if (line.isNotEmpty() && size.height >= WITH_LINE) {
                Spacer(GlanceModifier.height(6.dp))
                Text(
                    text = line.joinToString(" · "),
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
                    maxLines = 1,
                )
            }
            if (chart) {
                val charted = snapshot.values.charted
                Spacer(GlanceModifier.height(10.dp))
                Text(
                    context.getString(R.string.widget_chart_label, context.getString(charted.label)),
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
                    maxLines = 1,
                )
                val width = size.width - 24.dp
                Image(
                    provider = ImageProvider(sparkline(context, snapshot.values.history, width.value, CHART_HEIGHT)),
                    contentDescription = context.getString(R.string.widget_chart_description, context.getString(charted.label)),
                    modifier = GlanceModifier.fillMaxWidth().height(CHART_HEIGHT.dp),
                )
            }
        }
    }

    @Composable
    private fun Figure(context: Context, measure: WidgetMeasure, values: WidgetValues, modifier: GlanceModifier = GlanceModifier) {
        val (value, word) = figure(context, measure, values)
        Column(modifier = modifier) {
            Text(
                context.getString(measure.label),
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
                maxLines = 1,
            )
            Text(
                value ?: DASH,
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 22.sp, fontWeight = FontWeight.Medium),
                maxLines = 1,
            )
            if (word != null) Text(word, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp), maxLines = 1)
        }
    }

    /** A figure's value and the word under it. */
    private fun figure(context: Context, measure: WidgetMeasure, v: WidgetValues): Pair<String?, String?> = when (measure) {
        WidgetMeasure.READINESS -> v.readiness?.toString() to v.readinessLevel?.let { context.getString(it.word()) }

        WidgetMeasure.SLEEP_SCORE -> v.sleepScore?.toString() to v.sleepRating?.let { context.getString(it.word()) }

        WidgetMeasure.FORM -> v.form?.let { if (it > 0) "+$it" else it.toString() } to null

        WidgetMeasure.HRV -> v.hrv?.let { context.getString(R.string.widget_ms, it.roundToInt()) } to v.hrvStanding?.word(context)

        WidgetMeasure.RESTING_HR ->
            v.restingHr?.let { context.getString(R.string.widget_bpm, it.roundToInt()) } to v.restingStanding?.word(context)

        WidgetMeasure.TIME_ASLEEP -> v.asleepMillis?.let(Formats::hoursMinutes) to null

        WidgetMeasure.LOAD_WEEK -> v.loadWeek?.roundToInt()?.toString() to context.getString(R.string.widget_this_week)

        WidgetMeasure.VO2MAX -> v.vo2max?.let { "%.1f".format(Locale.getDefault(), it) } to null

        WidgetMeasure.STATUS, WidgetMeasure.VITALS -> lineText(context, measure, v) to null
    }

    /** A worded measure for the line under the figures; null when unknown. */
    private fun lineText(context: Context, measure: WidgetMeasure, v: WidgetValues): String? = when (measure) {
        WidgetMeasure.STATUS -> v.status?.let { context.getString(it.label()) }

        WidgetMeasure.VITALS -> v.vitalsOutside?.let {
            if (it == 0) {
                context.getString(R.string.widget_vitals_typical)
            } else {
                context.resources.getQuantityString(R.plurals.widget_vitals_outside, it, it)
            }
        }

        else -> null
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

    private companion object {
        const val DASH = "–"

        /** Width a figure needs, in dp, to fit a three-digit value and its label. */
        const val FIGURE_WIDTH = 80f
        const val MAX_COLUMNS = 4
        val TWO_ROWS = 125.dp
        val WITH_LINE = 90.dp
        val WITH_CHART = 190.dp
        const val CHART_HEIGHT = 44f
    }
}

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
