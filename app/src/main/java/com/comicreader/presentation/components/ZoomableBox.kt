package com.comicreader.presentation.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import com.comicreader.presentation.reader.clampZoom
import com.comicreader.presentation.reader.toggleDoubleTapZoom

/**
 * A container composable providing pinch-to-zoom (1.0x to 5.0x), bounded panning,
 * double-tap toggle zoom, and tap/long-press gesture propagation.
 */
@Composable
fun ZoomableBox(
    modifier: Modifier = Modifier,
    minScale: Float = 1.0f,
    maxScale: Float = 5.0f,
    onScaleChanged: ((Float) -> Unit)? = null,
    onTap: ((Offset) -> Unit)? = null,
    onDoubleTap: ((Offset) -> Unit)? = null,
    onLongPress: ((Offset) -> Unit)? = null,
    content: @Composable () -> Unit
) {
    var scale by remember { mutableFloatStateOf(1.0f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { tapOffset ->
                        onTap?.invoke(tapOffset)
                    },
                    onDoubleTap = { tapOffset ->
                        val newScale = toggleDoubleTapZoom(scale)
                        scale = newScale
                        offset = Offset.Zero
                        onScaleChanged?.invoke(newScale)
                        onDoubleTap?.invoke(tapOffset)
                    },
                    onLongPress = { tapOffset ->
                        onLongPress?.invoke(tapOffset)
                    }
                )
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val newScale = clampZoom(scale * zoom)
                    scale = newScale
                    onScaleChanged?.invoke(newScale)

                    if (newScale > 1.0f) {
                        val maxPanX = (newScale - 1f) * size.width / 2f
                        val maxPanY = (newScale - 1f) * size.height / 2f
                        offset = Offset(
                            x = (offset.x + pan.x).coerceIn(-maxPanX, maxPanX),
                            y = (offset.y + pan.y).coerceIn(-maxPanY, maxPanY)
                        )
                    } else {
                        offset = Offset.Zero
                    }
                }
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            }
    ) {
        content()
    }
}

