package app.pacinglab.core.database.store

import app.pacinglab.core.database.PacingLabDatabase
import app.pacinglab.core.database.entity.ReactionTestEntity
import app.pacinglab.core.model.ReactionTest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton

/** Persists the athlete's reaction tests, one per start time. */
@Singleton
class ReactionTestStore @Inject constructor(database: PacingLabDatabase) {
    private val dao = database.reactionTestDao()

    /** Every test, oldest first. */
    fun observe(): Flow<List<ReactionTest>> = dao.observeAll().map { rows -> rows.map { it.toModel() } }

    /** Saves [tests], replacing any of the same start. */
    suspend fun save(tests: List<ReactionTest>) {
        if (tests.isEmpty()) return
        dao.upsert(
            tests.map { test ->
                ReactionTestEntity(
                    test.start.toEpochMilli(),
                    test.zoneOffset?.totalSeconds,
                    test.reactionMillis.joinToString(","),
                    test.falseStarts,
                )
            },
        )
    }

    suspend fun delete(start: Instant) = dao.delete(start.toEpochMilli())

    private fun ReactionTestEntity.toModel() = ReactionTest(
        start = Instant.ofEpochMilli(startMs),
        zoneOffset = zoneOffsetSeconds?.let(ZoneOffset::ofTotalSeconds),
        reactionMillis = reactions.split(',').filter { it.isNotEmpty() }.map { it.toInt() },
        falseStarts = falseStarts,
    )
}
