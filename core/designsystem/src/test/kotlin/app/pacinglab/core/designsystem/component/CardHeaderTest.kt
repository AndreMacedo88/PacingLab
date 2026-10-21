package app.pacinglab.core.designsystem.component

import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.pacinglab.core.designsystem.theme.PacingLabTheme
import assertk.assertThat
import assertk.assertions.containsExactly
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

    private val explanation = "Fitness and fatigue are averages of daily load.\n\n# Fitness and fatigue\n" +
        "- Fitness: about six weeks.\n- Fatigue: about a week.\n\n# Form\nYesterday's fitness minus fatigue."

    @Test
    fun theInfoButtonOpensAndClosesTheExplanation() {
        compose.setContent {
            PacingLabTheme { Surface { CardHeader(title = "Fitness and form", info = explanation) } }
        }
        compose.onNodeWithText("Fitness: about six weeks.").assertDoesNotExist()

        compose.onNodeWithContentDescription("About Fitness and form").performClick()
        compose.onNodeWithText("Fitness: about six weeks.").assertExists()

        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithText("Fitness: about six weeks.").assertDoesNotExist()
    }

    @Test
    fun withoutInfoThereIsNoButton() {
        compose.setContent {
            PacingLabTheme { Surface { CardHeader(title = "Recorded parts") } }
        }

        compose.onNodeWithContentDescription("About Recorded parts").assertDoesNotExist()
    }

    @Test
    fun explanationsSplitIntoHeadingsParagraphsAndBullets() {
        val blocks = InfoBlock.parse("Intro line one\nline two.\n\n# Heading\nLead-in:\n- first\n- second\nAfter.\n\n- alone")

        assertThat(blocks).containsExactly(
            InfoBlock.Paragraph("Intro line one line two."),
            InfoBlock.Heading("Heading"),
            InfoBlock.Paragraph("Lead-in:"),
            InfoBlock.Bullets(listOf("first", "second")),
            InfoBlock.Paragraph("After."),
            InfoBlock.Bullets(listOf("alone")),
        )
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
