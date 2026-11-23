package app.pacinglab.feature.workouts.list

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import app.pacinglab.core.analytics.load.CalendarDay
import app.pacinglab.core.analytics.load.IntensityDomain
import app.pacinglab.core.data.sync.SyncStatus
import app.pacinglab.core.designsystem.component.SubTabs
import app.pacinglab.core.designsystem.theme.PacingLabTheme
import app.pacinglab.core.model.SessionKind
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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [Config.NEWEST_SDK], qualifiers = "w384dp-h832dp-xxhdpi")
class SessionsScreenScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val cest = ZoneOffset.ofHours(2)
    private val today = LocalDate.of(2026, 10, 21)

    private val sessions = listOf(
        item("s1", Sport.STRENGTH, listOf("Weights"), 1, "2026-10-13T14:50:35Z", 76.5, 585.0, 102, 116),
        item("s2", Sport.RUN, listOf("Run", "Interval workout"), 2, "2026-10-12T17:08:18Z", 35.9, 6_590.0, 155, 178, route = true)
            .copy(kind = SessionKind.VO2MAX_INTERVALS),
        item("s3", Sport.RUN, listOf("Run", "Interval workout"), 2, "2026-10-08T17:34:42Z", 36.6, 6_825.0, 152, 182, route = true)
            .copy(kind = SessionKind.THRESHOLD_INTERVALS, leftOut = true),
        item("s6", Sport.RUN, listOf("Interval workout"), 2, "2026-10-04T07:25:06Z", 9.2, 930.0, 122, 149).copy(kind = SessionKind.SPRINTS),
        item("s4", Sport.RIDE, emptyList(), 1, "2026-09-30T07:41:00Z", 18.2, 5_210.0, null, null, capture = Capture.DETECTED),
        item(
            "s5", Sport.WALK,
            listOf(
                "Walk",
            ),
            1, "2026-09-28T12:10:00Z", 41.0, 3_400.0, 98, 121, capture = Capture.DETECTED, origin = FITBIT,
        ),
    ).zip(listOf(48.0, 92.0, 81.0, 22.0, null, 12.0)) { session, load -> session.copy(load = load) }

    /** The training days of those sessions, as the calendar draws them. */
    private val calendar = listOf(
        CalendarDay(LocalDate.of(2026, 10, 13), 48.0, 76 * 60_000L, null, listOf("s1")),
        CalendarDay(LocalDate.of(2026, 10, 12), 92.0, 36 * 60_000L, IntensityDomain.ABOVE_VT2, listOf("s2")),
        CalendarDay(LocalDate.of(2026, 10, 8), 81.0, 37 * 60_000L, IntensityDomain.VT1_TO_VT2, listOf("s3")),
        CalendarDay(LocalDate.of(2026, 10, 4), 22.0, 9 * 60_000L, IntensityDomain.ABOVE_VT2, listOf("s6")),
        CalendarDay(LocalDate.of(2026, 9, 28), 12.0, 41 * 60_000L, IntensityDomain.BELOW_VT1, listOf("s5")),
    ).associateBy { it.date }

    @Test
    fun list() = capture(WorkoutsUiState(loading = false, sessions = sessions))

    @Test
    fun listDark() = capture(WorkoutsUiState(loading = false, sessions = sessions), darkTheme = true)

    /** The same sessions as a month, the latest training day picked and its sessions under the grid. */
    @Test
    fun calendar() = capture(WorkoutsUiState(loading = false, sessions = sessions, calendar = calendar), view = SessionsView.CALENDAR)

    @Test
    fun calendarDark() = capture(
        WorkoutsUiState(loading = false, sessions = sessions, calendar = calendar),
        darkTheme = true,
        view = SessionsView.CALENDAR,
    )

    @Test
    fun weeksAreNamedByTheirFirstAndLastDay() {
        assertThat(weekRange(LocalDate.of(2026, 10, 12), today, Locale.UK)).isEqualTo("12–18 October")
        assertThat(weekRange(LocalDate.of(2026, 9, 28), today, Locale.UK)).isEqualTo("28 September – 4 October")
        assertThat(weekRange(LocalDate.of(2025, 12, 29), today, Locale.UK)).isEqualTo("29 December 2025 – 4 January 2026")
        assertThat(weekRange(LocalDate.of(2025, 10, 13), today, Locale.UK)).isEqualTo("13–19 October 2025")
    }

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

    @Test
    fun filteredToRecordedRuns() = capture(
        WorkoutsUiState(
            loading = false,
            sessions = sessions,
            filter = WorkoutFilter(
                types = setOf(SessionType.INTERVALS, SessionType.SPRINTS),
                captures = setOf(Capture.RECORDED),
            ),
        ),
    )

    @Test
    fun sortedByLoad() = capture(
        WorkoutsUiState(
            loading = false,
            sessions = sessions,
            sort = WorkoutSort.HIGHEST_LOAD,
        ),
    )

    @Test
    fun filterSheet() {
        compose.setContent {
            PacingLabTheme {
                Surface {
                    FilterSheetContent(
                        sessions = sessions,
                        filter = WorkoutFilter(captures = setOf(Capture.DETECTED)),
                        onFilter = {},
                        onDone = {},
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
            }
        }
        compose.onRoot().captureRoboImage()
    }

    @Test
    fun choosingASortReportsIt() {
        var chosen: WorkoutSort? = null
        compose.setContent {
            PacingLabTheme {
                SessionsScreen(
                    state = WorkoutsUiState(loading = false, sessions = sessions),
                    title = "Training",
                    tabs = {},
                    onRefresh = {},
                    onOpenSession = {},
                    onOpenDataSources = {},
                    zoneId = ZoneOffset.UTC,
                    onSort = { chosen = it },
                )
            }
        }

        compose.onNodeWithContentDescription("Sort sessions").performClick()
        compose.onNodeWithText("Longest distance").performClick()

        assertThat(chosen).isEqualTo(WorkoutSort.FARTHEST)
    }

    @Test
    fun clearingFiltersReportsAnEmptyFilter() {
        var filter: WorkoutFilter? = null
        compose.setContent {
            PacingLabTheme {
                SessionsScreen(
                    state = WorkoutsUiState(loading = false, sessions = sessions, filter = WorkoutFilter(types = setOf(SessionType.WALKS))),
                    title = "Training",
                    tabs = {},
                    onRefresh = {},
                    onOpenSession = {},
                    onOpenDataSources = {},
                    zoneId = ZoneOffset.UTC,
                    onFilter = { filter = it },
                )
            }
        }

        compose.onNodeWithText("1 of 6 sessions").assertExists()
        compose.onNodeWithText("Clear filters").performClick()

        assertThat(filter).isEqualTo(WorkoutFilter())
    }

    private fun capture(state: WorkoutsUiState, darkTheme: Boolean = false, view: SessionsView = SessionsView.LIST) {
        compose.setContent {
            PacingLabTheme(darkTheme = darkTheme) {
                SessionsScreen(
                    state = state,
                    title = "Training",
                    tabs = { SubTabs(listOf("Sessions", "Load"), selected = 0, onSelect = {}) },
                    onRefresh = {},
                    onOpenSession = {},
                    onOpenDataSources = {},
                    zoneId = ZoneOffset.UTC,
                    initialView = view,
                    today = today,
                )
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
