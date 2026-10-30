package app.pacinglab.feature.settings

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import app.pacinglab.core.designsystem.icon.PacingLabIcons

/** The groups of settings, each a page of its own under the settings list, in the list's order. */
enum class SettingsSection(@param:StringRes val title: Int, @param:StringRes val summary: Int, @param:DrawableRes val icon: Int) {
    PROFILE(R.string.settings_section_profile, R.string.settings_profile_summary, PacingLabIcons.Person),
    TRAINING(R.string.settings_section_training, R.string.settings_training_summary, PacingLabIcons.Run),
    SLEEP(R.string.settings_section_sleep, R.string.settings_sleep_summary, PacingLabIcons.Bedtime),
    APPEARANCE(R.string.settings_appearance_title, R.string.settings_appearance_summary, PacingLabIcons.Palette),
    WIDGET(R.string.settings_widget_title, R.string.settings_widget_summary, PacingLabIcons.Widgets),
    DATA(R.string.settings_data_title, R.string.settings_data_summary, PacingLabIcons.Database),
}
