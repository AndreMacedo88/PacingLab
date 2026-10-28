package app.pacinglab.feature.performance

import androidx.annotation.StringRes
import app.pacinglab.core.data.repository.CardSpec

/** The cards of the performance screen, in the default order. */
enum class PerformanceCardKind(val id: String, @param:StringRes val title: Int, @param:StringRes val description: Int) {
    VO2MAX("vo2max", R.string.vo2_title, R.string.card_vo2max),
    RACES("races", R.string.race_title, R.string.card_races),
    CRITICAL_SPEED("critical-speed", R.string.cs_title, R.string.card_critical_speed),
    EFFORTS("efforts", R.string.efforts_title, R.string.card_efforts),
    EFFICIENCY("efficiency", R.string.efficiency_title, R.string.card_efficiency),
    DECOUPLING("decoupling", R.string.decoupling_title, R.string.card_decoupling),
    ;

    companion object {
        val catalog: List<CardSpec> = entries.map { CardSpec(it.id) }

        fun of(id: String): PerformanceCardKind? = entries.firstOrNull { it.id == id }
    }
}
