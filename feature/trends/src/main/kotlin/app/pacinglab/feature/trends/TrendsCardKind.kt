package app.pacinglab.feature.trends

import androidx.annotation.StringRes
import app.pacinglab.core.data.repository.CardSpec
import app.pacinglab.core.designsystem.R as DesignR

/** The cards of the trends screen, in the default order. */
enum class TrendsCardKind(val id: String, @param:StringRes val title: Int, @param:StringRes val description: Int) {
    STATUS("status", DesignR.string.training_status_title, R.string.card_status),
    FORM("form", R.string.form_title, R.string.card_form),
    RECOVERY("recovery", R.string.recovery_title, R.string.card_recovery),
    WEEKS("weeks", R.string.weeks_title, R.string.card_weeks),
    FOCUS("focus", R.string.focus_title, R.string.card_focus),
    ;

    companion object {
        val catalog: List<CardSpec> = entries.map { CardSpec(it.id) }

        fun of(id: String): TrendsCardKind? = entries.firstOrNull { it.id == id }
    }
}
