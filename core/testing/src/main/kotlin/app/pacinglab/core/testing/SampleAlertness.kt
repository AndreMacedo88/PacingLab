package app.pacinglab.core.testing

import app.pacinglab.core.analytics.alertness.Alertness
import app.pacinglab.core.analytics.alertness.AlertnessHistory
import app.pacinglab.core.analytics.recovery.Night
import app.pacinglab.core.analytics.recovery.SleepHistory
import app.pacinglab.core.model.ReactionTest
import app.pacinglab.core.model.SleepSession
import app.pacinglab.core.model.SleepStage
import app.pacinglab.core.model.SleepStageType
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin

/** Made-up nights and reaction tests run through the alertness model, for screenshots. */
object SampleAlertness {
    /**
     * Five weeks of nights from about 23:15 to 07:15 up to [today], the night before the day [shortNight]
     * cut to five hours, and [tests] reaction tests on recent days at varied times, the last on [today]
     * at 09:30 when [testedToday]; seen at [now] on [today].
     */
    fun history(
        today: LocalDate,
        zone: ZoneId = ZoneOffset.UTC,
        now: LocalTime = LocalTime.of(14, 30),
        tests: Int = 5,
        shortNight: LocalDate? = null,
        testedToday: Boolean = false,
    ): AlertnessHistory {
        val nights = (35 downTo 0).map { back ->
            val date = today.minusDays(back.toLong())
            val bed = if (date == shortNight) LocalTime.of(2, 10) else LocalTime.of(23, 10).plusMinutes((back * 7L) % 25)
            val start = (if (bed.hour > 12) date.minusDays(1) else date).atTime(bed).atZone(zone).toInstant()
            val end = date.atTime(LocalTime.of(7, 5).plusMinutes((back * 11L) % 30)).atZone(zone).toInstant()
            Night(
                date = date,
                start = start,
                end = end,
                asleepMillis = Duration.between(start, end).toMillis(),
                stageMillis = emptyMap(),
                hrvMs = null,
                restingHr = null,
                respiratoryRate = null,
                zone = zone,
            ) to SleepSession("sample-$date", start, end, null, "sample", listOf(SleepStage(start, end, SleepStageType.LIGHT)))
        }
        val history = SleepHistory.of(nights.map { it.first })
        val sleeps = nights.map { it.second }
        val instant = today.atTime(now).atZone(zone).toInstant()
        val model = Alertness.of(sleeps, history, null, emptyList(), zone, instant)
        val times = listOf(LocalTime.of(9, 30), LocalTime.of(15, 0), LocalTime.of(11, 15), LocalTime.of(19, 40), LocalTime.of(8, 20))
        val dates = (if (testedToday) 0 else 1) until (if (testedToday) 0 else 1) + tests
        val made = dates.mapIndexed { i, back ->
            val date = today.minusDays(back * 2L)
            val start = date.atTime(times[i % times.size]).atZone(zone).toInstant()
            val day = model.days[date]
            val a =
                day?.points?.minBy { kotlin.math.abs(Duration.between(it.time, start.plusSeconds(90)).toMillis()) }?.alertness
                    ?: model.reference
            val speed = 3.3 * exp(0.045 * (a - model.reference) + 0.03 * sin(i * 2.3))
            val millis = (1000 / speed).roundToInt()
            ReactionTest(start, null, List(58) { millis + ((it * 37) % 9) - 4 }, falseStarts = i % 2)
        }
        return Alertness.of(sleeps, history, null, made, zone, instant)
    }
}
