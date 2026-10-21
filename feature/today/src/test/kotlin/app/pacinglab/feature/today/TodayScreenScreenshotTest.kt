package app.pacinglab.feature.today

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.pacinglab.core.analytics.load.FitnessModel
import app.pacinglab.core.analytics.load.FormDay
import app.pacinglab.core.analytics.load.WeekStrain
import app.pacinglab.core.analytics.load.WeekSummary
import app.pacinglab.core.analytics.recovery.Night
import app.pacinglab.core.analytics.recovery.Readiness
import app.pacinglab.core.designsystem.theme.PacingLabTheme
import app.pacinglab.core.model.Route
import app.pacinglab.core.model.SleepStageType
import app.pacinglab.core.model.Sport
import assertk.assertThat
import assertk.assertions.isEqualTo
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [Config.NEWEST_SDK], qualifiers = "w384dp-h1500dp-xxhdpi")
class TodayScreenScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 11, 18)
    private val cest = ZoneOffset.ofHours(1)

    private fun night(date: LocalDate, hrv: Double, restingHr: Double, hours: Double, breathing: Double): Night {
        val asleep = (hours * 3_600_000).toLong()
        return Night(
            date = date,
            start = Instant.parse("${date.minusDays(1)}T21:30:00Z"),
            end = Instant.parse("${date}T05:45:00Z"),
            asleepMillis = asleep,
            stageMillis = mapOf(
                SleepStageType.DEEP to asleep / 6,
                SleepStageType.REM to asleep / 5,
                SleepStageType.LIGHT to asleep - asleep / 6 - asleep / 5,
                SleepStageType.AWAKE to 35 * 60_000L,
            ),
            hrvMs = hrv,
            restingHr = restingHr,
            respiratoryRate = breathing,
        )
    }

    /** [count] nights up to [last], wobbling around HRV 60 ms and resting heart rate 52. */
    private fun nights(count: Int, last: LocalDate = today) = (count - 1 downTo 0).map { back ->
        val w = sin(back * 1.7)
        night(last.minusDays(back.toLong()), 60.0 * exp(0.08 * w), 52.0 + 1.5 * w, 7.4 + 0.5 * cos(back * 0.9), 12.3 + 0.3 * w)
    }

    /** Six weeks of four sessions a week. */
    private val form: List<FormDay> = FitnessModel.series(
        (0L until 120L).mapNotNull { back ->
            val date = today.minusDays(back)
            when (date.dayOfWeek) {
                DayOfWeek.TUESDAY -> date to 58.0
                DayOfWeek.THURSDAY -> date to 35.0
                DayOfWeek.SATURDAY -> date to 20.0
                DayOfWeek.SUNDAY -> date to 64.0
                else -> null
            }
        }.toMap(),
        until = today,
    ).takeLast(TodayViewModel.FORM_DAYS)

    private val route: Route = run {
        val n = 90
        Route(
            LongArray(n) { it * 4_000L },
            DoubleArray(n) { 55.6761 + 0.0060 * it / n },
            DoubleArray(n) { 12.5683 + 0.0050 * it / n + 0.0004 * sin(it / 9.0) },
            DoubleArray(n) { Double.NaN },
            DoubleArray(n) { Double.NaN },
        )
    }

    private val workout = LastWorkout(
        sessionId = "s1",
        titles = listOf("Run", "Interval workout"),
        sport = Sport.RUN,
        start = Instant.parse("2026-11-17T17:31:00Z"),
        zoneOffset = cest,
        movingMillis = 35 * 60_000L + 49_000,
        distanceMeters = 6_940.0,
        averageHr = 159,
        load = 52.9,
        routes = listOf(route),
    )

    private val week = WeekSummary(
        start = LocalDate.of(2026, 11, 16),
        load = 93.0,
        durationMillis = 81 * 60_000L,
        workouts = 3,
        intensityMillis = longArrayOf(50 * 60_000L, 12 * 60_000L, 14 * 60_000L),
        strain = WeekStrain.of(doubleArrayOf(0.0, 58.0, 35.0, 0.0, 0.0, 0.0, 0.0)),
    )

    private fun loaded(nights: List<Night>) = TodayUiState.Loaded(
        today = today,
        readiness = Readiness.of(nights, form.last(), today),
        nights = nights.filter { it.date > today.minusDays(TodayViewModel.NIGHT_DAYS.toLong()) },
        form = form,
        week = week,
        weekLoads = listOf(0.0, 58.0, 35.0, 0.0, 0.0, 0.0, 0.0),
        weekSessions = 2,
        lastWorkout = workout,
        heartRateValuesSet = true,
    )

    @Test
    fun learningTheNormalRange() = capture(loaded(nights(6)))

    @Test
    fun ready() = capture(loaded(nights(40)))

    @Test
    fun readyDark() = capture(loaded(nights(40)), darkTheme = true)

    @Test
    fun takeItEasy() {
        val strained = nights(33, last = today.minusDays(7)) +
            (6 downTo 1).map { night(today.minusDays(it.toLong()), 46.0, 55.0, 6.4, 12.4) } +
            night(today, 41.0, 58.0, 5.6, 14.0)
        capture(loaded(strained))
    }

    @Test
    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    fun withoutHeartRateValues() = capture(loaded(nights(6)).copy(heartRateValuesSet = false))

    @Test
    fun theLastWorkoutOpensItsSession() {
        var opened: String? = null
        compose.setContent {
            PacingLabTheme {
                TodayScreen(loaded(nights(40)), onOpenSession = {
                    opened = it
                }, onOpenTrends = {}, onOpenPhysiology = {}, zoneId = ZoneOffset.UTC)
            }
        }

        compose.onNodeWithText("Run + Interval workout").performScrollTo().performClick()

        assertThat(opened).isEqualTo("s1")
    }

    private fun capture(state: TodayUiState, darkTheme: Boolean = false) {
        compose.setContent {
            PacingLabTheme(darkTheme = darkTheme) {
                TodayScreen(state, onOpenSession = {}, onOpenTrends = {}, onOpenPhysiology = {}, zoneId = ZoneOffset.UTC)
            }
        }
        compose.onRoot().captureRoboImage()
    }
}
