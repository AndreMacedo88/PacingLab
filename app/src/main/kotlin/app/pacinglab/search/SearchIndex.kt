package app.pacinglab.search

import androidx.annotation.StringRes
import androidx.navigation3.runtime.NavKey
import app.pacinglab.R
import app.pacinglab.core.analytics.recovery.NightlyMetric
import app.pacinglab.feature.metrics.BodyCardKind
import app.pacinglab.feature.metrics.navigation.AlertnessKey
import app.pacinglab.feature.metrics.navigation.BodyKey
import app.pacinglab.feature.metrics.navigation.HeartRateDayKey
import app.pacinglab.feature.metrics.navigation.NightlyMetricKey
import app.pacinglab.feature.metrics.navigation.ReactionTestKey
import app.pacinglab.feature.metrics.navigation.ReadinessKey
import app.pacinglab.feature.performance.PerformanceCardKind
import app.pacinglab.feature.performance.navigation.PerformanceKey
import app.pacinglab.feature.settings.SettingsSection
import app.pacinglab.feature.settings.navigation.AboutKey
import app.pacinglab.feature.settings.navigation.DataSourcesKey
import app.pacinglab.feature.settings.navigation.HomeAssistantKey
import app.pacinglab.feature.settings.navigation.PhysiologyKey
import app.pacinglab.feature.settings.navigation.SettingsKey
import app.pacinglab.feature.settings.navigation.SettingsSectionKey
import app.pacinglab.feature.sleep.SleepCardKind
import app.pacinglab.feature.sleep.navigation.SleepKey
import app.pacinglab.feature.today.TodayCard
import app.pacinglab.feature.today.navigation.TodayKey
import app.pacinglab.feature.trends.LoadCardKind
import app.pacinglab.navigation.TrainingKey
import app.pacinglab.navigation.TrainingView
import app.pacinglab.feature.metrics.R as MetricsR
import app.pacinglab.feature.performance.R as PerformanceR
import app.pacinglab.feature.settings.R as SettingsR
import app.pacinglab.feature.sleep.R as SleepR
import app.pacinglab.feature.today.R as TodayR
import app.pacinglab.feature.trends.R as LoadR
import app.pacinglab.feature.workouts.R as WorkoutsR

/** Where a search result lives, which groups the list shown before anything is typed. */
enum class SearchGroup(@param:StringRes val label: Int) {
    TODAY(R.string.destination_today),
    TRAINING(R.string.destination_training),
    PERFORMANCE(R.string.destination_performance),
    BODY(R.string.destination_body),
    SLEEP(R.string.destination_sleep),
    SETTINGS(R.string.search_settings),
}

/**
 * Something the search finds: a card, page or setting, the words a runner might type for it besides its
 * [title] ([keywords], lower case), where it is ([group], then [place] as more steps of the path) and the
 * link that opens it, scrolled to it where it is a card or a setting.
 */
data class SearchEntry(
    @param:StringRes val title: Int,
    val group: SearchGroup,
    val target: NavKey,
    val keywords: List<String> = emptyList(),
    val place: List<Int> = emptyList(),
)

/** Every card, page and setting the search knows, in the order the list shows them before anything is typed. */
object SearchIndex {
    val entries: List<SearchEntry> by lazy { today + training + performance + body + sleep + settings }

    private val today = listOf(
        SearchEntry(TodayR.string.readiness_title, SearchGroup.TODAY, ReadinessKey, listOf("ready", "recovery", "score", "train today")),
        SearchEntry(MetricsR.string.reaction_title, SearchGroup.TODAY, ReactionTestKey, listOf("pvt", "vigilance", "test", "reflexes")),
        SearchEntry(
            TodayCard.ALERTNESS.title,
            SearchGroup.TODAY,
            AlertnessKey,
            listOf("mental", "sharp", "sleepiness", "focus", "concentration"),
        ),
        SearchEntry(
            TodayR.string.weather_title,
            SearchGroup.TODAY,
            TodayKey(TodayCard.WEATHER.id),
            listOf("temperature", "rain", "wind", "dew point", "forecast", "humidity"),
        ),
        SearchEntry(
            TodayR.string.workout_title,
            SearchGroup.TODAY,
            TodayKey(TodayCard.LAST_WORKOUT.id),
            listOf("latest session", "recent run"),
        ),
    )

    private val training = listOf(
        SearchEntry(
            R.string.training_sessions,
            SearchGroup.TRAINING,
            TrainingKey(),
            listOf("workouts", "runs", "activities", "history", "list", "filter", "sort"),
        ),
        SearchEntry(
            WorkoutsR.string.calendar_title,
            SearchGroup.TRAINING,
            TrainingKey(TrainingView.CALENDAR),
            listOf("month", "days", "log"),
            listOf(R.string.training_sessions),
        ),
        load(LoadCardKind.STATUS, "productive", "maintaining", "strained", "detraining", "acwr", "load ratio"),
        load(LoadCardKind.FORM, "ctl", "atl", "tsb", "fitness", "fatigue", "freshness", "banister", "performance management"),
        load(LoadCardKind.WEEKS, "weekly load", "hours", "volume", "time in zones", "trimp", "stress score"),
        load(LoadCardKind.RUNNING, "mileage", "distance", "km per week", "injury", "ramp", "tolerance"),
        load(LoadCardKind.FOCUS, "polarized", "80/20", "vt1", "vt2", "intensity distribution", "seiler"),
    )

