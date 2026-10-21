package app.pacinglab.feature.workouts.detail

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import app.pacinglab.core.analytics.intervals.PhaseKind
import app.pacinglab.core.analytics.load.LoadScores
import app.pacinglab.core.analytics.session.PhaseSummary
import app.pacinglab.core.analytics.session.SessionAnalysis
import app.pacinglab.core.analytics.zones.ZoneDistribution
import app.pacinglab.core.analytics.zones.Zones
import app.pacinglab.core.designsystem.theme.PacingLabTheme
import app.pacinglab.core.model.HeartRateAnchors
import app.pacinglab.core.model.HeartRateSeries
import app.pacinglab.core.model.RecordingMethod
import app.pacinglab.core.model.Route
import app.pacinglab.core.model.Sport
import app.pacinglab.core.model.ZoneModel
import app.pacinglab.feature.workouts.list.SessionItem
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [Config.NEWEST_SDK], qualifiers = "w384dp-h2200dp-xxhdpi")
class SessionScreenScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val cest = ZoneOffset.ofHours(2)
    private val start = Instant.parse("2026-10-20T16:20:00Z")
    private val anchors = HeartRateAnchors(maxHr = 186, thresholdHr = 167)
    private val five = Zones.of(ZoneModel.LTHR_FIVE, anchors)
    private val three = Zones.of(ZoneModel.POLARIZED_THREE, anchors)

    private val session = SessionItem(
        id = "s1",
        sport = Sport.RUN,
        titles = listOf("Run", "Interval workout"),
        parts = 2,
        start = start,
        zoneOffset = cest,
        movingMillis = 36 * 60_000L,
        distanceMeters = 6_840.0,
        averageHr = 158,
        maxHr = 181,
        hasRoute = true,
    )

    /** Warm-up run, 5 x 4 min with 2 min walking, and a short cool-down. */
    private val phases = buildList {
        var t = start.toEpochMilli()
        fun add(kind: PhaseKind, seconds: Int, meters: Double, average: Int, max: Int) {
            add(PhaseSummary(kind, t, t + seconds * 1_000L, meters, average, max))
            t += seconds * 1_000L
        }
        add(PhaseKind.WARM_UP, 360, 1_190.0, 139, 151)
        val repMeters = listOf(1_032.0, 1_007.0, 1_007.0, 988.0, 1_002.0)
        val repHr = listOf(159 to 171, 166 to 176, 170 to 179, 169 to 180, 169 to 181)
        repMeters.forEachIndexed { i, meters ->
            add(PhaseKind.WORK, 241, meters, repHr[i].first, repHr[i].second)
            if (i < repMeters.lastIndex) add(PhaseKind.RECOVERY, 120, listOf(116.0, 142.0, 139.0, 119.0)[i], 147 + i, repHr[i].second)
        }
        add(PhaseKind.COOL_DOWN, 180, 600.0, 150, 158)
    }

    private val zones = listOf(
        ZoneDistribution(five, longArrayOf(9 * 60_000L, 5 * 60_000L, 6 * 60_000L, 11 * 60_000L, 5 * 60_000L)),
        ZoneDistribution(three, longArrayOf(20 * 60_000L, 11 * 60_000L, 5 * 60_000L)),
    )

    /** A 1.2 km warm-up out and back along a park path. */
    private val warmUpRoute: Route = run {
        val n = 120
        val latitudes = DoubleArray(n) { i -> 55.6800 + 0.0045 * sin(Math.PI * i / (n - 1)) + 0.0004 * sin(6 * Math.PI * i / (n - 1)) }
        val longitudes = DoubleArray(n) { i -> 12.5700 + 0.0060 * (1 - cos(2 * Math.PI * i / (n - 1))) / 2 }
        Route(LongArray(n) { it * 3_000L }, latitudes, longitudes, DoubleArray(n) { Double.NaN }, DoubleArray(n) { Double.NaN })
    }

    /**
     * Heart rate once a second between [from] and [until] seconds after the start: a warm-up, a minute's
     * pause, then five reps and their recoveries, heart rate following each change with a lag.
     */
    private fun heartRate(from: Int, until: Int): HeartRateSeries {
        val times = ArrayList<Long>()
        val bpm = ArrayList<Int>()
        var hr = 100.0
        val repPeaks = listOf(171.0, 176.0, 179.0, 180.0, 181.0)
        for (second in 0 until until) {
            if (second in 360 until 420) {
                hr = 128.0
                continue
            }
            val u = second - 420
            val target = when {
                second < 360 -> 148.0
                u < 5 * 361 - 120 && u % 361 < 241 -> repPeaks[u / 361]
                u < 5 * 361 - 120 -> 135.0
                else -> 150.0
            }
            hr += (target - hr) / 30
            if (second >= from) {
                times += start.toEpochMilli() + second * 1_000L
                bpm += (hr + 1.5 * sin(second / 7.0)).roundToInt()
            }
        }
        return HeartRateSeries(times.toLongArray(), bpm.toIntArray())
    }

    private val analysis =
        SessionAnalysis(phases, zones, LoadScores(trimp = 89.1, stressScore = 52.9, edwards = 145.8, heartRateMillis = 2_160_000))

    private val warmUp = DetailScope(
        title = "Run",
        sport = Sport.RUN,
        start = start,
        end = start.plusSeconds(360),
        zoneOffset = cest,
        movingMillis = 360_000L,
        distanceMeters = 1_190.0,
        averageHr = 139,
        maxHr = 151,
        routes = listOf(warmUpRoute),
        routeOnlyFrom = emptyList(),
        analysis = SessionAnalysis(
            phases = emptyList(),
            zones = listOf(
                ZoneDistribution(five, longArrayOf(4 * 60_000L, 2 * 60_000L, 0, 0, 0)),
                ZoneDistribution(three, longArrayOf(6 * 60_000L, 0, 0)),
            ),
        ),
        recording = RecordingItem("com.fitbit.FitbitMobile", RecordingMethod.ACTIVE, "running", 193, 348, 349, 2),
        heartRate = heartRate(0, 360),
    )

    private val intervalWorkout = warmUp.copy(
        title = "Interval workout",
        start = start.plusSeconds(420),
        end = start.plusSeconds(2_580),
        movingMillis = 2_160_000L,
        distanceMeters = 5_650.0,
        averageHr = 162,
        maxHr = 181,
        routes = emptyList(),
        analysis = SessionAnalysis(
            phases.drop(1),
            listOf(
                ZoneDistribution(five, longArrayOf(5 * 60_000L, 3 * 60_000L, 6 * 60_000L, 11 * 60_000L, 5 * 60_000L)),
                ZoneDistribution(three, longArrayOf(14 * 60_000L, 11 * 60_000L, 5 * 60_000L)),
            ),
        ),
        recording = RecordingItem("com.fitbit.FitbitMobile", RecordingMethod.ACTIVE, "boot_camp", 994, 1_799, 0, 0),
        heartRate = heartRate(420, 2_400),
    )

    private val whole = warmUp.copy(
        title = null,
        end = intervalWorkout.end,
        movingMillis = session.movingMillis,
        distanceMeters = session.distanceMeters,
        averageHr = session.averageHr,
        maxHr = session.maxHr,
        routeOnlyFrom = listOf("Run"),
        analysis = analysis,
        recording = null,
        heartRate = heartRate(0, 2_400),
    )

    private val loaded = SessionUiState.Loaded(session, listOf(whole, warmUp, intervalWorkout), zonesAvailable = true)

    @Test
    fun intervals() = capture(loaded)

    @Test
    fun intervalsDark() = capture(loaded, darkTheme = true)

    @Test
    @Config(qualifiers = "w384dp-h1400dp-xxhdpi")
    fun warmUpPart() = capture(loaded, scope = 1)

    @Test
    fun intervalWorkoutPart() = capture(loaded, scope = 2)

    @Test
    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    fun intervalsAsSpeed() {
        compose.setContent {
            PacingLabTheme {
                Surface {
                    IntervalsCard(
                        analysis = SessionAnalysis(phases, zones),
                        toggle = SpeedToggle(showSpeed = true, label = "", onToggle = {}),
                        locale = Locale.UK,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
        compose.onRoot().captureRoboImage()
    }

    @Test
    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    fun heartRateCard() = captureHeartRateCard(darkTheme = false)

    @Test
    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    fun heartRateCardDark() = captureHeartRateCard(darkTheme = true)

    @Test
    fun theZoneSwitchMovesChartAndBarsToThreeZones() {
        compose.setContent {
            PacingLabTheme {
                HeartRateCard(zones, whole.heartRate, ZoneOffset.UTC, onOpenPhysiology = {})
            }
        }
        compose.onNodeWithText("Threshold", useUnmergedTree = true).assertExists()

        compose.onNodeWithText("3 zones").performClick()

        compose.onNodeWithText("Threshold", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("Above VT2", useUnmergedTree = true).assertExists()
    }

    private fun captureHeartRateCard(darkTheme: Boolean) {
        compose.setContent {
            PacingLabTheme(darkTheme = darkTheme) {
                Surface {
                    HeartRateCard(zones, whole.heartRate, ZoneOffset.UTC, onOpenPhysiology = {}, modifier = Modifier.padding(16.dp))
                }
            }
        }
        compose.onRoot().captureRoboImage()
    }

    @Test
    fun tappingThePaceShowsSpeedEverywhere() {
        compose.setContent {
            PacingLabTheme {
                SessionScreen(
                    state = loaded,
                    onBack = {},
                    onOpenPhysiology = {},
                    zoneId = ZoneOffset.UTC,
                )
            }
        }

        compose.onNodeWithText("Average pace").performClick()

        compose.onNodeWithText("Average speed").assertExists()
        compose.onNodeWithText("15.0 km/h").assertExists()
        compose.onNodeWithText("km/h").assertExists()
        compose.onNodeWithText("Average speed").performClick()
        compose.onNodeWithText("Average pace").assertExists()
    }

    @Test
    fun tappingARecordedPartShowsOnlyThatPart() {
        compose.setContent {
            PacingLabTheme {
                SessionScreen(state = loaded, onBack = {}, onOpenPhysiology = {}, zoneId = ZoneOffset.UTC)
            }
        }
        compose.onNodeWithText("Recorded with Fitbit", substring = true).assertDoesNotExist()

        compose.onNodeWithText("Recorded parts").performScrollTo()
        compose.onAllNodesWithText("Interval workout")[1].performScrollTo().performClick()

        compose.onNodeWithText("Recorded with Fitbit", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("No GPS", substring = true).assertExists()
        compose.onNodeWithText("Recorded parts").assertDoesNotExist()
        compose.onNodeWithText("5.65 km").assertExists()
    }

    @Test
    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    fun steadyRunWithoutZones() = capture(
        SessionUiState.Loaded(
            session.copy(titles = listOf("Run"), parts = 1, distanceMeters = 8_120.0, movingMillis = 41 * 60_000L),
            listOf(
                warmUp.copy(
                    end = start.plusSeconds(41 * 60L),
                    movingMillis = 41 * 60_000L,
                    distanceMeters = 8_120.0,
                    averageHr = 148,
                    maxHr = 163,
                    analysis = SessionAnalysis(phases = emptyList(), zones = emptyList()),
                ),
            ),
            zonesAvailable = false,
        ),
    )

    private fun capture(state: SessionUiState, darkTheme: Boolean = false, scope: Int = 0) {
        compose.setContent {
            PacingLabTheme(darkTheme = darkTheme) {
                SessionScreen(state = state, onBack = {}, onOpenPhysiology = {}, zoneId = ZoneOffset.UTC, initialScope = scope)
            }
        }
        compose.onRoot().captureRoboImage()
    }
}
