package app.pacinglab.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.pacinglab.core.data.reminder.EffortQuestion
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import java.time.Duration
import java.time.LocalDate

/** Posts the morning and gym questions with made-up details, for a look at them on a phone (debug builds only). */
class ShowQuestionsReceiver : BroadcastReceiver() {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Questions {
        fun notifier(): PlatformQuestionNotifier

        fun clock(): Clock
    }

    override fun onReceive(context: Context, intent: Intent) {
        val questions = EntryPointAccessors.fromApplication(context, Questions::class.java)
        val now = questions.clock().instant()
        questions.notifier().askRested(LocalDate.now(questions.clock()))
        questions.notifier().askEffort(
            EffortQuestion(
                workoutIds = listOf("sample-gym"),
                start = now.minus(Duration.ofMinutes(SAMPLE_MINUTES + 10)),
                zoneOffset = null,
                durationMillis = Duration.ofMinutes(SAMPLE_MINUTES).toMillis(),
            ),
        )
    }

    private companion object {
        const val SAMPLE_MINUTES = 52L
    }
}
