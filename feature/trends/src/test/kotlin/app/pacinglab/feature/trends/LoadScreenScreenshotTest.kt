package app.pacinglab.feature.trends

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import app.pacinglab.core.analytics.load.DistributionModel
import app.pacinglab.core.analytics.load.DomainShare
import app.pacinglab.core.analytics.load.FitnessModel
import app.pacinglab.core.analytics.load.IntensityDomain
import app.pacinglab.core.analytics.load.IntensityFocus
import app.pacinglab.core.analytics.load.LoadScores
import app.pacinglab.core.analytics.load.TrainingCalendar
import app.pacinglab.core.analytics.load.TrainingLoads
import app.pacinglab.core.analytics.load.TrainingStatus
import app.pacinglab.core.analytics.load.WeeklyTargets
import app.pacinglab.core.analytics.load.WorkoutLoad
import app.pacinglab.core.analytics.recovery.HrvVariation
import app.pacinglab.core.analytics.recovery.Night
import app.pacinglab.core.analytics.recovery.RecoveryTrend
import app.pacinglab.core.analytics.zones.ZoneDistribution
import app.pacinglab.core.analytics.zones.Zones
import app.pacinglab.core.designsystem.component.Span
import app.pacinglab.core.designsystem.component.SubTabs
import app.pacinglab.core.designsystem.theme.PacingLabTheme
import app.pacinglab.core.model.HeartRateAnchors
import app.pacinglab.core.model.Sport
import app.pacinglab.core.model.ZoneModel
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
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
import java.util.Locale
import kotlin.math.exp
import kotlin.math.sin

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [Config.NEWEST_SDK], qualifiers = "w384dp-h3500dp-xxhdpi")
class LoadScreenScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 10, 21)
    private val polarized = Zones.of(ZoneModel.POLARIZED_THREE, HeartRateAnchors(maxHr = 186, thresholdHr = 167))
    private val five = Zones.of(ZoneModel.LTHR_FIVE, HeartRateAnchors(maxHr = 186, thresholdHr = 167))

    /** The three domains' minutes spread over the five zones, roughly as threshold-based zones fall. */
    private fun fiveFrom(minutes: LongArray) = longArrayOf(
        minutes[0] * 45 / 100,
        minutes[0] - minutes[0] * 45 / 100,
        minutes[1] * 60 / 100,
        minutes[1] - minutes[1] * 60 / 100 + minutes[2] * 60 / 100,
        minutes[2] - minutes[2] * 60 / 100,
    )

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
                        zones = ZoneDistribution(five, fiveFrom(zones).map { it * 60_000 }.toLongArray()),
                        sport = Sport.RUN,
                        distanceMeters = minutes * 60 * 2.9 * (if (date.isAfter(today.minusDays(10))) 1.5 else 1.0),
                    ),
                )
            }
            date = date.plusDays(1)
            n++
        }
    }

    private val loaded = LoadUiState.Loaded(
        days = FitnessModel.series(TrainingLoads.daily(loads), until = today),
        weeks = TrainingLoads.weeks(loads, until = today, count = 8),
        today = today,
        status = TrainingStatus.of(
            FitnessModel.series(TrainingLoads.daily(loads), until = today),
            vo2maxChange = 0.8,
            hrvBelowNormal = false,
        ),
        focus = IntensityFocus.of(loads, today),
        loadBands = WeeklyTargets.loadBands(
            FitnessModel.series(TrainingLoads.daily(loads), until = today),
            TrainingLoads.weeks(loads, until = today, count = 8).map { it.start },
        ),
        running = WeeklyTargets.runningWeeks(loads.map { it.date to it.distanceMeters!! }, until = today, count = 12),
    )

    @Test
    fun threeMonths() = capture(loaded)

    @Test
    fun threeMonthsDark() = capture(loaded, darkTheme = true)

    @Test
    fun month() = capture(loaded, span = Span.MONTH)

    @Test
    fun year() = capture(loaded, span = Span.YEAR)

    /** Opened from Today's form tile: scrolled to the card, which flashes once. */
    @Test
    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    fun openedAtForm() = capture(loaded, focus = LoadCardKind.FORM.id)

    @Test
    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    fun needsHeartRateValues() = capture(LoadUiState.NeedsHeartRateValues)

    @Test
    fun choosingARangeKeepsTodaysValues() {
        compose.setContent {
            PacingLabTheme { LoadScreen(state = loaded, title = "Training", tabs = { Tabs() }, onOpenPhysiology = {}) }
        }
        val today = loaded.days.last()
        val fitness = "Fitness ${Math.round(today.fitness)}"

        // The fitness card's range buttons.
        compose.onAllNodesWithText("Year")[0].performClick()
        compose.onNodeWithText("Today", substring = true).assertExists()
        compose.onAllNodesWithText("Month")[0].performClick()
        compose.onNodeWithText("Today", substring = true).assertExists()
        compose.onNode(androidx.compose.ui.test.hasContentDescription(fitness)).assertExists()
    }

    /** The weekly table with its load band and running tolerance, large enough to read. */
    @Test
    @Config(qualifiers = "w384dp-h1100dp-xxhdpi")
    fun weeksAndRunning() {
        compose.setContent {
            PacingLabTheme {
                Surface {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        WeeksCard(loaded.weeks, today, Locale.UK, bands = loaded.loadBands, fiveZones = true)
                        RunningVolumeCard(loaded.running, today, Locale.UK)
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage()
    }

    /** The weekly table in the three zones of the intensity focus. */
    @Test
    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    fun weeksInThreeZones() {
        compose.setContent {
            PacingLabTheme {
                Surface { WeeksCard(loaded.weeks, today, Locale.UK, Modifier.padding(16.dp), bands = loaded.loadBands, fiveZones = false) }
            }
        }
        compose.onRoot().captureRoboImage()
    }

    @Test
    fun theZoneSwitchFlipsBetweenFiveAndThree() {
        var chosen: Boolean? = null
        compose.setContent {
            PacingLabTheme { WeeksCard(loaded.weeks, today, Locale.UK, fiveZones = true, onZonesChange = { chosen = it }) }
        }

        compose.onNodeWithContentDescription("Zones").assertIsOn().performClick()

        assertEquals(false, chosen)
    }

    /** A threshold-heavy month: the share dots lie outside their bands, which stay visible. */
    @Test
    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    fun intensityFocusOffTarget() {
        val hour = 3_600_000L
        val shares = listOf(0.30 to 4L, 0.38 to 5L, 0.32 to 4L).mapIndexed { i, (share, hours) ->
            val domain = IntensityDomain.entries[i]
            DomainShare(domain, hours * hour, share, IntensityFocus.TARGETS.getValue(domain))
        }
        val focus = IntensityFocus(today.minusDays(27), today, shares, polarizationIndex = 1.4, model = DistributionModel.THRESHOLD)
        compose.setContent {
            PacingLabTheme { Surface { IntensityFocusCard(focus, Modifier.padding(16.dp)) } }
        }
        compose.onRoot().captureRoboImage()
    }

    @Composable
    private fun Tabs() = SubTabs(listOf("Sessions", "Load"), selected = 1, onSelect = {})

    private fun capture(state: LoadUiState, darkTheme: Boolean = false, span: Span = Span.THREE_MONTHS, focus: String? = null) {
        compose.setContent {
            PacingLabTheme(darkTheme = darkTheme) {
                LoadScreen(state = state, title = "Training", tabs = { Tabs() }, onOpenPhysiology = {}, initialSpan = span, focus = focus)
            }
        }
        compose.onRoot().captureRoboImage()
    }
}
