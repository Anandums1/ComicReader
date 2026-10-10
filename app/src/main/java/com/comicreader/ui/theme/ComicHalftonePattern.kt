package com.comicreader.ui.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Custom Compose Canvas modifier that renders vintage Ben-Day halftone dots.
 * Typically applied across card backgrounds, app bar accents, and comic containers.
 */
fun Modifier.comicHalftonePattern(
    dotColor: Color = Color.Black.copy(alpha = 0.08f),
    dotRadius: Dp = 1.5.dp,
    spacing: Dp = 8.dp
): Modifier = this.drawBehind {
    val radiusPx = dotRadius.toPx()
    val stepPx = spacing.toPx()
    if (stepPx <= 0f || radiusPx <= 0f) return@drawBehind

    var y = stepPx / 2f
    while (y < size.height) {
        var x = stepPx / 2f
        while (x < size.width) {
            drawCircle(
                color = dotColor,
                radius = radiusPx,
                center = Offset(x, y)
            )
            x += stepPx
        }
        y += stepPx
    }
}
