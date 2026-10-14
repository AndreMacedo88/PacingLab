package app.pacinglab.core.healthconnect

import androidx.health.connect.client.records.ExerciseSessionRecord as Session

/** Stable lower-case names for Health Connect exercise types, used in reports and logs. */
fun exerciseTypeName(type: Int): String = when (type) {
    Session.EXERCISE_TYPE_OTHER_WORKOUT -> "other_workout"
    Session.EXERCISE_TYPE_RUNNING -> "running"
    Session.EXERCISE_TYPE_RUNNING_TREADMILL -> "running_treadmill"
    Session.EXERCISE_TYPE_WALKING -> "walking"
    Session.EXERCISE_TYPE_HIKING -> "hiking"
    Session.EXERCISE_TYPE_BIKING -> "biking"
    Session.EXERCISE_TYPE_BIKING_STATIONARY -> "biking_stationary"
    Session.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING -> "high_intensity_interval_training"
    Session.EXERCISE_TYPE_STRENGTH_TRAINING -> "strength_training"
    Session.EXERCISE_TYPE_WEIGHTLIFTING -> "weightlifting"
    Session.EXERCISE_TYPE_CALISTHENICS -> "calisthenics"
    Session.EXERCISE_TYPE_BOOT_CAMP -> "boot_camp"
    Session.EXERCISE_TYPE_EXERCISE_CLASS -> "exercise_class"
    Session.EXERCISE_TYPE_ELLIPTICAL -> "elliptical"
    Session.EXERCISE_TYPE_ROWING_MACHINE -> "rowing_machine"
    Session.EXERCISE_TYPE_STAIR_CLIMBING -> "stair_climbing"
    Session.EXERCISE_TYPE_STRETCHING -> "stretching"
    Session.EXERCISE_TYPE_YOGA -> "yoga"
    Session.EXERCISE_TYPE_PILATES -> "pilates"
    Session.EXERCISE_TYPE_SWIMMING_POOL -> "swimming_pool"
    Session.EXERCISE_TYPE_SWIMMING_OPEN_WATER -> "swimming_open_water"
    Session.EXERCISE_TYPE_GUIDED_BREATHING -> "guided_breathing"
    Session.EXERCISE_TYPE_MARTIAL_ARTS -> "martial_arts"
    Session.EXERCISE_TYPE_DANCING -> "dancing"
    Session.EXERCISE_TYPE_GOLF -> "golf"
    Session.EXERCISE_TYPE_TENNIS -> "tennis"
    Session.EXERCISE_TYPE_SOCCER -> "soccer"
    Session.EXERCISE_TYPE_SKIING -> "skiing"
    else -> "type_$type"
}
