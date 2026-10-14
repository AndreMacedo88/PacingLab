package app.pacinglab.core.designsystem.format

import java.util.Locale
import kotlin.math.roundToLong

/** Number formatting shared by all screens. Units are added by string resources. */
object Formats {
    /** "45:07" below an hour, "1:02:45" above. Rounded to whole seconds. */
    fun duration(millis: Long): String {
        val total = (millis / 1000.0).roundToLong().coerceAtLeast(0)
        val hours = total / 3600
        val minutes = (total % 3600) / 60
        val seconds = total % 60
        return if (hours > 0) {
            String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
        }
    }

    /** Kilometres with two decimals below 100 km, one above. */
    fun kilometres(meters: Double, locale: Locale): String {
        val km = meters / 1000.0
        return String.format(locale, if (km < 100) "%.2f" else "%.1f", km)
    }

    /** Pace as minutes:seconds per kilometre, or null when it would be meaningless. */
    fun pacePerKm(durationMillis: Long, meters: Double): String? {
        if (meters < MIN_PACE_DISTANCE || durationMillis <= 0) return null
        val secondsPerKm = (durationMillis / 1000.0) / (meters / 1000.0)
        if (secondsPerKm > MAX_PACE_SECONDS) return null
        val rounded = secondsPerKm.roundToLong()
        return String.format(Locale.ROOT, "%d:%02d", rounded / 60, rounded % 60)
    }

    private const val MIN_PACE_DISTANCE = 100.0

    /** Slower than an hour per kilometre is standing still, not a pace. */
    private const val MAX_PACE_SECONDS = 3600.0
}
