package app.pacinglab.feature.sleep

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pacinglab.core.analytics.recovery.Night
import app.pacinglab.core.analytics.recovery.NightlyMetric
import app.pacinglab.core.analytics.recovery.SleepRating
import app.pacinglab.core.data.repository.PlacedCard
import app.pacinglab.core.designsystem.component.CardHeader
import app.pacinglab.core.designsystem.component.DashboardEditAction
import app.pacinglab.core.designsystem.component.DashboardEditor
import app.pacinglab.core.designsystem.component.DashboardEntry
import app.pacinglab.core.designsystem.component.SettingsAction
import app.pacinglab.core.designsystem.icon.PacingLabIcons
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun SleepRoute(
    initialDate: String?,
    onOpenSettings: () -> Unit,
    onOpenMetric: (NightlyMetric) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SleepViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    SleepScreen(
        state = state,
        initialDate = initialDate?.let(LocalDate::parse),
        onOpenSettings = onOpenSettings,
        modifier = modifier,
        refreshing = refreshing,
        onRefresh = viewModel::refresh,
        onOpenMetric = onOpenMetric,
        onArrange = viewModel::arrange,
        onResetArrangement = viewModel::resetArrangement,
        onRate = viewModel::rate,
    )
}

/**
 * One night in depth, laid out like the sleep screen of Google Health: the night's score and the two
 * weeks' sleep health at the top with time asleep and sleep debt, the night's stages, both scores'
 * parts, sleep quality against published ranges, heart rate through the night, the night's vitals and
 * key metrics over recent nights. Arrows step from night to night. A top-level screen: settings sit at
 * the right of the app bar, beside the pencil that switches to arranging the cards.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepScreen(
    state: SleepUiState,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    initialDate: LocalDate? = null,
    refreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    onOpenMetric: (NightlyMetric) -> Unit = {},
    onArrange: (List<PlacedCard>) -> Unit = {},
    onResetArrangement: () -> Unit = {},
    initiallyEditing: Boolean = false,
    onRate: (LocalDate, Int) -> Unit = { _, _ -> },
) {
    val locale = LocalConfiguration.current.locales[0]
    var editing by rememberSaveable { mutableStateOf(initiallyEditing) }
    BackHandler(enabled = editing) { editing = false }
    val loaded = state as? SleepUiState.Loaded
    val nights = loaded?.nights.orEmpty()
    var selectedDay by rememberSaveable { mutableLongStateOf(initialDate?.toEpochDay() ?: Long.MAX_VALUE) }
    val index = nights.indexOfLast { it.date.toEpochDay() <= selectedDay }.takeIf { it >= 0 } ?: nights.lastIndex
    val night = nights.getOrNull(index)
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (editing) R.string.sleep_edit_title else R.string.sleep_title)) },
                actions = {
                    if (loaded != null) DashboardEditAction(editing, onEditingChange = { editing = it })
                    if (!editing) SettingsAction(onOpenSettings)
                },
            )
        },
    ) { scaffoldPadding ->
        if (editing && loaded != null) {
            DashboardEditor(
                entries = loaded.cards.mapNotNull { placed ->
                    SleepCardKind.of(placed.id)?.let {
                        DashboardEntry(it.id, stringResource(it.title), stringResource(it.description), placed.visible)
                    }
                },
                onChange = { changed -> onArrange(changed.map { PlacedCard(it.id, it.visible) }) },
                onReset = onResetArrangement,
                modifier = Modifier.fillMaxSize().padding(scaffoldPadding),
            )
            return@Scaffold
        }
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize().padding(scaffoldPadding),
        ) {
            when {
                state is SleepUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                loaded == null || night == null -> Text(
                    text = stringResource(R.string.sleep_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(24.dp),
                )

                else -> {
                    val score = loaded.scores.getValue(night.date)
                    val need = loaded.needs.getValue(night.date)
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item(key = "night") {
                            NightSelector(
                                night = night,
                                locale = locale,
                                onPrevious = nights.getOrNull(index - 1)?.let { { selectedDay = it.date.toEpochDay() } },
                                onNext = nights.getOrNull(index + 1)?.let { { selectedDay = it.date.toEpochDay() } },
                            )
                        }
                        for (card in loaded.cards.filter { it.visible }.mapNotNull { SleepCardKind.of(it.id) }) {
                            when (card) {
                                SleepCardKind.TILES -> item(key = card.id) { TopTiles(loaded, night, locale) }

                                SleepCardKind.NIGHT -> item(key = card.id) {
                                    NightCard(night, score, locale, rested = loaded.rested[night.date])
                                }

                                SleepCardKind.SCORE -> item(key = card.id) { ScoreCard(night, score, need) }

                                SleepCardKind.HEALTH -> loaded.health[night.date]?.let { health ->
                                    item(key = card.id) { HealthCard(health, need) }
                                }

                                SleepCardKind.QUALITY -> if (night.continuity != null) item(key = card.id) { QualityCard(night, score) }

                                SleepCardKind.HEART_RATE -> if (night.sleepingHeartRate != null) {
                                    item(key = card.id) {
                                        HeartRateCard(night, locale, onClick = { onOpenMetric(NightlyMetric.SLEEPING_HR) })
                                    }
                                }

                                SleepCardKind.RECOVERY -> item(key = card.id) { VitalsCard(nights, night, onOpenMetric) }

                                SleepCardKind.KEY_METRICS -> item(key = card.id) {
                                    KeyMetricsCard(nights, night.date, loaded.scores, loaded.needs, locale)
                                }

                                SleepCardKind.FEELING -> item(key = card.id) {
                                    FeelingCard(
                                        agreement = loaded.feeling,
                                        morning = night.date,
                                        rating = loaded.rested[night.date],
                                        onRate = { onRate(night.date, it) },
                                        today = loaded.today,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NightSelector(night: Night, locale: Locale, onPrevious: (() -> Unit)?, onNext: (() -> Unit)?, modifier: Modifier = Modifier) {
    val date = remember(locale) { DateTimeFormatter.ofPattern("EEEE d MMMM", locale) }
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onPrevious?.invoke() }, enabled = onPrevious != null) {
            Icon(painterResource(PacingLabIcons.ChevronLeft), stringResource(R.string.sleep_previous))
        }
        Text(
            text = date.format(night.date).replaceFirstChar { it.titlecase(locale) },
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { onNext?.invoke() }, enabled = onNext != null) {
            Icon(painterResource(PacingLabIcons.ChevronRight), stringResource(R.string.sleep_next))
        }
    }
}

@Composable
internal fun SleepCard(
    title: String,
    info: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    val body: @Composable () -> Unit = {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CardHeader(title = title, info = info)
            content()
        }
    }
    if (onClick == null) {
        Card(modifier = modifier.fillMaxWidth(), colors = colors) { body() }
    } else {
        Card(onClick = onClick, modifier = modifier.fillMaxWidth(), colors = colors) { body() }
    }
}

internal fun SleepRating.label(): Int = when (this) {
    SleepRating.GOOD -> R.string.sleep_good
    SleepRating.FAIR -> R.string.sleep_fair
    SleepRating.POOR -> R.string.sleep_poor
}

internal fun clock(minuteOfDay: Int): String = "%02d:%02d".format(Locale.ROOT, minuteOfDay / 60, minuteOfDay % 60)

internal const val MINUTE = 60_000.0
internal const val HOUR = 3_600_000.0
internal const val DASH = "–"
internal const val WEEK = 7L
internal const val MONTH = 30L
