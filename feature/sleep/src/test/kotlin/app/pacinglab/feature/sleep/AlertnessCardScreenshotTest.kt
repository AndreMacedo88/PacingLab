package app.pacinglab.feature.sleep

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import app.pacinglab.core.analytics.alertness.AlertnessHistory
import app.pacinglab.core.designsystem.component.AdvancedInfo
import app.pacinglab.core.designsystem.component.InfoText
import app.pacinglab.core.designsystem.theme.PacingLabTheme
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
import java.time.LocalDate
import java.time.ZoneOffset
import app.pacinglab.core.ui.R as UiR

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [Config.NEWEST_SDK], qualifiers = "w384dp-h832dp-xxhdpi")
class AlertnessCardScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 12, 18)
    private val zone = ZoneOffset.UTC
    private val history = SampleAlertness.history(today, zone, shortNight = today.minusDays(2))

    private fun card(history: AlertnessHistory?, morning: LocalDate = today, onTest: () -> Unit = {}): @Composable () -> Unit = {
        AlertnessCard(history, morning, zone, onOpen = {}, onTest = onTest, modifier = Modifier.padding(16.dp))
    }

    @Test
    fun today() = capture(content = card(history))

    @Test
    fun todayDark() = capture(darkTheme = true, content = card(history))

    @Test
    fun afterAShortNight() = capture(content = card(history, morning = today.minusDays(2)))

    @Test
    fun tunedByTests() = capture(content = card(SampleAlertness.history(today, zone, tests = 14, testedToday = true)))

    @Test
    fun withoutNights() = capture(content = card(SampleAlertness.history(today, zone).copy(days = emptyMap())))

    @Test
    @Config(qualifiers = "w384dp-h4000dp-xxhdpi")
    fun infoWithTheMathematics() = capture {
        // The width of the text in an alert dialog on a 360 dp phone.
        Column(Modifier.padding(24.dp).width(264.dp)) {
            InfoText(stringResource(UiR.string.alertness_info))
            AdvancedInfo(stringResource(UiR.string.alertness_advanced), initiallyOpen = true)
        }
    }

    @Test
    fun theTestIsOfferedOnTodayOnly() {
        var tests = 0
        val morning = androidx.compose.runtime.mutableStateOf(today)
        compose.setContent { PacingLabTheme { Surface { AlertnessCard(history, morning.value, zone, onOpen = {}, onTest = { tests++ }) } } }
        compose.onNodeWithText("Take the 3-minute test").performClick()
        assertThat(tests).isEqualTo(1)

        morning.value = today.minusDays(1)
        compose.onNodeWithText("Take the 3-minute test").assertDoesNotExist()
    }

    private fun capture(darkTheme: Boolean = false, content: @Composable () -> Unit) {
        compose.setContent { PacingLabTheme(darkTheme = darkTheme) { Surface { content() } } }
        compose.onRoot().captureRoboImage()
    }
}