    private val performance = listOf(
        performance(PerformanceCardKind.VO2MAX, "fitness age", "aerobic capacity", "vdot", "cardio fitness", "test"),
        performance(PerformanceCardKind.CRITICAL_SPEED, "cs", "d prime", "threshold pace", "anaerobic"),
        performance(
            PerformanceCardKind.ZONES,
            "hrmax",
            "max heart rate",
            "lthr",
            "threshold",
            "resting heart rate",
            "zone 2",
            "z1",
            "z5",
        ),
        performance(PerformanceCardKind.RACES, "5k", "5 km", "10k", "10 km", "half marathon", "marathon", "race time", "daniels"),
        performance(PerformanceCardKind.PACES, "easy pace", "threshold pace", "interval pace", "tempo", "repetition"),
        performance(PerformanceCardKind.EFFORTS, "personal best", "pb", "pr", "records", "fastest", "1 km", "speed duration"),
        performance(PerformanceCardKind.EFFICIENCY, "economy", "pace at heart rate", "zone 2"),
        performance(PerformanceCardKind.DECOUPLING, "pa:hr", "cardiac drift", "endurance"),
        performance(PerformanceCardKind.RECOVERY, "hrr", "recovery between reps", "heart rate drop"),
    )

    private val body = listOf(
        body(BodyCardKind.VITALS, "overnight", "nightly", "vital signs", "illness"),
        metric(MetricsR.string.metric_hrv, NightlyMetric.HRV, "hrv", "rmssd"),
        metric(MetricsR.string.metric_resting_hr, NightlyMetric.RESTING_HR, "rhr", "pulse"),
        metric(MetricsR.string.metric_breathing, NightlyMetric.BREATHING_RATE, "respiratory rate", "respiration", "breaths"),
        metric(MetricsR.string.metric_sleeping_hr, NightlyMetric.SLEEPING_HR, "night heart rate"),
        metric(MetricsR.string.metric_oxygen, NightlyMetric.OXYGEN_SATURATION, "spo2", "blood oxygen"),
        metric(MetricsR.string.metric_skin_temperature, NightlyMetric.SKIN_TEMPERATURE, "temperature", "fever"),
        body(BodyCardKind.RECOVERY, "hrv trend", "resting heart rate trend", "baseline", "normal range", "variation"),
        SearchEntry(
            R.string.search_heart_rate_today,
            SearchGroup.BODY,
            HeartRateDayKey,
            listOf("all-day heart rate", "current", "pulse", "zones today"),
        ),
        body(BodyCardKind.STRAIN, "stress", "still heart rate", "eda", "tension"),
    )

    private val sleep = listOf(
        night(SleepR.string.sleep_score_title, SleepCardKind.TILES, "sleep quality"),
        night(SleepR.string.sleep_time_asleep, SleepCardKind.TILES, "duration", "hours", "sleep need"),
        night(R.string.search_rested, SleepCardKind.RESTED, "morning question", "feeling", "rating", "rested"),
        night(SleepCardKind.NIGHT.title, SleepCardKind.NIGHT, "stages", "deep", "rem", "light", "awake", "hypnogram", "restless"),
        night(SleepCardKind.SCORE.title, SleepCardKind.SCORE, "score parts", "continuity"),
        night(SleepCardKind.QUALITY.title, SleepCardKind.QUALITY, "efficiency", "awakenings", "waso"),
        night(SleepCardKind.HEART_RATE.title, SleepCardKind.HEART_RATE, "night heart rate", "dip"),
        night(R.string.search_night_vitals, SleepCardKind.RECOVERY, "hrv", "resting heart rate", "breathing"),
        patterns(SleepR.string.sleep_health_title, SleepCardKind.HEALTH, "regularity", "sri", "habits"),
        patterns(SleepR.string.sleep_debt_title, SleepCardKind.HABITS, "missed sleep", "deficit"),
        patterns(SleepCardKind.KEY_METRICS.title, SleepCardKind.KEY_METRICS, "week", "month", "averages"),
        patterns(SleepCardKind.TIMING.title, SleepCardKind.TIMING, "chronotype", "mctq", "social jetlag", "bedtime", "midpoint"),
        patterns(SleepCardKind.FEELING.title, SleepCardKind.FEELING, "spearman", "correlation", "rested"),
        patterns(
            SleepCardKind.FACTORS.title,
            SleepCardKind.FACTORS,
            "what affects sleep",
            "bedroom",
            "temperature",
            "co2",
            "humidity",
            "late training",
        ),
    )

