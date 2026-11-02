package app.pacinglab.core.model

import java.time.Instant

/** Steps counted between [start] and [end]; watches write one per minute with steps. */
data class StepCount(val start: Instant, val end: Instant, val count: Long)
