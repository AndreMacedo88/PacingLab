package app.pacinglab.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * Seven steps of one violet hue (OKLCH hue 300) for pace on a map, slowest first. Violet stays clear of
 * the zone colours, so a pace map is not read as zones. Faster running stands out more from the map:
 * darker on the light basemap, lighter on the dark one. Lightness falls (light) or rises (dark) evenly
 * from step to step, neighbours about 6 apart in OKLab, so the steps read as a gradient alongside the
 * legend's pace at each end. Against the basemaps (#F2F3F0 light, #0C0C0C dark) the slowest step has
 * 2.3:1 (light) and 3.3:1 (dark), the fastest 10.6:1 and 14.2:1.
 */
object PaceColors {
    private val light = listOf(0xFFAD98D5, 0xFF9C83C8, 0xFF8B6FBB, 0xFF7B5AAE, 0xFF6B46A1, 0xFF5C3094, 0xFF4E1586)
    private val dark = listOf(0xFF794DB6, 0xFF8964C3, 0xFF9B7AD1, 0xFFAD91DF, 0xFFBFA7EC, 0xFFD2BDF9, 0xFFE5D4FF)

    /** The steps, slowest first. */
    fun of(dark: Boolean): List<Color> = (if (dark) this.dark else light).map { Color(it) }
}

/** Pace colours for the current theme. */
@Composable
@ReadOnlyComposable
fun paceColors(): List<Color> = PaceColors.of(dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f)
