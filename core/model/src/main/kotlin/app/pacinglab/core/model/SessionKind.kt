package app.pacinglab.core.model

/**
 * What a running session was for, judged from its reps and its heart rate. Other sports are named by
 * their [Sport].
 */
enum class SessionKind {
    SPRINTS,
    VO2MAX_INTERVALS,
    THRESHOLD_INTERVALS,
    TEMPO_INTERVALS,

    /** Reps whose heart rate does not say what they were for, such as short reps it cannot follow. */
    INTERVALS,
    THRESHOLD_RUN,
    TEMPO_RUN,
    EASY_RUN,
    LONG_RUN,

    /** A run too short or too mixed to name more closely. */
    RUN,
}
