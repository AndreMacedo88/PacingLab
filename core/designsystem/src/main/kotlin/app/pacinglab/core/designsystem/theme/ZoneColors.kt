package app.pacinglab.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * Colours for heart rate zones: a cool-to-hot ramp (blue, green, amber, orange, deep red), the
 * "semantic heat" exception to one-hue ordinal ramps, so that a heart rate line coloured by zone reads at
 * a glance. Neighbouring zones were checked against the card surface of each theme: at least 20 (light)
 * and 16.8 (dark) apart in OKLab for normal vision and 12 and 11.4 under simulated protanopia and
 * deuteranopia. Amber sits near 2.1:1 on the light card, so zones always carry their name or a legend
 * beside the colour. The dark theme uses lighter steps of the same hues.
 *
 * Colour never carries a zone alone: every use labels the zone in text beside it.
 */
object ZoneColors {
    private val fiveLight = listOf(0xFF2A78D6, 0xFF109E7B, 0xFFD1A30A, 0xFFE0491B, 0xFF8C1C38)
    private val fiveDark = listOf(0xFF6DA7EC, 0xFF1BB27B, 0xFFEBC323, 0xFFF28149, 0xFFD92B57)

    // The polarized model: easy in blue, between the thresholds in amber, above the second in red.
    private val threeLight = listOf(0xFF2A78D6, 0xFFD1A30A, 0xFF8C1C38)
    private val threeDark = listOf(0xFF6DA7EC, 0xFFEBC323, 0xFFD92B57)

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
