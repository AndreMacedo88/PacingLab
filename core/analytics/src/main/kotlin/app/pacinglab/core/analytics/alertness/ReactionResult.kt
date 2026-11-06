package app.pacinglab.core.analytics.alertness

import app.pacinglab.core.model.ReactionTest

/**
 * The outcomes of one reaction test. Response speed, the mean of 1/RT with RT in seconds, is the most
 * sensitive measure of the 3-minute test to sleep loss (Basner, Mollicone and Dinges 2011); the reaction
 * time shown is its reciprocal, a mean that the slowest answers do not dominate.
 *
 * Lapses count answers of [LAPSE_MILLIS] or more. The 3-minute test's own threshold is 355 ms, set on a
 * dedicated device; a phone's screen and touch add some tens of milliseconds to every answer, so the
 * classic threshold of the 10-minute test is used instead.
 *
 * @property speed mean response speed, per second.
 * @property responses stimuli answered or timed out.
 */
data class ReactionResult(val speed: Double, val responses: Int, val lapses: Int, val falseStarts: Int) {
    /** Reaction time as shown: 1000 / [speed], in milliseconds. */
    val millis: Double get() = 1000.0 / speed

    companion object {
        const val LAPSE_MILLIS = 500

        /** A test with fewer answers than this was cut short and is not scored. */
        const val MIN_RESPONSES = 20

        fun of(test: ReactionTest): ReactionResult? {
            val valid = test.reactionMillis.filter { it >= ReactionTest.FASTEST_MILLIS }
            if (valid.size < MIN_RESPONSES) return null
            return ReactionResult(
                speed = valid.sumOf { 1000.0 / it } / valid.size,
                responses = valid.size,
                lapses = valid.count { it >= LAPSE_MILLIS },
                falseStarts = test.falseStarts,
            )
        }
    }
}
