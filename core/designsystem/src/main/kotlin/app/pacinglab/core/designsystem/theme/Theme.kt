package app.pacinglab.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * The accent colours the app can wear, each a full Material 3 scheme for light and dark generated from
 * one seed (see Palettes.kt). Chart, zone and status colours do not change with it: they carry meaning.
 */
enum class ThemePalette(internal val light: ColorScheme, internal val dark: ColorScheme) {
    INDIGO(IndigoLight, IndigoDark),
    AMBER(AmberLight, AmberDark),
    GREEN(GreenLight, GreenDark),
    BLUE(BlueLight, BlueDark),
}

/** Root theme for every PacingLab screen. Follows the system light/dark setting unless told otherwise. */
@Composable
fun PacingLabTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    palette: ThemePalette = ThemePalette.INDIGO,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) palette.dark else palette.light,
        typography = PacingLabTypography,
        content = content,
    )
}

/** The palette's seed colour, for showing the choice in settings. */
fun ThemePalette.swatch(): Color = light.primaryContainer
