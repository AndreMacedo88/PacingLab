package app.pacinglab.feature.sleep

import androidx.annotation.StringRes
import app.pacinglab.core.data.repository.CardSpec
import app.pacinglab.core.data.repository.Dashboard
import app.pacinglab.core.ui.R as UiR

/** The two views of Sleep: one night, and what many nights show. */
enum class SleepView(val dashboard: Dashboard, @param:StringRes val title: Int, @param:StringRes val editTitle: Int) {
    NIGHT(Dashboard.SLEEP, R.string.sleep_tab_night, R.string.sleep_edit_night),
    PATTERNS(Dashboard.SLEEP_PATTERNS, R.string.sleep_tab_patterns, R.string.sleep_edit_patterns),
}

/**
 * The cards of Sleep, in the default order of each [view]; Night follows the night picked above it, Patterns
 * the nights up to the latest. [jump] is the word of a card's jump chip on Patterns; [todayId] the Today card a
 * card can be pinned as (its tiles have their own).
 */
enum class SleepCardKind(
    val id: String,
    val view: SleepView,
    @param:StringRes val title: Int,
    @param:StringRes val description: Int,
    @param:StringRes val jump: Int? = null,
) {
    TILES("tiles", SleepView.NIGHT, R.string.card_tiles_title, R.string.card_tiles),
    ALERTNESS("alertness", SleepView.NIGHT, UiR.string.alertness_title, R.string.sleep_card_alertness),
    RESTED("rested", SleepView.NIGHT, R.string.card_rested_question_title, R.string.card_rested_question),
    NIGHT("stages", SleepView.NIGHT, R.string.card_night_title, R.string.card_night),
    SCORE("score", SleepView.NIGHT, R.string.sleep_score_title, R.string.card_score),
    QUALITY("quality", SleepView.NIGHT, R.string.sleep_quality_title, R.string.card_quality),
    HEART_RATE("heart", SleepView.NIGHT, R.string.sleep_heart_rate_title, R.string.card_heart_rate),
    RECOVERY("body", SleepView.NIGHT, R.string.sleep_body_title, R.string.sleep_card_recovery),
    HABITS("habits", SleepView.PATTERNS, R.string.card_habits_title, R.string.card_habits),
    HEALTH("health", SleepView.PATTERNS, R.string.sleep_health_title, R.string.card_health, R.string.sleep_jump_health),
    KEY_METRICS("metrics", SleepView.PATTERNS, R.string.sleep_metrics_title, R.string.card_key_metrics, R.string.sleep_jump_metrics),
    TIMING("timing", SleepView.PATTERNS, R.string.timing_title, R.string.card_timing, R.string.sleep_jump_timing),
    FEELING("feeling", SleepView.PATTERNS, R.string.feeling_title, R.string.card_feeling, R.string.sleep_jump_feeling),
    FACTORS("factors", SleepView.PATTERNS, R.string.factors_title, R.string.card_factors, R.string.sleep_jump_factors),
    ;

    companion object {
        fun catalog(view: SleepView): List<CardSpec> = entries.filter { it.view == view }.map { CardSpec(it.id) }

        fun of(id: String, view: SleepView): SleepCardKind? = entries.firstOrNull { it.id == id && it.view == view }
    }
}
