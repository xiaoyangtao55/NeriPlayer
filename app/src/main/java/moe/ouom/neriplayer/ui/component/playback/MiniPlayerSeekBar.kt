package moe.ouom.neriplayer.ui.component.playback

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp

internal val MiniPlayerSeekBarTouchHeight = NeriMiniPlayerDefaults.SeekBarHeight

private val MiniPlayerSeekBarStrokeWidth = 3.dp
private val MiniPlayerSeekBarThumbRadius = 3.5.dp

/**
 * 迷你播放条底部的细进度条：可单击跳转，也可按住拖动调整进度。
 *
 * @param progressFraction 当前播放进度（0..1）
 * @param enabled 是否允许调整进度（例如听歌房听众无权控制时禁用）
 * @param onSeek 抬手后提交的进度（0..1），拖动过程中只更新内部预览
 */
@Composable
internal fun MiniPlayerSeekBar(
    progressFraction: Float,
    enabled: Boolean,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    trackColor: Color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.24f)
) {
    var previewFraction by remember { mutableStateOf<Float?>(null) }
    val displayedFraction = normalizeMiniPlayerSeekFraction(previewFraction ?: progressFraction)
    LaunchedEffect(enabled) {
        if (!enabled) previewFraction = null
    }

    val gestureModifier = Modifier.seekGestureInput(
        enabled = enabled,
        onValueChangeStarted = { previewFraction = it },
        onValueChange = { previewFraction = it },
        onValueChangeFinished = {
            val target = previewFraction
            previewFraction = null
            if (target != null) onSeek(normalizeMiniPlayerSeekFraction(target))
        },
        onValueChangeCanceled = { previewFraction = null }
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(MiniPlayerSeekBarTouchHeight)
            .then(gestureModifier)
    ) {
        val centerY = size.height / 2f
        val strokeWidth = MiniPlayerSeekBarStrokeWidth.toPx()
        val thumbRadius = MiniPlayerSeekBarThumbRadius.toPx()
        drawLine(
            color = trackColor,
            start = Offset(0f, centerY),
            end = Offset(size.width, centerY),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
        val activeEndX = size.width * displayedFraction
        if (activeEndX > 0f) {
            drawLine(
                color = activeColor,
                start = Offset(0f, centerY),
                end = Offset(activeEndX, centerY),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        }
        drawCircle(
            color = activeColor,
            radius = thumbRadius,
            center = Offset(
                x = activeEndX.coerceIn(thumbRadius, (size.width - thumbRadius).coerceAtLeast(thumbRadius)),
                y = centerY
            )
        )
    }
}

internal fun normalizeMiniPlayerSeekFraction(fraction: Float?): Float {
    if (fraction == null || !fraction.isFinite()) return 0f
    return fraction.coerceIn(0f, 1f)
}

/**
 * 播放进度换算成迷你播放条进度；时长未知（<= 0）时返回 null，表示不显示进度条。
 */
internal fun resolveMiniPlayerSeekProgressFraction(positionMs: Long, durationMs: Long): Float? {
    if (durationMs <= 0L) return null
    if (positionMs <= 0L) return 0f
    return normalizeMiniPlayerSeekFraction(positionMs.toFloat() / durationMs.toFloat())
}

/**
 * 迷你播放条进度换算成跳转位置；时长未知时返回 null，表示本次不提交跳转。
 */
internal fun resolveMiniPlayerSeekPositionMs(fraction: Float, durationMs: Long): Long? {
    if (durationMs <= 0L) return null
    val target = (normalizeMiniPlayerSeekFraction(fraction) * durationMs.toFloat()).toLong()
    return target.coerceIn(0L, durationMs)
}
