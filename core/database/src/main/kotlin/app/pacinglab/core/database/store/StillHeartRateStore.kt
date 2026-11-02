package app.pacinglab.core.database.store

import app.pacinglab.core.database.PacingLabDatabase
import app.pacinglab.core.database.entity.StillHeartRateEntity
import app.pacinglab.core.model.StillHeartRate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** Persists each day's still heart rate by hour, the basis of daytime strain. */
@Singleton
class StillHeartRateStore @Inject constructor(database: PacingLabDatabase, private val clock: Clock) {
    private val dao = database.stillHeartRateDao()

    /** Every stored day, oldest first. */
    fun observeAll(): Flow<List<StillHeartRate>> = dao.observeAll().map { rows -> rows.map { it.toModel() } }

    /** Days already summarised. */
    suspend fun days(): Set<LocalDate> = dao.days().mapTo(HashSet()) { LocalDate.ofEpochDay(it) }

    suspend fun save(days: List<StillHeartRate>) {
        if (days.isEmpty()) return
        val now = clock.millis()
        dao.upsert(
            days.map { day ->
                StillHeartRateEntity(
                    epochDay = day.date.toEpochDay(),
                    minutes = day.minutes.joinToString(","),
                    // Two decimals of a bpm sum are far below the watch's resolution.
                    sums = day.sums.joinToString(",") { String.format(Locale.ROOT, "%.2f", it) },
                    computedMs = now,
                )
            },
        )
    }

    private fun StillHeartRateEntity.toModel() = StillHeartRate(
        date = LocalDate.ofEpochDay(epochDay),
        minutes = minutes.split(",").map { it.toInt() }.toIntArray(),
        sums = sums.split(",").map { it.toDouble() }.toDoubleArray(),
    )
}
