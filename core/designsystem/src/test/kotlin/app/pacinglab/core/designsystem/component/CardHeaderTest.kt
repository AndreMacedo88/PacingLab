package app.pacinglab.core.designsystem.component

import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.pacinglab.core.designsystem.theme.PacingLabTheme
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [Config.NEWEST_SDK], qualifiers = "w384dp-h832dp-xxhdpi")
class CardHeaderTest {
    @get:Rule
    val compose = createComposeRule()

    private val explanation = "Fitness averages daily load over about six weeks.\n\nFatigue does the same over about a week."

    @Test
    fun theInfoButtonOpensAndClosesTheExplanation() {
        compose.setContent {
            PacingLabTheme { Surface { CardHeader(title = "Fitness and form", info = explanation) } }
        }
        compose.onNodeWithText(explanation).assertDoesNotExist()

        compose.onNodeWithContentDescription("About Fitness and form").performClick()
        compose.onNodeWithText(explanation).assertExists()

        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithText(explanation).assertDoesNotExist()
    }

    @Test
    fun withoutInfoThereIsNoButton() {
        compose.setContent {
            PacingLabTheme { Surface { CardHeader(title = "Recorded parts") } }
        }

        compose.onNodeWithContentDescription("About Recorded parts").assertDoesNotExist()
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun dialog() {
        compose.setContent {
            PacingLabTheme { InfoDialog(title = "Fitness and form", text = explanation, onDismiss = {}) }
        }
        captureScreenRoboImage()
    }
}
