package app.pacinglab.feature.performance

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.pacinglab.core.analytics.performance.AerobicEfficiency
import app.pacinglab.core.analytics.performance.BestPair
import app.pacinglab.core.analytics.performance.CriticalSpeed
import app.pacinglab.core.analytics.performance.CriticalSpeedGap
import app.pacinglab.core.analytics.performance.CriticalSpeedResult
import app.pacinglab.core.analytics.performance.DecouplingPoint
import app.pacinglab.core.analytics.performance.EfficiencyPoint
import app.pacinglab.core.analytics.performance.Effort
import app.pacinglab.core.analytics.performance.EffortBests
import app.pacinglab.core.analytics.performance.EffortSource
import app.pacinglab.core.analytics.performance.RaceDistance
import app.pacinglab.core.analytics.performance.RacePredictions
import app.pacinglab.core.analytics.performance.RunEfforts
import app.pacinglab.core.analytics.performance.Vo2MaxPoint
import app.pacinglab.core.analytics.performance.Vo2MaxSource
import app.pacinglab.core.analytics.performance.Vo2MaxTrend
import app.pacinglab.core.data.repository.EfficiencyHistory
import app.pacinglab.core.data.repository.RunningPerformance
import app.pacinglab.core.data.repository.Vo2MaxHistory
import app.pacinglab.core.designsystem.theme.PacingLabTheme
import assertk.assertThat
import assertk.assertions.isEqualTo
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import kotlin.math.sin

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [Config.NEWEST_SDK], qualifiers = "w384dp-h3600dp-xxhdpi")
class PerformanceScreenScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 10, 21)

    /** Four months of PacingLab estimates around a slow rise from 54 to 57, a few Fitbit values and one test. */
    private val vo2Points: List<Vo2MaxPoint> = buildList {
        for (d in 0 until 120 step 3) {
            add(Vo2MaxPoint(today.minusDays(120L - d), 54.0 + 3.0 * d / 120 + 2.5 * sin(d * 1.9), Vo2MaxSource.PACINGLAB))
        }
        listOf(100L to 52.4, 70L to 53.6, 45L to 55.1, 20L to 54.8, 6L to 55.9).forEach { (back, v) ->
            add(Vo2MaxPoint(today.minusDays(back), v, Vo2MaxSource.FITBIT))
        }
        add(Vo2MaxPoint(today.minusDays(30), 56.5, Vo2MaxSource.TEST))
    }

    /** Easy runs every few days over five months, getting a little faster. */
    private val efficiencyPoints = (150 downTo 0 step 4).map { back ->
        EfficiencyPoint(today.minusDays(back.toLong()), 2.55 + 0.0012 * (150 - back) + 0.05 * sin(back * 0.9), 6 + back % 5)
    }

    private fun effort(id: String, daysAgo: Long, source: EffortSource, seconds: Double, meters: Double) =
        Effort(id, today.minusDays(daysAgo), source, 0, seconds, meters)

    /** GPS runs at easy to moderate pace and faster interval reps measured by the watch alone. */
    private val bests = EffortBests(
        distances = mapOf(
            RaceDistance.M400 to BestPair(
                effort("g1", 40, EffortSource.GPS, 92.0, 400.0),
                effort("w1", 9, EffortSource.WATCH, 84.0, 400.0),
            ),
            RaceDistance.K1 to
                BestPair(effort("g1", 40, EffortSource.GPS, 257.0, 1_000.0), effort("w2", 58, EffortSource.WATCH, 228.0, 1_000.0)),
            RaceDistance.K3 to
                BestPair(effort("g2", 70, EffortSource.GPS, 925.0, 3_000.0), effort("w3", 22, EffortSource.WATCH, 848.0, 3_000.0)),
            RaceDistance.K5 to
                BestPair(effort("g3", 70, EffortSource.GPS, 1_617.0, 5_000.0), effort("w3", 22, EffortSource.WATCH, 1_453.0, 5_000.0)),
            RaceDistance.K10 to BestPair(effort("g3", 70, EffortSource.GPS, 3_383.0, 10_000.0), null),
        ),
        durations = listOf(
            Triple(60, 4.6 * 60, 4.9 * 60),
            Triple(120, 4.25 * 120, 4.6 * 120),
            Triple(180, 4.1 * 180, 4.46 * 180),
            Triple(300, 3.75 * 300, 4.05 * 300),
            Triple(420, 3.38 * 420, 3.57 * 420),
            Triple(600, 3.37 * 600, 3.66 * 600),
            Triple(720, 3.32 * 720, 3.46 * 720),
            Triple(1_200, 3.13 * 1_200, 3.49 * 1_200),
            Triple(1_800, 3.09 * 1_800, 3.43 * 1_800),
            Triple(3_600, 2.94 * 3_600, null),
        ).associate { (t, gps, watch) ->
            t to
                BestPair(
                    effort("g$t", 30, EffortSource.GPS, t.toDouble(), gps),
                    watch?.let {
                        effort("w$t", 20, EffortSource.WATCH, t.toDouble(), it)
                    },
                )
        },
    )

    private val gap = CriticalSpeedResult(null, CriticalSpeedGap.NO_LONG_EFFORT, listOf(effort("w1", 9, EffortSource.WATCH, 180.0, 802.0)))

    private val fitted = CriticalSpeed.of(
        listOf(
            RunEfforts(
                emptyMap(),
                emptyMap(),
                mapOf(
                    180 to effort("t1", 12, EffortSource.GPS, 180.0, 905.0),
                    420 to effort("t1", 12, EffortSource.GPS, 420.0, 1_830.0),
                    720 to effort("t2", 5, EffortSource.GPS, 720.0, 2_990.0),
                ),
            ),
        ),
        today,
    )

    private fun loaded(criticalSpeed: CriticalSpeedResult) = PerformanceUiState.Loaded(
        today = today,
        vo2max = Vo2MaxHistory(vo2Points.sortedBy { it.date }, Vo2MaxTrend.of(vo2Points, today)),
        efficiency = EfficiencyHistory(efficiencyPoints, AerobicEfficiency.trend(efficiencyPoints, today), referenceHr = 142),
        running = RunningPerformance(
            allTime = bests,
            recent = bests,
            criticalSpeed = criticalSpeed,
            predictions = RacePredictions.of(55.6, criticalSpeed.fit),
            vo2max = 55.6,
            decoupling = listOf(
                DecouplingPoint(today.minusDays(400), "d1", 0.7),
                DecouplingPoint(today.minusDays(320), "d2", 6.1),
                DecouplingPoint(today.minusDays(240), "d3", 2.1),
                DecouplingPoint(today.minusDays(60), "d4", 2.0),
            ),
        ),
    )

    @Test
    fun beforeCriticalSpeed() = capture(loaded(gap))

    @Test
    fun withCriticalSpeed() = capture(loaded(fitted))

    @Test
    fun withCriticalSpeedDark() = capture(loaded(fitted), darkTheme = true)

    @Test
    fun aBestEffortOpensItsRun() {
        var opened: String? = null
        compose.setContent {
            PacingLabTheme { PerformanceScreen(loaded(gap), onOpenWorkout = { opened = it }) }
        }

        compose.onNodeWithText("3:48").performScrollTo().performClick()

        assertThat(opened).isEqualTo("w2")
    }

    private fun capture(state: PerformanceUiState, darkTheme: Boolean = false) {
        compose.setContent {
            PacingLabTheme(darkTheme = darkTheme) { PerformanceScreen(state, onOpenWorkout = {}) }
        }
        compose.onRoot().captureRoboImage()
    }
}
