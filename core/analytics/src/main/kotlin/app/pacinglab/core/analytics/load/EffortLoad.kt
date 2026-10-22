package app.pacinglab.core.analytics.load

/**
 * Training load from how hard the athlete rated a session, for gym sessions, where heart rate barely
 * reflects the work done.
 *
 * Session RPE (Foster 2001) multiplies the rating, 1 to 10 on Borg's CR-10 scale, by the minutes: the
 * validated measure of strength training load, in arbitrary units. To sit beside heart rate load, the
 * rating is also turned into a stress score per hour, 10 x rating + 10, so that an hour at a rating of
 * 9 scores 100 like an hour at threshold heart rate; this follows the coaching guidance for estimating
 * a stress score from perceived exertion and is a heuristic, not a validated conversion.
 */
object EffortLoad {
    val RANGE = 1..10

    /** Foster's session RPE: rating times minutes. */
    fun sessionRpe(rating: Int, durationMillis: Long): Double = rating * durationMillis / 60_000.0

    /** Stress score of a session rated [rating] lasting [durationMillis]. */
    fun stressScore(rating: Int, durationMillis: Long): Double {
        require(rating in RANGE) { "Rating $rating outside $RANGE" }
        return (10.0 * rating + 10) * durationMillis / 3_600_000.0
    }
}
