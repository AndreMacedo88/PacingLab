package app.pacinglab

import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pacinglab.core.designsystem.math.MathFamily
import app.pacinglab.core.designsystem.math.MathLayout
import app.pacinglab.core.designsystem.math.MathParser
import app.pacinglab.core.designsystem.math.MathStyle
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The mathematics in the cards' "Advanced info": every displayed formula fits the text column of an
 * information dialog on the owner's phone (360 dp wide, so 264 dp of text) without scrolling, and inline
 * formulas are closed.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [Config.NEWEST_SDK], qualifiers = "w360dp-h800dp-xxhdpi")
class AdvancedInfoTest {
    @get:Rule
    val compose = createComposeRule()

    private val texts = listOf(
        app.pacinglab.feature.today.R.string.readiness_advanced,
        app.pacinglab.core.ui.R.string.ui_nightly_range_advanced,
        app.pacinglab.core.ui.R.string.ui_training_status_advanced,
        app.pacinglab.core.ui.R.string.ui_effort_advanced,
        app.pacinglab.feature.sleep.R.string.sleep_score_advanced,
        app.pacinglab.feature.sleep.R.string.sleep_health_advanced,
        app.pacinglab.feature.sleep.R.string.feeling_advanced,
        app.pacinglab.feature.sleep.R.string.timing_advanced,
        app.pacinglab.feature.sleep.R.string.factors_advanced,
        app.pacinglab.feature.trends.R.string.form_advanced,
        app.pacinglab.feature.trends.R.string.weeks_advanced,
        app.pacinglab.feature.trends.R.string.recovery_advanced,
        app.pacinglab.feature.trends.R.string.focus_advanced,
        app.pacinglab.feature.trends.R.string.running_advanced,
        app.pacinglab.feature.performance.R.string.vo2_advanced,
        app.pacinglab.feature.performance.R.string.efficiency_advanced,
        app.pacinglab.feature.performance.R.string.race_advanced,
        app.pacinglab.feature.performance.R.string.cs_advanced,
        app.pacinglab.feature.performance.R.string.decoupling_advanced,
        app.pacinglab.feature.performance.R.string.paces_advanced,
        app.pacinglab.feature.performance.R.string.hrr_advanced,
        app.pacinglab.feature.workouts.R.string.load_advanced,
        app.pacinglab.feature.workouts.R.string.zones_advanced,
        app.pacinglab.feature.workouts.R.string.effort_advanced,
        app.pacinglab.feature.workouts.R.string.session_decoupling_advanced,
        app.pacinglab.feature.workouts.R.string.conditions_advanced,
    )

    @Test
    fun everyFormulaFitsTheDialog() {
        val problems = mutableListOf<String>()
        val context = RuntimeEnvironment.getApplication()
        val sources = texts.associate { context.resources.getResourceEntryName(it) to context.getString(it) }
        compose.setContent {
            val measurer = rememberTextMeasurer()
            val density = LocalDensity.current
            val em = with(density) { 15.sp.toPx() }
            // The equation's own side padding is 0.15 em each way.
            val limit = with(density) { 264.dp.toPx() } - 0.3f * em
            for ((name, text) in sources) {
                for (line in text.lines().map { it.trim() }) {
                    if (line.startsWith("$$") && line.endsWith("$$")) {
                        val source = line.removePrefix("$$").removeSuffix("$$")
                        val box = MathLayout(measurer, density, em, MathFamily).layout(MathParser.parse(source), MathStyle.DISPLAY)
                        if (box.width > limit) problems += "$name: ${(box.width / density.density).toInt()} dp, $source"
                    } else if (line.count { it == '$' } % 2 != 0) {
                        problems += "$name: unclosed inline formula in \"$line\""
                    }
                }
            }
        }
        compose.waitForIdle()

        assertEquals(emptyList<String>(), problems)
    }
}
