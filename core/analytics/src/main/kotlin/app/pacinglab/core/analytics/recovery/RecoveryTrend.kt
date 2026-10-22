package app.pacinglab.core.analytics.recovery

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.exp
import kotlin.math.ln

/**
 * HRV and resting heart rate on one day, for trend charts.
 *
 * @property hrvMs that night's HRV; [hrvWeekMs] the average of the last seven days (on the log scale,
 *   as HRV-guided training uses it) once three nights have it.
 * @property hrvNormal the normal range readiness compares the week with; null before the baseline has a
 *   week of nights.
 * @property restingHr that night's resting heart rate; [restingWeek] the average of the last seven days.
 * @property restingNormal the range readiness counts as usual for resting heart rate; null on days
 *   without a night or before a week of them.
 */
data class RecoveryDay(
    val date: LocalDate,
    val hrvMs: Double?,
    val hrvWeekMs: Double?,
    val hrvNormal: ClosedFloatingPointRange<Double>?,
    val restingHr: Double?,
    val restingWeek: Double?,
    val restingNormal: ClosedFloatingPointRange<Double>?,
)

object RecoveryTrend {
    /** Nights in the last seven days needed for a weekly average. */
    const val MIN_WEEK_NIGHTS = 3

    /**
     * One day for every date from the first night to [until], with the ranges [Readiness] uses on that
     * day, so that the trend and the Today screen agree. [sleepScores] are passed on to readiness.
     * Stray nights are left out: stretches of fewer than [MIN_WEEK_NIGHTS] nights between gaps of more
     * than [Readiness.BASELINE_GAP_DAYS] days, such as a single night someone else wore the watch.
     */
    fun of(allNights: List<Night>, until: LocalDate, sleepScores: Map<LocalDate, SleepScore>? = null): List<RecoveryDay> {
        val nights = withoutStrayNights(allNights)
        val first = nights.minOfOrNull { it.date } ?: return emptyList()
        if (first.isAfter(until)) return emptyList()
        val byDate = nights.associateBy { it.date }
        val days = ChronoUnit.DAYS.between(first, until).toInt() + 1
        return List(days) { i ->
            val date = first.plusDays(i.toLong())
            val week = (0L until 7L).mapNotNull { byDate[date.minusDays(it)] }
            val readiness = Readiness.of(nights, null, date, sleepScores)
            RecoveryDay(
                date = date,
                hrvMs = byDate[date]?.hrvMs,
                hrvWeekMs = week.mapNotNull {
                    it.hrvMs
                }.takeIf { it.size >= MIN_WEEK_NIGHTS }?.let { values -> exp(values.map { ln(it) }.average()) },
                hrvNormal = readiness.hrv?.let { it.normalLowMs..it.normalHighMs },
                restingHr = byDate[date]?.restingHr,
                restingWeek = week.mapNotNull { it.restingHr }.takeIf { it.size >= MIN_WEEK_NIGHTS }?.average(),
                restingNormal = readiness.restingHr?.let { it.normalLow..it.normalHigh },
            )
        }
    }

    private fun withoutStrayNights(nights: List<Night>): List<Night> {
        val sorted = nights.sortedBy { it.date }
        val stretches = mutableListOf<MutableList<Night>>()
        for (night in sorted) {
            val last = stretches.lastOrNull()?.last()
            if (last == null || ChronoUnit.DAYS.between(last.date, night.date) > Readiness.BASELINE_GAP_DAYS) {
                stretches += mutableListOf(night)
            } else {
                stretches.last() += night
            }
        }
        return stretches.filter { it.size >= MIN_WEEK_NIGHTS }.flatten()
    }
}
