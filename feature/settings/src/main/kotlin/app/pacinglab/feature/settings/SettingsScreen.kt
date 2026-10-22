package app.pacinglab.feature.settings

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pacinglab.core.data.repository.DarkMode
import app.pacinglab.core.data.repository.ThemeChoice
import app.pacinglab.core.data.repository.UserPreferences
import app.pacinglab.core.designsystem.format.Formats
import app.pacinglab.core.designsystem.icon.PacingLabIcons
import app.pacinglab.core.designsystem.theme.ThemePalette
import app.pacinglab.core.designsystem.theme.swatch
import java.time.Duration

@Composable
fun SettingsRoute(
    onOpenPhysiology: () -> Unit,
    onOpenDataSources: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    SettingsScreen(
        preferences = preferences,
        actions = SettingsActions(
            onTheme = viewModel::setTheme,
            onDarkMode = viewModel::setDarkMode,
            onStrengthInLoad = viewModel::setStrengthInLoad,
            onMergeGap = viewModel::setMergeGap,
            onSpeedByDefault = viewModel::setSpeedByDefault,
            onSleepNeed = viewModel::setSleepNeed,
        ),
        onOpenPhysiology = onOpenPhysiology,
        onOpenDataSources = onOpenDataSources,
        onBack = onBack,
        modifier = modifier,
    )
}

/** What the settings change; one bundle so that the screen keeps a short parameter list. */
class SettingsActions(
    val onTheme: (ThemeChoice) -> Unit = {},
    val onDarkMode: (DarkMode) -> Unit = {},
    val onStrengthInLoad: (Boolean) -> Unit = {},
    val onMergeGap: (Duration) -> Unit = {},
    val onSpeedByDefault: (Boolean) -> Unit = {},
    val onSleepNeed: (Duration?) -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    preferences: UserPreferences,
    actions: SettingsActions,
    onOpenPhysiology: () -> Unit,
    onOpenDataSources: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(painterResource(PacingLabIcons.ArrowBack), stringResource(R.string.navigate_back)) }
                },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            item { SectionLabel(stringResource(R.string.settings_section_appearance)) }
            item { ThemeRow(preferences.theme, actions.onTheme) }
            item { DarkModeRow(preferences.darkMode, actions.onDarkMode) }
            item { SectionLabel(stringResource(R.string.settings_section_training)) }
            item {
                SettingsItem(
                    title = stringResource(R.string.physiology_title),
                    summary = stringResource(R.string.settings_physiology_summary),
                    icon = PacingLabIcons.Heart,
                    onClick = onOpenPhysiology,
                )
            }
            item {
                SwitchItem(
                    title = stringResource(R.string.settings_strength_in_load),
                    summary = stringResource(R.string.settings_strength_in_load_summary),
                    checked = preferences.strengthInLoad,
                    onChange = actions.onStrengthInLoad,
                )
            }
            item {
                SwitchItem(
                    title = stringResource(R.string.settings_speed_by_default),
                    summary = stringResource(R.string.settings_speed_by_default_summary),
                    checked = preferences.speedByDefault,
                    onChange = actions.onSpeedByDefault,
                )
            }
            item {
                ChoiceItem(
                    title = stringResource(R.string.settings_merge_gap),
                    summary = stringResource(R.string.settings_merge_gap_summary),
                    options = MERGE_GAPS.map { gap ->
                        (
                            if (gap.isZero) {
                                stringResource(
                                    R.string.settings_merge_gap_off,
                                )
                            } else {
                                stringResource(R.string.settings_minutes, gap.toMinutes())
                            }
                            ) to
                            gap
                    },
                    selected = preferences.sessionMergeGap,
                    onSelect = actions.onMergeGap,
                )
            }
            item { SectionLabel(stringResource(R.string.settings_section_sleep)) }
            item {
                ChoiceItem(
                    title = stringResource(R.string.settings_sleep_need),
                    summary = stringResource(R.string.settings_sleep_need_summary),
                    options = listOf(stringResource(R.string.settings_sleep_need_automatic) to null) +
                        SLEEP_NEEDS.map { Formats.hoursMinutes(it.toMillis()) to it },
                    selected = preferences.sleepNeed,
                    onSelect = actions.onSleepNeed,
                )
            }
            item { SectionLabel(stringResource(R.string.settings_section_data)) }
            item {
                SettingsItem(
                    title = stringResource(R.string.data_sources_title),
                    summary = stringResource(R.string.settings_data_sources_summary),
                    icon = PacingLabIcons.Database,
                    onClick = onOpenDataSources,
                )
            }
        }
    }
}

@Composable
private fun ThemeRow(selected: ThemeChoice, onSelect: (ThemeChoice) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.bodyLarge)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            ThemeChoice.entries.forEach { choice ->
                val chosen = choice == selected
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.selectable(selected = chosen, role = Role.RadioButton, onClick = { onSelect(choice) }),
                ) {
                    Box(
                        Modifier
                            .size(48.dp)
                            .border(
                                if (chosen) 3.dp else 1.dp,
                                if (chosen) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
                                CircleShape,
                            )
                            .padding(5.dp)
                            .background(ThemePalette.valueOf(choice.name).swatch(), CircleShape),
                    )
                    Text(
                        text = stringResource(choice.label()),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (chosen) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DarkModeRow(selected: DarkMode, onSelect: (DarkMode) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.settings_dark_mode), style = MaterialTheme.typography.bodyLarge)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            DarkMode.entries.forEachIndexed { i, mode ->
                SegmentedButton(
                    selected = mode == selected,
                    onClick = { onSelect(mode) },
                    shape = SegmentedButtonDefaults.itemShape(i, DarkMode.entries.size),
                ) { Text(stringResource(mode.label())) }
            }
        }
    }
}

@Composable
private fun SwitchItem(title: String, summary: String, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        modifier = modifier.selectable(selected = checked, role = Role.Switch, onClick = { onChange(!checked) }),
    )
}

/** A setting with a few values, shown as chips under its title. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChoiceItem(
    title: String,
    summary: String,
    options: List<Pair<String, T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((label, value) in options) {
                FilterChip(selected = value == selected, onClick = { onSelect(value) }, label = { Text(label) })
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun SettingsItem(title: String, summary: String, @DrawableRes icon: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        leadingContent = { Icon(painterResource(icon), contentDescription = null) },
        trailingContent = { Icon(painterResource(PacingLabIcons.ChevronRight), contentDescription = null) },
        modifier = modifier.clickable(onClick = onClick),
    )
}

private fun ThemeChoice.label(): Int = when (this) {
    ThemeChoice.INDIGO -> R.string.theme_indigo
    ThemeChoice.AMBER -> R.string.theme_amber
    ThemeChoice.GREEN -> R.string.theme_green
    ThemeChoice.BLUE -> R.string.theme_blue
}

private fun DarkMode.label(): Int = when (this) {
    DarkMode.SYSTEM -> R.string.dark_mode_system
    DarkMode.LIGHT -> R.string.dark_mode_light
    DarkMode.DARK -> R.string.dark_mode_dark
}

private val MERGE_GAPS = listOf(0L, 2L, 5L, 10L, 15L).map { Duration.ofMinutes(it) }
private val SLEEP_NEEDS = listOf(420L, 450L, 480L, 510L, 540L).map { Duration.ofMinutes(it) }
