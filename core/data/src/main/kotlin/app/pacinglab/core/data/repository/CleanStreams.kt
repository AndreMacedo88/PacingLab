package app.pacinglab.core.data.repository

import app.pacinglab.core.analytics.workout.StreamCleaning
import app.pacinglab.core.database.store.WorkoutStore
import app.pacinglab.core.model.Workout
import app.pacinglab.core.model.WorkoutStreams

/**
 * A workout's streams with measurement errors removed. Every analysis reads streams this way; only sync
 * and the raw export see them as recorded.
 */
internal suspend fun WorkoutStore.cleanStreams(workout: Workout): WorkoutStreams = StreamCleaning.clean(streams(workout.id), workout.sport)
