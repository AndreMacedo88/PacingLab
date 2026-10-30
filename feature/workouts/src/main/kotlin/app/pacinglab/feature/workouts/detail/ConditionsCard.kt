package app.pacinglab.feature.workouts.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pacinglab.core.analytics.weather.HeatAdjustment
import app.pacinglab.core.analytics.weather.HeatStress
import app.pacinglab.core.analytics.weather.HeatStressLevel
import app.pacinglab.core.data.weather.SessionWeatherState
import app.pacinglab.core.designsystem.component.CardHeader
import app.pacinglab.core.designsystem.icon.PacingLabIcons
import app.pacinglab.core.designsystem.theme.StatusColors
import app.pacinglab.core.designsystem.theme.tabularNumbers
import app.pacinglab.core.model.SessionWeather
import app.pacinglab.core.model.WeatherCondition
import app.pacinglab.feature.workouts.R
import java.util.Locale
import kotlin.math.roundToInt

/** Heat stress of a session's weather; null without weather. */
internal fun SessionWeatherState?.heat(): HeatStressLevel? =
    (this as? SessionWeatherState.Found)?.weather?.let { HeatStress.level(HeatStress.wbgt(it.temperature, it.humidity)) }

/**
 * The weather during the session at the place it started: temperature and how it felt, dew point and
 * humidity, wind and rain, and a note when heat stress was high enough to push heart rate up.
 */
@Composable
internal fun ConditionsCard(state: SessionWeatherState, locale: Locale, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CardHeader(title = stringResource(R.string.conditions_title), info = stringResource(R.string.conditions_info))
            when (state) {
                is SessionWeatherState.Found -> Weather(state.weather, locale)
                SessionWeatherState.Unreachable -> Note(stringResource(R.string.conditions_unreachable))
                SessionWeatherState.Missing -> Note(stringResource(R.string.conditions_missing))
                SessionWeatherState.NoPlace, SessionWeatherState.Off -> Unit
            }
        }
    }
}

@Composable
private fun Weather(weather: SessionWeather, locale: Locale, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(painterResource(weather.condition.icon()), contentDescription = null, modifier = Modifier.size(32.dp))
            Text(
                stringResource(R.string.conditions_temperature, weather.temperature.roundToInt()),
                style = MaterialTheme.typography.headlineSmall.tabularNumbers(),
            )
            Text(
                stringResource(R.string.conditions_feels_like, weather.apparentTemperature.roundToInt()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = listOfNotNull(
                stringResource(R.string.conditions_dew_point, weather.dewPoint.roundToInt(), weather.humidity.roundToInt()),
                stringResource(R.string.conditions_wind, "%.0f".format(locale, weather.windSpeed)) +
                    (weather.windGusts?.let { " " + stringResource(R.string.conditions_gusts, "%.0f".format(locale, it)) } ?: ""),
                weather.precipitation.takeIf {
                    it >= MIN_RAIN
                }?.let { stringResource(R.string.conditions_rain, "%.1f".format(locale, it)) },
            ).joinToString("  ·  "),
            style = MaterialTheme.typography.bodyMedium.tabularNumbers(),
        )
        val wbgt = HeatStress.wbgt(weather.temperature, weather.humidity)
        val level = HeatStress.level(wbgt)
        val factor = HeatAdjustment.speedFactor(wbgt)
        if (factor > 1) {
            Text(
                stringResource(R.string.conditions_adjusted, "%.1f".format(locale, (factor - 1) * 100)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (level != HeatStressLevel.LOW) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    painterResource(PacingLabIcons.Warning),
                    contentDescription = null,
                    tint = if (level == HeatStressLevel.MODERATE) StatusColors.Warning else StatusColors.Serious,
                    modifier = Modifier.size(20.dp),
                )
                Text(stringResource(R.string.conditions_heat, wbgt.roundToInt()), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun WeatherCondition.icon(): Int = when (this) {
    WeatherCondition.CLEAR -> PacingLabIcons.WeatherSun
    WeatherCondition.PARTLY_CLOUDY -> PacingLabIcons.WeatherPartlyCloudy
    WeatherCondition.CLOUDY -> PacingLabIcons.WeatherCloud
    WeatherCondition.FOG -> PacingLabIcons.WeatherFog
    WeatherCondition.DRIZZLE, WeatherCondition.RAIN -> PacingLabIcons.WeatherRain
    WeatherCondition.SNOW -> PacingLabIcons.WeatherSnow
    WeatherCondition.THUNDERSTORM -> PacingLabIcons.WeatherStorm
}

/** Less rain than this in the hour is not worth a word. */
private const val MIN_RAIN = 0.1
