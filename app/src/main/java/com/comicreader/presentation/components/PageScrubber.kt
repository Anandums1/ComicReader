package com.comicreader.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * A continuous page scrubber slider providing instant visual feedback during dragging
 * and committing the page change on release.
 */
@Composable
fun PageScrubber(
    currentPage: Int,
    totalPages: Int,
    onPageScrubbed: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (totalPages <= 0) return

    val maxPageIndex = (totalPages - 1).coerceAtLeast(0)
    var sliderValue by remember { mutableFloatStateOf(currentPage.toFloat()) }
    var isDragging by remember { mutableStateOf(false) }

    LaunchedEffect(currentPage) {
        if (!isDragging) {
            sliderValue = currentPage.toFloat().coerceIn(0f, maxPageIndex.toFloat())
        }
    }

    val displayPage = (sliderValue.toInt() + 1).coerceIn(1, totalPages)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    val prev = (currentPage - 1).coerceAtLeast(0)
                    onPageScrubbed(prev)
                },
                enabled = currentPage > 0
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Previous page"
                )
            }

            Text(
                text = "Page $displayPage of $totalPages",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            IconButton(
                onClick = {
                    val next = (currentPage + 1).coerceAtMost(maxPageIndex)
                    onPageScrubbed(next)
                },
                enabled = currentPage < maxPageIndex
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Next page"
                )
            }
        }

        Slider(
            value = sliderValue,
            onValueChange = { newValue ->
                isDragging = true
                sliderValue = newValue
            },
            onValueChangeFinished = {
                isDragging = false
                val targetPage = sliderValue.toInt().coerceIn(0, maxPageIndex)
                onPageScrubbed(targetPage)
            },
            valueRange = 0f..maxPageIndex.toFloat().coerceAtLeast(1f),
            steps = if (maxPageIndex in 2..100) maxPageIndex - 1 else 0,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

