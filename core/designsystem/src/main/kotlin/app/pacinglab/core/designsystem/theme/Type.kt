package app.pacinglab.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle

internal val PacingLabTypography = Typography()

/**
 * Tabular figures keep digits the same width, so numbers that update or sit in columns
 * (paces, heart rates, durations) don't jitter or misalign.
 */
fun TextStyle.tabularNumbers(): TextStyle = copy(fontFeatureSettings = "tnum")
