package app.pacinglab.feature.metrics

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.pacinglab.core.analytics.alertness.ReactionTestRun
import app.pacinglab.core.designsystem.theme.PacingLabTheme
import app.pacinglab.core.model.ReactionTest
import app.pacinglab.core.testing.SampleAlertness
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

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [Config.NEWEST_SDK], qualifiers = "w384dp-h832dp-xxhdpi")
class AlertnessScreensScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 12, 18)
    private val zone = ZoneOffset.UTC
    private val history = SampleAlertness.history(today, zone, tests = 14, testedToday = true, shortNight = today.minusDays(5))
    private val begin = { Instant.EPOCH to ZoneOffset.UTC }

    @Test
    @Config(qualifiers = "w384dp-h2400dp-xxhdpi")
    fun alertnessPage() = capture { AlertnessScreen(history, onBack = {}, onTest = {}, onDelete = {}, zoneId = zone) }

    @Test
    fun alertnessPageWithoutTestsDark() = capture(dark = true) {
        AlertnessScreen(SampleAlertness.history(today, zone, tests = 0), onBack = {}, onTest = {}, onDelete = {}, zoneId = zone)
    }

    @Test
    fun testIntro() = capture { ReactionTestScreen(history, onBack = {}, onBegin = begin, onFinish = {}, onDiscard = {}) }

    @Test
    fun testCounterRunning() = capture { TestArea(ReactionTestRun.State.Stimulus(0), counter = 247, progress = 0.4f) }

    @Test
    fun testTooSoonDark() = capture(dark = true) { TestArea(ReactionTestRun.State.FalseStart(0, 0), counter = 0, progress = 0.7f) }

    @Test
    fun testStopped() = capture {
        ReactionTestScreen(history, onBack = {}, onBegin = begin, onFinish = {}, onDiscard = {}, initialPhase = TestPhase.Stopped)
    }

    @Test
    fun testResult() = capture {
        ReactionTestScreen(history, onBack = {
        }, onBegin = begin, onFinish = {}, onDiscard = {}, initialPhase = TestPhase.Done(history.tests.last().test))
    }

    @Test
    fun aTestCutShortIsNotKept() {
        val short = ReactionTest(Instant.EPOCH, null, List(5) { 300 }, falseStarts = 0)
        compose.setContent {
            PacingLabTheme {
                ReactionTestScreen(history, onBack = {
                }, onBegin = begin, onFinish = {}, onDiscard = {}, initialPhase = TestPhase.Done(short))
            }
        }
        compose.onNodeWithText("Too few answers to score this test, so it was not kept.").assertExists()
    }

    @Test
    fun deletingATestAsksFirst() {
        val deleted = mutableListOf<Instant>()
        compose.setContent {
            PacingLabTheme { AlertnessScreen(history, onBack = {}, onTest = {}, onDelete = { deleted += it }, zoneId = zone) }
        }
        compose.onAllNodes(hasContentDescription("Delete test"))[0].performScrollTo().performClick()
        compose.onNodeWithText("Delete this test?").assertExists()
        compose.onNodeWithText("Delete").performClick()
        assertThat(deleted).isEqualTo(listOf(history.tests.last().test.start))
    }

    private fun capture(dark: Boolean = false, content: @Composable () -> Unit) {
        compose.setContent { PacingLabTheme(darkTheme = dark) { content() } }
        compose.onRoot().captureRoboImage()
    }
}
