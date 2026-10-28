package app.pacinglab.feature.metrics

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import app.pacinglab.core.analytics.load.FormDay
import app.pacinglab.core.analytics.recovery.Night
import app.pacinglab.core.analytics.recovery.NightlyMetric
import app.pacinglab.core.analytics.recovery.SleepScore
import app.pacinglab.core.analytics.zones.Zones
import app.pacinglab.core.data.repository.DayHeartRate
import app.pacinglab.core.designsystem.theme.PacingLabTheme
import app.pacinglab.core.model.HeartRateAnchors
import app.pacinglab.core.model.HeartRateSeries
import app.pacinglab.core.model.SleepStageType
import app.pacinglab.core.model.ZoneModel
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.sin

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [Config.NEWEST_SDK], qualifiers = "w384dp-h1700dp-xxhdpi")
class MetricScreensScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 10, 28)

    /** A hundred nights: HRV wobbling around 60 ms with one low night last week and one night shared only in part. */
    private val nights: List<Night> = (100 downTo 0).map { back ->
        val date = today.minusDays(back.toLong())
        val asleep = 7 * 3_600_000L + (back % 5) * 600_000L
        val start = Instant.parse("${date.minusDays(1)}T22:00:00Z")
        Night(
            date = date,
            start = start,
            end = start.plusMillis(asleep + 30 * 60_000L),
            asleepMillis = asleep,
            stageMillis = mapOf(SleepStageType.LIGHT to asleep, SleepStageType.AWAKE to 30 * 60_000L),
            hrvMs = when (back) {
                1 -> null
                4 -> 41.0
                else -> 60.0 + 7 * sin(back * 1.3)
            },
            restingHr = 56.0 + (back % 3),
            respiratoryRate = 12.4 + 0.3 * sin(back * 0.7),
            zone = ZoneOffset.UTC,
            hrvPartial = back == 1,
        )
    }

    @Test
    fun hrvWeek() = capture { NightlyMetricScreen(NightlyMetric.HRV, NightlyMetricState(NightlyMetric.HRV, nights, today), onBack = {}) }

    @Test
    fun hrvThreeMonths() = capture {
        NightlyMetricScreen(NightlyMetric.HRV, NightlyMetricState(NightlyMetric.HRV, nights, today), onBack = {
        }, initialSpan = Span.THREE_MONTHS)
    }

    @Test
    fun restingHeartRateMonthDark() = capture(dark = true) {
        NightlyMetricScreen(
            NightlyMetric.RESTING_HR,
            NightlyMetricState(NightlyMetric.RESTING_HR, nights, today),
            onBack = {},
            initialSpan = Span.MONTH,
        )
    }

    @Test
    fun readinessWeek() {
        val form = (100 downTo 0).map { back ->
            FormDay(
                today.minusDays(back.toLong()),
                30.0,
                30.0,
                25.0 + 6 * sin(back * 0.8),
                5.0 - 6 * sin(back * 0.8),
            )
        }
        capture {
            ReadinessScreen(
                ReadinessState(nights, SleepScore.all(nights), form.associateBy { it.date }, today),
                onBack = {},
            )
        }
    }

    @Test
    fun heartRateDay() {
        val start = today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val samples = 19 * 60 * 4
        val bpm = IntArray(samples) { n ->
            val hour = n / 240.0
            when {
                hour < 7 -> 50 + (3 * sin(n / 70.0)).toInt()
                hour in 17.0..17.75 -> 158
                else -> 68 + (6 * sin(n / 90.0)).toInt()
            }
        }
        val day = DayHeartRate(today, ZoneOffset.UTC, HeartRateSeries(LongArray(samples) { start + it * 15_000L }, bpm))
        val zones = Zones.of(ZoneModel.LTHR_FIVE, HeartRateAnchors(maxHr = 186, thresholdHr = 167, restingHr = 52))
        capture { HeartRateDayScreen(HeartRateDayState(day, zones, 56.0, today), onBack = {}, onDay = {}) }
    }

    @Test
    fun choosingASpanShowsItsPeriod() {
        compose.setContent {
            PacingLabTheme { NightlyMetricScreen(NightlyMetric.HRV, NightlyMetricState(NightlyMetric.HRV, nights, today), onBack = {}) }
        }

        compose.onNodeWithText("Month").performClick()

        compose.onNodeWithText("– 28 Oct", substring = true).assertExists()
    }

    private fun capture(dark: Boolean = false, content: @Composable () -> Unit) {
        compose.setContent { PacingLabTheme(darkTheme = dark) { content() } }
        compose.onRoot().captureRoboImage()
    }
}
