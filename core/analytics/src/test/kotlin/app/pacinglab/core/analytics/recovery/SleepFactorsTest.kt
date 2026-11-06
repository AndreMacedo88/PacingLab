package app.pacinglab.core.analytics.recovery

import app.pacinglab.core.model.SleepStage
import app.pacinglab.core.model.SleepStageType
import app.pacinglab.core.model.home.NightClimate
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import assertk.assertions.isTrue
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Random
import kotlin.math.roundToInt

class SleepFactorsTest {
    private val first = LocalDate.of(2026, 3, 2)
    private val weekend = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
    private val need = Duration.ofHours(8).toMillis()

    /** A night ending on [date] after falling asleep [bedtime] minutes after 22:00. */
    private fun night(
        date: LocalDate,
        bedtime: Int,
        asleepMinutes: Int,
        hrv: Double = 60.0,
        restingHr: Double = 50.0,
        deepAtEnd: Boolean = false,
        deepMinutes: Int = 60,
        remMinutes: Int = 90,
    ): Night {
        val start = date.minusDays(1).atTime(22, 0).toInstant(ZoneOffset.UTC).plus(Duration.ofMinutes(bedtime.toLong()))
        val end = start.plus(Duration.ofMinutes(asleepMinutes.toLong()))
        val stages = listOf(
            SleepStage(start, end.minus(Duration.ofMinutes(40)), SleepStageType.LIGHT),
            SleepStage(end.minus(Duration.ofMinutes(40)), end, if (deepAtEnd) SleepStageType.DEEP else SleepStageType.REM),
        )
        return Night(
            date = date,
            start = start,
            end = end,
            asleepMillis = asleepMinutes * 60_000L,
            stageMillis = mapOf(
                SleepStageType.DEEP to deepMinutes * 60_000L,
                SleepStageType.REM to remMinutes * 60_000L,
                SleepStageType.LIGHT to (asleepMinutes - deepMinutes - remMinutes) * 60_000L,
            ),
            hrvMs = hrv,
            restingHr = restingHr,
            respiratoryRate = 14.0,
            continuity = SleepContinuity(10 * 60_000L, 0),
            stages = stages,
        )
    }

    private fun history(nights: List<Night>, scores: List<Int>) = SleepHistory(
        nights = nights,
        scores = nights.indices.associate { nights[it].date to SleepScore(scores[it], 1.0, 1.0) },
        needs = nights.associate { it.date to need },
    )

    @Test
    fun `too few nights only count towards the start`() {
        val random = Random(1)
        val nights = List(20) { night(first.plusDays(it.toLong()), random.nextInt(90), 470) }

        val factors = SleepFactors.of(history(nights, List(20) { 80 }), emptyMap(), emptyList(), emptyMap(), weekend)

        assertThat(factors.score.learning).isTrue()
        assertThat(factors.score.nights).isEqualTo(20)
        assertThat(factors.score.effects).isEmpty()
        assertThat(factors.rested.nights).isEqualTo(0)
    }

    @Test
    fun `planted effects come out and an unrelated factor does not`() {
        val random = Random(2)
        val dates = List(150) { first.plusDays(it.toLong()) }
        // Late sessions on a third of the evenings, and some load on most days, which does nothing.
        val late = dates.map { random.nextDouble() < 0.3 }
        val training = dates.flatMapIndexed { i, date ->
            val bedtime = date.minusDays(1).atTime(22, 0).toInstant(ZoneOffset.UTC)
            buildList {
                if (late[i]) add(TrainingBout(bedtime.minus(Duration.ofHours(2)), 60.0))
                if (random.nextDouble() < 0.6) add(TrainingBout(bedtime.minus(Duration.ofHours(12)), 20.0 + random.nextInt(80)))
            }
        }
        val nights = dates.map { night(it, random.nextInt(90), 440 + random.nextInt(60)) }
        val scores = dates.mapIndexed { i, date ->
            val free = if (date.dayOfWeek in weekend) 8 else 0
            val lateCost = if (late[i]) -7 else 0
            (78 + free + lateCost + 8 * random.nextGaussian()).roundToInt().coerceIn(0, 100)
        }

        val score = SleepFactors.of(history(nights, scores), emptyMap(), training, emptyMap(), weekend).score

        val byFactor = score.effects.associateBy { it.factor }
        assertThat(score.learning).isFalse()
        assertThat(score.predictive).isTrue()
        assertThat(byFactor.getValue(SleepFactor.FREE_DAY).clear).isTrue()
        assertThat(byFactor.getValue(SleepFactor.FREE_DAY).mean).isGreaterThan(3.0)
        assertThat(byFactor.getValue(SleepFactor.LATE_TRAINING).clear).isTrue()
        assertThat(byFactor.getValue(SleepFactor.LATE_TRAINING).mean).isLessThan(-3.0)
        assertThat(byFactor.getValue(SleepFactor.BEDTIME).clear).isFalse()
        // The 8-point weekend effect shows in the adjusted points as well.
        val points = byFactor.getValue(SleepFactor.FREE_DAY).points
        assertThat(points.filter { it.value == 1.0 }.map { it.adjusted }.average())
            .isGreaterThan(points.filter { it.value == 0.0 }.map { it.adjusted }.average() + 3)
        // Deep sleep before waking and the nap-free markers belong to other outcomes or lack variety.
        assertThat(byFactor.keys.contains(SleepFactor.DEEP_BEFORE_WAKING)).isFalse()
    }

