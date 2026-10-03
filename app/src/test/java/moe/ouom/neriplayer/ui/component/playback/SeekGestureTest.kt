package moe.ouom.neriplayer.ui.component.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeekGestureTest {
    @Test
    fun `tap fraction follows the touched position and clamps to the track`() {
        assertEquals(0f, resolveSeekFraction(-40f, 400f), 0.0001f)
        assertEquals(0f, resolveSeekFraction(0f, 400f), 0.0001f)
        assertEquals(0.25f, resolveSeekFraction(100f, 400f), 0.0001f)
        assertEquals(1f, resolveSeekFraction(400f, 400f), 0.0001f)
        assertEquals(1f, resolveSeekFraction(9_000f, 400f), 0.0001f)
    }

    @Test
    fun `invalid track width or position never produces a NaN fraction`() {
        assertEquals(0f, resolveSeekFraction(120f, 0f), 0.0001f)
        assertEquals(0f, resolveSeekFraction(120f, -3f), 0.0001f)
        assertEquals(0f, resolveSeekFraction(120f, Float.NaN), 0.0001f)
        assertEquals(0f, resolveSeekFraction(Float.NaN, 400f), 0.0001f)
        assertEquals(0f, resolveSeekFraction(120f, Float.POSITIVE_INFINITY), 0.0001f)
    }

    @Test
    fun `short press does not start a seek drag so a plain tap can commit it`() {
        assertFalse(shouldBeginSeekDrag(deltaX = 0f, deltaY = 0f, touchSlop = 8f))
        assertFalse(shouldBeginSeekDrag(deltaX = 8f, deltaY = 0f, touchSlop = 8f))
        assertFalse(shouldBeginSeekDrag(deltaX = -6f, deltaY = 2f, touchSlop = 8f))
    }

    @Test
    fun `horizontal movement past the touch slop starts the seek drag`() {
        assertTrue(shouldBeginSeekDrag(deltaX = 9f, deltaY = 0f, touchSlop = 8f))
        assertTrue(shouldBeginSeekDrag(deltaX = -24f, deltaY = 6f, touchSlop = 8f))
    }

    @Test
    fun `vertical movement keeps the parent scroll instead of seeking`() {
        assertFalse(shouldBeginSeekDrag(deltaX = 9f, deltaY = 40f, touchSlop = 8f))
        assertFalse(shouldBeginSeekDrag(deltaX = 9f, deltaY = 13f, touchSlop = 8f))
    }

    @Test
    fun `equal horizontal and vertical movement still counts as a seek drag`() {
        assertTrue(shouldBeginSeekDrag(deltaX = 12f, deltaY = 12f, touchSlop = 8f))
        assertTrue(shouldBeginSeekDrag(deltaX = -12f, deltaY = 12f, touchSlop = 8f))
    }

    @Test
    fun `non finite drag deltas never start a seek drag`() {
        assertFalse(shouldBeginSeekDrag(deltaX = Float.NaN, deltaY = 0f, touchSlop = 8f))
        assertFalse(shouldBeginSeekDrag(deltaX = 40f, deltaY = Float.NaN, touchSlop = 8f))
    }
}
