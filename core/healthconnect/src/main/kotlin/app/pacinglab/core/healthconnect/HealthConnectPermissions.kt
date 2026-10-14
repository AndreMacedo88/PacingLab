package app.pacinglab.core.healthconnect

import android.os.Build
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ElevationGainedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.FloorsClimbedRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.HeightRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.PowerRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SkinTemperatureRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.SpeedRecord
import androidx.health.connect.client.records.StepsCadenceRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.Vo2MaxRecord
import androidx.health.connect.client.records.WeightRecord
import kotlin.reflect.KClass

/**
 * Every Health Connect permission PacingLab asks for. The app only ever reads.
 *
 * Each entry here needs a matching `<uses-permission>` in the app manifest, otherwise
 * Health Connect silently drops it from the request.
 */
object HealthConnectPermissions {
    /** Record types the app reads, in the order they are shown in diagnostics. */
    val recordTypes: List<KClass<out Record>> = listOf(
        ExerciseSessionRecord::class,
        HeartRateRecord::class,
        RestingHeartRateRecord::class,
        HeartRateVariabilityRmssdRecord::class,
        SpeedRecord::class,
        DistanceRecord::class,
        ElevationGainedRecord::class,
        StepsRecord::class,
        StepsCadenceRecord::class,
        PowerRecord::class,
        Vo2MaxRecord::class,
        SleepSessionRecord::class,
        RespiratoryRateRecord::class,
        SkinTemperatureRecord::class,
        OxygenSaturationRecord::class,
        ActiveCaloriesBurnedRecord::class,
        TotalCaloriesBurnedRecord::class,
        FloorsClimbedRecord::class,
        WeightRecord::class,
        HeightRecord::class,
    )

    /**
     * Lets the app read routes of sessions recorded by other apps without asking per session.
     * Platform permission from Android 15; the 1.1 client library has no constant for it yet.
     */
    const val READ_EXERCISE_ROUTES = "android.permission.health.READ_EXERCISE_ROUTES"

    val recordReadPermissions: Set<String> = recordTypes.mapTo(linkedSetOf()) { HealthPermission.getReadPermission(it) }

    /** Everything requested in one go from the permission screen. */
    val all: Set<String> = buildSet {
        addAll(recordReadPermissions)
        add(HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY)
        add(HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) add(READ_EXERCISE_ROUTES)
    }
}
