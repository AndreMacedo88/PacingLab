package app.pacinglab.feature.sleep

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import app.pacinglab.core.analytics.recovery.RestednessEstimate
import app.pacinglab.core.designsystem.component.AdvancedInfo
import app.pacinglab.core.designsystem.component.InfoText
import app.pacinglab.core.designsystem.theme.PacingLabTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [Config.NEWEST_SDK], qualifiers = "w384dp-h832dp-xxhdpi")
class RestednessCardScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val morning = LocalDate.of(2026, 12, 20)

    // Made-up values: a week of expected answers, the last morning not answered yet.
    private val expected = listOf(2.6, 3.1, 3.4, 2.9, 2.4, 3.2, 3.6)
    private val answers = listOf(2, 3, 4, 3, 2, 3, null)
    private val days = (6 downTo 0).map { morning.minusDays(it.toLong()) }
    private val rested = days.zip(answers).mapNotNull { (day, answer) -> answer?.let { day to it } }.toMap()

    private val closer = RestednessEstimate(
        mornings = 48,
        expected = days.zip(expected).toMap(),
        error = 0.58,
        averageError = 0.71,
        scoreError = 0.68,
        usual = 3.0,
    )

    @Test
    fun estimate() = capture { RestednessCard(closer, morning, rested, Modifier.padding(16.dp)) }

    @Test
    fun estimateDark() = capture(darkTheme = true) { RestednessCard(closer, morning, rested, Modifier.padding(16.dp)) }

    @Test
    fun answeredMorning() = capture {
        // A morning already answered, about as close as the sleep score.
        RestednessCard(closer.copy(scoreError = 0.6), morning.minusDays(1), rested, Modifier.padding(16.dp))
    }

    @Test
    fun learning() = capture { RestednessCard(RestednessEstimate(13), morning, rested, Modifier.padding(16.dp)) }

    @Test
    fun noPattern() = capture {
        RestednessCard(
            RestednessEstimate(34, error = 0.7, averageError = 0.71, scoreError = 0.72, usual = 2.9),
            morning,
            rested,
            Modifier.padding(16.dp),
        )
    }

    @Test
    @Config(qualifiers = "w384dp-h6000dp-xxhdpi")
    fun infoWithTheMathematics() = capture {
        // The width of the text in an alert dialog on a 360 dp phone.
        Column(Modifier.padding(24.dp).width(264.dp)) {
            InfoText(stringResource(R.string.sleep_restedness_info))
            AdvancedInfo(stringResource(R.string.sleep_restedness_advanced), initiallyOpen = true)
        }
    }

    @Test
    fun anEstimateCloserThanTheScoreSaysSo() {
        compose.setContent { PacingLabTheme { Surface { RestednessCard(closer, morning, rested) } } }

        compose.onNodeWithText("3.6").assertExists()
        compose.onNodeWithText("Above usual").assertExists()
        compose.onNodeWithText("Closer to your answers than the sleep score").assertExists()
        compose.onNodeWithText("Typically 0.58 off your answer; the sleep score 0.68").assertExists()
    }

    private fun capture(darkTheme: Boolean = false, content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent { PacingLabTheme(darkTheme = darkTheme) { Surface { content() } } }
        compose.onRoot().captureRoboImage()
    }
}
