package app.pacinglab.core.ui

import androidx.annotation.StringRes

/**
 * What the home-screen widget can show, in its default order. The athlete picks and orders them in
 * Settings; the widget shows as many as its size allows, figures first and the worded ones ([line]) in
 * a line underneath.
 */
enum class WidgetMeasure(
    val id: String,
    @param:StringRes val label: Int,
    @param:StringRes val description: Int,
    val hiddenByDefault: Boolean = false,
    val line: Boolean = false,
) {
    READINESS("readiness", R.string.widget_measure_readiness, R.string.widget_measure_readiness_about),
    SLEEP_SCORE("sleep", R.string.widget_measure_sleep, R.string.widget_measure_sleep_about),
    FORM("form", R.string.widget_measure_form, R.string.widget_measure_form_about),
    HRV("hrv", R.string.widget_measure_hrv, R.string.widget_measure_hrv_about),
    RESTING_HR("resting_hr", R.string.widget_measure_resting_hr, R.string.widget_measure_resting_hr_about, hiddenByDefault = true),
    TIME_ASLEEP("asleep", R.string.widget_measure_asleep, R.string.widget_measure_asleep_about, hiddenByDefault = true),
    LOAD_WEEK("load_week", R.string.widget_measure_load, R.string.widget_measure_load_about, hiddenByDefault = true),
    VO2MAX("vo2max", R.string.widget_measure_vo2max, R.string.widget_measure_vo2max_about, hiddenByDefault = true),
    STATUS("status", R.string.widget_measure_status, R.string.widget_measure_status_about, line = true),
    VITALS("vitals", R.string.widget_measure_vitals, R.string.widget_measure_vitals_about, line = true),
    ;

    companion object {
        fun of(id: String): WidgetMeasure? = entries.firstOrNull { it.id == id }
    }
}
