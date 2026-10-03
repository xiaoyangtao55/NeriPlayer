package moe.ouom.neriplayer.ui.component.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MiniPlayerSeekBarTest {
    @Test
    fun `preview fractions are normalized before they reach the track`() {
        assertEquals(0f, normalizeMiniPlayerSeekFraction(null), 0.0001f)
        assertEquals(0f, normalizeMiniPlayerSeekFraction(Float.NaN), 0.0001f)
        assertEquals(0f, normalizeMiniPlayerSeekFraction(-1.5f), 0.0001f)
        assertEquals(0.42f, normalizeMiniPlayerSeekFraction(0.42f), 0.0001f)
        assertEquals(1f, normalizeMiniPlayerSeekFraction(3f), 0.0001f)
    }

    @Test
    fun `unknown duration hides the mini player progress bar`() {
        assertNull(resolveMiniPlayerSeekProgressFraction(positionMs = 5_000L, durationMs = 0L))
        assertNull(resolveMiniPlayerSeekProgressFraction(positionMs = 5_000L, durationMs = -1L))
    }

    @Test
    fun `progress fraction clamps positions outside the track`() {
        assertEquals(0f, resolveMiniPlayerSeekProgressFraction(positionMs = -2_000L, durationMs = 100_000L)!!, 0.0001f)
        assertEquals(0.25f, resolveMiniPlayerSeekProgressFraction(positionMs = 25_000L, durationMs = 100_000L)!!, 0.0001f)
        assertEquals(1f, resolveMiniPlayerSeekProgressFraction(positionMs = 250_000L, durationMs = 100_000L)!!, 0.0001f)
    }

    @Test
    fun `seek commits a clamped position inside the track`() {
        assertNull(resolveMiniPlayerSeekPositionMs(fraction = 0.5f, durationMs = 0L))
        assertEquals(0L, resolveMiniPlayerSeekPositionMs(fraction = -1f, durationMs = 100_000L)!!)
        assertEquals(50_000L, resolveMiniPlayerSeekPositionMs(fraction = 0.5f, durationMs = 100_000L)!!)
        assertEquals(100_000L, resolveMiniPlayerSeekPositionMs(fraction = 4f, durationMs = 100_000L)!!)
        assertEquals(0L, resolveMiniPlayerSeekPositionMs(fraction = Float.NaN, durationMs = 100_000L)!!)
    }
}
