package app.pacinglab.feature.trends

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import app.pacinglab.core.analytics.load.FitnessModel
import app.pacinglab.core.analytics.load.IntensityFocus
import app.pacinglab.core.analytics.load.LoadScores
import app.pacinglab.core.analytics.load.TrainingLoads
import app.pacinglab.core.analytics.load.TrainingStatus
import app.pacinglab.core.analytics.load.WorkoutLoad
import app.pacinglab.core.analytics.recovery.HrvVariation
import app.pacinglab.core.analytics.recovery.Night
import app.pacinglab.core.analytics.recovery.RecoveryTrend
import app.pacinglab.core.analytics.zones.ZoneDistribution
import app.pacinglab.core.analytics.zones.Zones
import app.pacinglab.core.designsystem.theme.PacingLabTheme
import app.pacinglab.core.model.HeartRateAnchors
import app.pacinglab.core.model.ZoneModel
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
import kotlin.math.exp
import kotlin.math.sin

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [Config.NEWEST_SDK], qualifiers = "w384dp-h3000dp-xxhdpi")
class TrendsScreenScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 10, 21)
    private val polarized = Zones.of(ZoneModel.POLARIZED_THREE, HeartRateAnchors(maxHr = 186, thresholdHr = 167))

    /**
     * A year of four sessions a week: intervals on Tuesday, easy runs on Thursday and Sunday, strength on
     * Saturday, with a slow build, a summer break and a recent block of harder weeks.
     */
    private val loads: List<WorkoutLoad> = buildList {
        var date = today.minusDays(400)
        var n = 0
        while (!date.isAfter(today)) {
            val build = 0.7 + 0.3 * sin(n / 60.0)
            val summerBreak = date.monthValue == 7 && date.dayOfMonth in 5..25
            val (stress, minutes, zones) = when (date.dayOfWeek) {
                DayOfWeek.TUESDAY -> Triple(55.0, 40L, longArrayOf(20, 8, 12))
                DayOfWeek.THURSDAY -> Triple(38.0, 45L, longArrayOf(42, 3, 0))
                DayOfWeek.SATURDAY -> Triple(18.0, 75L, longArrayOf(70, 5, 0))
                DayOfWeek.SUNDAY -> Triple(62.0, 70L, longArrayOf(62, 8, 0))
                else -> Triple(0.0, 0L, longArrayOf(0, 0, 0))
            }
            if (stress > 0 && !summerBreak) {
                val scaled = stress * build * (if (date.isAfter(today.minusDays(21))) 1.3 else 1.0)
                add(
                    WorkoutLoad(
                        workoutId = "w$n",
                        date = date,
                        durationMillis = minutes * 60_000,
                        scores = LoadScores(scaled * 1.7, scaled, scaled * 2.6, minutes * 60_000),
                        intensity = ZoneDistribution(polarized, LongArray(3) { zones[it] * 60_000 }),
                    ),
                )
            }
            date = date.plusDays(1)
            n++
        }
    }

    /** Ten weeks of nights: HRV wobbling around 60 ms with a dip in the last week, resting heart rate rising then. */
    private val nights = (69 downTo 0).map { back ->
        val date = today.minusDays(back.toLong())
        val start = Instant.parse("${date.minusDays(1)}T22:00:00Z")
        val dip = if (back < 6) 0.82 else 1.0
        Night(
            date = date,
            start = start,
            end = start.plusSeconds(8 * 3_600L),
            asleepMillis = 7 * 3_600_000L,
            stageMillis = emptyMap(),
            hrvMs = 60.0 * dip * exp(0.09 * sin(back * 1.3)),
            restingHr = 54.0 + 1.2 * sin(back * 0.8) + if (back < 6) 3.5 else 0.0,
            respiratoryRate = 12.5,
            zone = ZoneOffset.UTC,
        )
    }

    private val loaded = TrendsUiState.Loaded(
        days = FitnessModel.series(TrainingLoads.daily(loads), until = today),
        weeks = TrainingLoads.weeks(loads, until = today, count = 8),
        today = today,
        recovery = RecoveryTrend.of(nights, today),
        status = TrainingStatus.of(
            FitnessModel.series(TrainingLoads.daily(loads), until = today),
            vo2maxChange = 0.8,
            hrvBelowNormal = false,
        ),
        focus = IntensityFocus.of(loads, today),
        hrvVariation = HrvVariation.of(nights, today),
    )

    @Test
    fun threeMonths() = capture(loaded)

    @Test
    fun threeMonthsDark() = capture(loaded, darkTheme = true)

    @Test
    fun sixWeeks() = capture(loaded, range = TrendRange.SIX_WEEKS)

    @Test
    fun year() = capture(loaded, range = TrendRange.YEAR)

    @Test
    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    fun needsHeartRateValues() = capture(TrendsUiState.NeedsHeartRateValues)

    @Test
    fun choosingARangeKeepsTodaysValues() {
        compose.setContent {
            PacingLabTheme { TrendsScreen(state = loaded, onOpenPhysiology = {}) }
        }
        val today = loaded.days.last()
        val fitness = "Fitness ${Math.round(today.fitness)}"

        // The fitness card's range buttons.
        compose.onAllNodesWithText("1 year")[0].performClick()
        compose.onNodeWithText("Today", substring = true).assertExists()
        compose.onAllNodesWithText("6 weeks")[0].performClick()
        compose.onNodeWithText("Today", substring = true).assertExists()
        compose.onNode(androidx.compose.ui.test.hasContentDescription(fitness)).assertExists()
    }

    private fun capture(state: TrendsUiState, darkTheme: Boolean = false, range: TrendRange = TrendRange.THREE_MONTHS) {
        compose.setContent {
            PacingLabTheme(darkTheme = darkTheme) {
                TrendsScreen(state = state, onOpenPhysiology = {}, initialRange = range)
            }
        }
        compose.onRoot().captureRoboImage()
    }
}
