package app.pacinglab.feature.workouts.list

import app.pacinglab.core.model.SessionKind
import app.pacinglab.core.model.Sport

/** What the list can be narrowed to: running sessions by what they were for, other sessions by sport. */
enum class SessionType {
    INTERVALS,
    SPRINTS,
    THRESHOLD_TEMPO_RUNS,
    EASY_RUNS,
    LONG_RUNS,
    OTHER_RUNS,
    WEIGHTS,
    WALKS,
    RIDES,
    OTHER,
    ;

    companion object {
        fun of(item: SessionItem): SessionType = when (item.sport) {
            Sport.RUN -> when (item.kind) {
                SessionKind.SPRINTS -> SPRINTS

                SessionKind.VO2MAX_INTERVALS,
                SessionKind.THRESHOLD_INTERVALS,
                SessionKind.TEMPO_INTERVALS,
                SessionKind.INTERVALS,
                -> INTERVALS

                SessionKind.THRESHOLD_RUN, SessionKind.TEMPO_RUN -> THRESHOLD_TEMPO_RUNS

                SessionKind.EASY_RUN -> EASY_RUNS

                SessionKind.LONG_RUN -> LONG_RUNS

                SessionKind.RUN, null -> OTHER_RUNS
            }

            Sport.STRENGTH -> WEIGHTS

            Sport.WALK -> WALKS

            Sport.RIDE -> RIDES

            Sport.OTHER -> OTHER
        }
    }
}

/** The order of the list. Newest and oldest first keep the month headings; the others list sessions plainly. */
enum class WorkoutSort {
    NEWEST,
    OLDEST,
    LONGEST,
    FARTHEST,
    HIGHEST_LOAD,
    HIGHEST_HEART_RATE,
    ;

    val byDate: Boolean get() = this == NEWEST || this == OLDEST
}

/**
 * Which sessions the list shows. Within a group any chosen value will do; across groups every group
 * must match; a group with nothing chosen lets everything through.
 */
data class WorkoutFilter(
    val types: Set<SessionType> = emptySet(),
    val captures: Set<Capture> = emptySet(),
    val origins: Set<String> = emptySet(),
) {
    val active: Boolean get() = types.isNotEmpty() || captures.isNotEmpty() || origins.isNotEmpty()

    fun matches(item: SessionItem): Boolean = (types.isEmpty() || SessionType.of(item) in types) &&
        (captures.isEmpty() || item.capture in captures) &&
        (origins.isEmpty() || item.origin in origins)

    /** The filter as stored: "type:INTERVALS", "capture:DETECTED", "origin:com.fitbit.FitbitMobile". */
    fun names(): Set<String> = types.map { "$TYPE${it.name}" }.toSet() +
        captures.map { "$CAPTURE${it.name}" } +
        origins.map { "$ORIGIN$it" }

    companion object {
        private const val TYPE = "type:"
        private const val CAPTURE = "capture:"
        private const val ORIGIN = "origin:"

        /** Reads [names] as [names] writes them; names no longer known are skipped. */
        fun of(names: Set<String>) = WorkoutFilter(
            types = names.mapNotNull { name -> SessionType.entries.firstOrNull { "$TYPE${it.name}" == name } }.toSet(),
            captures = names.mapNotNull { name -> Capture.entries.firstOrNull { "$CAPTURE${it.name}" == name } }.toSet(),
            origins = names.filter { it.startsWith(ORIGIN) }.map { it.removePrefix(ORIGIN) }.toSet(),
        )
    }
}

/** The sessions [filter] lets through, in [sort] order; [this] is newest first, which breaks ties. */
fun List<SessionItem>.arranged(filter: WorkoutFilter, sort: WorkoutSort): List<SessionItem> {
    val shown = if (filter.active) filter(filter::matches) else this
    return when (sort) {
        WorkoutSort.NEWEST -> shown
        WorkoutSort.OLDEST -> shown.reversed()
        WorkoutSort.LONGEST -> shown.sortedByDescending { it.movingMillis }
        WorkoutSort.FARTHEST -> shown.sortedWith(compareByDescending(nullsFirst()) { it.shownDistance })
        WorkoutSort.HIGHEST_LOAD -> shown.sortedWith(compareByDescending(nullsFirst()) { it.load })
        WorkoutSort.HIGHEST_HEART_RATE -> shown.sortedWith(compareByDescending(nullsFirst()) { it.averageHr })
    }
}

/** Distance as the list shows it: none for gym sessions, whose distance only counts steps. */
internal val SessionItem.shownDistance: Double? get() = distanceMeters.takeIf { sport != Sport.STRENGTH }
