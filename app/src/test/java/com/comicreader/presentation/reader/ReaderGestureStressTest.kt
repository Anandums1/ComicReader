package com.comicreader.presentation.reader

import com.comicreader.domain.model.ReadingDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Empirical stress and property test suite for ReaderGestures algorithms.
 * Tests extreme touch inputs: exact zone boundaries, floating point overflow/underflow,
 * negative screen coordinates, infinite/NaN inputs, rapid double-tap cycles, and fuzzing.
 */
class ReaderGestureStressTest {

    @Test
    fun testResolveTapZone_exactBoundariesAndEpsilons() {
        // Left zone: < 0.30f
        assertEquals(TapZone.LEFT, resolveTapZone(0.0f))
        assertEquals(TapZone.LEFT, resolveTapZone(0.299999f))
        assertEquals(TapZone.LEFT, resolveTapZone(0.299f))

        // Center zone: [0.30f, 0.70f]
        assertEquals(TapZone.CENTER, resolveTapZone(0.30f))
        assertEquals(TapZone.CENTER, resolveTapZone(0.300001f))
        assertEquals(TapZone.CENTER, resolveTapZone(0.50f))
        assertEquals(TapZone.CENTER, resolveTapZone(0.699999f))
        assertEquals(TapZone.CENTER, resolveTapZone(0.70f))

        // Right zone: > 0.70f
        assertEquals(TapZone.RIGHT, resolveTapZone(0.700001f))
        assertEquals(TapZone.RIGHT, resolveTapZone(0.85f))
        assertEquals(TapZone.RIGHT, resolveTapZone(1.0f))
    }

    @Test
    fun testResolveTapZone_negativeCoordinatesAndOverflow() {
        // Negative coordinates (e.g. touch dragged or registered off-screen left)
        assertEquals(TapZone.LEFT, resolveTapZone(-0.00001f))
        assertEquals(TapZone.LEFT, resolveTapZone(-1.0f))
        assertEquals(TapZone.LEFT, resolveTapZone(-99999.0f))
        assertEquals(TapZone.LEFT, resolveTapZone(-Float.MAX_VALUE))
        assertEquals(TapZone.LEFT, resolveTapZone(Float.NEGATIVE_INFINITY))

        // Large overflow coordinates (e.g. multi-display or off-screen right)
        assertEquals(TapZone.RIGHT, resolveTapZone(1.00001f))
        assertEquals(TapZone.RIGHT, resolveTapZone(10.0f))
        assertEquals(TapZone.RIGHT, resolveTapZone(99999.0f))
        assertEquals(TapZone.RIGHT, resolveTapZone(Float.MAX_VALUE))
        assertEquals(TapZone.RIGHT, resolveTapZone(Float.POSITIVE_INFINITY))

        // Float.NaN comparison behavior
        assertEquals(TapZone.CENTER, resolveTapZone(Float.NaN))
    }

    @Test
    fun testResolveTapAction_extremeScreenDimensions() {
        val normalWidth = 1080f

        // Screen width <= 0f must safely return NO_OP
        assertEquals(TapAction.NO_OP, resolveTapAction(100f, screenWidth = 0f))
        assertEquals(TapAction.NO_OP, resolveTapAction(100f, screenWidth = -1080f))
        assertEquals(TapAction.NO_OP, resolveTapAction(100f, screenWidth = -Float.MIN_VALUE))

        // Negative tapX on normal screen width -> tapFraction < 0 -> LEFT
        assertEquals(TapAction.PREVIOUS_PAGE, resolveTapAction(tapX = -50f, screenWidth = normalWidth, direction = ReadingDirection.LTR))
        assertEquals(TapAction.NEXT_PAGE, resolveTapAction(tapX = -50f, screenWidth = normalWidth, direction = ReadingDirection.RTL))

        // Oversized tapX on normal screen width -> tapFraction > 1 -> RIGHT
        assertEquals(TapAction.NEXT_PAGE, resolveTapAction(tapX = 2500f, screenWidth = normalWidth, direction = ReadingDirection.LTR))
        assertEquals(TapAction.PREVIOUS_PAGE, resolveTapAction(tapX = 2500f, screenWidth = normalWidth, direction = ReadingDirection.RTL))

        // Infinite screenWidth
        assertEquals(TapAction.PREVIOUS_PAGE, resolveTapAction(tapX = 500f, screenWidth = Float.POSITIVE_INFINITY, direction = ReadingDirection.LTR))
    }

