package app.pacinglab.feature.workouts.detail

import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.pacinglab.core.designsystem.format.Formats
import app.pacinglab.feature.workouts.R
import java.util.Locale

/** Pace ("4:01 /km") or, when [showSpeed], speed ("14.9 km/h"); null when it would be meaningless. */
@Composable
internal fun paceText(durationMillis: Long, meters: Double?, showSpeed: Boolean, locale: Locale): String? {
    if (meters == null) return null
    return if (showSpeed) {
        Formats.speedKmh(durationMillis, meters, locale)?.let { stringResource(R.string.unit_speed, it) }
    } else {
        Formats.pacePerKm(durationMillis, meters)?.let { stringResource(R.string.unit_pace, it) }
    }
}

/** Like [paceText] without the unit, for table columns whose header names it. */
internal fun paceValue(durationMillis: Long, meters: Double, showSpeed: Boolean, locale: Locale): String =
    (if (showSpeed) Formats.speedKmh(durationMillis, meters, locale) else Formats.pacePerKm(durationMillis, meters)) ?: DASH

/** Whether running speed shows as pace or as km/h, and how to switch. [label] describes the switch for accessibility. */
@Immutable
internal class SpeedToggle(val showSpeed: Boolean, val label: String, val onToggle: () -> Unit)

/** Makes the element switch between pace and speed when tapped. */
internal fun Modifier.speedToggle(toggle: SpeedToggle): Modifier = clickable(onClickLabel = toggle.label, onClick = toggle.onToggle)
