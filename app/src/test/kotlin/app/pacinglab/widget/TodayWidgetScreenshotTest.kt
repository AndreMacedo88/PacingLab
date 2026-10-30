package app.pacinglab.widget

import android.app.Activity
import android.app.Application
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.compose
import androidx.glance.appwidget.provideContent
import app.pacinglab.core.analytics.load.TrainingStatusKind
import app.pacinglab.core.analytics.recovery.NightlyMetric
import app.pacinglab.core.analytics.recovery.ReadinessLevel
import app.pacinglab.core.analytics.recovery.SleepRating
import app.pacinglab.core.data.repository.WidgetChart
import app.pacinglab.core.model.RangeStanding
import app.pacinglab.core.ui.WidgetMeasure
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.roundToInt

/**
 * The widget rendered by Glance into the views a launcher would show, at the cell sizes a Galaxy launcher
 * with a dense grid reports (63 by 95 dp a cell) and the owner's slightly smaller font. Made-up values.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [Config.NEWEST_SDK], qualifiers = "w384dp-h832dp-night-xxhdpi", application = Application::class)
class TodayWidgetScreenshotTest {
    private val values = WidgetValues(
        readiness = 76,
        readinessLevel = ReadinessLevel.READY,
        sleepScore = 81,
        sleepRating = SleepRating.GOOD,
        form = -4,
        hrv = 58.0,
        hrvStanding = RangeStanding.IN_RANGE,
        restingHr = 51.0,
        restingStanding = RangeStanding.IN_RANGE,
        asleepMillis = 7 * 3_600_000L + 25 * 60_000L,
        status = TrainingStatusKind.PRODUCTIVE,
        vitals = listOf(
            WidgetVital(NightlyMetric.HRV, 58.0, RangeStanding.IN_RANGE),
            WidgetVital(NightlyMetric.RESTING_HR, 51.0, RangeStanding.IN_RANGE),
            WidgetVital(NightlyMetric.BREATHING_RATE, 15.2, RangeStanding.ABOVE),
            WidgetVital(NightlyMetric.SKIN_TEMPERATURE, -0.2, null),
        ),
        chart = WidgetChart.READINESS,
        history = listOf(68.0, 72.0, 80.0, null, 74.0, 70.0, 76.0),
    )

    private val measures =
        listOf(WidgetMeasure.VITALS, WidgetMeasure.READINESS, WidgetMeasure.SLEEP_SCORE, WidgetMeasure.STATUS, WidgetMeasure.FORM)

    @Test
    fun oneByOne() = capture(1, 1)

    @Test
    fun oneByOneReadinessFirst() = capture(1, 1, measures.drop(1))

    @Test
    fun twoByOne() = capture(2, 1)

    @Test
    fun fourByOne() = capture(4, 1)

    @Test
    fun twoByTwo() = capture(2, 2)

    @Test
    fun threeByTwo() = capture(3, 2)

    @Test
    fun fourByTwo() = capture(4, 2)

    @Test
    fun fourByTwoAlert() = capture(
        4,
        2,
        values = values.copy(
            vitals = values.vitals.map {
                if (it.metric ==
                    NightlyMetric.HRV
                ) {
                    it.copy(value = 38.0, standing = RangeStanding.BELOW)
                } else {
                    it
                }
            },
        ),
    )

    @Test
    fun fourByThreeMore() = capture(4, 3, measures + listOf(WidgetMeasure.HRV, WidgetMeasure.RESTING_HR, WidgetMeasure.TIME_ASLEEP))

    @Test
    @Config(qualifiers = "w384dp-h832dp-notnight-xxhdpi")
    fun fourByTwoLight() = capture(4, 2)

    private fun capture(
        columns: Int,
        rows: Int,
        measures: List<WidgetMeasure> = this.measures,
        values: WidgetValues = this.values,
        fontScale: Float = 0.9f,
    ) {
        RuntimeEnvironment.setFontScale(fontScale)
        val context: Context = RuntimeEnvironment.getApplication()
        val size = DpSize(cellWidth(columns).dp, cellHeight(rows).dp)
        val views = runBlocking { FixedWidget(WidgetSnapshot(measures, values)).compose(context, size = size) }
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val density = context.resources.displayMetrics.density
        // A dark surface around the widget, roughly a wallpaper, so its rounded corners show.
        val frame = FrameLayout(activity).apply { setBackgroundColor(WALLPAPER) }
        val host = FrameLayout(activity)
        frame.addView(host, FrameLayout.LayoutParams((size.width.value * density).roundToInt(), (size.height.value * density).roundToInt()))
        host.addView(views.apply(activity, host))
        activity.setContentView(frame, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        frame.padding(MARGIN * density)
        frame.captureRoboImage()
    }

    private fun View.padding(px: Float) = setPadding(px.roundToInt(), px.roundToInt(), px.roundToInt(), px.roundToInt())

    /** The widget with fixed contents, laid out for the size it is composed at. */
    private class FixedWidget(private val snapshot: WidgetSnapshot) : GlanceAppWidget() {
        override val sizeMode = SizeMode.Exact

        override suspend fun provideGlance(context: Context, id: GlanceId) = provideContent { GlanceTheme { WidgetContent(snapshot) } }
    }

    private companion object {
        /** The sizes the Galaxy launcher reported for widgets of one to four cells. */
        fun cellWidth(columns: Int) = listOf(63f, 121f, 184f, 251f)[columns - 1]

        fun cellHeight(rows: Int) = listOf(95f, 206f, 317f)[rows - 1]

        const val MARGIN = 12f
        const val WALLPAPER = 0xFF1B2433.toInt()
    }
}