    @Test
    fun testZoomLockout_stressThresholds() {
        assertFalse("At 1.0f zoom must not be locked out", isZoomLockedOut(1.0f))
        assertFalse("At 1.0499f zoom must not be locked out", isZoomLockedOut(1.0499f))
        assertFalse("At 1.05f threshold zoom must not be locked out", isZoomLockedOut(1.05f))

        assertTrue("At 1.05001f zoom MUST be locked out", isZoomLockedOut(1.05001f))
        assertTrue("At 1.051f zoom MUST be locked out", isZoomLockedOut(1.051f))
        assertTrue("At 2.5f zoom MUST be locked out", isZoomLockedOut(2.5f))
        assertTrue("At 5.0f zoom MUST be locked out", isZoomLockedOut(5.0f))
        assertTrue("At Float.MAX_VALUE zoom MUST be locked out", isZoomLockedOut(Float.MAX_VALUE))
        assertTrue("At POSITIVE_INFINITY zoom MUST be locked out", isZoomLockedOut(Float.POSITIVE_INFINITY))

        // Negative or NaN scale
        assertFalse("Negative scale is not locked out", isZoomLockedOut(-1.0f))
        assertFalse("NaN scale is not locked out", isZoomLockedOut(Float.NaN))
    }

    @Test
    fun testClampZoom_extremeValues() {
        assertEquals(1.0f, clampZoom(0.0f), 0.0001f)
        assertEquals(1.0f, clampZoom(-1000f), 0.0001f)
        assertEquals(1.0f, clampZoom(Float.NEGATIVE_INFINITY), 0.0001f)
        assertEquals(1.0f, clampZoom(Float.MIN_VALUE), 0.0001f)

        assertEquals(1.0f, clampZoom(1.0f), 0.0001f)
        assertEquals(2.718f, clampZoom(2.718f), 0.0001f)
        assertEquals(5.0f, clampZoom(5.0f), 0.0001f)

        assertEquals(5.0f, clampZoom(5.0001f), 0.0001f)
        assertEquals(5.0f, clampZoom(100f), 0.0001f)
        assertEquals(5.0f, clampZoom(Float.MAX_VALUE), 0.0001f)
        assertEquals(5.0f, clampZoom(Float.POSITIVE_INFINITY), 0.0001f)
    }

    @Test
    fun testToggleDoubleTapZoom_rapidCyclesAndArbitraryValues() {
        var scale = 1.0f
        // 10,000 rapid double-tap cycles
        for (i in 0 until 10_000) {
            scale = toggleDoubleTapZoom(scale)
            if (i % 2 == 0) {
                assertEquals(2.5f, scale, 0.0001f)
            } else {
                assertEquals(1.0f, scale, 0.0001f)
            }
        }

        // Exact boundary at 1.5f
        assertEquals(2.5f, toggleDoubleTapZoom(1.5f), 0.0001f)
        assertEquals(1.0f, toggleDoubleTapZoom(1.50001f), 0.0001f)

        // Extreme scales
        assertEquals(2.5f, toggleDoubleTapZoom(0.0f), 0.0001f)
        assertEquals(2.5f, toggleDoubleTapZoom(-10.0f), 0.0001f)
        assertEquals(1.0f, toggleDoubleTapZoom(100.0f), 0.0001f)
        assertEquals(1.0f, toggleDoubleTapZoom(Float.MAX_VALUE), 0.0001f)
        assertEquals(1.0f, toggleDoubleTapZoom(Float.POSITIVE_INFINITY), 0.0001f)
        assertEquals(2.5f, toggleDoubleTapZoom(Float.NEGATIVE_INFINITY), 0.0001f)
    }

