package com.comicreader.presentation.reader

import com.comicreader.domain.model.ReadingDirection

/**
 * Screen interaction tap zones.
 * - LEFT: 0% to 30% of screen width
 * - CENTER: 30% to 70% of screen width
 * - RIGHT: 70% to 100% of screen width
 */
enum class TapZone {
    LEFT,
    CENTER,
    RIGHT
}

/**
 * Action resolved from a user tap interaction.
 */
enum class TapAction {
    PREVIOUS_PAGE,
    NEXT_PAGE,
    TOGGLE_OVERLAY,
    NO_OP
}

/**
 * Determines which tap zone [tapFractionX] (0.0f..1.0f) falls into.
 */
fun resolveTapZone(tapFractionX: Float): TapZone {
    return when {
        tapFractionX < 0.30f -> TapZone.LEFT
        tapFractionX > 0.70f -> TapZone.RIGHT
        else -> TapZone.CENTER
    }
}

/**
 * Checks whether pinch zoom lockout is active (scale > 1.05x).
 * When active, taps must not trigger page turns to allow panning without accidental turns.
 */
fun isZoomLockedOut(scale: Float): Boolean = scale > 1.05f

/**
 * Clamps zoom scale within allowed boundaries [1.0f, 5.0f].
 */
fun clampZoom(scale: Float): Float = scale.coerceIn(1.0f, 5.0f)

/**
 * Returns toggled zoom scale for double-tap gestures:
 * Toggles between 1.0f and 2.5f.
 */
fun toggleDoubleTapZoom(currentScale: Float): Float =
    if (currentScale > 1.5f) 1.0f else 2.5f

/**
 * Resolves tap action given normalized horizontal coordinate [tapFractionX], reading direction, and zoom state.
 * If [isZoomed] is true, returns TOGGLE_OVERLAY (never page turns).
 */
fun resolveTapAction(
    tapFractionX: Float,
    direction: ReadingDirection = ReadingDirection.LTR,
    isZoomed: Boolean = false
): TapAction {
    if (isZoomed) {
        // Zoom lockout: tap turns are disabled to prevent accidental page turn while panning
        return TapAction.TOGGLE_OVERLAY
    }

    return when (resolveTapZone(tapFractionX)) {
        TapZone.CENTER -> TapAction.TOGGLE_OVERLAY
        TapZone.LEFT -> if (direction == ReadingDirection.LTR) TapAction.PREVIOUS_PAGE else TapAction.NEXT_PAGE
        TapZone.RIGHT -> if (direction == ReadingDirection.LTR) TapAction.NEXT_PAGE else TapAction.PREVIOUS_PAGE
    }
}

/**
 * Overload resolving tap action from raw pixel coordinates and screen width.
 */
fun resolveTapAction(
    tapX: Float,
    screenWidth: Float,
    direction: ReadingDirection = ReadingDirection.LTR,
    scale: Float = 1.0f
): TapAction {
    if (screenWidth <= 0f) return TapAction.NO_OP
    val fraction = tapX / screenWidth
    return resolveTapAction(
        tapFractionX = fraction,
        direction = direction,
        isZoomed = isZoomLockedOut(scale)
    )
}

/**
 * Calculates updated page index based on tap fraction and reading direction.
 */
fun resolveTapPageChange(
    tapFraction: Float,
    current: Int,
    max: Int,
    direction: ReadingDirection
): Int {
    return if (direction == ReadingDirection.LTR) {
        if (tapFraction < 0.30f) (current - 1).coerceAtLeast(0)
        else if (tapFraction > 0.70f) (current + 1).coerceAtMost(max)
        else current
    } else {
        if (tapFraction < 0.30f) (current + 1).coerceAtMost(max)
        else if (tapFraction > 0.70f) (current - 1).coerceAtLeast(0)
        else current
    }
}

/**
 * Helper to process hardware volume key presses for page turning.
 * KEYCODE_VOLUME_DOWN (25) -> Next page
 * KEYCODE_VOLUME_UP (24) -> Previous page
 */
fun handleVolumeKey(keyCode: Int, current: Int, max: Int): Int {
    return when (keyCode) {
        25 /* KEYCODE_VOLUME_DOWN */ -> (current + 1).coerceAtMost(max)
        24 /* KEYCODE_VOLUME_UP */ -> (current - 1).coerceAtLeast(0)
        else -> current
    }
}

