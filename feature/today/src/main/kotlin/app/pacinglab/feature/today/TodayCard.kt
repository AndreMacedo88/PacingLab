package app.pacinglab.feature.today

import androidx.annotation.StringRes
import app.pacinglab.core.data.repository.CardSpec
import app.pacinglab.core.ui.R as UiR

/** The groups of Today's cards; a heading opens each group that has a title. */
enum class TodaySection(@param:StringRes val title: Int?) {
    /** How the athlete is this morning: no heading, it is what the screen opens with. */
    MORNING(null),
    TRAINING(R.string.today_section_training),
}

/**
 * Everything Today can show, in the default order: a briefing that answers "how am I, and what about training".
 * Wide cards take the whole width; tiles take one column and pair up, two to a row. Cards hidden by default
 * have their home on another screen (Body, Sleep, Training, Performance) and can be pinned back from there with
 * a long press, or shown in the editor. Some show only when they have something to say, such as [TODO] while
 * something waits for the athlete.
 */
enum class TodayCard(
    val id: String,
    val wide: Boolean,
    @param:StringRes val title: Int,
    @param:StringRes val description: Int,
    val section: TodaySection,
    val hiddenByDefault: Boolean = false,
) {
    TODO("todo", true, R.string.todo_title, R.string.card_todo, TodaySection.MORNING),
    READINESS("readiness", true, R.string.readiness_title, R.string.card_readiness, TodaySection.MORNING),
    ALERTNESS("alertness", true, UiR.string.alertness_title, R.string.today_card_alertness, TodaySection.MORNING),
    VITALS("vitals", true, UiR.string.vitals_title, R.string.card_vitals, TodaySection.MORNING),
    HEART_RATE("heart_rate", true, UiR.string.heart_rate_title, R.string.today_card_heart_rate, TodaySection.MORNING),
    SLEEP_SCORE("sleep", false, R.string.tile_sleep, R.string.card_sleep, TodaySection.MORNING, hiddenByDefault = true),
    SLEEP_HEALTH(
        "sleep_health",
        false,
        R.string.tile_sleep_health,
        R.string.card_sleep_health,
        TodaySection.MORNING,
        hiddenByDefault = true,
    ),
    SLEEP_DEBT("sleep_debt", false, R.string.tile_sleep_debt, R.string.card_sleep_debt, TodaySection.MORNING, hiddenByDefault = true),
    HRV("hrv", false, UiR.string.tile_hrv, R.string.card_hrv, TodaySection.MORNING, hiddenByDefault = true),
    RESTING_HR("resting_hr", false, UiR.string.tile_resting_hr, R.string.card_resting_hr, TodaySection.MORNING, hiddenByDefault = true),
    BREATHING_RATE("breathing", false, UiR.string.tile_breathing, R.string.card_breathing, TodaySection.MORNING, hiddenByDefault = true),
    SLEEPING_HR("sleeping_hr", false, UiR.string.tile_sleeping_hr, R.string.card_sleeping_hr, TodaySection.MORNING, hiddenByDefault = true),
    OXYGEN_SATURATION("spo2", false, UiR.string.tile_oxygen, R.string.card_oxygen, TodaySection.MORNING, hiddenByDefault = true),
    SKIN_TEMPERATURE(
        "skin_temperature",
        false,
        UiR.string.tile_skin_temperature,
        R.string.card_skin_temperature,
        TodaySection.MORNING,
        hiddenByDefault = true,
    ),
    STRAIN("strain", false, UiR.string.strain_title, R.string.card_strain_tile, TodaySection.MORNING, hiddenByDefault = true),
    LAST_WORKOUT("workout", true, R.string.workout_title, R.string.card_workout, TodaySection.TRAINING),
    TRAINING_STATUS("training_status", true, UiR.string.training_status_title, R.string.card_training_status, TodaySection.TRAINING),
    FORM("form", false, R.string.tile_form, R.string.today_card_form, TodaySection.TRAINING),
    WEEK_LOAD("week", false, R.string.tile_week, R.string.card_week, TodaySection.TRAINING),
    VO2MAX("vo2max", false, R.string.tile_vo2max, R.string.today_card_vo2max, TodaySection.TRAINING, hiddenByDefault = true),
    RACE_5K("race_5k", false, R.string.tile_race_5k, R.string.card_race_5k, TodaySection.TRAINING, hiddenByDefault = true),
    EFFICIENCY(
        "efficiency",
        false,
        R.string.tile_efficiency,
        R.string.today_card_efficiency,
        TodaySection.TRAINING,
        hiddenByDefault = true,
    ),
    WEATHER("weather", true, R.string.weather_title, R.string.card_weather, TodaySection.TRAINING),
    ;

    companion object {
        val catalog: List<CardSpec> = entries.map { CardSpec(it.id, it.hiddenByDefault) }

        fun of(id: String): TodayCard? = entries.firstOrNull { it.id == id }
    }
}
