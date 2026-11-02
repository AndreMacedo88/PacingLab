package app.pacinglab.feature.trends

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import app.pacinglab.core.analytics.strain.DaytimeStrain
import app.pacinglab.core.analytics.strain.StrainCause
import app.pacinglab.core.analytics.strain.StrainDay
import app.pacinglab.core.data.strain.StrainHistory
import app.pacinglab.core.designsystem.theme.PacingLabTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.util.Locale
import kotlin.math.sin

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [Config.NEWEST_SDK], qualifiers = "w384dp-h832dp-xxhdpi")
class StrainCardScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 12, 3)

    /** Four weeks of made-up days around a usual 50, with the last one raised. */
    private val days = (0 until 28).map { i ->
        val score = if (i == 27) 66 else (50 + 9 * sin(i * 1.3)).toInt()
        StrainDay(today.minusDays(27L - i), (score - 50) / 4.0, 380, score, DaytimeStrain.levelOf(score))
    }

    @Test
    fun raisedWithACause() = capture(
        StrainHistory(today, days, causes = mapOf(today to listOf(StrainCause.HARD_TRAINING)), progress = 21),
    )

    @Test
    fun raisedWithACauseDark() = capture(
        StrainHistory(today, days, causes = mapOf(today to listOf(StrainCause.HARD_TRAINING)), progress = 21),
        darkTheme = true,
    )

    @Test
    fun learning() = capture(StrainHistory(today, emptyList(), progress = 9))

    private fun capture(history: StrainHistory, darkTheme: Boolean = false) {
        compose.setContent {
            PacingLabTheme(darkTheme = darkTheme) {
                Surface { StrainCard(history, Locale.UK, Modifier.padding(16.dp)) }
            }
        }
        compose.onRoot().captureRoboImage()
    }
}
