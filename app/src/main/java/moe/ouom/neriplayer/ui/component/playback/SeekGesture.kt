package moe.ouom.neriplayer.ui.component.playback

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs

/**
 * 进度条手势：既支持按住拖动，也支持单击直接跳转。
 *
 * 与 [androidx.compose.foundation.gestures.detectDragGestures] 不同，按下后不移动直接抬起
 * 也会按抬起位置提交一次进度变更，因此进度条可以“单击调整进度”。
 * 未超过触摸阈值的横向拖动判定为单击；纵向拖动仍交给父级（列表滚动）。
 *
 * 手势协程不随重组重启，回调始终取最新值，避免播放进度刷新时打断正在进行的拖动。
 */
@Composable
internal fun Modifier.seekGestureInput(
    enabled: Boolean,
    onValueChangeStarted: (Float) -> Unit,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    onValueChangeCanceled: () -> Unit
): Modifier {
    val latestOnValueChangeStarted by rememberUpdatedState(onValueChangeStarted)
    val latestOnValueChange by rememberUpdatedState(onValueChange)
    val latestOnValueChangeFinished by rememberUpdatedState(onValueChangeFinished)
    val latestOnValueChangeCanceled by rememberUpdatedState(onValueChangeCanceled)
    if (!enabled) return this
    return pointerInput(Unit) {
        detectSeekGestures(
            onValueChangeStarted = { latestOnValueChangeStarted(it) },
            onValueChange = { latestOnValueChange(it) },
            onValueChangeFinished = { latestOnValueChangeFinished() },
            onValueChangeCanceled = { latestOnValueChangeCanceled() }
        )
    }
}

internal suspend fun PointerInputScope.detectSeekGestures(
    onValueChangeStarted: (Float) -> Unit,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    onValueChangeCanceled: () -> Unit
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val width = size.width.toFloat()
        if (width <= 0f || !width.isFinite()) return@awaitEachGesture
        val touchSlop = viewConfiguration.touchSlop
        var dragStarted = false
        var canceled = false
        var movedBeyondSlop = false
        var lastFraction = resolveSeekFraction(down.position.x, width)

        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id }
            if (change == null) {
                canceled = true
                break
            }
            lastFraction = resolveSeekFraction(change.position.x, width)
            if (!change.pressed) {
                change.consume()
                break
            }
            if (dragStarted) {
                change.consume()
                onValueChange(lastFraction)
                continue
            }
            val deltaX = change.position.x - down.position.x
            val deltaY = change.position.y - down.position.y
            when {
                shouldBeginSeekDrag(deltaX, deltaY, touchSlop) -> {
                    dragStarted = true
                    change.consume()
                    onValueChangeStarted(lastFraction)
                    onValueChange(lastFraction)
                }

                abs(deltaX) > touchSlop || abs(deltaY) > touchSlop -> {
                    // 纵向/斜向拖动：不是单击，也不接管进度，交回父级
                    movedBeyondSlop = true
                }

                change.isConsumed -> {
                    // 父级手势（滚动等）已接管，本次不再提交进度
                    canceled = true
                }

                else -> Unit
            }
            if (canceled) break
        }

        when {
            canceled -> if (dragStarted) onValueChangeCanceled()
            dragStarted -> {
                onValueChange(lastFraction)
                onValueChangeFinished()
            }

            movedBeyondSlop -> Unit
            else -> {
                // 单击：按下与抬起之间没有超过触摸阈值
                onValueChangeStarted(lastFraction)
                onValueChange(lastFraction)
                onValueChangeFinished()
            }
        }
    }
}

/**
 * 把触点横坐标换算成 0..1 的进度；宽度非法或坐标非有限值时返回 0。
 */
internal fun resolveSeekFraction(positionX: Float, width: Float): Float {
    if (!width.isFinite() || width <= 0f) return 0f
    if (!positionX.isFinite()) return 0f
    return (positionX / width).coerceIn(0f, 1f)
}

/**
 * 只有横向位移超过触摸阈值、且横向意图强于纵向时才认为进入拖动，避免抢走纵向滚动。
 */
internal fun shouldBeginSeekDrag(deltaX: Float, deltaY: Float, touchSlop: Float): Boolean {
    if (!deltaX.isFinite() || !deltaY.isFinite()) return false
    val horizontal = abs(deltaX)
    return horizontal > touchSlop && horizontal >= abs(deltaY)
}
