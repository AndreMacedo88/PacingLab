package app.pacinglab.feature.sleep

import androidx.annotation.StringRes
import app.pacinglab.core.data.repository.CardSpec

/** The cards of the sleep screen, in the default order; the night selector above them stays put. */
enum class SleepCardKind(val id: String, @param:StringRes val title: Int, @param:StringRes val description: Int) {
    TILES("tiles", R.string.card_tiles_title, R.string.card_tiles),
    NIGHT("stages", R.string.card_night_title, R.string.card_night),
    SCORE("score", R.string.sleep_score_title, R.string.card_score),
    HEALTH("health", R.string.sleep_health_title, R.string.card_health),
    QUALITY("quality", R.string.sleep_quality_title, R.string.card_quality),
    HEART_RATE("heart", R.string.sleep_heart_rate_title, R.string.card_heart_rate),
    RECOVERY("body", R.string.sleep_body_title, R.string.card_recovery),
    KEY_METRICS("metrics", R.string.sleep_metrics_title, R.string.card_key_metrics),
    FEELING("feeling", R.string.feeling_title, R.string.card_feeling),
    ;

    companion object {
        val catalog: List<CardSpec> = entries.map { CardSpec(it.id) }

        fun of(id: String): SleepCardKind? = entries.firstOrNull { it.id == id }
    }
}