    @Test
    fun `a yes-or-no factor seen on too few nights waits`() {
        val random = Random(3)
        val dates = List(40) { first.plusDays(it.toLong()) }
        val nights = dates.map { night(it, random.nextInt(90), 470) }
        // A single late session.
        val training = listOf(TrainingBout(dates[5].minusDays(1).atTime(21, 0).toInstant(ZoneOffset.UTC), 80.0))

        val score = SleepFactors.of(history(nights, List(40) { 70 + random.nextInt(20) }), emptyMap(), training, emptyMap(), weekend).score

        assertThat(score.waiting).contains(SleepFactor.LATE_TRAINING)
        assertThat(score.effects.none { it.factor == SleepFactor.LATE_TRAINING }).isTrue()
    }

    @Test
    fun `nights with vitals off their range are left out`() {
        val random = Random(4)
        val dates = List(60) { first.plusDays(it.toLong()) }
        val ill = setOf(45, 46, 47)
        val nights = dates.mapIndexed { i, date ->
            if (i in ill) {
                night(date, 30, 400, hrv = 30.0, restingHr = 62.0)
            } else {
                night(date, random.nextInt(90), 470, hrv = 58.0 + random.nextInt(5), restingHr = 49.0 + random.nextInt(3))
            }
        }
        val scores = dates.indices.map { if (it in ill) 40 else 75 + random.nextInt(15) }

        val score = SleepFactors.of(history(nights, scores), emptyMap(), emptyList(), emptyMap(), weekend).score

        assertThat(score.excluded).isEqualTo(3)
        assertThat(score.nights).isEqualTo(57)
    }

    @Test
    fun `how rested the athlete felt is analysed on rated mornings with the night's own measures`() {
        val random = Random(5)
        val dates = List(80) { first.plusDays(it.toLong()) }
        val asleep = dates.map { 400 + random.nextInt(120) }
        val nights = dates.mapIndexed { i, date -> night(date, random.nextInt(90), asleep[i], deepAtEnd = random.nextDouble() < 0.3) }
        // Rested follows time asleep: about one step per two hours.
        val rested = dates.indices.associate { i ->
            dates[i] to (3 + (asleep[i] - 460) / 120.0 + 0.5 * random.nextGaussian()).roundToInt().coerceIn(1, 5)
        }

        val factors = SleepFactors.of(history(nights, List(80) { 80 }), rested, emptyList(), emptyMap(), weekend)

        val asleepEffect = factors.rested.effects.single { it.factor == SleepFactor.TIME_ASLEEP }
        assertThat(factors.rested.nights).isEqualTo(80)
        assertThat(asleepEffect.clear).isTrue()
        assertThat(asleepEffect.mean).isGreaterThan(0.2)
        assertThat(factors.rested.effects.any { it.factor == SleepFactor.DEEP_BEFORE_WAKING }).isTrue()
    }

    @Test
    fun `a warm bedroom shows among the factors, and unmeasured factors are not listed as waiting`() {
        val random = Random(6)
        val dates = List(120) { first.plusDays(it.toLong()) }
        val nights = dates.map { night(it, random.nextInt(90), 470) }
        val temperature = dates.associateWith { 17.0 + random.nextDouble() * 6 }
        // Three points worse per degree above 17.
        val scores = dates.map { (85 - 3 * (temperature.getValue(it) - 17) + 4 * random.nextGaussian()).roundToInt() }
        val climate = dates.associateWith { NightClimate(it, temperature = temperature.getValue(it)) }

        val score = SleepFactors.of(history(nights, scores), emptyMap(), emptyList(), emptyMap(), weekend, climate = climate).score
        val withoutHome = SleepFactors.of(history(nights, scores), emptyMap(), emptyList(), emptyMap(), weekend).score

        val bedroom = score.effects.single { it.factor == SleepFactor.BEDROOM_TEMPERATURE }
        assertThat(bedroom.clear).isTrue()
        assertThat(bedroom.mean).isLessThan(-3.0)
        assertThat(withoutHome.waiting.none { it.group == FactorGroup.BEDROOM }).isTrue()
        assertThat(withoutHome.effects.none { it.factor.group == FactorGroup.BEDROOM }).isTrue()
    }

