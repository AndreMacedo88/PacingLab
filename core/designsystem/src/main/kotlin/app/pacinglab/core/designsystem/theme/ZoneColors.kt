package app.pacinglab.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * Colours for heart rate zones. Zones are ordered, so they share one hue (blue) stepped by lightness
 * and the order reads in the colour itself. The steps were checked as an ordinal ramp against the card
 * surfaces of both themes: each step visibly lighter or darker than its neighbour (OKLCH delta L of at
 * least 0.06) and the palest at least 2:1 against the surface. In the dark theme the ramp runs the
 * other way, so that easy zones recede into the surface in both themes.
 *
 * Colour never carries a zone alone: every use labels the zone in text beside it.
 */
object ZoneColors {
    private val fiveLight = listOf(0xFF6DA7EC, 0xFF3987E5, 0xFF256ABF, 0xFF184F95, 0xFF0D366B)
    private val fiveDark = listOf(0xFF184F95, 0xFF256ABF, 0xFF3987E5, 0xFF6DA7EC, 0xFF9EC5F4)
    private val threeLight = listOf(0xFF6DA7EC, 0xFF256ABF, 0xFF0D366B)
    private val threeDark = listOf(0xFF184F95, 0xFF3987E5, 0xFF9EC5F4)

    /** One colour per zone, from the easiest zone to the hardest. Supports three- and five-zone models. */
    fun of(zoneCount: Int, dark: Boolean): List<Color> {
        val argb = when (zoneCount) {
            3 -> if (dark) threeDark else threeLight
            5 -> if (dark) fiveDark else fiveLight
            else -> throw IllegalArgumentException("No zone colours for $zoneCount zones")
        }
        return argb.map { Color(it) }
    }
}

/** Zone colours for the current theme. */
@Composable
@ReadOnlyComposable
fun zoneColors(zoneCount: Int): List<Color> = ZoneColors.of(zoneCount, dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f)
