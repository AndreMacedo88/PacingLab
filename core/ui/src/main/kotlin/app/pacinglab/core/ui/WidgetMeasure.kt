package app.pacinglab.core.ui

import androidx.annotation.StringRes

/**
 * What the home-screen widget can show, in its default order. The athlete picks and orders them in
 * Settings; the widget shows as many as its size allows, each figure in a tile of its own, vitals in a
 * panel of four tiles when there is room, and the worded ones ([Kind.LINE]) on a line underneath.
 */
enum class WidgetMeasure(
    val id: String,
    @param:StringRes val label: Int,
    @param:StringRes val description: Int,
    val hiddenByDefault: Boolean = false,
    val kind: Kind = Kind.FIGURE,
) {
    VITALS("vitals", R.string.widget_measure_vitals, R.string.widget_measure_vitals_about, kind = Kind.PANEL),
    READINESS("readiness", R.string.widget_measure_readiness, R.string.widget_measure_readiness_about),
    SLEEP_SCORE("sleep", R.string.widget_measure_sleep, R.string.widget_measure_sleep_about),
    STATUS("status", R.string.widget_measure_status, R.string.widget_measure_status_about, kind = Kind.LINE),
    FORM("form", R.string.widget_measure_form, R.string.widget_measure_form_about),
    HRV("hrv", R.string.widget_measure_hrv, R.string.widget_measure_hrv_about, hiddenByDefault = true),
    RESTING_HR("resting_hr", R.string.widget_measure_resting_hr, R.string.widget_measure_resting_hr_about, hiddenByDefault = true),
    TIME_ASLEEP("asleep", R.string.widget_measure_asleep, R.string.widget_measure_asleep_about, hiddenByDefault = true),
    LOAD_WEEK("load_week", R.string.widget_measure_load, R.string.widget_measure_load_about, hiddenByDefault = true),
    VO2MAX("vo2max", R.string.widget_measure_vo2max, R.string.widget_measure_vo2max_about, hiddenByDefault = true),
    ;

    /** How a measure takes its place: a figure in one tile, a panel of rows, or a few words on the line underneath. */
    enum class Kind { FIGURE, PANEL, LINE }

    companion object {
        fun of(id: String): WidgetMeasure? = entries.firstOrNull { it.id == id }
    }
}
