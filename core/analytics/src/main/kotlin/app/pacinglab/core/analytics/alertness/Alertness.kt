package app.pacinglab.core.analytics.alertness

import app.pacinglab.core.analytics.recovery.Night
import app.pacinglab.core.analytics.recovery.SleepHistory
import app.pacinglab.core.model.ReactionTest
import app.pacinglab.core.model.SleepSession
import app.pacinglab.core.model.SleepStageType
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.ln

/** How a waking day compares with a rested one. */
enum class AlertnessLevel { ALERT, REDUCED, LOW }

/**
 * One point of a day: the model's alertness (1 to 21) and the performance it predicts, as a percentage of a
 * rested day's average.
 */
data class AlertnessPoint(val time: Instant, val alertness: Double, val performance: Double)

/**
 * A waking day from the final awakening of its night.
 *
 * @property score the predicted performance averaged over the day, where 100 is the average of a rested day.
 * @property assumed the night was not recorded and the athlete's usual night stands in for it.
 * @property best the [Alertness.BEST_HOURS] of the day with the highest predicted performance, starting on
 *   a half hour.
 */
data class AlertnessDay(
    val date: LocalDate,
    val wake: Instant,
    val end: Instant,
    val points: List<AlertnessPoint>,
    val score: Double,
    val assumed: Boolean,
    val best: Pair<Instant, Instant>?,
) {
    val level: AlertnessLevel
        get() = when {
            score >= Alertness.ALERT_FROM -> AlertnessLevel.ALERT
            score >= Alertness.REDUCED_FROM -> AlertnessLevel.REDUCED
            else -> AlertnessLevel.LOW
        }

    /** Predicted performance at the point nearest [time]; null outside the day or while asleep. */
    fun at(time: Instant): Double? {
        if (time < wake || time > end) return null
        return points.minByOrNull { kotlin.math.abs(Duration.between(it.time, time).toMillis()) }?.performance
    }
}

/**
 * A reaction test with what the model expected of it.
 *
 * @property alertness the model's alertness in the middle of the test; null when the model had the athlete asleep.
 * @property expected the predicted performance then, in percent of rested.
 * @property measured the test's speed as a percentage of the athlete's rested speed; null without calibration.
 */
data class ScoredTest(
    val test: ReactionTest,
    val result: ReactionResult,
    val alertness: Double?,
    val expected: Double?,
    val measured: Double?,
)

/**
 * Alertness through the day from the three-process model ([ThreeProcessModel]) run on the athlete's
 * sleep, as predicted performance: 100 is the average of a rested waking day, one of their full sleep need
 * at their usual times after a week of such nights. The athlete's reaction tests set how strongly
 * alertness moves their performance ([AlertnessCalibration]); until there are some, the population value does.
 *
 * @property days the days of the last [DAYS] with an estimate, by date.
 * @property tests every test, oldest first; tests cut short are left out.
 * @property reference the model's mean alertness over a rested day, R.
 * @property testDue whether the app should suggest a test today: every other day while learning, then
 *   weekly, and monthly once settled ([AlertnessCalibration.SETTLED_TESTS]).
 * @property now when this was worked out.
 */
data class AlertnessHistory(
    val days: Map<LocalDate, AlertnessDay>,
    val tests: List<ScoredTest>,
    val calibration: AlertnessCalibration,
    val reference: Double,
    val testDue: Boolean,
    val now: Instant,
    val today: LocalDate,
) {
    fun testsOn(date: LocalDate, zone: ZoneId): List<ScoredTest> = tests.filter { it.test.start.atZone(zone).toLocalDate() == date }

    companion object {
        val EMPTY = AlertnessHistory(emptyMap(), emptyList(), AlertnessCalibration.POPULATION, 0.0, false, Instant.EPOCH, LocalDate.MIN)
    }
}

object Alertness {
    /** Days shown, and days run before them so that sleep pressure has settled. */
    const val DAYS = 30
    private const val SETTLING_DAYS = 5

    const val ALERT_FROM = 97.0
    const val REDUCED_FROM = 92.0

    /** The length of the best stretch of a day. */
    const val BEST_HOURS = 3

    /** A day needs one of its own and the two nights before it recorded; other nights may be assumed. */
    private const val RECORDED_WITHIN = 3

    /** Nights that set the usual wake time and mid-sleep. */
    private const val USUAL_NIGHTS = 28
    private const val MIN_USUAL_NIGHTS = 3
    private val DEFAULT_WAKE: LocalTime = LocalTime.of(7, 0)
    private const val DEFAULT_MIDSLEEP_MINUTES = 3 * 60

    /**
     * The model's default circadian phase describes people who sleep from about 23:00 to 07:00 on workdays,
     * an intermediate chronotype with mid-sleep on free days near 04:00, the middle of Sleep timing's band.
     * The paper moves the rhythm one or two hours for moderate and pronounced morning and evening types;
     * here the shift is the distance of the athlete's chronotype from 04:00, at most two hours.
     */
    private const val INTERMEDIATE_CHRONOTYPE_MINUTES = 4 * 60
    private const val MAX_SHIFT_HOURS = 2.0

