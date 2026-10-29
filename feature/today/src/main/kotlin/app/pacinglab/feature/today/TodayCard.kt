package app.pacinglab.feature.today

import androidx.annotation.StringRes
import app.pacinglab.core.data.repository.CardSpec
import app.pacinglab.core.designsystem.R as DesignR

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
    VITALS("vitals", true, R.string.vitals_title, R.string.card_vitals),
    RESTED("rested", true, R.string.card_rested_title, R.string.card_rested),
    GYM_EFFORT("gym", true, R.string.card_gym_title, R.string.card_gym),
    HEART_RATE("heart_rate", true, R.string.heart_rate_title, R.string.today_card_heart_rate),
    SLEEP_SCORE("sleep", false, R.string.tile_sleep, R.string.card_sleep),
    SLEEP_HEALTH("sleep_health", false, R.string.tile_sleep_health, R.string.card_sleep_health),
    SLEEP_DEBT("sleep_debt", false, R.string.tile_sleep_debt, R.string.card_sleep_debt),
    HRV("hrv", false, R.string.tile_hrv, R.string.card_hrv),
    RESTING_HR("resting_hr", false, R.string.tile_resting_hr, R.string.card_resting_hr),
    BREATHING_RATE("breathing", false, R.string.tile_breathing, R.string.card_breathing),
    FORM("form", false, R.string.tile_form, R.string.today_card_form),
    WEEK_LOAD("week", false, R.string.tile_week, R.string.card_week),
    TRAINING_STATUS("training_status", true, DesignR.string.training_status_title, R.string.card_training_status),
    LAST_WORKOUT("workout", true, R.string.workout_title, R.string.card_workout),
    WEATHER("weather", true, R.string.weather_title, R.string.card_weather),
    SLEEPING_HR("sleeping_hr", false, R.string.tile_sleeping_hr, R.string.card_sleeping_hr, hiddenByDefault = true),
    VO2MAX("vo2max", false, R.string.tile_vo2max, R.string.today_card_vo2max, hiddenByDefault = true),
    RACE_5K("race_5k", false, R.string.tile_race_5k, R.string.card_race_5k, hiddenByDefault = true),
    EFFICIENCY("efficiency", false, R.string.tile_efficiency, R.string.today_card_efficiency, hiddenByDefault = true),
    OXYGEN_SATURATION("spo2", false, R.string.tile_oxygen, R.string.card_oxygen, hiddenByDefault = true),
    SKIN_TEMPERATURE("skin_temperature", false, R.string.tile_skin_temperature, R.string.card_skin_temperature, hiddenByDefault = true),
    ;

    companion object {
        val catalog: List<CardSpec> = entries.map { CardSpec(it.id, it.hiddenByDefault) }

        fun of(id: String): TodayCard? = entries.firstOrNull { it.id == id }
    }
}
