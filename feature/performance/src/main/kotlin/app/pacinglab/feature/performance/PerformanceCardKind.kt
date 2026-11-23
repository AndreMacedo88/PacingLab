package app.pacinglab.feature.performance

import androidx.annotation.StringRes
import app.pacinglab.core.data.repository.CardSpec

/** The groups of Performance, each under its heading and reached by its chip. */
enum class PerformanceSection(@param:StringRes val title: Int) {
    CAPACITY(R.string.performance_section_capacity),
    RACING(R.string.performance_section_racing),
    RECORDS(R.string.performance_section_records),
    ECONOMY(R.string.performance_section_economy),
}

/**
 * The cards of the performance screen, in the default order. [todayId] is the Today card each can be pinned as.
 *
 * @property hero the card that answers "what can I do": VO2max.
 */
enum class PerformanceCardKind(
    val id: String,
    @param:StringRes val title: Int,
    @param:StringRes val description: Int,
    val section: PerformanceSection,
    val todayId: String? = null,
    val hero: Boolean = false,
) {
    VO2MAX("vo2max", R.string.vo2_title, R.string.card_vo2max, PerformanceSection.CAPACITY, todayId = "vo2max", hero = true),
    CRITICAL_SPEED("critical-speed", R.string.cs_title, R.string.card_critical_speed, PerformanceSection.CAPACITY),
    ZONES("zones", R.string.performance_zones_title, R.string.card_performance_zones, PerformanceSection.CAPACITY),
    RACES("races", R.string.race_title, R.string.card_races, PerformanceSection.RACING, todayId = "race_5k"),
    PACES("paces", R.string.paces_title, R.string.card_paces, PerformanceSection.RACING),
    EFFORTS("efforts", R.string.efforts_title, R.string.card_efforts, PerformanceSection.RECORDS),
    EFFICIENCY("efficiency", R.string.efficiency_title, R.string.card_efficiency, PerformanceSection.ECONOMY, todayId = "efficiency"),
    DECOUPLING("decoupling", R.string.decoupling_title, R.string.card_decoupling, PerformanceSection.ECONOMY),
    RECOVERY("hrr", R.string.hrr_title, R.string.card_hrr, PerformanceSection.ECONOMY),
    ;

    companion object {
        val catalog: List<CardSpec> = entries.map { CardSpec(it.id) }

        fun of(id: String): PerformanceCardKind? = entries.firstOrNull { it.id == id }
    }
}
