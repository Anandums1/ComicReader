package com.comicreader.presentation.reader

import com.comicreader.domain.model.ReadingDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderGesturesTest {

    @Test
    fun testResolveTapZone_leftZoneDivision() {
        assertEquals(TapZone.LEFT, resolveTapZone(0.0f))
        assertEquals(TapZone.LEFT, resolveTapZone(0.15f))
        assertEquals(TapZone.LEFT, resolveTapZone(0.299f))
    }

    @Test
    fun testResolveTapZone_centerZoneDivision() {
        assertEquals(TapZone.CENTER, resolveTapZone(0.30f))
        assertEquals(TapZone.CENTER, resolveTapZone(0.50f))
        assertEquals(TapZone.CENTER, resolveTapZone(0.70f))
    }

    @Test
    fun testResolveTapZone_rightZoneDivision() {
        assertEquals(TapZone.RIGHT, resolveTapZone(0.701f))
        assertEquals(TapZone.RIGHT, resolveTapZone(0.85f))
        assertEquals(TapZone.RIGHT, resolveTapZone(1.0f))
    }

    @Test
    fun testResolveTapAction_ltrMode() {
        // Left zone: Previous page
        assertEquals(TapAction.PREVIOUS_PAGE, resolveTapAction(0.15f, ReadingDirection.LTR))
        // Center zone: Toggle overlay
        assertEquals(TapAction.TOGGLE_OVERLAY, resolveTapAction(0.50f, ReadingDirection.LTR))
        // Right zone: Next page
        assertEquals(TapAction.NEXT_PAGE, resolveTapAction(0.85f, ReadingDirection.LTR))
    }

    @Test
    fun testResolveTapAction_rtlModeInversion() {
        // Left zone: Next page
        assertEquals(TapAction.NEXT_PAGE, resolveTapAction(0.15f, ReadingDirection.RTL))
        // Center zone: Toggle overlay
        assertEquals(TapAction.TOGGLE_OVERLAY, resolveTapAction(0.50f, ReadingDirection.RTL))
        // Right zone: Previous page
        assertEquals(TapAction.PREVIOUS_PAGE, resolveTapAction(0.85f, ReadingDirection.RTL))
    }

    @Test
    fun testResolveTapAction_zoomLockoutDisablesPageTurns() {
        // When zoomed in, taps must NEVER flip pages regardless of zone or direction
        assertEquals(TapAction.TOGGLE_OVERLAY, resolveTapAction(0.10f, ReadingDirection.LTR, isZoomed = true))
        assertEquals(TapAction.TOGGLE_OVERLAY, resolveTapAction(0.90f, ReadingDirection.LTR, isZoomed = true))
        assertEquals(TapAction.TOGGLE_OVERLAY, resolveTapAction(0.10f, ReadingDirection.RTL, isZoomed = true))
        assertEquals(TapAction.TOGGLE_OVERLAY, resolveTapAction(0.90f, ReadingDirection.RTL, isZoomed = true))
    }

    @Test
    fun testIsZoomLockedOut_boundaryThreshold() {
        assertFalse(isZoomLockedOut(1.0f))
        assertFalse(isZoomLockedOut(1.05f))
        assertTrue(isZoomLockedOut(1.051f))
        assertTrue(isZoomLockedOut(2.0f))
        assertTrue(isZoomLockedOut(5.0f))
    }

    @Test
    fun testClampZoom_boundaries() {
        assertEquals(1.0f, clampZoom(0.5f), 0.001f)
        assertEquals(1.0f, clampZoom(1.0f), 0.001f)
        assertEquals(2.5f, clampZoom(2.5f), 0.001f)
        assertEquals(5.0f, clampZoom(5.0f), 0.001f)
        assertEquals(5.0f, clampZoom(10.0f), 0.001f)
    }

    @Test
    fun testToggleDoubleTapZoom() {
        assertEquals(2.5f, toggleDoubleTapZoom(1.0f), 0.001f)
        assertEquals(2.5f, toggleDoubleTapZoom(1.5f), 0.001f)
        assertEquals(1.0f, toggleDoubleTapZoom(2.0f), 0.001f)
        assertEquals(1.0f, toggleDoubleTapZoom(2.5f), 0.001f)
        assertEquals(1.0f, toggleDoubleTapZoom(5.0f), 0.001f)
    }

    @Test
    fun testResolveTapPageChange_ltrNavigation() {
        val max = 10
        // Left zone -> page - 1
        assertEquals(4, resolveTapPageChange(0.15f, current = 5, max = max, direction = ReadingDirection.LTR))
        // Center zone -> unchanged
        assertEquals(5, resolveTapPageChange(0.50f, current = 5, max = max, direction = ReadingDirection.LTR))
        // Right zone -> page + 1
        assertEquals(6, resolveTapPageChange(0.85f, current = 5, max = max, direction = ReadingDirection.LTR))

        // Clamping to boundaries
        assertEquals(0, resolveTapPageChange(0.15f, current = 0, max = max, direction = ReadingDirection.LTR))
        assertEquals(max, resolveTapPageChange(0.85f, current = max, max = max, direction = ReadingDirection.LTR))
    }

    @Test
    fun testResolveTapPageChange_rtlNavigation() {
        val max = 10
        // Left zone -> page + 1
        assertEquals(6, resolveTapPageChange(0.15f, current = 5, max = max, direction = ReadingDirection.RTL))
        // Center zone -> unchanged
        assertEquals(5, resolveTapPageChange(0.50f, current = 5, max = max, direction = ReadingDirection.RTL))
        // Right zone -> page - 1
        assertEquals(4, resolveTapPageChange(0.85f, current = 5, max = max, direction = ReadingDirection.RTL))

        // Clamping to boundaries
        assertEquals(max, resolveTapPageChange(0.15f, current = max, max = max, direction = ReadingDirection.RTL))
        assertEquals(0, resolveTapPageChange(0.85f, current = 0, max = max, direction = ReadingDirection.RTL))
    }

    @Test
    fun testHandleVolumeKey() {
        val max = 20
        // Volume down -> Next page (+1)
        assertEquals(6, handleVolumeKey(25, current = 5, max = max))
        // Volume up -> Previous page (-1)
        assertEquals(4, handleVolumeKey(24, current = 5, max = max))

        // Clamping
        assertEquals(0, handleVolumeKey(24, current = 0, max = max))
        assertEquals(max, handleVolumeKey(25, current = max, max = max))

        // Irrelevant keys
        assertEquals(5, handleVolumeKey(66 /* KEYCODE_ENTER */, current = 5, max = max))
    }

    @Test
    fun testResolveTapAction_pixelCoordinates() {
        val screenWidth = 1080f
        assertEquals(TapAction.PREVIOUS_PAGE, resolveTapAction(150f, screenWidth))
        assertEquals(TapAction.TOGGLE_OVERLAY, resolveTapAction(540f, screenWidth))
        assertEquals(TapAction.NEXT_PAGE, resolveTapAction(900f, screenWidth))

        // Zoom lockout active via scale parameter
        assertEquals(TapAction.TOGGLE_OVERLAY, resolveTapAction(150f, screenWidth, scale = 2.0f))
        assertEquals(TapAction.TOGGLE_OVERLAY, resolveTapAction(900f, screenWidth, scale = 2.0f))

        // Invalid screen width
        assertEquals(TapAction.NO_OP, resolveTapAction(150f, 0f))
    }
}