    private val settings = listOf(
        SearchEntry(SettingsR.string.settings_title, SearchGroup.SETTINGS, SettingsKey, listOf("preferences", "options")),
        setting(SettingsR.string.settings_sex, SettingsSection.PROFILE, "sex", "gender", "male", "female"),
        setting(SettingsR.string.settings_birth_date, SettingsSection.PROFILE, "birth_date", "age", "birthday"),
        SearchEntry(
            SettingsR.string.physiology_title,
            SearchGroup.SETTINGS,
            PhysiologyKey,
            listOf("max heart rate", "hrmax", "lthr", "threshold", "resting heart rate", "zone boundaries", "custom zones"),
            listOf(SettingsR.string.settings_section_profile),
        ),
        setting(SettingsR.string.settings_merge_gap, SettingsSection.TRAINING, "merge_gap", "merge", "warm-up", "combine", "gap"),
        setting(SettingsR.string.settings_strength_in_load, SettingsSection.TRAINING, "strength_in_load", "weights", "strength", "gym"),
        setting(SettingsR.string.settings_running_increase, SettingsSection.TRAINING, "running_increase", "tolerance", "ramp", "mileage"),
        setting(SettingsR.string.settings_heat, SettingsSection.TRAINING, "heat", "heat adjustment", "wbgt", "hot weather"),
        setting(SettingsR.string.settings_sleep_need, SettingsSection.SLEEP, "sleep_need", "hours of sleep", "need"),
        setting(SettingsR.string.settings_free_days, SettingsSection.SLEEP, "free_days", "weekend", "work days"),
        setting(SettingsR.string.settings_morning_reminder, SettingsSection.SLEEP, "morning_reminder", "notification", "reminder"),
        setting(SettingsR.string.settings_theme, SettingsSection.APPEARANCE, "theme", "colour", "color", "palette"),
        setting(SettingsR.string.settings_dark_mode, SettingsSection.APPEARANCE, "dark_mode", "dark", "light", "night mode"),
        setting(SettingsR.string.settings_speed_by_default, SettingsSection.APPEARANCE, "speed", "km/h", "pace"),
        setting(SettingsR.string.settings_route_coloring, SettingsSection.APPEARANCE, "route_coloring", "map", "route colours"),
        SearchEntry(
            SettingsR.string.settings_widget_title,
            SearchGroup.SETTINGS,
            SettingsSectionKey(SettingsSection.WIDGET),
            listOf("home screen"),
        ),
        SearchEntry(
            SettingsR.string.data_sources_title,
            SearchGroup.SETTINGS,
            DataSourcesKey,
            listOf("health connect", "google health", "fitbit", "sync", "permissions", "export"),
            listOf(SettingsR.string.settings_data_title),
        ),
        setting(SettingsR.string.settings_session_weather, SettingsSection.DATA, "session_weather", "weather"),
        setting(SettingsR.string.settings_backup, SettingsSection.DATA, "backup", "export", "save"),
        setting(SettingsR.string.settings_restore, SettingsSection.DATA, "restore", "import"),
        SearchEntry(
            SettingsR.string.home_assistant_title,
            SearchGroup.SETTINGS,
            HomeAssistantKey,
            listOf("bedroom", "co2", "humidity", "sensors", "webhook"),
            listOf(SettingsR.string.settings_data_title, SettingsR.string.data_sources_title),
        ),
        SearchEntry(
            SettingsR.string.about_title,
            SearchGroup.SETTINGS,
            AboutKey,
            listOf("version", "licence", "license", "privacy", "source"),
        ),
    )

    private fun load(card: LoadCardKind, vararg keywords: String) = SearchEntry(
        card.title,
        SearchGroup.TRAINING,
        TrainingKey(TrainingView.LOAD, card.id),
        keywords.toList(),
        listOf(R.string.training_load),
    )

    private fun performance(card: PerformanceCardKind, vararg keywords: String) =
        SearchEntry(card.title, SearchGroup.PERFORMANCE, PerformanceKey(card.id), keywords.toList(), listOf(card.section.title))

    private fun body(card: BodyCardKind, vararg keywords: String) =
        SearchEntry(card.title, SearchGroup.BODY, BodyKey(card.id), keywords.toList())

    private fun metric(@StringRes title: Int, metric: NightlyMetric, vararg keywords: String) =
        SearchEntry(title, SearchGroup.BODY, NightlyMetricKey(metric.name), keywords.toList())

    private fun night(@StringRes title: Int, card: SleepCardKind, vararg keywords: String) =
        SearchEntry(title, SearchGroup.SLEEP, SleepKey(focus = card.id), keywords.toList(), listOf(SleepR.string.sleep_tab_night))

    private fun patterns(@StringRes title: Int, card: SleepCardKind, vararg keywords: String) = SearchEntry(
        title,
        SearchGroup.SLEEP,
        SleepKey(patterns = true, focus = card.id),
        keywords.toList(),
        listOf(SleepR.string.sleep_tab_patterns),
    )

    private fun setting(@StringRes title: Int, section: SettingsSection, id: String, vararg keywords: String) =
        SearchEntry(title, SearchGroup.SETTINGS, SettingsSectionKey(section, id), keywords.toList(), listOf(section.title))
}
