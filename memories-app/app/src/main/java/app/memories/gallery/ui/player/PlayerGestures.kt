package app.memories.gallery.ui.player

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.geometry.Offset
import kotlin.math.abs

private enum class DragMode { NONE, SEEK, VERTICAL, ZOOM }

/**
 * MX-Player style gestures:
 *  - horizontal drag anywhere → seek
 *  - vertical drag on the left half → brightness, right half → volume
 *  - two-finger pinch → zoom the video
 * Taps / double-taps are handled separately by detectTapGestures.
 */
internal suspend fun PointerInputScope.detectPlayerDragGestures(
    onSeekStart: () -> Unit,
    onSeek: (dx: Float) -> Unit,
    onSeekEnd: () -> Unit,
    onVerticalStart: (leftSide: Boolean) -> Unit,
    onVertical: (leftSide: Boolean, dy: Float) -> Unit,
    onVerticalEnd: () -> Unit,
    onZoom: (factor: Float) -> Unit,
    onZoomEnd: () -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val slop = viewConfiguration.touchSlop
        val leftSide = down.position.x < size.width / 2f
        var mode = DragMode.NONE
        var total = Offset.Zero

        while (true) {
            val event = awaitPointerEvent()
            if (event.changes.none { it.pressed }) break

            val pressed = event.changes.count { it.pressed }
            if (pressed >= 2 && (mode == DragMode.NONE || mode == DragMode.ZOOM)) {
                mode = DragMode.ZOOM
                val zoom = event.calculateZoom()
                if (zoom != 1f) onZoom(zoom)
                event.changes.forEach { it.consume() }
                continue
            }

            val change = event.changes.firstOrNull { it.id == down.id } ?: event.changes.first()
            val delta = change.positionChange()
            when (mode) {
                DragMode.NONE -> {
                    total += delta
                    if (total.getDistance() > slop) {
                        mode = if (abs(total.x) > abs(total.y)) DragMode.SEEK else DragMode.VERTICAL
                        if (mode == DragMode.SEEK) onSeekStart() else onVerticalStart(leftSide)
                        change.consume()
                    }
                }
                DragMode.SEEK -> {
                    onSeek(delta.x)
                    change.consume()
                }
                DragMode.VERTICAL -> {
                    onVertical(leftSide, delta.y)
                    change.consume()
                }
                DragMode.ZOOM -> change.consume()
            }
        }

        when (mode) {
            DragMode.SEEK -> onSeekEnd()
            DragMode.VERTICAL -> onVerticalEnd()
            DragMode.ZOOM -> onZoomEnd()
            DragMode.NONE -> Unit
        }
    }
}
