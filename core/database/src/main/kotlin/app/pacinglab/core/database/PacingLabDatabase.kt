package app.pacinglab.core.database

import androidx.room3.AutoMigration
import androidx.room3.Database
import androidx.room3.RoomDatabase
import app.pacinglab.core.database.dao.EffortDao
import app.pacinglab.core.database.dao.MeasurementDao
import app.pacinglab.core.database.dao.PhysiologyDao
import app.pacinglab.core.database.dao.SleepDao
import app.pacinglab.core.database.dao.SyncStateDao
import app.pacinglab.core.database.dao.WellnessDao
import app.pacinglab.core.database.dao.WorkoutDao
import app.pacinglab.core.database.entity.EffortEntity
import app.pacinglab.core.database.entity.MeasurementEntity
import app.pacinglab.core.database.entity.PhysiologyEntity
import app.pacinglab.core.database.entity.SleepSessionEntity
import app.pacinglab.core.database.entity.SyncStateEntity
import app.pacinglab.core.database.entity.WellnessEntity
import app.pacinglab.core.database.entity.WorkoutEntity
import app.pacinglab.core.database.entity.WorkoutStreamEntity

@Database(
    entities = [
        WorkoutEntity::class,
        WorkoutStreamEntity::class,
        MeasurementEntity::class,
        SleepSessionEntity::class,
        SyncStateEntity::class,
        PhysiologyEntity::class,
        WellnessEntity::class,
        EffortEntity::class,
    ],
    version = 8,
    exportSchema = true,
    autoMigrations = [
        // 2: workouts.route_pending
        AutoMigration(from = 1, to = 2),
        // 3: workouts.recording_method
        AutoMigration(from = 2, to = 3),
        // 4: physiology
        AutoMigration(from = 3, to = 4),
        // 5: physiology.max_hr may be empty (estimated from workouts)
        AutoMigration(from = 4, to = 5),
        // 6: sleep_sessions.heart_rate
        AutoMigration(from = 5, to = 6),
        // 7: wellness (morning rating)
        AutoMigration(from = 6, to = 7),
        // 8: efforts (session RPE)
        AutoMigration(from = 7, to = 8),
    ],
)
abstract class PacingLabDatabase : RoomDatabase() {
    abstract fun workoutDao(): WorkoutDao

    abstract fun measurementDao(): MeasurementDao

    abstract fun sleepDao(): SleepDao

    abstract fun syncStateDao(): SyncStateDao

    abstract fun physiologyDao(): PhysiologyDao

    abstract fun wellnessDao(): WellnessDao

    abstract fun effortDao(): EffortDao

    companion object {
        const val FILE_NAME = "pacinglab.db"
    }
}
