package app.pacinglab.feature.trends

import androidx.annotation.StringRes
import app.pacinglab.core.data.repository.CardSpec
import app.pacinglab.core.ui.R as UiR

/**
 * The cards of Training > Load, in the default order, each with the word of its jump chip. [todayId] is the Today
 * card each can be pinned as.
 *
 * @property hero the card that answers "how is my training going": the training status.
 */
enum class LoadCardKind(
    val id: String,
    @param:StringRes val title: Int,
    @param:StringRes val description: Int,
    @param:StringRes val jump: Int,
    val todayId: String? = null,
    val hero: Boolean = false,
) {
    STATUS("status", UiR.string.training_status_title, R.string.card_status, R.string.load_jump_status, "training_status", hero = true),
    FORM("form", R.string.form_title, R.string.card_form, R.string.load_jump_form, "form"),
    WEEKS("weeks", R.string.weeks_title, R.string.card_weeks, R.string.load_jump_weeks, "week"),
    RUNNING("running", R.string.running_title, R.string.card_running, R.string.load_jump_running),
    FOCUS("focus", R.string.focus_title, R.string.card_focus, R.string.load_jump_focus),
    ;

    companion object {
        val catalog: List<CardSpec> = entries.map { CardSpec(it.id) }

        fun of(id: String): LoadCardKind? = entries.firstOrNull { it.id == id }
    }
}
