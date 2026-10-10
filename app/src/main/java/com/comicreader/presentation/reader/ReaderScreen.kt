package com.comicreader.presentation.reader

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.comicreader.domain.model.ReadingDirection
import com.comicreader.domain.model.ReadingMode
import com.comicreader.presentation.components.BookmarkPanel
import com.comicreader.presentation.components.ReaderOverlay

/**
 * Top-level screen composable for the Comic Reader.
 * Coordinates gestures, readers, overlay HUD, screen flags, and navigation.
 */
@Composable
fun ReaderScreen(
    comicId: Long,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReaderViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val activity = context as? Activity

    // Initialize comic if not already loaded with this comicId
    LaunchedEffect(comicId) {
        if (comicId > 0L && uiState.comic?.id != comicId) {
            viewModel.loadComic(comicId)
        }
    }

    // Handle system back navigation: save reading session before popping backstack
    BackHandler {
        viewModel.onSessionEnd()
        onNavigateBack()
    }

    // Keep screen awake flag management
    DisposableEffect(uiState.keepScreenAwake) {
        if (uiState.keepScreenAwake) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Reader custom brightness override
    DisposableEffect(uiState.brightness) {
        if (uiState.brightness in 0f..1f) {
            val layoutParams = activity?.window?.attributes
            layoutParams?.screenBrightness = uiState.brightness
            activity?.window?.attributes = layoutParams
        }
        onDispose {
            val layoutParams = activity?.window?.attributes
            layoutParams?.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            activity?.window?.attributes = layoutParams
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            val screenWidthPx = constraints.maxWidth.toFloat()

            // Main Reader Viewport
            if (uiState.readingMode == ReadingMode.HORIZONTAL) {
                HorizontalReader(
                    pages = uiState.pages,
                    currentPage = uiState.currentPage,
                    readingDirection = uiState.readingDirection,
                    pageAnimation = uiState.pageAnimation,
                    parser = viewModel.getActiveParser(),
                    onPageChanged = { index -> viewModel.onPageChanged(index) },
                    onTap = { offset ->
                        val tapFraction = if (screenWidthPx > 0f) offset.x / screenWidthPx else 0.5f
                        val isZoomed = isZoomLockedOut(uiState.zoomScale)
                        val action = resolveTapAction(tapFraction, uiState.readingDirection, isZoomed)
                        when (action) {
                            TapAction.PREVIOUS_PAGE -> viewModel.previousPage()
                            TapAction.NEXT_PAGE -> viewModel.nextPage()
                            TapAction.TOGGLE_OVERLAY -> viewModel.toggleOverlay()
                            TapAction.NO_OP -> Unit
                        }
                    },
                    onLongPress = {
                        viewModel.toggleBookmark()
                    },
                    onScaleChanged = { scale ->
                        viewModel.setZoomScale(scale)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                VerticalReader(
                    pages = uiState.pages,
                    currentPage = uiState.currentPage,
                    parser = viewModel.getActiveParser(),
                    onPageChanged = { index -> viewModel.onPageChanged(index) },
                    onTap = { offset ->
                        val tapFraction = if (screenWidthPx > 0f) offset.x / screenWidthPx else 0.5f
                        val isZoomed = isZoomLockedOut(uiState.zoomScale)
                        val action = resolveTapAction(tapFraction, uiState.readingDirection, isZoomed)
                        when (action) {
                            TapAction.PREVIOUS_PAGE -> viewModel.previousPage()
                            TapAction.NEXT_PAGE -> viewModel.nextPage()
                            TapAction.TOGGLE_OVERLAY -> viewModel.toggleOverlay()
                            TapAction.NO_OP -> Unit
                        }
                    },
                    onLongPress = {
                        viewModel.toggleBookmark()
                    },
                    onScaleChanged = { scale ->
                        viewModel.setZoomScale(scale)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // HUD Overlay Controls
            ReaderOverlay(
                isVisible = uiState.isOverlayVisible,
                title = uiState.comic?.title ?: "Comic Reader",
                currentPage = uiState.currentPage,
                totalPages = uiState.totalPages,
                isBookmarked = uiState.isCurrentPageBookmarked,
                readingMode = uiState.readingMode,
                readingDirection = uiState.readingDirection,
                onBackClick = {
                    viewModel.onSessionEnd()
                    onNavigateBack()
                },
                onBookmarkToggle = { viewModel.toggleBookmark() },
                onShowBookmarks = { viewModel.showBookmarksPanel(true) },
                onReadingModeToggle = {
                    val newMode = if (uiState.readingMode == ReadingMode.HORIZONTAL) {
                        ReadingMode.VERTICAL
                    } else {
                        ReadingMode.HORIZONTAL
                    }
                    viewModel.setReadingMode(newMode)
                },
                onReadingDirectionToggle = {
                    val newDir = if (uiState.readingDirection == ReadingDirection.LTR) {
                        ReadingDirection.RTL
                    } else {
                        ReadingDirection.LTR
                    }
                    viewModel.setReadingDirection(newDir)
                },
                onPageScrubbed = { targetPage -> viewModel.jumpToPage(targetPage) }
            )

            // Bookmarks Drawer Panel
            if (uiState.isBookmarksPanelVisible) {
                BookmarkPanel(
                    bookmarks = uiState.bookmarks,
                    currentPage = uiState.currentPage,
                    onBookmarkClick = { targetPage ->
                        viewModel.jumpToPage(targetPage)
                        viewModel.showBookmarksPanel(false)
                    },
                    onDeleteBookmark = { bookmarkId ->
                        viewModel.deleteBookmark(bookmarkId)
                    },
                    onDismiss = {
                        viewModel.showBookmarksPanel(false)
                    }
                )
            }

            // Loading Indicator
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }

            // Error Display
            if (uiState.errorMessage != null && !uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = uiState.errorMessage ?: "Unknown error",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}