    @Test
    fun `the expected restedness waits for enough rated mornings`() {
        val random = Random(7)
        val dates = List(40) { first.plusDays(it.toLong()) }
        val nights = dates.map { night(it, random.nextInt(90), 470, deepMinutes = 40 + random.nextInt(50)) }
        val rested = dates.take(20).associateWith { 2 + random.nextInt(3) }

        val restedness = SleepFactors.of(history(nights, List(40) { 80 }), rested, emptyList(), emptyMap(), weekend).restedness

        assertThat(restedness.mornings).isEqualTo(20)
        assertThat(restedness.learning).isTrue()
        assertThat(restedness.predictive).isFalse()
        assertThat(restedness.expected).isEmpty()
    }

    @Test
    fun `the expected restedness follows deep sleep that the sleep score ignores, and covers unrated nights`() {
        val random = Random(8)
        val dates = List(91) { first.plusDays(it.toLong()) }
        val deep = dates.map { 40 + random.nextInt(60) }
        val nights = dates.mapIndexed { i, date ->
            night(date, random.nextInt(90), 440 + random.nextInt(60), deepMinutes = deep[i], remMinutes = 70 + random.nextInt(40))
        }
        // About a step per half hour of deep sleep, which the score knows nothing about. The last morning is not rated yet.
        val rested = dates.indices.take(90).associate { i ->
            dates[i] to (3 + (deep[i] - 70) / 30.0 + 0.4 * random.nextGaussian()).roundToInt().coerceIn(1, 5)
        }
        val scores = dates.map { 75 + random.nextInt(15) }

        val factors = SleepFactors.of(history(nights, scores), rested, emptyList(), emptyMap(), weekend)
        val restedness = factors.restedness

        assertThat(factors.rested.effects.single { it.factor == SleepFactor.DEEP_SLEEP }.clear).isTrue()
        assertThat(restedness.predictive).isTrue()
        assertThat(restedness.comparison).isEqualTo(ScoreComparison.CLOSER)
        assertThat(restedness.error!!).isLessThan(restedness.scoreError!!)
        assertThat(restedness.expected.keys).isEqualTo(dates.toSet())
        // The rated mornings carry their left-out predictions, which still follow deep sleep.
        val deepNights = dates.indices.take(90).filter { deep[it] >= 85 }.map { restedness.expected.getValue(dates[it]) }
        val lightNights = dates.indices.take(90).filter { deep[it] <= 55 }.map { restedness.expected.getValue(dates[it]) }
        assertThat(deepNights.average()).isGreaterThan(lightNights.average() + 0.5)
        val tonight = dates.last()
        val expected = restedness.expected.getValue(tonight)
        val standing = restedness.standing(tonight)
        if (deep.last() >= 85) assertThat(standing).isEqualTo(RestednessStanding.ABOVE)
        if (deep.last() <= 55) assertThat(standing).isEqualTo(RestednessStanding.BELOW)
        assertThat(expected).isGreaterThan(RestednessEstimate.LOWEST - 1e-9)
        assertThat(expected).isLessThan(RestednessEstimate.HIGHEST + 1e-9)
    }

    @Test
    fun `answers unrelated to the nights give no estimate`() {
        val random = Random(9)
        val dates = List(60) { first.plusDays(it.toLong()) }
        val nights = dates.map { night(it, random.nextInt(90), 430 + random.nextInt(80), deepMinutes = 40 + random.nextInt(50)) }
        val rested = dates.associateWith { 2 + random.nextInt(3) }

        val restedness = SleepFactors.of(history(nights, List(60) { 70 + random.nextInt(20) }), rested, emptyList(), emptyMap(), weekend)
            .restedness

        assertThat(restedness.learning).isFalse()
        assertThat(restedness.predictive).isFalse()
        assertThat(restedness.comparison).isEqualTo(null)
        assertThat(restedness.expected).isEmpty()
    }

    @Test
    fun `typical changes are rounded to readable steps`() {
        assertThat(SleepFactors.niceStep(47.0)).isEqualTo(45.0)
        assertThat(SleepFactors.niceStep(12.4)).isEqualTo(12.0)
        assertThat(SleepFactors.niceStep(2.3)).isEqualTo(2.5)
        assertThat(SleepFactors.niceStep(0.34)).isEqualTo(0.3)
        assertThat(SleepFactors.niceStep(0.02)).isEqualTo(0.0)
    }
}
