package com.comicreader.presentation.reader

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import coil.compose.AsyncImage
import com.comicreader.domain.model.ComicPage
import com.comicreader.domain.model.PageAnimation
import com.comicreader.domain.model.ReadingDirection
import com.comicreader.format.ComicParser
import com.comicreader.presentation.components.ZoomableBox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

/**
 * Horizontal pager comic reader supporting LTR and RTL reading directions,
 * page turn transition animations, pinch-to-zoom, and gesture tap zones.
 */
@Composable
fun HorizontalReader(
    pages: List<ComicPage>,
    currentPage: Int,
    readingDirection: ReadingDirection,
    pageAnimation: PageAnimation,
    parser: ComicParser? = null,
    onPageChanged: (Int) -> Unit,
    onTap: (Offset) -> Unit,
    onLongPress: () -> Unit,
    onScaleChanged: ((Float) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (pages.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    val pageCount = pages.size
    val initialPage = currentPage.coerceIn(0, pageCount - 1)
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { pageCount }
    )

    // Synchronize external currentPage changes (scrubber, volume buttons, jump to page)
    LaunchedEffect(currentPage) {
        if (pagerState.currentPage != currentPage && currentPage in 0 until pageCount) {
            pagerState.scrollToPage(currentPage)
        }
    }

    // Emit page changed events when user swipes between pages
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage != currentPage) {
            onPageChanged(pagerState.currentPage)
        }
    }

    val layoutDirection = if (readingDirection == ReadingDirection.RTL) {
        LayoutDirection.Rtl
    } else {
        LayoutDirection.Ltr
    }

    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
        HorizontalPager(
            state = pagerState,
            modifier = modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
            key = { pages[it].pageNumber }
        ) { pageIndex ->
            val page = pages[pageIndex]

            ZoomableBox(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        if (pageAnimation == PageAnimation.FADE) {
                            val pageOffset = (pagerState.currentPage - pageIndex) + pagerState.currentPageOffsetFraction
                            alpha = 1f - abs(pageOffset).coerceIn(0f, 1f)
                        }
                    },
                onTap = onTap,
                onLongPress = { onLongPress() },
                onScaleChanged = onScaleChanged
            ) {
                ComicPageItem(
                    page = page,
                    parser = parser,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
fun ComicPageItem(
    page: ComicPage,
    parser: ComicParser? = null,
    contentScale: ContentScale = ContentScale.Fit,
    modifier: Modifier = Modifier
) {
    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = page, key2 = parser) {
        if (parser != null) {
            value = withContext(Dispatchers.IO) {
                parser.loadPageBitmap(page)
            }
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = "Page ${page.pageNumber + 1}",
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize()
            )
        } else if (!page.filePath.isNullOrBlank() && File(page.filePath).exists() && isDirectImage(page.filePath)) {
            AsyncImage(
                model = File(page.filePath),
                contentDescription = "Page ${page.pageNumber + 1}",
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize()
            )
        } else if (!page.imageUri.isNullOrBlank() && (page.imageUri.startsWith("http") || page.imageUri.startsWith("content://") || page.imageUri.startsWith("file://"))) {
            AsyncImage(
                model = page.imageUri,
                contentDescription = "Page ${page.pageNumber + 1}",
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
    }
}

private fun isDirectImage(path: String): Boolean {
    val lower = path.lowercase()
    return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
            lower.endsWith(".webp") || lower.endsWith(".bmp")
}

