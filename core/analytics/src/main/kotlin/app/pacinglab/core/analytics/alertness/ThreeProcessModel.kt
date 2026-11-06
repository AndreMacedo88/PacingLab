package app.pacinglab.core.analytics.alertness

import java.time.Instant
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp

/**
 * The three-process model of alertness (Åkerstedt, Folkard and Portin 2004), which predicts alertness on
 * its 1 to 21 scale from sleep and wake alone: 14 is high alertness, 10 the level normal daytime
 * performance stays above, 7 the threshold of sleepiness and 3 extreme sleepiness. It was fitted to group
 * means of subjective alertness and EEG, and checked against vigilance tests and airline crews.
 *
 * Its parts, with the paper's default values:
 * - S, sleep pressure, falls while awake towards 2.4 at 0.0353 an hour and recovers during sleep towards
 *   14.3 at 0.381 an hour; below 12.5 recovery runs no faster than at 12.5, so a long sleep cannot make
 *   up a large debt in one night.
 * - C, the circadian rhythm, 2.5 cos((t - 16.8) pi / 12) by the clock time t in hours.
 * - U, an ultradian rhythm of 0.5 with a 12-hour period lowest 3 hours before the circadian peak: the
 *   afternoon dip.
 * - W, sleep inertia after waking, -5.7 e^(-0.65692 t) with t hours since waking.
 *
 * Two of the paper's individual adjustments are applied: [needHours] scales the speed of recovery so that
 * full recovery takes the athlete's own sleep need instead of 8 hours, and [phaseShiftHours] moves C (and
 * U with it) later for evening types and earlier for morning types.
 */
class ThreeProcessModel(val needHours: Double = DEFAULT_NEED_HOURS, val phaseShiftHours: Double = 0.0) {
    init {
        require(needHours > 0) { "Sleep need must be positive" }
    }

    private val recovery = RECOVERY_RATE * DEFAULT_NEED_HOURS / needHours

    /** Clock time of the circadian peak, in hours. */
    val acrophase: Double = ACROPHASE + phaseShiftHours

    /** Sleep pressure after [hours] awake from [s]. */
    fun awake(s: Double, hours: Double): Double = (s - LOWER) * exp(-DECAY * hours) + LOWER

    /** Sleep pressure after [hours] asleep from [s]: linear up to [BREAK] at the speed the exponential has there, then exponential. */
    fun asleep(s: Double, hours: Double): Double {
        var level = s
        var left = hours
        if (level < BREAK) {
            val slope = recovery * (UPPER - BREAK)
            val toBreak = (BREAK - level) / slope
            if (left <= toBreak) return level + slope * left
            level = BREAK
            left -= toBreak
        }
        return UPPER - (UPPER - level) * exp(-recovery * left)
    }

    fun circadian(clockHours: Double): Double = AMPLITUDE * cos((clockHours - acrophase) * PI / 12)

    fun ultradian(clockHours: Double): Double = -ULTRADIAN_AMPLITUDE * cos(2 * PI / 12 * (clockHours - (acrophase - ULTRADIAN_LEAD)))

    fun inertia(hoursAwake: Double): Double = -INERTIA * exp(-INERTIA_DECAY * hoursAwake)

    /** Alertness with sleep pressure [s] at [clockHours], [hoursAwake] after waking (null: long ago). */
    fun alertness(s: Double, clockHours: Double, hoursAwake: Double?): Double =
        s + circadian(clockHours) + ultradian(clockHours) + (hoursAwake?.let(::inertia) ?: 0.0)

    /**
     * Alertness every [stepMillis] from [from] to [to] (epoch milliseconds), given the spans of sleep
     * [asleep] (epoch milliseconds, end exclusive, in any order and possibly overlapping) and sleep pressure
     * [initial] at [from]. Sleep pressure changes exactly between every grid point and sleep boundary;
     * points inside sleep have no alertness.
     */
    fun run(asleep: List<LongRange>, from: Long, to: Long, stepMillis: Long, zone: ZoneId, initial: Double): AlertnessSeries {
        require(stepMillis > 0 && to >= from) { "Bad run from $from to $to every $stepMillis" }
        val spans = merge(asleep)
        val count = ((to - from) / stepMillis + 1).toInt()
        val times = LongArray(count) { from + it * stepMillis }
        val values = DoubleArray(count)
        val pressure = DoubleArray(count)
        var s = initial
        var t = from
        var lastWake: Long? = spans.lastOrNull { it.last + 1 <= from }?.let { it.last + 1 }
        var span = 0
        for (i in 0 until count) {
            val target = times[i]
            while (t < target) {
                while (span < spans.size && spans[span].last + 1 <= t) span++
                val current = spans.getOrNull(span)
                val inside = current != null && current.first <= t
                val next = when {
                    current == null -> target
                    inside -> minOf(target, current.last + 1)
                    else -> minOf(target, current.first)
                }
                val hours = (next - t) / MILLIS_PER_HOUR
                s = if (inside) asleep(s, hours) else awake(s, hours)
                if (inside && next == current.last + 1) lastWake = next
                t = next
            }
            while (span < spans.size && spans[span].last + 1 <= target) span++
            val sleeping = spans.getOrNull(span)?.let { it.first <= target } == true
            pressure[i] = s
            values[i] = if (sleeping) {
                Double.NaN
            } else {
                val clock = Instant.ofEpochMilli(target).atZone(zone).toLocalTime().toSecondOfDay() / SECONDS_PER_HOUR
                alertness(s, clock, lastWake?.let { (target - it) / MILLIS_PER_HOUR })
            }
        }
        return AlertnessSeries(times, values, pressure)
    }

    companion object {
        const val DEFAULT_NEED_HOURS = 8.0
        const val LOWER = 2.4
        const val UPPER = 14.3
        const val BREAK = 12.5
        const val DECAY = 0.0353
        const val RECOVERY_RATE = 0.381
        const val ACROPHASE = 16.8
        const val AMPLITUDE = 2.5
        const val ULTRADIAN_AMPLITUDE = 0.5
        const val ULTRADIAN_LEAD = 3.0
        const val INERTIA = 5.7
        const val INERTIA_DECAY = 0.65692

        /** Alertness normal daytime performance stays above, and the threshold of sleepiness. */
        const val DAYTIME = 10.0
        const val SLEEPY = 7.0

        private const val MILLIS_PER_HOUR = 3_600_000.0
        private const val SECONDS_PER_HOUR = 3_600.0

        /** Sorted, non-overlapping spans. */
        internal fun merge(spans: List<LongRange>): List<LongRange> {
            val merged = mutableListOf<LongRange>()
            for (span in spans.filter { !it.isEmpty() }.sortedBy { it.first }) {
                val last = merged.lastOrNull()
                if (last != null && span.first <= last.last + 1) {
                    merged[merged.lastIndex] = last.first..maxOf(last.last, span.last)
                } else {
                    merged += span
                }
            }
            return merged
        }
    }
}

/** Alertness on a time grid: [values] is NaN while asleep; [pressure] is sleep pressure S at each point. */
class AlertnessSeries(val times: LongArray, val values: DoubleArray, val pressure: DoubleArray) {
    val size: Int get() = times.size

    /** Alertness at the grid point nearest [time]; null outside the grid or asleep. */
    fun at(time: Long): Double? {
        if (times.isEmpty() || time < times.first() || time > times.last()) return null
        val step = if (times.size > 1) times[1] - times[0] else 1
        val i = ((time - times.first() + step / 2) / step).toInt().coerceIn(0, times.lastIndex)
        return values[i].takeUnless { it.isNaN() }
    }
}
