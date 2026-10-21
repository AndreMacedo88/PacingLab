package app.pacinglab.feature.workouts.detail

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import app.pacinglab.core.analytics.intervals.PhaseKind
import app.pacinglab.core.analytics.session.PhaseSummary
import app.pacinglab.core.analytics.session.SessionAnalysis
import app.pacinglab.core.analytics.zones.ZoneDistribution
import app.pacinglab.core.analytics.zones.Zones
import app.pacinglab.core.designsystem.theme.PacingLabTheme
import app.pacinglab.core.model.HeartRateAnchors
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

    private val parts = listOf(
        PartItem("p1", "Run", "running", Sport.RUN, start, start.plusSeconds(360), cest, 360, 360, 360, 2),
        PartItem(
            "p2", "Interval workout", "boot_camp", Sport.RUN,
            start.plusSeconds(
                420,
            ),
            start.plusSeconds(2_580), cest, 1_090, 2_150, 0, 0,
        ),
    )

    @Test
    fun intervals() = capture(SessionUiState.Loaded(session, parts, SessionAnalysis(phases, zones), zonesAvailable = true))

    @Test
    fun intervalsDark() =
        capture(SessionUiState.Loaded(session, parts, SessionAnalysis(phases, zones), zonesAvailable = true), darkTheme = true)

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
    fun tappingThePaceShowsSpeedEverywhere() {
        compose.setContent {
            PacingLabTheme {
                SessionScreen(
                    state = SessionUiState.Loaded(session, parts, SessionAnalysis(phases, zones), zonesAvailable = true),
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
    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    fun steadyRunWithoutZones() = capture(
        SessionUiState.Loaded(
            session.copy(titles = listOf("Run"), parts = 1, distanceMeters = 8_120.0, movingMillis = 41 * 60_000L),
            parts.take(1),
            SessionAnalysis(phases = emptyList(), zones = emptyList()),
            zonesAvailable = false,
        ),
    )

    private fun capture(state: SessionUiState, darkTheme: Boolean = false) {
        compose.setContent {
            PacingLabTheme(darkTheme = darkTheme) {
                SessionScreen(state = state, onBack = {}, onOpenPhysiology = {}, zoneId = ZoneOffset.UTC)
            }
        }
        compose.onRoot().captureRoboImage()
    }
}
