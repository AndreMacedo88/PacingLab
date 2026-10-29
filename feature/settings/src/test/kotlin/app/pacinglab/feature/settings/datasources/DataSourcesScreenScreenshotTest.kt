package app.pacinglab.feature.settings.datasources

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import app.pacinglab.core.data.repository.GoogleHealthProblem
import app.pacinglab.core.data.repository.GoogleHealthStatus
import app.pacinglab.core.data.sync.SyncStatus
import app.pacinglab.core.designsystem.theme.PacingLabTheme
import app.pacinglab.core.healthconnect.HealthConnectAvailability
import app.pacinglab.core.healthconnect.probe.ProbeProgress
import app.pacinglab.feature.settings.PHONE
import app.pacinglab.feature.settings.TALL_PHONE
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

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [Config.NEWEST_SDK], qualifiers = PHONE)
class DataSourcesScreenScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun permissionsMissing() = capture(SampleProbe.available)

    @Test
    fun onlyRoutesMissing() = capture(SampleProbe.available.copy(grantedCount = 21, requestedCount = 22, onlyRoutesMissing = true))

    @Test
    fun probeRunning() = capture(
        SampleProbe.available.copy(
            probe = ProbeState.Running(ProbeProgress(ProbeProgress.Stage.RECORD_TYPES, 2, 20, "HeartRateRecord")),
        ),
    )

    @Test
    fun importing() =
        capture(SampleProbe.available.copy(sync = SyncCardState(status = SyncStatus.Importing(null, 0.35f), workoutCount = 120)))

    @Test
    @Config(qualifiers = TALL_PHONE)
    fun googleHealthConnected() = capture(
        SampleProbe.available.copy(
            grantedCount = 22,
            requestedCount = 22,
            googleHealth = GoogleHealthCardState(
                GoogleHealthStatus(
                    connected = true,
                    lastSync = Instant.parse("2026-10-29T07:12:00Z"),
                    days = 15,
                    newest = LocalDate.of(2026, 10, 29),
                ),
            ),
        ),
    )

    @Test
    @Config(qualifiers = TALL_PHONE)
    fun googleHealthNeedsSignIn() = capture(
        SampleProbe.available.copy(
            grantedCount = 22,
            requestedCount = 22,
            googleHealth = GoogleHealthCardState(GoogleHealthStatus(connected = true, problem = GoogleHealthProblem.SIGN_IN_NEEDED)),
        ),
    )

    @Test
    fun unavailable() = capture(DataSourcesUiState(availability = HealthConnectAvailability.UNAVAILABLE))

    @Test
    @Config(qualifiers = TALL_PHONE)
    fun finishedLight() = capture(SampleProbe.finished)

    @Test
    @Config(qualifiers = TALL_PHONE)
    fun finishedDark() = capture(SampleProbe.finished, darkTheme = true)

    @Test
    @Config(qualifiers = TALL_PHONE, fontScale = 1.5f)
    fun finishedLargeFont() = capture(SampleProbe.finished)

    private fun capture(state: DataSourcesUiState, darkTheme: Boolean = false) {
        compose.setContent {
            PacingLabTheme(darkTheme = darkTheme) {
                DataSourcesScreen(
                    state = state,
                    exportSessionCount = 8,
                    onBack = {},
                    onGrantAccess = {},
                    onOpenHealthConnect = {},
                    onSyncNow = {},
                    onRunProbe = {},
                    onExport = {},
                    onShareFile = {},
                    zoneId = ZoneOffset.UTC,
                )
            }
        }
        compose.onRoot().captureRoboImage()
    }
}