    @Test
    fun testResolveTapPageChange_propertyBasedNavigationAcrossExtremeIndices() {
        val max = 10

        // LTR: left tap at page 0 -> stays 0
        assertEquals(0, resolveTapPageChange(0.1f, current = 0, max = max, direction = ReadingDirection.LTR))
        // LTR: right tap at max -> stays max
        assertEquals(max, resolveTapPageChange(0.9f, current = max, max = max, direction = ReadingDirection.LTR))

        // Negative current index clamped up
        assertEquals(0, resolveTapPageChange(0.1f, current = -10, max = max, direction = ReadingDirection.LTR))
        // Current index beyond max clamped down
        assertEquals(max, resolveTapPageChange(0.9f, current = 100, max = max, direction = ReadingDirection.LTR))

        // RTL: left tap advances (+1), right tap goes back (-1)
        assertEquals(max, resolveTapPageChange(0.1f, current = max, max = max, direction = ReadingDirection.RTL))
        assertEquals(0, resolveTapPageChange(0.9f, current = 0, max = max, direction = ReadingDirection.RTL))

        // Single-page comic (max = 0)
        assertEquals(0, resolveTapPageChange(0.1f, current = 0, max = 0, direction = ReadingDirection.LTR))
        assertEquals(0, resolveTapPageChange(0.5f, current = 0, max = 0, direction = ReadingDirection.LTR))
        assertEquals(0, resolveTapPageChange(0.9f, current = 0, max = 0, direction = ReadingDirection.LTR))

        // Large comic (1,000,000 pages)
        val largeMax = 1_000_000
        assertEquals(999_999, resolveTapPageChange(0.1f, current = 1_000_000, max = largeMax, direction = ReadingDirection.LTR))
        assertEquals(largeMax, resolveTapPageChange(0.9f, current = 999_999, max = largeMax, direction = ReadingDirection.LTR))
        assertEquals(largeMax, resolveTapPageChange(0.9f, current = largeMax, max = largeMax, direction = ReadingDirection.LTR))
    }

    @Test
    fun testHandleVolumeKey_stressAndEdgeKeyCodes() {
        val max = 20

        // Standard keys
        assertEquals(6, handleVolumeKey(25, current = 5, max = max)) // Volume Down
        assertEquals(4, handleVolumeKey(24, current = 5, max = max)) // Volume Up

        // Clamping
        assertEquals(0, handleVolumeKey(24, current = 0, max = max))
        assertEquals(max, handleVolumeKey(25, current = max, max = max))

        // Irrelevant and extreme keycodes
        assertEquals(5, handleVolumeKey(-1, current = 5, max = max))
        assertEquals(5, handleVolumeKey(Int.MIN_VALUE, current = 5, max = max))
        assertEquals(5, handleVolumeKey(Int.MAX_VALUE, current = 5, max = max))
        assertEquals(5, handleVolumeKey(0, current = 5, max = max))
        assertEquals(5, handleVolumeKey(66, current = 5, max = max)) // Enter
        assertEquals(5, handleVolumeKey(4, current = 5, max = max)) // Back
    }

    @Test
    fun testRandomizedTapFuzzing_10000Events() {
        val random = Random(42)
        val screenWidth = 1080f
        val maxPage = 50

        for (i in 0 until 10_000) {
            val tapX = (random.nextFloat() * 3000f) - 1000f // Range -1000f to +2000f
            val direction = if (random.nextBoolean()) ReadingDirection.LTR else ReadingDirection.RTL
            val scale = random.nextFloat() * 6.0f // Range 0.0f to 6.0f
            val currentPage = random.nextInt(-10, 100)

            // Verify resolveTapAction never throws
            val action = resolveTapAction(
                tapX = tapX,
                screenWidth = screenWidth,
                direction = direction,
                scale = scale
            )
            assertTrue(action in TapAction.values())

            // Verify resolveTapPageChange never outputs an out-of-bounds page if current is in bounds
            val tapFraction = tapX / screenWidth
            val validCurrent = currentPage.coerceIn(0, maxPage)
            val newPage = resolveTapPageChange(
                tapFraction = tapFraction,
                current = validCurrent,
                max = maxPage,
                direction = direction
            )
            assertTrue("Page index $newPage must be >= 0", newPage >= 0)
            assertTrue("Page index $newPage must be <= $maxPage", newPage <= maxPage)
        }
    }
}
