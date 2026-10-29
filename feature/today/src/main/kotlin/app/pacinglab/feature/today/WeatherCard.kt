package app.pacinglab.feature.today

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.pacinglab.core.data.weather.WeatherState
import app.pacinglab.core.designsystem.component.CardHeader
import app.pacinglab.core.designsystem.icon.PacingLabIcons
import app.pacinglab.core.designsystem.theme.tabularNumbers
import app.pacinglab.core.model.WeatherCondition
import app.pacinglab.core.model.WeatherForecast
import app.pacinglab.core.model.WeatherReading
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * What the weather card can ask for.
 *
 * @property locationRefused the athlete turned down the permission once, so the button opens the app's
 *   system settings instead of asking again.
 */
class WeatherAccess(val locationRefused: Boolean = false, val onAllowLocation: () -> Unit = {}, val onRetry: () -> Unit = {})

/**
 * The weather where the athlete is, for planning a run: now, with how warm it feels, wind and dew
 * point, and the coming hours with their chance of rain.
 */
@Composable
internal fun WeatherCard(state: WeatherState?, locale: Locale, access: WeatherAccess, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CardHeader(title = stringResource(R.string.weather_title), info = stringResource(R.string.weather_info))
            when (state) {
                null -> Note(stringResource(R.string.weather_loading))

                WeatherState.NeedsLocation -> {
                    Note(stringResource(R.string.weather_needs_location))
                    FilledTonalButton(onClick = access.onAllowLocation) {
                        Text(
                            stringResource(if (access.locationRefused) R.string.weather_open_settings else R.string.weather_allow_location),
                        )
                    }
                }

                WeatherState.NoLocation -> Note(stringResource(R.string.weather_no_location))

                WeatherState.NoConnection -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Note(stringResource(R.string.weather_no_connection), Modifier.weight(1f))
                    TextButton(onClick = access.onRetry) { Text(stringResource(R.string.weather_retry)) }
                }

                is WeatherState.Ready -> Forecast(state.forecast, state.outdated, locale)
            }
        }
    }
}

/** Rows of the card's column: now, wind and dew point, the coming hours. */
@Composable
private fun ColumnScope.Forecast(forecast: WeatherForecast, outdated: Boolean, locale: Locale) {
    val now = forecast.now
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Icon(
            painterResource(now.icon()),
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(degrees(now.temperature), style = MaterialTheme.typography.headlineMedium.tabularNumbers())
        Column(Modifier.weight(1f)) {
            Text(stringResource(now.condition.label()), style = MaterialTheme.typography.titleSmall)
            Text(
                listOfNotNull(
                    stringResource(R.string.weather_feels_like, degrees(now.apparentTemperature)),
                    if (forecast.todayLow != null && forecast.todayHigh != null) {
                        stringResource(R.string.weather_today_range, degrees(forecast.todayLow!!), degrees(forecast.todayHigh!!))
                    } else {
                        null
                    },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall.tabularNumbers(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Measure(
            stringResource(R.string.weather_wind),
            now.windGusts?.let { stringResource(R.string.weather_wind_gusts, now.windSpeed.roundToInt(), it.roundToInt()) }
                ?: stringResource(R.string.weather_wind_speed, now.windSpeed.roundToInt()),
            Modifier.weight(1f),
        )
        Measure(stringResource(R.string.weather_dew_point), degrees(now.dewPoint), Modifier.weight(1f))
    }
    if (forecast.hours.isNotEmpty()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Hours(forecast, locale)
    }
    if (outdated) {
        val time = remember(locale) { DateTimeFormatter.ofPattern("HH:mm", locale) }
        Note(stringResource(R.string.weather_outdated, time.format(forecast.fetched.atOffset(forecast.offset))))
    }
}

@Composable
private fun Hours(forecast: WeatherForecast, locale: Locale) {
    val hour = remember(locale) { DateTimeFormatter.ofPattern("HH", locale) }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        items(forecast.hours, key = { it.time.epochSecond }) { reading ->
            val label = hour.format(reading.time.atOffset(forecast.offset))
            val chance = reading.precipitationChance?.takeIf { it >= MIN_CHANCE_SHOWN }
            val description = listOfNotNull(
                label,
                stringResource(reading.condition.label()),
                degrees(reading.temperature),
                chance?.let { stringResource(R.string.weather_chance_description, it) },
            ).joinToString(", ")
            Column(
                modifier = Modifier.width(44.dp).clearAndSetSemantics { contentDescription = description },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Icon(
                    painterResource(reading.icon()),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(degrees(reading.temperature), style = MaterialTheme.typography.labelLarge.tabularNumbers())
                Text(
                    chance?.let { stringResource(R.string.weather_chance, it) } ?: "",
                    style = MaterialTheme.typography.labelSmall.tabularNumbers(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun Measure(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium.tabularNumbers())
    }
}

@Composable
private fun Note(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun degrees(celsius: Double): String = stringResource(R.string.weather_degrees, celsius.roundToInt())

@DrawableRes
private fun WeatherReading.icon(): Int = when (condition) {
    WeatherCondition.CLEAR -> if (daylight) PacingLabIcons.WeatherSun else PacingLabIcons.WeatherClearNight
    WeatherCondition.PARTLY_CLOUDY -> if (daylight) PacingLabIcons.WeatherPartlyCloudy else PacingLabIcons.WeatherPartlyCloudyNight
    WeatherCondition.CLOUDY -> PacingLabIcons.WeatherCloud
    WeatherCondition.FOG -> PacingLabIcons.WeatherFog
    WeatherCondition.DRIZZLE, WeatherCondition.RAIN -> PacingLabIcons.WeatherRain
    WeatherCondition.SNOW -> PacingLabIcons.WeatherSnow
    WeatherCondition.THUNDERSTORM -> PacingLabIcons.WeatherStorm
}

@StringRes
private fun WeatherCondition.label(): Int = when (this) {
    WeatherCondition.CLEAR -> R.string.weather_clear
    WeatherCondition.PARTLY_CLOUDY -> R.string.weather_partly_cloudy
    WeatherCondition.CLOUDY -> R.string.weather_cloudy
    WeatherCondition.FOG -> R.string.weather_fog
    WeatherCondition.DRIZZLE -> R.string.weather_drizzle
    WeatherCondition.RAIN -> R.string.weather_rain
    WeatherCondition.SNOW -> R.string.weather_snow
    WeatherCondition.THUNDERSTORM -> R.string.weather_thunderstorm
}

/** Chances below this are left blank, so the hours with a real chance of rain stand out. */
private const val MIN_CHANCE_SHOWN = 20
