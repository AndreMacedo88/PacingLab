package app.pacinglab.core.data.repository

import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** The screens whose cards the athlete can arrange, and the home-screen widget's measures. */
enum class Dashboard(val key: String) {
    TODAY("today"),

    /** Training > Load. */
    LOAD("load"),
    PERFORMANCE("performance"),
    BODY("body"),

    /** Sleep > Night; its key is the one the whole sleep screen had before it was split. */
    SLEEP("sleep"),

    /** Sleep > Patterns. */
    SLEEP_PATTERNS("sleep_patterns"),
    WIDGET("widget"),
}

/** A card a dashboard can show, in its default place in the catalogue. */
data class CardSpec(val id: String, val hiddenByDefault: Boolean = false)

/** A card in the athlete's arrangement. */
data class PlacedCard(val id: String, val visible: Boolean)

/**
 * The athlete's arrangement of one dashboard: every card they have placed, in order, and the ones they
 * hid. Null fields mean the dashboard was never edited.
 */
data class DashboardLayout(val order: List<String>? = null, val hidden: Set<String>? = null) {
    /**
     * The cards of [catalog] in the athlete's order. Cards the arrangement does not know yet (added in an
     * update) go right after the card they follow in the catalogue, with their default visibility, so
     * that an update never reshuffles a dashboard.
     */
    fun arrange(catalog: List<CardSpec>): List<PlacedCard> {
        val known = catalog.associateBy { it.id }
        val placed = order.orEmpty().filter { it in known }.distinct().toMutableList()
        catalog.forEachIndexed { index, card ->
            if (card.id in placed) return@forEachIndexed
            val before = catalog.subList(0, index).lastOrNull { it.id in placed }?.id
            placed.add(before?.let { placed.indexOf(it) + 1 } ?: 0, card.id)
        }
        return placed.map { id ->
            val visible = if (order?.contains(id) == true) id !in hidden.orEmpty() else !known.getValue(id).hiddenByDefault
            PlacedCard(id, visible)
        }
    }
}

/** Card arrangements of the dashboards, kept in DataStore beside the other preferences. */
@Singleton
class DashboardRepository @Inject constructor(private val dataStore: DataStore<Preferences>) {
    fun layout(dashboard: Dashboard): Flow<DashboardLayout> = dataStore.data.map { values ->
        DashboardLayout(
            order = values[orderKey(dashboard)]?.split(SEPARATOR)?.filter { it.isNotEmpty() },
            hidden = values[hiddenKey(dashboard)],
        )
    }.distinctUntilChanged()

    /** Saves the whole arrangement: every card in order and the ones hidden. */
    suspend fun save(dashboard: Dashboard, cards: List<PlacedCard>) {
        dataStore.edit {
            it[orderKey(dashboard)] = cards.joinToString(SEPARATOR) { card -> card.id }
            it[hiddenKey(dashboard)] = cards.filterNot { card -> card.visible }.mapTo(HashSet()) { card -> card.id }
        }
    }

    /**
     * Shows or hides one card of [catalog], as pinning a card to Today from another screen does; every other card
     * keeps its place and visibility.
     */
    suspend fun setVisible(dashboard: Dashboard, catalog: List<CardSpec>, id: String, visible: Boolean) {
        dataStore.edit {
            val layout = DashboardLayout(
                order = it[orderKey(dashboard)]?.split(SEPARATOR)?.filter(String::isNotEmpty),
                hidden = it[hiddenKey(dashboard)],
            )
            val cards = layout.arrange(catalog).map { card -> if (card.id == id) card.copy(visible = visible) else card }
            it[orderKey(dashboard)] = cards.joinToString(SEPARATOR) { card -> card.id }
            it[hiddenKey(dashboard)] = cards.filterNot { card -> card.visible }.mapTo(HashSet()) { card -> card.id }
        }
    }

    /** Back to the default arrangement. */
    suspend fun reset(dashboard: Dashboard) {
        dataStore.edit {
            it.remove(orderKey(dashboard))
            it.remove(hiddenKey(dashboard))
        }
    }

    companion object {
        private const val SEPARATOR = ","

        internal fun orderKey(dashboard: Dashboard) = orderKey(dashboard.key)

        internal fun hiddenKey(dashboard: Dashboard) = hiddenKey(dashboard.key)

        internal fun orderKey(key: String) = stringPreferencesKey("dashboard_${key}_order")

        internal fun hiddenKey(key: String) = stringSetPreferencesKey("dashboard_${key}_hidden")
    }
}

/**
 * Carries arrangements over the reorganisation of 2026-11 (version 2), in which Trends was split into
 * Training > Load and Body and Sleep into Night and Patterns: Load and Body take Trends' order and hidden cards,
 * Patterns takes Sleep's, and Today goes back to its new default, which leaves to Body and Sleep the tiles they
 * now hold. Run once by the data store on upgrade, and on a restored backup made before it.
 */
object DashboardMigration {
    /** The version of the arrangements in a store; absent means 1. */
    val VERSION = intPreferencesKey("dashboards_version")

    const val CURRENT = 2

    private const val TRENDS = "trends"

    /** The cards each new dashboard took from Trends. */
    private val FROM_TRENDS = mapOf(
        Dashboard.LOAD to setOf("status", "form", "weeks", "running", "focus"),
        Dashboard.BODY to setOf("recovery", "strain"),
    )

    val dataMigration: DataMigration<Preferences> = object : DataMigration<Preferences> {
        override suspend fun shouldMigrate(currentData: Preferences): Boolean = (currentData[VERSION] ?: 1) < CURRENT

        override suspend fun migrate(currentData: Preferences): Preferences =
            currentData.toMutablePreferences().also { upgrade(it) }.toPreferences()

        override suspend fun cleanUp() = Unit
    }

    /** Moves version 1 arrangements in [prefs] to version 2; does nothing to a store already there. */
    fun upgrade(prefs: MutablePreferences) {
        if ((prefs[VERSION] ?: 1) >= CURRENT) return
        val trendsOrder = prefs[DashboardRepository.orderKey(TRENDS)]?.split(",")?.filter(String::isNotEmpty)
        val trendsHidden = prefs[DashboardRepository.hiddenKey(TRENDS)].orEmpty()
        if (trendsOrder != null) {
            for ((dashboard, ids) in FROM_TRENDS) {
                prefs[DashboardRepository.orderKey(dashboard)] = trendsOrder.filter { it in ids }.joinToString(",")
                prefs[DashboardRepository.hiddenKey(dashboard)] = trendsHidden.intersect(ids)
            }
        }
        prefs.remove(DashboardRepository.orderKey(TRENDS))
        prefs.remove(DashboardRepository.hiddenKey(TRENDS))
        prefs[DashboardRepository.orderKey(Dashboard.SLEEP)]?.let { prefs[DashboardRepository.orderKey(Dashboard.SLEEP_PATTERNS)] = it }
        prefs[DashboardRepository.hiddenKey(Dashboard.SLEEP)]?.let { prefs[DashboardRepository.hiddenKey(Dashboard.SLEEP_PATTERNS)] = it }
        prefs.remove(DashboardRepository.orderKey(Dashboard.TODAY))
        prefs.remove(DashboardRepository.hiddenKey(Dashboard.TODAY))
        prefs[VERSION] = CURRENT
    }
}