    private const val MIN_NEED_HOURS = 6.0
    private const val MAX_NEED_HOURS = 10.0
    private const val STEP_MILLIS = 5 * 60_000L
    private const val INITIAL_PRESSURE = 12.0
    private const val REFERENCE_NIGHTS = 7
    private const val HOUR_MILLIS = 3_600_000L
    private val ASLEEP = setOf(SleepStageType.LIGHT, SleepStageType.DEEP, SleepStageType.REM, SleepStageType.ASLEEP_UNSPECIFIED)

    /** The circadian shift for a chronotype (MSFsc, minutes after midnight), in hours. */
    fun phaseShift(chronotypeMinutes: Int?): Double {
        val clock = chronotypeMinutes ?: return 0.0
        val minutes = if (clock >= 12 * 60) clock - 24 * 60 else clock
        return ((minutes - INTERMEDIATE_CHRONOTYPE_MINUTES) / 60.0).coerceIn(-MAX_SHIFT_HOURS, MAX_SHIFT_HOURS)
    }

    /**
     * @param sleeps every recorded sleep of the last [DAYS] and more, naps included; awake stages inside a
     *   sleep count as awake.
     * @param history the main nights, for which mornings are recorded, the usual times and the sleep need.
     * @param chronotype MSFsc from Sleep timing, minutes after midnight; null keeps the default phase.
     */
    fun of(
        sleeps: List<SleepSession>,
        history: SleepHistory,
        chronotype: Int?,
        tests: List<ReactionTest>,
        zone: ZoneId,
        now: Instant,
    ): AlertnessHistory {
        val today = now.atZone(zone).toLocalDate()
        val nightsByDate = history.nights.associateBy { it.date }
        val needHours = (
            (
                history.needs.entries.maxByOrNull { it.key }?.value
                    ?: (ThreeProcessModel.DEFAULT_NEED_HOURS * HOUR_MILLIS).toLong()
                ) /
                HOUR_MILLIS.toDouble()
            ).coerceIn(MIN_NEED_HOURS, MAX_NEED_HOURS)
        val model = ThreeProcessModel(needHours, phaseShift(chronotype))
        val dayLength = Duration.ofMinutes(((24 - needHours) * 60).toLong())
        val recent = history.nights.filter { it.date < today && it.date >= today.minusDays(USUAL_NIGHTS.toLong()) }
        val usualWake = usualWake(recent)
        val reference = reference(model, usualMidsleep(recent), needHours, dayLength, zone.rules.getOffset(now))

        val first = today.minusDays((DAYS + SETTLING_DAYS).toLong())
        val recorded = sleeps.flatMap(::asleepSpans).filter { it.last >= first.atStartOfDay(zone).toInstant().toEpochMilli() }
        val assumed = mutableSetOf<LocalDate>()
        val spans = recorded.toMutableList()
        var date = first
        while (!date.isAfter(today)) {
            if (date !in nightsByDate) {
                val wake = date.atTime(usualWake).atZone(zone).toInstant().toEpochMilli()
                val span = (wake - (needHours * HOUR_MILLIS).toLong()) until wake
                if (recorded.none { it.first <= span.last && span.first <= it.last }) {
                    spans += span
                    assumed += date
                }
            }
            date = date.plusDays(1)
        }

        val from = first.atStartOfDay(zone).toInstant().toEpochMilli()
        val to = today.plusDays(2).atStartOfDay(zone).toInstant().toEpochMilli()
        val series = model.run(spans, from, to, STEP_MILLIS, zone, INITIAL_PRESSURE)

        val scored = tests.mapNotNull { test -> ReactionResult.of(test)?.let { test to it } }.sortedBy { it.first.start }
        val alertnessAt = scored.map { (test, _) -> series.at(test.start.toEpochMilli() + ReactionTestRun.DURATION_MILLIS / 2) }
        val fitted = scored.indices.filter { alertnessAt[it] != null }
        val calibration = AlertnessCalibration.fit(
            DoubleArray(fitted.size) { checkNotNull(alertnessAt[fitted[it]]) - reference },
            DoubleArray(fitted.size) { ln(scored[fitted[it]].second.speed) },
        )

        val days = mutableMapOf<LocalDate, AlertnessDay>()
        for (offset in DAYS - 1 downTo 0) {
            val day = today.minusDays(offset.toLong())
            val recordedNearby = (0 until RECORDED_WITHIN).any { day.minusDays(it.toLong()) in nightsByDate }
            if (!recordedNearby) continue
            val wake = nightsByDate[day]?.end ?: if (day in assumed) day.atTime(usualWake).atZone(zone).toInstant() else continue
            day(series, day, wake, wake.plus(dayLength), day in assumed, calibration, reference)?.let { days[day] = it }
        }

        val testsShown = scored.mapIndexed { i, (test, result) ->
            val alertness = alertnessAt[i]
            ScoredTest(
                test = test,
                result = result,
                alertness = alertness,
                expected = alertness?.let { calibration.performance(it, reference) },
                measured = calibration.restedSpeed?.let { 100.0 * result.speed / it },
            )
        }
        return AlertnessHistory(days, testsShown, calibration, reference, testDue(scored.map { it.first }, zone, today), now, today)
    }

