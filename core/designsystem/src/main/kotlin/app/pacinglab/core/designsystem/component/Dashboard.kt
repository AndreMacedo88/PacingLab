package app.pacinglab.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.pacinglab.core.designsystem.R
import app.pacinglab.core.designsystem.icon.PacingLabIcons

/**
 * What the app frame offers every dashboard: the search, and pinning cards of other screens to Today.
 *
 * @property onToday the ids of the Today cards shown now.
 * @property onPin shows ([pinned] true) or hides a Today card by its id.
 */
@Immutable
data class DashboardHost(
    val onSearch: () -> Unit = {},
    val onToday: Set<String> = emptySet(),
    val onPin: (id: String, pinned: Boolean) -> Unit = { _, _ -> },
)

/** The app frame's [DashboardHost]; null in previews and tests, where search and pinning do nothing. */
val LocalDashboardHost = compositionLocalOf<DashboardHost?> { null }

/**
 * The frame of a top-level screen: an app bar that slides away while the content scrolls down and returns on
 * the way up, with the search, the pencil that arranges the cards and the settings; under it, [tabs] and
 * [pinned] (jump chips, a night selector) that stay in place; then the content, refreshed by pulling it.
 * While [editing], [editor] takes the content's place and the app bar keeps only the pencil's "Done".
 *
 * @param onEditingChange null for a screen without arrangeable cards.
 * @param actions further actions before the search, such as a filter.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScaffold(
    title: String,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    editing: Boolean = false,
    onEditingChange: ((Boolean) -> Unit)? = null,
    editTitle: String = stringResource(R.string.dashboard_edit),
    actions: @Composable RowScope.() -> Unit = {},
    tabs: (@Composable () -> Unit)? = null,
    pinned: (@Composable () -> Unit)? = null,
    editor: @Composable (Modifier) -> Unit = {},
    refreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val surface = MaterialTheme.colorScheme.surface
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        if (editing) {
                            Text(editTitle)
                        } else {
                            Column {
                                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (subtitle != null) {
                                    Text(
                                        text = subtitle,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    },
                    actions = {
                        if (!editing) {
                            actions()
                            SearchAction()
                        }
                        if (onEditingChange != null) DashboardEditAction(editing, onEditingChange)
                        if (!editing) SettingsAction(onOpenSettings)
                    },
                    // The tabs under the bar keep the page's colour, so the bar does too when content passes under it.
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = surface, scrolledContainerColor = surface),
                    scrollBehavior = scrollBehavior,
                )
                if (!editing) {
                    tabs?.invoke()
                    pinned?.invoke()
                }
            }
        },
    ) { padding ->
        if (editing) {
            editor(Modifier.fillMaxSize().padding(padding))
        } else {
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize().padding(padding),
                content = content,
            )
        }
    }
}

/** The magnifier that opens the search over every card, page and setting. */
@Composable
fun SearchAction(modifier: Modifier = Modifier) {
    val host = LocalDashboardHost.current
    IconButton(onClick = { host?.onSearch?.invoke() }, modifier = modifier) {
        Icon(painterResource(PacingLabIcons.Search), contentDescription = stringResource(R.string.search_action))
    }
}

/** The two or more views of one screen, such as Sessions and Load, as tabs under the app bar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubTabs(titles: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    PrimaryTabRow(selectedTabIndex = selected, modifier = modifier, containerColor = MaterialTheme.colorScheme.surface) {
        titles.forEachIndexed { i, title ->
            Tab(selected = i == selected, onClick = { onSelect(i) }, text = { Text(title, maxLines = 1) })
        }
    }
}

/** Chips that scroll a long screen to one of its parts; they stay under the app bar. */
@Composable
fun JumpChips(labels: List<String>, onJump: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        labels.forEachIndexed { i, label ->
            SuggestionChip(onClick = { onJump(i) }, label = { Text(label, maxLines = 1) })
        }
    }
}

/** The heading over a group of cards, which screen readers can jump between. */
@Composable
fun SectionHeading(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(top = 12.dp)) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
    }
}

/**
 * The colours of a dashboard card. The [hero], the card that answers the screen's question, sits on a container
 * tinted with the palette's primary colour so that the eye lands there first; every other card is plain.
 */
@Composable
fun dashboardCardColors(hero: Boolean = false): CardColors =
    CardDefaults.cardColors(containerColor = if (hero) heroContainer() else MaterialTheme.colorScheme.surfaceContainerLow)

/** The thin outline of the hero card; none for the others. */
@Composable
fun heroBorder(hero: Boolean = true): BorderStroke? =
    if (hero) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = HERO_BORDER_ALPHA)) else null

@Composable
private fun heroContainer(): Color =
    MaterialTheme.colorScheme.primary.copy(alpha = HERO_TINT).compositeOver(MaterialTheme.colorScheme.surfaceContainerLow)

private const val HERO_TINT = 0.12f
private const val HERO_BORDER_ALPHA = 0.32f
