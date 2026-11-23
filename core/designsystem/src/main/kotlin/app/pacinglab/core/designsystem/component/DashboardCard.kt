package app.pacinglab.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.pacinglab.core.designsystem.R
import app.pacinglab.core.designsystem.icon.PacingLabIcons
import kotlinx.coroutines.delay

/**
 * What long-pressing a dashboard card offers.
 *
 * @property screen the screen's name, as in "Hide from Body".
 * @property todayId the Today card this card can be pinned as; null when there is none, and on Today itself.
 * @property onHide hides the card from this screen, as the eye in the editor does.
 * @property onArrange opens the screen's editor.
 */
@Immutable
data class CardActions(
    val screen: String,
    val todayId: String? = null,
    val onHide: (() -> Unit)? = null,
    val onArrange: (() -> Unit)? = null,
)

/**
 * A card on a dashboard. Long-pressing it opens a small menu of [actions] at the finger; [highlighted] flashes
 * its outline once, when a link from another screen or the search brought the athlete to it.
 */
@Composable
fun DashboardCard(
    modifier: Modifier = Modifier,
    actions: CardActions? = null,
    highlighted: Boolean = false,
    content: @Composable () -> Unit,
) {
    val press = remember { CardPress() }
    var menuAt by remember { mutableStateOf<Offset?>(null) }
    var height by remember { mutableIntStateOf(0) }
    val haptics = LocalHapticFeedback.current
    val flash = remember { Animatable(0f) }
    LaunchedEffect(highlighted) {
        if (highlighted) {
            flash.animateTo(1f, tween(FLASH_IN_MILLIS))
            flash.animateTo(0f, tween(FLASH_OUT_MILLIS, delayMillis = FLASH_HOLD_MILLIS))
        }
    }
    val outline = MaterialTheme.colorScheme.primary
    val menu = actions != null && (actions.todayId != null || actions.onHide != null || actions.onArrange != null)
    Box(
        modifier
            .onSizeChanged { height = it.height }
            .then(
                if (menu) {
                    // A card inside another card (a tile in a group) keeps its long press from the outer card.
                    Modifier.claimsPress().pointerInput(Unit) {
                        detectCardLongPress(press) { offset ->
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            menuAt = offset
                        }
                    }
                } else {
                    Modifier
                },
            )
            .drawWithContent {
                drawContent()
                val shown = flash.value
                if (shown > 0f) {
                    val radius = CornerRadius(CARD_CORNER.toPx())
                    drawRoundRect(outline.copy(alpha = FLASH_FILL * shown), cornerRadius = radius)
                    drawRoundRect(outline.copy(alpha = shown), cornerRadius = radius, style = Stroke(FLASH_STROKE.toPx()))
                }
            },
        propagateMinConstraints = true,
    ) {
        CompositionLocalProvider(LocalCardPress provides press, content = content)
        if (actions != null) {
            val density = LocalDensity.current
            val at = menuAt ?: Offset.Zero
            DropdownMenu(
                expanded = menuAt != null,
                onDismissRequest = { menuAt = null },
                offset = with(density) { DpOffset(at.x.toDp(), (at.y - height).toDp()) },
            ) {
                CardMenuItems(actions, onDone = { menuAt = null })
            }
        }
    }
}

@Composable
private fun CardMenuItems(actions: CardActions, onDone: () -> Unit, modifier: Modifier = Modifier) = Column(modifier) {
    val host = LocalDashboardHost.current
    actions.todayId?.let { id ->
        val pinned = host?.onToday?.contains(id) == true
        DropdownMenuItem(
            text = { Text(stringResource(if (pinned) R.string.card_menu_unpin else R.string.card_menu_pin)) },
            onClick = {
                onDone()
                host?.onPin?.invoke(id, !pinned)
            },
            leadingIcon = { Icon(painterResource(PacingLabIcons.Pin), contentDescription = null) },
        )
    }
    actions.onHide?.let { hide ->
        DropdownMenuItem(
            text = { Text(stringResource(R.string.card_menu_hide, actions.screen)) },
            onClick = {
                onDone()
                hide()
            },
            leadingIcon = { Icon(painterResource(PacingLabIcons.Hidden), contentDescription = null) },
        )
    }
    actions.onArrange?.let { arrange ->
        DropdownMenuItem(
            text = { Text(stringResource(R.string.dashboard_edit)) },
            onClick = {
                onDone()
                arrange()
            },
            leadingIcon = { Icon(painterResource(PacingLabIcons.DragHandle), contentDescription = null) },
        )
    }
}

