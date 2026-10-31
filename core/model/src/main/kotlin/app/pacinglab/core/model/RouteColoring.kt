package app.pacinglab.core.model

/** What the line of a route on a map is coloured by. */
enum class RouteColoring {
    /** The heart rate zone of the five-zone model each stretch was run in. */
    ZONES_FIVE,

    /** The zone of the three-zone (polarized) model. */
    ZONES_THREE,

    /** Pace, from the slowest to the fastest running of the session. */
    PACE,

    /** One colour for the whole route. */
    PLAIN,

    ;

    /**
     * This choice when a session offers it, otherwise the nearest one it does: the other heart rate view,
     * then pace or zones, and a plain line when nothing else is there.
     */
    fun within(available: Set<RouteColoring>): RouteColoring {
        val order = when (this) {
            ZONES_FIVE -> listOf(ZONES_FIVE, ZONES_THREE, PACE)
            ZONES_THREE -> listOf(ZONES_THREE, ZONES_FIVE, PACE)
            PACE -> listOf(PACE, ZONES_FIVE, ZONES_THREE)
            PLAIN -> emptyList()
        }
        return order.firstOrNull { it in available } ?: PLAIN
    }
}
