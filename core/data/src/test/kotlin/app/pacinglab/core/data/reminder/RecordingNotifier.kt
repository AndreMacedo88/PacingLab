package app.pacinglab.core.data.reminder

import java.time.LocalDate

/** Records what would have been shown. */
class RecordingNotifier : QuestionNotifier {
    val rested = mutableListOf<LocalDate>()
    val efforts = mutableListOf<EffortQuestion>()
    val closed = mutableListOf<String>()

    override fun askRested(morning: LocalDate) {
        rested += morning
    }

    override fun closeRested(morning: LocalDate) {
        closed += morning.toString()
    }

    override fun askEffort(question: EffortQuestion) {
        efforts += question
    }

    override fun closeEffort(workoutId: String) {
        closed += workoutId
    }
}
