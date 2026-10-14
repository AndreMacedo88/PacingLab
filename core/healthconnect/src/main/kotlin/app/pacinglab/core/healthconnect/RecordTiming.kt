package app.pacinglab.core.healthconnect

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
import java.time.Instant

/**
 * Where a record sits in time.
 *
 * The client library keeps its `InstantaneousRecord`/`IntervalRecord`/`SeriesRecord` interfaces
 * internal, so the timing of each supported type is spelled out here.
 *
 * @property end null for instantaneous records.
 * @property samples number of samples for series records, null otherwise.
 */
internal data class RecordTiming(val start: Instant, val end: Instant?, val samples: Int?)

internal fun timingOf(record: Record): RecordTiming = when (record) {
    is ExerciseSessionRecord -> RecordTiming(record.startTime, record.endTime, null)
    is HeartRateRecord -> RecordTiming(record.startTime, record.endTime, record.samples.size)
    is SpeedRecord -> RecordTiming(record.startTime, record.endTime, record.samples.size)
    is StepsCadenceRecord -> RecordTiming(record.startTime, record.endTime, record.samples.size)
    is PowerRecord -> RecordTiming(record.startTime, record.endTime, record.samples.size)
    is DistanceRecord -> RecordTiming(record.startTime, record.endTime, null)
    is ElevationGainedRecord -> RecordTiming(record.startTime, record.endTime, null)
    is StepsRecord -> RecordTiming(record.startTime, record.endTime, null)
    is ActiveCaloriesBurnedRecord -> RecordTiming(record.startTime, record.endTime, null)
    is TotalCaloriesBurnedRecord -> RecordTiming(record.startTime, record.endTime, null)
    is FloorsClimbedRecord -> RecordTiming(record.startTime, record.endTime, null)
    is SleepSessionRecord -> RecordTiming(record.startTime, record.endTime, null)
    is SkinTemperatureRecord -> RecordTiming(record.startTime, record.endTime, record.deltas.size)
    is RestingHeartRateRecord -> RecordTiming(record.time, null, null)
    is HeartRateVariabilityRmssdRecord -> RecordTiming(record.time, null, null)
    is Vo2MaxRecord -> RecordTiming(record.time, null, null)
    is RespiratoryRateRecord -> RecordTiming(record.time, null, null)
    is OxygenSaturationRecord -> RecordTiming(record.time, null, null)
    is WeightRecord -> RecordTiming(record.time, null, null)
    is HeightRecord -> RecordTiming(record.time, null, null)
    else -> throw IllegalArgumentException("No timing defined for ${record::class.simpleName}")
}
