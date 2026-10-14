package app.pacinglab.feature.settings.datasources

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import app.pacinglab.core.data.sync.SyncStatus
import app.pacinglab.core.designsystem.theme.PacingLabTheme
import app.pacinglab.core.healthconnect.HealthConnectAvailability
import app.pacinglab.core.healthconnect.probe.ProbeProgress
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
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

/** Roughly a Galaxy S25 in its default display size. */
private const val PHONE = "w384dp-h832dp-xxhdpi"

/** Same width, tall enough to capture long scrolling content in one image. */
private const val TALL_PHONE = "w384dp-h1800dp-xxhdpi"
