package app.pacinglab.feature.today

import androidx.annotation.StringRes
import app.pacinglab.core.data.repository.CardSpec

/**
 * Everything Today can show, in the default order. Wide cards take the whole width; tiles take one
 * column and pair up, two to a row. Cards hidden by default come from other screens and can be added
 * from the editor.
 */
enum class TodayCard(
    val id: String,
    val wide: Boolean,
    @param:StringRes val title: Int,
    @param:StringRes val description: Int,
    val hiddenByDefault: Boolean = false,
) {
    READINESS("readiness", true, R.string.readiness_title, R.string.card_readiness),
    RESTED("rested", true, R.string.card_rested_title, R.string.card_rested),
    GYM_EFFORT("gym", true, R.string.card_gym_title, R.string.card_gym),
    HEART_RATE("heart_rate", true, R.string.heart_rate_title, R.string.card_heart_rate),
    SLEEP_SCORE("sleep", false, R.string.tile_sleep, R.string.card_sleep),
    HRV("hrv", false, R.string.tile_hrv, R.string.card_hrv),
    RESTING_HR("resting_hr", false, R.string.tile_resting_hr, R.string.card_resting_hr),
    BREATHING_RATE("breathing", false, R.string.tile_breathing, R.string.card_breathing),
    FORM("form", false, R.string.tile_form, R.string.card_form),
    WEEK_LOAD("week", false, R.string.tile_week, R.string.card_week),
    LAST_WORKOUT("workout", true, R.string.workout_title, R.string.card_workout),
    WEATHER("weather", true, R.string.weather_title, R.string.card_weather),
    SLEEPING_HR("sleeping_hr", false, R.string.tile_sleeping_hr, R.string.card_sleeping_hr, hiddenByDefault = true),
    VO2MAX("vo2max", false, R.string.tile_vo2max, R.string.card_vo2max, hiddenByDefault = true),
    RACE_5K("race_5k", false, R.string.tile_race_5k, R.string.card_race_5k, hiddenByDefault = true),
    EFFICIENCY("efficiency", false, R.string.tile_efficiency, R.string.card_efficiency, hiddenByDefault = true),
    ;

    companion object {
        val catalog: List<CardSpec> = entries.map { CardSpec(it.id, it.hiddenByDefault) }

        fun of(id: String): TodayCard? = entries.firstOrNull { it.id == id }
    }
}
