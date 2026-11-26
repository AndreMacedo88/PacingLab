package app.pacinglab.notification

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import app.pacinglab.core.data.di.ApplicationScope
import app.pacinglab.core.data.reminder.EffortQuestion
import app.pacinglab.core.data.repository.EffortRepository
import app.pacinglab.core.data.repository.WellnessRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate

/**
 * Saves an answer tapped on a question notification. The notification first changes to show the answer as
 * saved, then the answer is stored; it is the same store the app's pickers write to, so Today and Sleep show it
 * at once and can change it.
 */
class QuestionAnswerReceiver : BroadcastReceiver() {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Answers {
        fun wellness(): WellnessRepository

        fun efforts(): EffortRepository

        fun notifier(): PlatformQuestionNotifier

        @ApplicationScope
        fun scope(): CoroutineScope
    }

    override fun onReceive(context: Context, intent: Intent) {
        val answers = EntryPointAccessors.fromApplication(context, Answers::class.java)
        val pending = goAsync()
        answers.scope().launch {
            try {
                save(answers, intent)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("PacingLab", "Could not save the answer from a notification", e)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun save(answers: Answers, intent: Intent) {
        val answer = intent.getIntExtra(EXTRA_ANSWER, 0).takeIf { it > 0 } ?: return
        when (intent.action) {
            ACTION_RESTED -> {
                val morning = LocalDate.parse(intent.getStringExtra(EXTRA_MORNING) ?: return)
                answers.notifier().confirmRested(morning, answer)
                answers.wellness().setRested(morning, answer, fromNotification = true)
            }

            ACTION_EFFORT -> {
                val question = EffortQuestion(
                    workoutIds = intent.getStringArrayExtra(PlatformQuestionNotifier.EXTRA_WORKOUTS)?.toList() ?: return,
                    start = Instant.ofEpochMilli(intent.getLongExtra(EXTRA_START, 0)),
                    zoneOffset = offsetOf(intent.getIntExtra(EXTRA_OFFSET, Int.MIN_VALUE)),
                    durationMillis = intent.getLongExtra(EXTRA_DURATION, 0),
                )
                answers.notifier().confirmEffort(question, answer)
                question.workoutIds.forEach { answers.efforts().rate(it, answer, fromNotification = true) }
            }
        }
    }

    internal companion object {
        private const val ACTION_RESTED = "app.pacinglab.action.ANSWER_RESTED"
        private const val ACTION_EFFORT = "app.pacinglab.action.ANSWER_EFFORT"
        private const val EXTRA_ANSWER = "app.pacinglab.extra.ANSWER"
        private const val EXTRA_MORNING = "app.pacinglab.extra.MORNING"
        private const val EXTRA_START = "app.pacinglab.extra.START"
        private const val EXTRA_OFFSET = "app.pacinglab.extra.OFFSET"
        private const val EXTRA_DURATION = "app.pacinglab.extra.DURATION"

        /** Request codes: one pending intent per answer, so that each button sends its own value. */
        private const val RESTED_CODES = 100
        private const val EFFORT_CODES = 200

        fun restedIntent(context: Context, morning: LocalDate, rating: Int): PendingIntent = broadcast(
            context,
            RESTED_CODES + rating,
            Intent(context, QuestionAnswerReceiver::class.java)
                .setAction(ACTION_RESTED)
                .putExtra(EXTRA_MORNING, morning.toString())
                .putExtra(EXTRA_ANSWER, rating),
        )

        fun effortIntent(context: Context, question: EffortQuestion, effort: Int): PendingIntent = broadcast(
            context,
            EFFORT_CODES + effort,
            Intent(context, QuestionAnswerReceiver::class.java)
                .setAction(ACTION_EFFORT)
                .putExtra(PlatformQuestionNotifier.EXTRA_WORKOUTS, question.workoutIds.toTypedArray())
                .putExtra(EXTRA_START, question.start.toEpochMilli())
                .putExtra(EXTRA_OFFSET, question.zoneOffset.seconds())
                .putExtra(EXTRA_DURATION, question.durationMillis)
                .putExtra(EXTRA_ANSWER, effort),
        )

        private fun broadcast(context: Context, code: Int, intent: Intent): PendingIntent =
            PendingIntent.getBroadcast(context, code, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}