/**
 * A long press that stays in place: no scroll or drag took the finger and nothing inside claimed it
 * ([claimsPress]). The rest of that gesture is then kept from the card, so that lifting the finger does not
 * count as a tap.
 */
private suspend fun PointerInputScope.detectCardLongPress(press: CardPress, onLongPress: (Offset) -> Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val ended = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            var over = false
            while (!over) {
                val change = awaitPointerEvent(PointerEventPass.Final).changes.firstOrNull { it.id == down.id }
                over = change == null ||
                    !change.pressed ||
                    (change.isConsumed && change.positionChanged()) ||
                    (change.position - down.position).getDistance() > viewConfiguration.touchSlop
            }
            true
        }
        if (ended == null && !press.claimed) {
            onLongPress(down.position)
            var event: PointerEvent
            do {
                event = awaitPointerEvent(PointerEventPass.Initial)
                event.changes.forEach { it.consume() }
            } while (event.changes.any { it.pressed })
        }
        press.claimed = false
    }
}

/** Whether something inside a card took the current press for itself. */
internal class CardPress {
    var claimed = false
}

internal val LocalCardPress = staticCompositionLocalOf<CardPress?> { null }

/**
 * Keeps a finger held on this element (a chart read by touch) for the element, so that holding it still reads
 * the chart rather than opening the card's menu.
 */
fun Modifier.claimsPress(): Modifier = this then ClaimsPressElement

private data object ClaimsPressElement : ModifierNodeElement<ClaimsPressNode>() {
    override fun create() = ClaimsPressNode()

    override fun update(node: ClaimsPressNode) = Unit

    override fun InspectorInfo.inspectableProperties() {
        name = "claimsPress"
    }
}

private class ClaimsPressNode :
    Modifier.Node(),
    PointerInputModifierNode,
    CompositionLocalConsumerModifierNode {
    override fun onPointerEvent(pointerEvent: PointerEvent, pass: PointerEventPass, bounds: IntSize) {
        if (pass == PointerEventPass.Initial && pointerEvent.changes.any { it.changedToDownIgnoreConsumed() }) {
            currentValueOf(LocalCardPress)?.claimed = true
        }
    }

    override fun onCancelPointerInput() = Unit
}

/**
 * Scrolls [listState] once to the card a link asked for ([focus], a card id), when the screen is [ready], and
 * returns that card's id while it should flash. [indexOf] finds a card's row in the list; a card that is not
 * there (hidden, or without data) is skipped.
 */
@Composable
fun rememberFocusedCard(focus: String?, listState: LazyListState, ready: Boolean, indexOf: (String) -> Int?): String? {
    var handled by rememberSaveable(focus) { mutableStateOf(false) }
    var flashing by remember { mutableStateOf<String?>(null) }
    val find by rememberUpdatedState(indexOf)
    LaunchedEffect(focus, ready) {
        if (focus == null || handled || !ready) return@LaunchedEffect
        handled = true
        val index = find(focus) ?: return@LaunchedEffect
        listState.animateScrollToItem(index)
        flashing = focus
        delay(FLASH_IN_MILLIS + FLASH_HOLD_MILLIS + FLASH_OUT_MILLIS.toLong())
        flashing = null
    }
    return flashing
}

private val CARD_CORNER = 12.dp
private val FLASH_STROKE = 2.dp
private const val FLASH_FILL = 0.14f
private const val FLASH_IN_MILLIS = 200
private const val FLASH_HOLD_MILLIS = 600
private const val FLASH_OUT_MILLIS = 900
