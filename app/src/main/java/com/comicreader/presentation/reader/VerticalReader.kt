package com.comicreader.presentation.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.comicreader.domain.model.ComicPage
import com.comicreader.format.ComicParser
import com.comicreader.presentation.components.ZoomableBox
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Vertical continuous scroll reader for webtoons and vertical-strip comics.
 * Features zero gap between pages, dynamic index tracking, and zoom capabilities.
 */
@Composable
fun VerticalReader(
    pages: List<ComicPage>,
    currentPage: Int,
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
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialPage)

    // Synchronize external currentPage changes (scrubber, volume keys, jump to page)
    LaunchedEffect(currentPage) {
        if (listState.firstVisibleItemIndex != currentPage && currentPage in 0 until pageCount) {
            listState.scrollToItem(currentPage)
        }
    }

    // Emit onPageChanged when user scrolls through items
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { index ->
                if (index in 0 until pageCount) {
                    onPageChanged(index)
                }
            }
    }

    ZoomableBox(
        modifier = modifier.fillMaxSize(),
        onTap = onTap,
        onLongPress = { onLongPress() },
        onScaleChanged = onScaleChanged
    ) {
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(0.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(
                items = pages,
                key = { _, page -> page.pageNumber }
            ) { _, page ->
                ComicPageItem(
                    page = page,
                    parser = parser,
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                )
            }
        }
    }
}

