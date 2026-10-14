package app.pacinglab.core.analytics.workout

/** Which activity types can carry a GPS route at all. */
object RouteExpectation {
    private val indoor = setOf(
        "strength_training",
        "weightlifting",
        "calisthenics",
        "yoga",
        "pilates",
        "stretching",
        "guided_breathing",
        "swimming_pool",
        "elliptical",
        "rowing_machine",
        "stair_climbing",
        "biking_stationary",
        "running_treadmill",
    )

    fun possible(activity: String): Boolean = activity !in indoor
}