    private fun day(
        series: AlertnessSeries,
        date: LocalDate,
        wake: Instant,
        end: Instant,
        assumed: Boolean,
        calibration: AlertnessCalibration,
        reference: Double,
    ): AlertnessDay? {
        val points = (0 until series.size)
            .filter { series.times[it] >= wake.toEpochMilli() && series.times[it] <= end.toEpochMilli() && !series.values[it].isNaN() }
            .map {
                AlertnessPoint(
                    Instant.ofEpochMilli(series.times[it]),
                    series.values[it],
                    calibration.performance(series.values[it], reference),
                )
            }
        if (points.isEmpty()) return null
        return AlertnessDay(date, wake, end, points, points.map { it.performance }.average(), assumed, best(points, wake, end))
    }

    /** The best [BEST_HOURS] starting on a half hour, entirely inside the day. */
    private fun best(points: List<AlertnessPoint>, wake: Instant, end: Instant): Pair<Instant, Instant>? {
        val length = Duration.ofHours(BEST_HOURS.toLong())
        var start = wake.truncatedTo(ChronoUnit.HOURS)
        while (start < wake) start = start.plus(Duration.ofMinutes(30))
        var best: Pair<Instant, Instant>? = null
        var bestMean = Double.NEGATIVE_INFINITY
        while (!start.plus(length).isAfter(end)) {
            val stop = start.plus(length)
            val inside = points.filter { it.time >= start && it.time < stop }
            if (inside.isNotEmpty()) {
                val mean = inside.sumOf { it.performance } / inside.size
                if (mean > bestMean) {
                    bestMean = mean
                    best = start to stop
                }
            }
            start = start.plus(Duration.ofMinutes(30))
        }
        return best
    }

    /** Mean alertness over the last of [REFERENCE_NIGHTS] nights of the full need centred on the usual mid-sleep. */
    private fun reference(model: ThreeProcessModel, midsleepMinutes: Int, needHours: Double, dayLength: Duration, zone: ZoneId): Double {
        val base = LocalDate.of(2026, 1, 5)
        val half = (needHours * HOUR_MILLIS / 2).toLong()
        val nights = (0 until REFERENCE_NIGHTS).map { night ->
            val middle = base.plusDays(night.toLong()).atStartOfDay(zone).toInstant().toEpochMilli() + midsleepMinutes * 60_000L
            (middle - half) until (middle + half)
        }
        val wake = nights.last().last + 1
        val series = model.run(nights, nights.first().first - HOUR_MILLIS, wake + dayLength.toMillis(), STEP_MILLIS, zone, INITIAL_PRESSURE)
        val values = (0 until series.size).filter { series.times[it] >= wake && !series.values[it].isNaN() }.map { series.values[it] }
        return values.average()
    }

    private fun testDue(tests: List<ReactionTest>, zone: ZoneId, today: LocalDate): Boolean {
        val last = tests.maxOfOrNull { it.start }?.atZone(zone)?.toLocalDate() ?: return true
        val gap = when {
            tests.size < AlertnessCalibration.MIN_TESTS -> 2L
            tests.size < AlertnessCalibration.SETTLED_TESTS -> 7L
            else -> 28L
        }
        return ChronoUnit.DAYS.between(last, today) >= gap
    }

    private fun asleepSpans(sleep: SleepSession): List<LongRange> = if (sleep.stages.isEmpty()) {
        listOf(sleep.start.toEpochMilli() until sleep.end.toEpochMilli())
    } else {
        sleep.stages.filter { it.type in ASLEEP }.map { it.start.toEpochMilli() until it.end.toEpochMilli() }
    }

    private fun usualWake(nights: List<Night>): LocalTime {
        if (nights.size < MIN_USUAL_NIGHTS) return DEFAULT_WAKE
        val minutes = nights.map { it.end.atZone(it.zone).toLocalTime().toSecondOfDay() / 60 }.sorted()
        return LocalTime.ofSecondOfDay(minutes[minutes.size / 2] * 60L)
    }

    /** Mid-sleep in minutes after midnight, negative before it. */
    private fun usualMidsleep(nights: List<Night>): Int {
        if (nights.size < MIN_USUAL_NIGHTS) return DEFAULT_MIDSLEEP_MINUTES
        val minutes = nights.map { it.midpointMinuteOfDay.let { m -> if (m >= 12 * 60) m - 24 * 60 else m } }.sorted()
        return minutes[minutes.size / 2]
    }
}
