package app.pacinglab.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * Colours for chart marks. Series take the categorical slots in fixed order (blue, orange, aqua), which
 * were checked against the card surfaces of both themes for colour-vision deficiency and contrast; the
 * light orange sits just under 3:1, so it always comes with a visible label. Signed quantities use the
 * blue-red diverging pair. Text never uses these colours: labels stay in the theme's text colours with
 * a colour key beside them.
 */
@Immutable
class ChartColors(
    val series: List<Color>,
    val positive: Color,
    val negative: Color,
    val muted: Color,
    val grid: Color,
    val baseline: Color,
    /** Sleep stages; validated as neighbours in the order deep, REM, light, awake, so draw them in that order. */
    val deepSleep: Color,
    val remSleep: Color,
    val lightSleep: Color,
    val awake: Color,
) {
    companion object {
        val Light = ChartColors(
            series = listOf(Color(0xFF2A78D6), Color(0xFFEB6834), Color(0xFF1BAF7A)),
            positive = Color(0xFF2A78D6),
            negative = Color(0xFFE34948),
            muted = Color(0xFF898781),
            grid = Color(0xFFE1E0D9),
            baseline = Color(0xFFC3C2B7),
            deepSleep = Color(0xFF6250D6),
            remSleep = Color(0xFF1BAF7A),
            lightSleep = Color(0xFF2A78D6),
            awake = Color(0xFFEB6834),
        )

        val Dark = ChartColors(
            series = listOf(Color(0xFF3987E5), Color(0xFFD95926), Color(0xFF199E70)),
            positive = Color(0xFF3987E5),
            negative = Color(0xFFE66767),
            muted = Color(0xFF898781),
            grid = Color(0xFF2C2C2A),
            baseline = Color(0xFF383835),
            deepSleep = Color(0xFF9085E9),
            remSleep = Color(0xFF199E70),
            lightSleep = Color(0xFF3987E5),
            awake = Color(0xFFD95926),
        )
    }
}

/**
 * Colours for states such as "ready" or "take it easy". They are fixed across themes and always come
 * with an icon and a label: colour never carries the state alone.
 */
object StatusColors {
    val Good = Color(0xFF0CA30C)
    val Warning = Color(0xFFFAB219)
    val Serious = Color(0xFFEC835A)
    val Critical = Color(0xFFD03B3B)
}

/** Chart colours for the current theme. */
@Composable
@ReadOnlyComposable
fun chartColors(): ChartColors = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) ChartColors.Dark else ChartColors.Light
