package app.pacinglab.core.designsystem.component

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier

/**
 * What a shared element needs from the app frame: the scope that moves elements between screens and the scope
 * animating the screen the element sits on.
 */
@Immutable
class SharedScreens(val transition: SharedTransitionScope, val screen: AnimatedVisibilityScope)

/** The frame's [SharedScreens] for the screen being composed; null in previews and tests, where nothing moves. */
val LocalSharedScreens = compositionLocalOf<SharedScreens?> { null }

/** The keys that link a summary to the page it opens. */
object SharedKeys {
    const val READINESS = "readiness"
    const val HEART_RATE_DAY = "heart-rate-day"

    fun metric(name: String): String = "metric-$name"
}

/**
 * Content whose bounds grow into, or shrink back from, the element with the same [key] on the next screen: a
 * tile opening its page grows into it, and the page shrinks back into the tile on the way back.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SharedBounds(key: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val shared = LocalSharedScreens.current
    val bounds = if (shared == null) {
        Modifier
    } else {
        with(shared.transition) { Modifier.sharedBounds(rememberSharedContentState(key), animatedVisibilityScope = shared.screen) }
    }
    Box(modifier.then(bounds), propagateMinConstraints = true) { content() }
}
