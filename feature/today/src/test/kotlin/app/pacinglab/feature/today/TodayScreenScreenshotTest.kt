package app.pacinglab.feature.today

import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.pacinglab.core.analytics.load.FitnessModel
import app.pacinglab.core.analytics.load.FormDay
import app.pacinglab.core.analytics.load.WeekStrain
import app.pacinglab.core.analytics.load.WeekSummary
import app.pacinglab.core.analytics.performance.Vo2MaxPoint
import app.pacinglab.core.analytics.performance.Vo2MaxSource
import app.pacinglab.core.analytics.performance.Vo2MaxTrend
import app.pacinglab.core.analytics.recovery.Night
import app.pacinglab.core.analytics.recovery.NightlyMetric
import app.pacinglab.core.analytics.recovery.NightlyRange
import app.pacinglab.core.analytics.recovery.Readiness
import app.pacinglab.core.analytics.recovery.SleepContinuity
import app.pacinglab.core.analytics.recovery.SleepScore
import app.pacinglab.core.analytics.zones.Zones
import app.pacinglab.core.data.repository.DashboardLayout
import app.pacinglab.core.data.repository.DayHeartRate
import app.pacinglab.core.data.repository.PlacedCard
import app.pacinglab.core.data.repository.Vo2MaxHistory
import app.pacinglab.core.designsystem.component.InfoDialog
import app.pacinglab.core.designsystem.theme.PacingLabTheme
import app.pacinglab.core.model.HeartRateAnchors
import app.pacinglab.core.model.HeartRateSeries
import app.pacinglab.core.model.Route
import app.pacinglab.core.model.SleepStageType
import app.pacinglab.core.model.Sport
import app.pacinglab.core.model.ZoneModel
import assertk.assertThat
import assertk.assertions.isEqualTo
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
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
import kotlin.math.roundToInt
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
        val start = Instant.parse("${date.minusDays(1)}T21:30:00Z")
        val end = start.plusMillis(asleep + 35 * 60_000L)
        return Night(
            date = date,
            start = start,
            end = end,
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
            continuity = SleepContinuity(start.plusSeconds(14 * 60L), end, 14 * 60_000L, 21 * 60_000L, 1),
            zone = cest,
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

    /** A warm-up, a minute's pause and five reps, heart rate following each change with a lag. */
    private val workoutHeartRate: HeartRateSeries = run {
        val start = Instant.parse("2026-11-17T17:31:00Z").toEpochMilli()
        val times = ArrayList<Long>()
        val bpm = ArrayList<Int>()
        var hr = 100.0
        for (second in 0 until 2_150) {
            if (second in 360 until 420) {
                hr = 128.0
                continue
            }
            val u = second - 420
            val target = when {
                second < 360 -> 148.0
                u < 5 * 361 - 120 && u % 361 < 241 -> 170.0 + 2 * (u / 361)
                u < 5 * 361 - 120 -> 135.0
                else -> 150.0
            }
            hr += (target - hr) / 30
            times += start + second * 1_000L
            bpm += (hr + 1.5 * sin(second / 7.0)).roundToInt()
        }
        HeartRateSeries(times.toLongArray(), bpm.toIntArray())
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
        heartRate = workoutHeartRate,
        zones = Zones.of(ZoneModel.LTHR_FIVE, HeartRateAnchors(maxHr = 186, thresholdHr = 167)),
    )

    private val week = WeekSummary(
        start = LocalDate.of(2026, 11, 16),
        load = 93.0,
        durationMillis = 81 * 60_000L,
        workouts = 3,
        intensityMillis = longArrayOf(50 * 60_000L, 12 * 60_000L, 14 * 60_000L),
        strain = WeekStrain.of(doubleArrayOf(0.0, 58.0, 35.0, 0.0, 0.0, 0.0, 0.0)),
    )

    private fun loaded(nights: List<Night>): TodayUiState.Loaded {
        val formByDate = form.associateBy { it.date }
        val history = (TodayViewModel.TILE_DAYS - 1 downTo 0).map { back ->
            val day = today.minusDays(back.toLong())
            Readiness.of(nights, formByDate[day], day)
        }
        return loaded(nights, history)
    }

    private fun loaded(nights: List<Night>, history: List<Readiness>) = TodayUiState.Loaded(
        today = today,
        readiness = history.last(),
        readinessHistory = history,
        nights = nights.filter { it.date > today.minusDays(TodayViewModel.NIGHT_DAYS.toLong()) },
        sleepScores = SleepScore.all(nights).filterKeys { it > today.minusDays(TodayViewModel.NIGHT_DAYS.toLong()) },
        form = form,
        week = week,
        weekLoads = listOf(0.0, 58.0, 35.0, 0.0, 0.0, 0.0, 0.0),
        weekSessions = 2,
        lastWorkout = workout,
        heartRateValuesSet = true,
        ranges = NightlyMetric.entries.associateWith { metric ->
            (TodayViewModel.TILE_DAYS - 1 downTo 0).map { back -> NightlyRange.of(nights, metric, today.minusDays(back.toLong())) }
        },
        rangeNights = NightlyMetric.entries.associateWith { NightlyRange.nightsBefore(nights, it, today) },
        extras = TodayExtras(heartRate = dayHeartRate, heartRateZones = Zones.of(ZoneModel.LTHR_FIVE, anchors)),
    )

    private val anchors = HeartRateAnchors(maxHr = 186, thresholdHr = 167, restingHr = 52)

    /** Today until 19:30: a quiet night, a walk in the morning and a run in the late afternoon, a sample every 15 s. */
    private val dayHeartRate: DayHeartRate = run {
        val start = today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val samples = (19 * 60 + 30) * 4
        val bpm = IntArray(samples) { n ->
            val m = n / 4
            val hour = m / 60.0
            when {
                hour < 7 -> 50 + (3 * sin(m / 17.0)).toInt()
                hour in 8.0..8.6 -> 95
                hour in 17.0..17.75 -> if (hour < 17.15) 130 else 158
                else -> 68 + (6 * sin(m / 23.0)).toInt()
            }
        }
        DayHeartRate(today, ZoneOffset.UTC, HeartRateSeries(LongArray(samples) { start + it * 15_000L }, bpm))
    }

    @Test
    fun learningTheNormalRange() = capture(loaded(nights(6)))

    @Test
    fun provisional() = capture(loaded(nights(10)))

    @Test
    fun ready() = capture(loaded(nights(40)).copy(rested = 4))

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

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    fun readinessExplanation() {
        compose.setContent {
            PacingLabTheme {
                InfoDialog(title = stringResource(R.string.readiness_title), text = stringResource(R.string.readiness_info), onDismiss = {})
            }
        }
        captureScreenRoboImage()
    }

    @Test
    fun theLastWorkoutOpensItsSession() {
        var opened: String? = null
        compose.setContent {
            PacingLabTheme {
                TodayScreen(loaded(nights(40)), onOpen = { opened = (it as? TodayDestination.Session)?.id }, zoneId = ZoneOffset.UTC)
            }
        }

        compose.onNodeWithText("Run + Interval workout").performScrollTo().performClick()

        assertThat(opened).isEqualTo("s1")
    }

    private val gym = GymToRate("g1", listOf("w9"), today.minusDays(1).atTime(17, 30).toInstant(ZoneOffset.UTC), null, 52 * 60_000L)

    @Test
    fun gymSessionToRate() = capture(loaded(nights(40)).copy(rested = 4, gymToRate = gym))

    @Test
    fun ratingTheGymSessionReportsItsWorkouts() {
        var rated: Pair<List<String>, Int>? = null
        compose.setContent {
            PacingLabTheme {
                TodayScreen(
                    loaded(nights(40)).copy(gymToRate = gym),
                    onOpen = {},
                    zoneId = ZoneOffset.UTC,
                    onRateGym = { ids, effort -> rated = ids to effort },
                )
            }
        }

        compose.onNodeWithContentDescription("8, Very, very hard").performScrollTo().performClick()

        assertThat(rated).isEqualTo(listOf("w9") to 8)
    }

    @Test
    @Config(qualifiers = "w384dp-h2800dp-xxhdpi")
    fun cardsFromOtherScreensCanJoinToday() {
        val all = DashboardLayout().arrange(TodayCard.catalog).map { PlacedCard(it.id, true) }
        val vo2 = (60L downTo 0L step 3).map { Vo2MaxPoint(today.minusDays(it), 52.0 + it % 5 * 0.4, Vo2MaxSource.FITBIT) }
        capture(
            loaded(nights(40)).let {
                it.copy(cards = all, extras = it.extras.copy(vo2max = Vo2MaxHistory(vo2, Vo2MaxTrend.of(vo2, today))))
            },
        )
    }

    @Test
    @Config(qualifiers = "w384dp-h1200dp-xxhdpi")
    fun arrangingToday() {
        compose.setContent {
            PacingLabTheme { TodayScreen(loaded(nights(40)), onOpen = {}, zoneId = ZoneOffset.UTC, initiallyEditing = true) }
        }
        compose.onRoot().captureRoboImage()
    }

    @Test
    fun aTileOpensItsDetailPage() {
        var opened: TodayDestination? = null
        compose.setContent {
            PacingLabTheme { TodayScreen(loaded(nights(40)), onOpen = { opened = it }, zoneId = ZoneOffset.UTC) }
        }

        compose.onNodeWithText("HRV").performScrollTo().performClick()

        assertThat(opened).isEqualTo(TodayDestination.Metric(NightlyMetric.HRV))
    }

    private fun capture(state: TodayUiState, darkTheme: Boolean = false) {
        compose.setContent {
            PacingLabTheme(darkTheme = darkTheme) {
                TodayScreen(state, onOpen = {}, zoneId = ZoneOffset.UTC)
            }
        }
        compose.onRoot().captureRoboImage()
    }
}
