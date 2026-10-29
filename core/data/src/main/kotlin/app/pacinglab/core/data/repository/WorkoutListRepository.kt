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

/**
 * How the athlete left the workout list: its order and its filters, by the names the list gives them.
 * Names the list no longer knows are ignored there.
 */
data class WorkoutListView(val sort: String? = null, val filters: Set<String> = emptySet())

/** Keeps the workout list's order and filters between visits; a backup carries them like any setting. */
@Singleton
class WorkoutListRepository @Inject constructor(private val dataStore: DataStore<Preferences>) {
    val view: Flow<WorkoutListView> = dataStore.data
        .map { WorkoutListView(it[SORT], it[FILTERS].orEmpty()) }
        .distinctUntilChanged()

    suspend fun setSort(sort: String) {
        dataStore.edit { it[SORT] = sort }
    }

    suspend fun setFilters(filters: Set<String>) {
        dataStore.edit {
            if (filters.isEmpty()) it.remove(FILTERS) else it[FILTERS] = filters
        }
    }

    private companion object {
        val SORT = stringPreferencesKey("workouts_sort")
        val FILTERS = stringSetPreferencesKey("workouts_filters")
    }
}
