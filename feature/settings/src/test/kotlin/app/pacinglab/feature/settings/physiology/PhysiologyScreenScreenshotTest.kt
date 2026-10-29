package app.pacinglab.feature.settings.physiology

import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import app.pacinglab.core.analytics.zones.MaxHeartRateEstimate
import app.pacinglab.core.designsystem.theme.PacingLabTheme
import app.pacinglab.core.model.PhysiologyEntry
import app.pacinglab.core.model.ZoneBoundaries
import app.pacinglab.feature.settings.PHONE
import app.pacinglab.feature.settings.TALL_PHONE
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [Config.NEWEST_SDK], qualifiers = PHONE)
class PhysiologyScreenScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 10, 21)
    private val first = PhysiologyEntry(LocalDate.of(2025, 8, 14), maxHr = 186)
    private val latest = PhysiologyEntry(LocalDate.of(2026, 10, 1), maxHr = 186, thresholdHr = 170, restingHr = 52)
    private val estimate = MaxHeartRateEstimate(bpm = 186, reachedOn = LocalDate.of(2026, 9, 22), workouts = 120)

    @Test
    fun empty() = capture(PhysiologyUiState.Loaded(emptyList(), current = null, today = today))

    @Test
    @Config(qualifiers = TALL_PHONE)
    fun estimatedThreshold() =
        capture(PhysiologyUiState.Loaded(listOf(first), current(first, measuredRestingHr = 58), today, measuredRestingHr = 58))

    @Test
    fun beforeAnyNightIsRecorded() = capture(PhysiologyUiState.Loaded(listOf(first), current(first), today))

    @Test
    @Config(qualifiers = TALL_PHONE)
    fun everythingAutomatic() = capture(
        PhysiologyUiState.Loaded(
            entries = emptyList(),
            current = current(null, estimate, measuredRestingHr = 58, today = today),
            today = today,
            measuredRestingHr = 58,
            estimatedMax = estimate,
        ),
    )

    @Test
    @Config(qualifiers = TALL_PHONE)
    fun history() = capture(PhysiologyUiState.Loaded(listOf(latest, first), current(latest), today))

    @Test
    @Config(qualifiers = TALL_PHONE)
    fun historyDark() = capture(PhysiologyUiState.Loaded(listOf(latest, first), current(latest), today), darkTheme = true)

    @Test
    @OptIn(ExperimentalRoborazziApi::class)
    fun zoneBoundariesDialog() {
        compose.setContent {
            PacingLabTheme {
                ZoneBoundariesDialog(zones = current(latest)!!.fiveZones, custom = true, onSave = {}, onDismiss = {})
            }
        }
        captureScreenRoboImage()
    }

    @Test
    fun customZonesAreLabelledAsYours() {
        val custom = PhysiologyUiState.Loaded(
            listOf(latest, first),
            current(latest),
            today,
            boundaries = ZoneBoundaries(five = listOf(140, 150, 160, 172)),
        )
        compose.setContent { PacingLabTheme { PhysiologyScreen(custom, onBack = {}, onSave = { _, _ -> }, onDelete = {}) } }

        compose.onNodeWithText("Change your boundaries").assertExists()
    }

    @Test
    fun editorForNewValues() {
        compose.setContent {
            PacingLabTheme {
                PhysiologyEditor(
                    editing = null,
                    template = first,
                    today = today,
                    measuredRestingHr = 58,
                    estimatedMaxHr = 186,
                    estimatedThresholdHr = null,
                    takenDates = setOf(first.effectiveFrom),
                    onDismiss = {},
                    onSave = {},
                    onDelete = null,
                )
            }
        }
        compose.onNode(isDialog()).captureRoboImage()
    }

    private fun capture(state: PhysiologyUiState, darkTheme: Boolean = false) {
        compose.setContent {
            PacingLabTheme(darkTheme = darkTheme) {
                PhysiologyScreen(state = state, onBack = {}, onSave = { _, _ -> }, onDelete = {})
            }
        }
        compose.onRoot().captureRoboImage()
    }
}
