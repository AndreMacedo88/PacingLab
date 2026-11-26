package app.pacinglab.notification

import android.app.PendingIntent
import android.content.Context
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import app.pacinglab.R

/**
 * The content of a question notification: [title], an optional [detail] line and one button per word of [words],
 * the scale from 1 up, in a connected row like the pickers in the app. The compact form, for the collapsed
 * notification, keeps the title and the buttons. Once [chosen] is set the answer shows as saved and the buttons
 * no longer send anything.
 */
internal object QuestionViews {
    fun build(
        context: Context,
        title: String,
        detail: String?,
        words: List<String>,
        compact: Boolean,
        chosen: Int? = null,
        answer: ((Int) -> PendingIntent)? = null,
    ): RemoteViews {
        require(words.size in 2..BUTTONS.size) { "A question has 2 to ${BUTTONS.size} answers" }
        val views =
            RemoteViews(context.packageName, if (compact) R.layout.notification_question_compact else R.layout.notification_question)
        views.setTextViewText(R.id.question_title, title)
        if (!compact && detail != null) {
            views.setTextViewText(R.id.question_detail, detail)
            views.setViewVisibility(R.id.question_detail, View.VISIBLE)
        }
        val dense = words.size > DENSE_FROM
        val textSize = when {
            !dense -> null
            compact -> R.dimen.question_text_size_dense_compact
            else -> R.dimen.question_text_size_dense
        }?.let(context.resources::getDimension)
        BUTTONS.forEachIndexed { index, id ->
            val value = index + 1
            if (value > words.size) {
                views.setViewVisibility(id, View.GONE)
                return@forEachIndexed
            }
            val shape = when (value) {
                1 -> if (dense) R.drawable.question_button_start_dense else R.drawable.question_button_start
                words.size -> if (dense) R.drawable.question_button_end_dense else R.drawable.question_button_end
                else -> R.drawable.question_button_middle
            }
            views.setInt(id, "setBackgroundResource", shape)
            textSize?.let { views.setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_PX, it) }
            views.setContentDescription(id, context.getString(R.string.notification_answer, value, words[index]))
            when {
                chosen == value -> {
                    // Colour resources, not values, so that the shade resolves them in its own light or dark theme.
                    views.setColorStateList(id, "setBackgroundTintList", R.color.question_chosen)
                    views.setColor(id, "setTextColor", R.color.question_on_chosen)
                }

                chosen != null -> views.setFloat(id, "setAlpha", OTHERS_ALPHA)

                answer != null -> views.setOnClickPendingIntent(id, answer(value))
            }
        }
        if (!compact) {
            // As in the app's pickers: both ends of the scale until an answer is given, then that answer.
            views.setTextViewText(R.id.question_word_start, words[(chosen ?: 1) - 1])
            views.setTextViewText(
                R.id.question_word_end,
                if (chosen ==
                    null
                ) {
                    words.last()
                } else {
                    context.getString(R.string.notification_saved)
                },
            )
        }
        return views
    }

    private val BUTTONS = listOf(
        R.id.question_b1,
        R.id.question_b2,
        R.id.question_b3,
        R.id.question_b4,
        R.id.question_b5,
        R.id.question_b6,
        R.id.question_b7,
        R.id.question_b8,
        R.id.question_b9,
        R.id.question_b10,
    )

    /** Rows longer than this get the dense shapes and smaller numbers. */
    private const val DENSE_FROM = 5

    /** The answers not chosen, faded once one is. */
    private const val OTHERS_ALPHA = 0.38f
}
