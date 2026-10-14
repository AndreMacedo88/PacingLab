package app.pacinglab.core.database

import androidx.room3.AutoMigration
import androidx.room3.Database
import androidx.room3.RoomDatabase
import app.pacinglab.core.database.dao.MeasurementDao
import app.pacinglab.core.database.dao.SleepDao
import app.pacinglab.core.database.dao.SyncStateDao
import app.pacinglab.core.database.dao.WorkoutDao
import app.pacinglab.core.database.entity.MeasurementEntity
import app.pacinglab.core.database.entity.SleepSessionEntity
import app.pacinglab.core.database.entity.SyncStateEntity
import app.pacinglab.core.database.entity.WorkoutEntity
import app.pacinglab.core.database.entity.WorkoutStreamEntity

@Database(
    entities = [
        WorkoutEntity::class,
        WorkoutStreamEntity::class,
        MeasurementEntity::class,
        SleepSessionEntity::class,
        SyncStateEntity::class,
    ],
    version = 2,
    exportSchema = true,
    autoMigrations = [
        // 2: workouts.route_pending
        AutoMigration(from = 1, to = 2),
    ],
)
abstract class PacingLabDatabase : RoomDatabase() {
    abstract fun workoutDao(): WorkoutDao

    abstract fun measurementDao(): MeasurementDao

    abstract fun sleepDao(): SleepDao

    abstract fun syncStateDao(): SyncStateDao

    companion object {
        const val FILE_NAME = "pacinglab.db"
    }
}
