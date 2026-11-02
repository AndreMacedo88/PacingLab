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
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import app.pacinglab.core.analytics.recovery.FactorEffect
import app.pacinglab.core.analytics.recovery.FactorPoint
import app.pacinglab.core.analytics.recovery.OutcomeFactors
import app.pacinglab.core.analytics.recovery.SleepFactor
import app.pacinglab.core.analytics.recovery.SleepFactors
import app.pacinglab.core.analytics.recovery.SleepOutcome
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
import kotlin.math.sin

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [Config.NEWEST_SDK], qualifiers = "w384dp-h1000dp-xxhdpi")
class SleepFactorsScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val last = LocalDate.of(2026, 12, 20)

    /** Nights spread around a line of [slope] per unit, made-up values. */
    private fun points(slope: Double, from: Double, to: Double, binary: Boolean = false) = List(60) { i ->
        val value = if (binary) (if (i % 3 == 0) 1.0 else 0.0) else from + (to - from) * ((i * 37) % 60) / 59.0
        FactorPoint(last.minusDays(59L - i), value, 80 + slope * (value - (from + to) / 2) + 7 * sin(i * 1.7))
    }

    private fun effect(
        factor: SleepFactor,
        step: Double,
        mean: Double,
        spread: Double,
        chance: Double,
        clear: Boolean,
        from: Double = 0.0,
        to: Double = 1.0,
    ) = FactorEffect(
        factor = factor,
        step = step,
        mean = mean,
        low = mean - spread,
        high = mean + spread,
        chance = chance,
        clear = clear,
        nights = 60,
        yes = if (factor.binary) 20 else null,
        points = points(mean / step, from, to, factor.binary),
        slope = mean / step,
        anchor = (from + to) / 2,
        anchorValue = 80.0,
    )

    private val score = OutcomeFactors(
        outcome = SleepOutcome.SCORE,
        nights = 60,
        excluded = 2,
        effects = listOf(
            effect(SleepFactor.FREE_DAY, 1.0, 5.2, 3.1, 0.96, clear = true),
            effect(SleepFactor.BEDTIME, 45.0, -3.1, 1.9, 0.91, clear = true, from = 300.0, to = 420.0),
            effect(SleepFactor.TRAINING_LOAD, 60.0, 1.3, 2.6, 0.42, clear = false, from = 0.0, to = 150.0),
            effect(SleepFactor.PREVIOUS_NIGHT, 50.0, 0.8, 2.2, 0.31, clear = false, from = -90.0, to = 60.0),
            effect(SleepFactor.FORM, 12.0, -0.4, 2.4, 0.22, clear = false, from = -25.0, to = 10.0),
            effect(SleepFactor.SLEEPING_HR, 3.0, -3.4, 2.0, 0.9, clear = true, from = -4.0, to = 6.0),
            effect(SleepFactor.HRV, 15.0, 1.9, 2.3, 0.62, clear = false, from = -30.0, to = 25.0),
        ),
        waiting = listOf(SleepFactor.LATE_TRAINING, SleepFactor.SKIN_TEMPERATURE),
        predictive = true,
        spread = 8.4,
    )

    private val factors = SleepFactors(score, OutcomeFactors(SleepOutcome.RESTED, nights = 9))

    @Test
    fun factors() = capture { SleepFactorsCard(factors, Modifier.padding(16.dp)) }

    @Test
    fun factorsDark() = capture(darkTheme = true) { SleepFactorsCard(factors, Modifier.padding(16.dp)) }

    @Test
    fun learning() = capture { SleepFactorsCard(factors, Modifier.padding(16.dp), initialOutcome = SleepOutcome.RESTED) }

    @Test
    fun factorDetails() = capture {
        Column {
            FactorDetail(score.effects[1], SleepOutcome.SCORE, predictive = true, Modifier.padding(24.dp))
            FactorDetail(score.effects[0], SleepOutcome.SCORE, predictive = true, Modifier.padding(24.dp))
        }
    }

    @Test
    @Config(qualifiers = "w384dp-h6000dp-xxhdpi")
    fun infoWithTheMathematics() = capture {
        // The width of the text in an alert dialog on a 360 dp phone.
        Column(Modifier.padding(24.dp).width(264.dp)) {
            InfoText(stringResource(R.string.factors_info))
            AdvancedInfo(stringResource(R.string.factors_advanced), initiallyOpen = true)
        }
    }

    @Test
    fun switchingOutcomeShowsTheRatedMornings() {
        compose.setContent { PacingLabTheme { Surface { SleepFactorsCard(factors) } } }
        compose.onNodeWithText("Clearest so far: Free day next morning, +5 points").assertExists()

        compose.onNodeWithText("Feeling rested").performClick()

        compose.onNodeWithText("Learning: 9 of 30 rated mornings").assertExists()
    }

    private fun capture(darkTheme: Boolean = false, content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent { PacingLabTheme(darkTheme = darkTheme) { Surface { content() } } }
        compose.onRoot().captureRoboImage()
    }
}
