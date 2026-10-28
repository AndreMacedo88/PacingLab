package app.pacinglab.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.pacinglab.core.designsystem.R
import app.pacinglab.core.designsystem.icon.PacingLabIcons
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/** A card as the dashboard editor lists it. */
@Immutable
data class DashboardEntry(val id: String, val title: String, val description: String?, val visible: Boolean)

/**
 * Arranges a dashboard: drag a card by its handle to move it, tap the eye to hide or show it. Hidden
 * cards keep their place, dimmed, so that showing one again puts it back where it was. Every change is
 * reported with the whole list, in order. Screen readers get "move up" and "move down" actions.
 */
@Composable
fun DashboardEditor(
    entries: List<DashboardEntry>,
    onChange: (List<DashboardEntry>) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
) {
    var items by remember(entries) { mutableStateOf(entries) }
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        // The hint above the cards shifts list indices, so cards are found by key.
        val fromIndex = items.indexOfFirst { it.id == from.key }
        val toIndex = items.indexOfFirst { it.id == to.key }
        if (fromIndex >= 0 && toIndex >= 0) items = items.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
    }
    fun move(from: Int, to: Int) {
        if (to !in items.indices) return
        items = items.toMutableList().apply { add(to, removeAt(from)) }
        onChange(items)
    }
    val moveUp = stringResource(R.string.dashboard_move_up)
    val moveDown = stringResource(R.string.dashboard_move_down)
    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "hint") {
            Text(
                stringResource(R.string.dashboard_edit_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        items(items, key = { it.id }) { entry ->
            ReorderableItem(reorderState, key = entry.id) { dragging ->
                val index = items.indexOf(entry)
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (dragging) {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        },
                    ),
                    modifier = Modifier.fillMaxWidth().semantics {
                        customActions = listOf(
                            CustomAccessibilityAction(moveUp) { move(index, index - 1).let { true } },
                            CustomAccessibilityAction(moveDown) { move(index, index + 1).let { true } },
                        )
                    },
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            onClick = {},
                            modifier = Modifier.draggableHandle(onDragStopped = { onChange(items) }),
                        ) {
                            Icon(painterResource(PacingLabIcons.DragHandle), contentDescription = stringResource(R.string.dashboard_drag))
                        }
                        Column(Modifier.weight(1f).alpha(if (entry.visible) 1f else HIDDEN_ALPHA).padding(vertical = 8.dp)) {
                            Text(entry.title, style = MaterialTheme.typography.bodyLarge)
                            entry.description?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        IconButton(onClick = {
                            items = items.map { if (it.id == entry.id) it.copy(visible = !it.visible) else it }
                            onChange(items)
                        }) {
                            Icon(
                                painterResource(if (entry.visible) PacingLabIcons.Visible else PacingLabIcons.Hidden),
                                contentDescription = stringResource(
                                    if (entry.visible) R.string.dashboard_hide else R.string.dashboard_show,
                                    entry.title,
                                ),
                            )
                        }
                    }
                }
            }
        }
        item(key = "reset") {
            TextButton(onClick = onReset, modifier = Modifier.padding(top = 8.dp)) {
                Icon(painterResource(PacingLabIcons.Reset), contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text(stringResource(R.string.dashboard_reset))
            }
        }
    }
}

private const val HIDDEN_ALPHA = 0.45f
