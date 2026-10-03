package moe.ouom.neriplayer.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import moe.ouom.neriplayer.core.di.AppContainer
import moe.ouom.neriplayer.core.player.PlayerManager
import moe.ouom.neriplayer.ui.component.playback.resolveMiniPlayerSeekPositionMs
import moe.ouom.neriplayer.ui.component.playback.resolveMiniPlayerSeekProgressFraction
import moe.ouom.neriplayer.ui.screen.playback.resolveListenTogetherProgressSeekEnabled

/**
 * 迷你播放条进度调整状态。
 *
 * @param progressFraction null 表示进度未知（不显示可调整的进度条）
 * @param enabled 是否允许用户调整进度（听歌房听众无权控制时禁用）
 * @param onSeek 提交 0..1 的进度，内部换算成毫秒后跳转
 */
internal data class AppMiniPlayerSeekControl(
    val progressFraction: Float?,
    val enabled: Boolean,
    val onSeek: (Float) -> Unit
)

@Composable
internal fun rememberAppMiniPlayerSeekControl(): AppMiniPlayerSeekControl {
    val durationMs by PlayerManager.playbackDurationFlow.collectAsStateWithLifecycle()
    val positionMs by PlayerManager.playbackPositionFlow.collectAsStateWithLifecycle()
    val listenTogetherSessionManager = remember { AppContainer.listenTogetherSessionManager }
    val sessionState by listenTogetherSessionManager.sessionState.collectAsStateWithLifecycle()
    val roomState by listenTogetherSessionManager.roomState.collectAsStateWithLifecycle()
    val seekAllowed = resolveListenTogetherProgressSeekEnabled(
        sessionUserUuid = sessionState.userUuid,
        fallbackRole = sessionState.role,
        roomId = sessionState.roomId,
        controllerUserUuid = roomState?.controllerUserUuid,
        controllerUserId = roomState?.controllerUserId,
        allowMemberControl = roomState?.settings?.allowMemberControl
    )
    val progressFraction = resolveMiniPlayerSeekProgressFraction(
        positionMs = positionMs,
        durationMs = durationMs
    )
    return remember(progressFraction, seekAllowed, durationMs) {
        AppMiniPlayerSeekControl(
            progressFraction = progressFraction,
            enabled = progressFraction != null && seekAllowed,
            onSeek = { fraction ->
                resolveMiniPlayerSeekPositionMs(fraction, durationMs)?.let { targetMs ->
                    PlayerManager.seekTo(targetMs)
                }
            }
        )
    }
}
