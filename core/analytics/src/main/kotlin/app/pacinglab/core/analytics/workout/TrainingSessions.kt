package app.pacinglab.core.analytics.workout

import app.pacinglab.core.model.Sport
import app.pacinglab.core.model.Workout
import java.time.Duration
import java.time.Instant

/**
 * One training session: a single workout, or several back-to-back workouts of the same sport, such as
 * a GPS warm-up run immediately followed by an interval workout.
 */
data class TrainingSession(val workouts: List<Workout>) {
    init {
        require(workouts.isNotEmpty()) { "A training session needs at least one workout" }
    }

    val id: String get() = workouts.first().id
    val start: Instant get() = workouts.first().start
    val end: Instant get() = workouts.maxOf { it.end }
    val sport: Sport get() = workouts.first().sport
}

object TrainingSessions {
    /** Default largest pause between two workouts that still counts as one session. */
    val DEFAULT_MAX_GAP: Duration = Duration.ofMinutes(5)

    /** Workouts shorter than this are accidental starts and are left out. */
    val MIN_DURATION: Duration = Duration.ofMinutes(1)

    /** Two workouts overlapping by more than this share of the shorter one record the same activity. */
    private const val DUPLICATE_OVERLAP = 0.5

    /**
     * Groups workouts into sessions. A workout joins the previous session when it has the same sport
     * and starts no more than [maxGap] after that session ends (overlaps also join).
     * Input order does not matter; output is sorted by start time.
     */
    fun group(workouts: List<Workout>, maxGap: Duration = DEFAULT_MAX_GAP): List<TrainingSession> {
        require(!maxGap.isNegative) { "maxGap must not be negative" }
        val sessions = mutableListOf<MutableList<Workout>>()
        val meaningful = workouts.filter { Duration.between(it.start, it.end) >= MIN_DURATION }
        for (workout in removeDuplicates(meaningful).sortedWith(compareBy({ it.start }, { it.id }))) {
            val current = sessions.lastOrNull()
            if (current != null && joins(current, workout, maxGap)) current.add(workout) else sessions.add(mutableListOf(workout))
        }
        return sessions.map(::TrainingSession)
    }

    /**
     * When two apps recorded the same activity (Fitbit and Google Fit both log a ride), keeps the
     * richer record: the one with heart rate, then with a route, then the longer one.
     */
    fun removeDuplicates(workouts: List<Workout>): List<Workout> {
        val ranked = workouts.sortedWith(
            compareByDescending<Workout> { it.summary.averageHr != null }
                .thenByDescending { it.summary.hasRoute }
                .thenByDescending { it.summary.durationMillis }
                .thenBy { it.id },
        )
        val kept = mutableListOf<Workout>()
        for (candidate in ranked) {
            val duplicate = kept.any { it.origin != candidate.origin && overlapShare(it, candidate) > DUPLICATE_OVERLAP }
            if (!duplicate) kept += candidate
        }
        return kept
    }

    private fun overlapShare(a: Workout, b: Workout): Double {
        val overlap = minOf(a.end, b.end).toEpochMilli() - maxOf(a.start, b.start).toEpochMilli()
        val shorter = minOf(a.summary.durationMillis, b.summary.durationMillis)
        return if (overlap <= 0 || shorter <= 0) 0.0 else overlap.toDouble() / shorter
    }

    private fun joins(session: List<Workout>, next: Workout, maxGap: Duration): Boolean {
        val sessionEnd = session.maxOf { it.end }
        return session.first().sport == next.sport && !next.start.isAfter(sessionEnd.plus(maxGap))
    }
}
