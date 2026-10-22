package app.pacinglab.feature.workouts.list

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import app.pacinglab.core.data.sync.SyncStatus
import app.pacinglab.core.designsystem.theme.PacingLabTheme
import app.pacinglab.core.model.SessionKind
import app.pacinglab.core.model.Sport
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [Config.NEWEST_SDK], qualifiers = "w384dp-h832dp-xxhdpi")
class WorkoutsScreenScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val cest = ZoneOffset.ofHours(2)

    private val sessions = listOf(
        item("s1", Sport.STRENGTH, listOf("Weights"), 1, "2026-10-13T14:50:35Z", 76.5, 585.0, 102, 116),
        item("s2", Sport.RUN, listOf("Run", "Interval workout"), 2, "2026-10-12T17:08:18Z", 35.9, 6_590.0, 155, 178, route = true)
            .copy(kind = SessionKind.VO2MAX_INTERVALS),
        item("s3", Sport.RUN, listOf("Run", "Interval workout"), 2, "2026-10-08T17:34:42Z", 36.6, 6_825.0, 152, 182, route = true)
            .copy(kind = SessionKind.THRESHOLD_INTERVALS),
        item("s6", Sport.RUN, listOf("Interval workout"), 2, "2026-10-04T07:25:06Z", 9.2, 930.0, 122, 149).copy(kind = SessionKind.SPRINTS),
        item("s4", Sport.RIDE, emptyList(), 1, "2026-09-30T07:41:00Z", 18.2, 5_210.0, null, null, capture = Capture.DETECTED),
        item(
            "s5", Sport.WALK,
            listOf(
                "Walk",
            ),
            1, "2026-09-28T12:10:00Z", 41.0, 3_400.0, 98, 121, capture = Capture.DETECTED, origin = FITBIT,
        ),
    )

    @Test
    fun list() = capture(WorkoutsUiState(loading = false, sessions = sessions))

    @Test
    fun listDark() = capture(WorkoutsUiState(loading = false, sessions = sessions), darkTheme = true)

    @Test
    fun importing() = capture(
        WorkoutsUiState(
            loading = false,
            sessions = sessions.take(2),
            sync = SyncStatus.Importing(Instant.parse("2026-03-14T00:00:00Z"), 0.42f),
        ),
    )

    @Test
    fun empty() = capture(WorkoutsUiState(loading = false))

    private fun capture(state: WorkoutsUiState, darkTheme: Boolean = false) {
        compose.setContent {
            PacingLabTheme(darkTheme = darkTheme) {
                WorkoutsScreen(state = state, onRefresh = {}, onOpenSession = {}, onOpenDataSources = {}, zoneId = ZoneOffset.UTC)
            }
        }
        compose.onRoot().captureRoboImage()
    }

    private fun item(
        id: String,
        sport: Sport,
        titles: List<String>,
        parts: Int,
        start: String,
        minutes: Double,
        meters: Double?,
        avgHr: Int?,
        maxHr: Int?,
        route: Boolean = false,
        capture: Capture = Capture.RECORDED,
        origin: String = GOOGLE_FIT,
    ) = SessionItem(
        id = id,
        sport = sport,
        titles = titles,
        parts = parts,
        start = Instant.parse(start),
        zoneOffset = cest,
        movingMillis = (minutes * 60_000).toLong(),
        distanceMeters = meters,
        averageHr = avgHr,
        maxHr = maxHr,
        hasRoute = route,
        capture = capture,
        origin = if (capture == Capture.RECORDED) FITBIT else origin,
    )

    private companion object {
        const val FITBIT = "com.fitbit.FitbitMobile"
        const val GOOGLE_FIT = "com.google.android.apps.fitness"
    }
}
