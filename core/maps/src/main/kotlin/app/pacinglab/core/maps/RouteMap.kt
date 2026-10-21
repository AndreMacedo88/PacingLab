package app.pacinglab.core.maps

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pacinglab.core.model.Route

/**
 * A small static map of [routes]: the basemap from [LocalMapSnapshots] when one is available, the
 * routes drawn on top, the start marked by a filled dot and the end by a ring.
 */
@Composable
fun RouteMap(routes: List<Route>, contentDescription: String, modifier: Modifier = Modifier, height: Dp = 180.dp) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .semantics {
                this.contentDescription = contentDescription
                role = Role.Image
            },
    ) {
        val frame = remember(routes, maxWidth, maxHeight) { MapFrame.fit(routes, maxWidth.value, maxHeight.value, PADDING_DP) }
            ?: return@BoxWithConstraints
        val density = LocalDensity.current.density
        val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
        val snapshots = LocalMapSnapshots.current
        val basemap by produceState<ImageBitmap?>(null, frame, density, dark, snapshots) {
            value = snapshots?.snapshot(frame, density, dark)
        }
        basemap?.let { Image(it, contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.matchParentSize()) }
        val line = MaterialTheme.colorScheme.primary
        val casing = MaterialTheme.colorScheme.surface
        Spacer(
            Modifier
                .matchParentSize()
                .drawWithCache {
                    val paths = routes.filterNot { it.isEmpty() }.map { route ->
                        Path().apply {
                            for (i in 0 until route.size) {
                                val x = frame.x(route.longitudes[i]) * density
                                val y = frame.y(route.latitudes[i]) * density
                                if (i == 0) moveTo(x, y) else lineTo(x, y)
                            }
                        }
                    }
                    val first = routes.first { !it.isEmpty() }
                    val last = routes.last { !it.isEmpty() }
                    val start = Offset(frame.x(first.longitudes[0]) * density, frame.y(first.latitudes[0]) * density)
                    val end = Offset(frame.x(last.longitudes[last.size - 1]) * density, frame.y(last.latitudes[last.size - 1]) * density)
                    val casingStroke = Stroke(LINE_WIDTH.toPx() + 2 * CASING_WIDTH.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    val lineStroke = Stroke(LINE_WIDTH.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    onDrawBehind {
                        paths.forEach { drawPath(it, casing, style = casingStroke) }
                        paths.forEach { drawPath(it, line, style = lineStroke) }
                        marker(end, filled = false, line, casing)
                        marker(start, filled = true, line, casing)
                    }
                },
        )
        if (basemap != null) {
            Text(
                text = stringResource(R.string.map_attribution),
                fontSize = 9.sp,
                lineHeight = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.75f), RoundedCornerShape(topStart = 4.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }
    }
}

private fun DrawScope.marker(center: Offset, filled: Boolean, line: Color, casing: Color) {
    val radius = MARKER_RADIUS.toPx()
    drawCircle(casing, radius + CASING_WIDTH.toPx(), center)
    drawCircle(line, radius, center)
    if (!filled) drawCircle(casing, radius * 0.5f, center)
}

private const val PADDING_DP = 20f
private val LINE_WIDTH = 3.dp
private val CASING_WIDTH = 1.5.dp
private val MARKER_RADIUS = 5.dp
