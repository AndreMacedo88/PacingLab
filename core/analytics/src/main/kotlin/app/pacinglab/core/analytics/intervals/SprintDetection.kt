package app.pacinglab.core.analytics.intervals

import app.pacinglab.core.model.SampleSeries

/**
 * Finds sprints: short all-out bursts with long rests between them, such as 10-second sprints with two
 * minutes of walking. Interval detection cannot see them, because it ignores stretches under 10 seconds
 * and its smoothing flattens a 6-second burst.
 *
 * The method:
 * 1. Speed on a one-second grid, smoothed with a three-second running median, which removes single
 *    spikes but keeps a burst of a few seconds.
 * 2. A sprint is at least [MIN_SPRINT_SECONDS] at [SPRINT_SPEED] or faster, extended on both sides to
 *    where the athlete was still running ([EDGE_SPEED]), so that the acceleration counts.
 * 3. The workout is a sprint session when it holds at least [MIN_SPRINTS] of them, none longer than
 *    [MAX_SPRINT_SECONDS], at least [MIN_RECOVERY_SECONDS] apart, sprinting is at most
 *    [MAX_SPRINT_SHARE] of its time, and the athlete rests between them: at most
 *    [MAX_RUNNING_REST_SHARE] of the time between sprints at running speed. A steady fast run fails the
 *    length test, a fartlek the rest test, and an interval session, whose rep starts can spike past
 *    sprint speed on a wrist sensor, the resting test (the owner's sprint sessions: 0-2 % running
 *    between sprints; interval sessions: 62-100 %).
 *
 * Heart rate plays no part: it barely reacts to a 10-second effort.
 */
object SprintDetection {
    /** About 18 km/h (3:20 per km): well beyond steady running for anyone but elite runners. */
    const val SPRINT_SPEED = 5.0

    const val MIN_SPRINT_SECONDS = 3
    const val MAX_SPRINT_SECONDS = 45
    const val MIN_RECOVERY_SECONDS = 20
    const val MIN_SPRINTS = 2
    const val MAX_SPRINT_SHARE = 0.3
    const val MAX_RUNNING_REST_SHARE = 0.25

    /** About 7:35 per km: from here on the athlete is running, not walking. */
    private const val RUNNING_SPEED = 2.2

    /** Jogging speed: where a sprint starts and ends. */
    private const val EDGE_SPEED = 2.5

    /** A sprint's start or end is moved out by at most this much. */
    private const val MAX_EDGE_SECONDS = 4

    /** Dips below sprint speed this short are part of the sprint, as when a stride lands badly. */
    private const val MAX_DIP_SECONDS = 2

    /**
     * Phases covering [startMillis, endMillis): sprints as [PhaseKind.WORK], the rests between them as
     * [PhaseKind.RECOVERY] and the time before and after as warm-up and cool-down. Null when the
     * workout is not a sprint session or its speed stream is too sparse. [speed] must be in m/s with
     * implausible values already removed.
     */
    fun detect(speed: SampleSeries, startMillis: Long, endMillis: Long): List<Phase>? {
        val grid = IntervalDetection.grid(speed, startMillis, endMillis) ?: return null
        val smoothed = IntervalDetection.runningMedian(grid, 1)
        val sprints = bursts(smoothed).map { extend(it, smoothed) }
        if (sprints.size < MIN_SPRINTS) return null
        if (sprints.any { it.last - it.first + 1 > MAX_SPRINT_SECONDS }) return null
        if (sprints.zipWithNext().any { (a, b) -> b.first - a.last - 1 < MIN_RECOVERY_SECONDS }) return null
        if (sprints.sumOf { it.last - it.first + 1 } > MAX_SPRINT_SHARE * grid.size) return null
        val between = sprints.zipWithNext().flatMap { (a, b) -> (a.last + 1 until b.first).toList() }.filter { smoothed[it].isFinite() }
        if (between.count { smoothed[it] >= RUNNING_SPEED } > MAX_RUNNING_REST_SHARE * between.size) return null

        fun meters(from: Int, until: Int): Double {
            var sum = 0.0
            for (k in from until until) if (grid[k].isFinite()) sum += grid[k]
            return sum
        }
        fun phase(kind: PhaseKind, from: Int, until: Int) =
            Phase(kind, startMillis + from * 1000L, startMillis + until * 1000L, meters(from, until))

        val phases = mutableListOf<Phase>()
        if (sprints.first().first > 0) phases += phase(PhaseKind.WARM_UP, 0, sprints.first().first)
        sprints.forEachIndexed { i, sprint ->
            phases += phase(PhaseKind.WORK, sprint.first, sprint.last + 1)
            val next = sprints.getOrNull(i + 1)
            if (next != null) phases += phase(PhaseKind.RECOVERY, sprint.last + 1, next.first)
        }
        if (sprints.last().last + 1 < grid.size) phases += phase(PhaseKind.COOL_DOWN, sprints.last().last + 1, grid.size)
        return phases
    }

    /** Stretches of at least [MIN_SPRINT_SECONDS] at sprint speed, bridging short dips. */
    private fun bursts(smoothed: DoubleArray): List<IntRange> {
        val fast = BooleanArray(smoothed.size) { smoothed[it] >= SPRINT_SPEED }
        val found = mutableListOf<IntRange>()
        var i = 0
        while (i < fast.size) {
            if (!fast[i]) {
                i++
                continue
            }
            var end = i
            var k = i + 1
            while (k < fast.size) {
                if (fast[k]) {
                    end = k
                } else if (k - end > MAX_DIP_SECONDS) {
                    break
                }
                k++
            }
            if (end - i + 1 >= MIN_SPRINT_SECONDS) found += i..end
            i = end + 1
        }
        return found
    }

    private fun extend(burst: IntRange, smoothed: DoubleArray): IntRange {
        var first = burst.first
        while (first > 0 && burst.first - first < MAX_EDGE_SECONDS && smoothed[first - 1] >= EDGE_SPEED) first--
        var last = burst.last
        while (last < smoothed.lastIndex && last - burst.last < MAX_EDGE_SECONDS && smoothed[last + 1] >= EDGE_SPEED) last++
        return first..last
    }
}
