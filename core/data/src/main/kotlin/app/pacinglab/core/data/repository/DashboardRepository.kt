package app.pacinglab.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** The screens whose cards the athlete can arrange. */
enum class Dashboard(val key: String) {
    TODAY("today"),
    PERFORMANCE("performance"),
    TRENDS("trends"),
    SLEEP("sleep"),
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

    /** Back to the default arrangement. */
    suspend fun reset(dashboard: Dashboard) {
        dataStore.edit {
            it.remove(orderKey(dashboard))
            it.remove(hiddenKey(dashboard))
        }
    }

    private fun orderKey(dashboard: Dashboard) = stringPreferencesKey("dashboard_${dashboard.key}_order")

    private fun hiddenKey(dashboard: Dashboard) = stringSetPreferencesKey("dashboard_${dashboard.key}_hidden")

    private companion object {
        const val SEPARATOR = ","
    }
}
