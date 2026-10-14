package app.pacinglab.core.database.store

import app.pacinglab.core.database.PacingLabDatabase
import app.pacinglab.core.database.entity.SyncStateEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** String key-value store for sync bookkeeping. */
@Singleton
class SyncStateStore @Inject constructor(database: PacingLabDatabase) {
    private val dao = database.syncStateDao()

    suspend fun get(key: String): String? = dao.get(key)

    suspend fun put(key: String, value: String) = dao.put(SyncStateEntity(key, value))

    suspend fun remove(key: String) = dao.remove(key)

    suspend fun clear() = dao.clear()

    fun observe(): Flow<Map<String, String>> = dao.observeAll().map { rows -> rows.associate { it.key to it.value } }
}
