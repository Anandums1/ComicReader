package com.comicreader.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.comicreader.domain.model.DailyReadingStat

/**
 * Data model for a single day bar in the WeeklyReadingChart.
 */
data class DayReadingStat(
    val dayLabel: String, // e.g., "M", "T", "W", "T", "F", "S", "S"
    val pagesRead: Int
)

/**
 * Canvas-rendered 7-day bar chart showing pages read per day (Monday to Sunday).
 * Safely guards against division by zero using `coerceAtLeast(10)`.
 */
@Composable
fun WeeklyReadingChart(
    weeklyCounts: List<DayReadingStat>,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    chartHeight: Dp = 150.dp
) {
    val defaultDays = listOf("M", "T", "W", "T", "F", "S", "S")

    // Normalize weeklyCounts to ensure exactly 7 items
    val normalizedCounts: List<DayReadingStat> = if (weeklyCounts.size == 7) {
        weeklyCounts
    } else {
        defaultDays.mapIndexed { index, label ->
            DayReadingStat(
                dayLabel = label,
                pagesRead = weeklyCounts.getOrNull(index)?.pagesRead ?: 0
            )
        }
    }

    val maxCount = (normalizedCounts.maxOfOrNull { it.pagesRead } ?: 0).coerceAtLeast(10)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Bar Chart Canvas
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(chartHeight)
                .padding(horizontal = 8.dp)
        ) {
            val totalBars = normalizedCounts.size
            val barSpacing = 12.dp.toPx()
            val totalSpacing = barSpacing * (totalBars + 1)
            val availableWidth = (size.width - totalSpacing).coerceAtLeast(0f)
            val barWidth = (availableWidth / totalBars).coerceAtLeast(8.dp.toPx())
            val availableHeight = size.height - 4.dp.toPx()

            normalizedCounts.forEachIndexed { index, stat ->
                val left = barSpacing + index * (barWidth + barSpacing)
                val fraction = (stat.pagesRead.toFloat() / maxCount).coerceIn(0f, 1f)
                val barHeight = (fraction * availableHeight).coerceAtLeast(4.dp.toPx())
                val top = size.height - barHeight

                drawRoundRect(
                    color = if (stat.pagesRead > 0) barColor else barColor.copy(alpha = 0.18f),
                    topLeft = Offset(left, top),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Day of Week Labels Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            normalizedCounts.forEach { stat ->
                Text(
                    text = stat.dayLabel,
                    color = textColor.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp
                    ),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * Overload of WeeklyReadingChart that accepts domain model `DailyReadingStat`.
 */
@Composable
fun WeeklyReadingChartFromDaily(
    stats: List<DailyReadingStat>,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    chartHeight: Dp = 150.dp
) {
    val dayLabels = listOf("M", "T", "W", "T", "F", "S", "S")
    val mapped = (0..6).map { index ->
        val stat = stats.getOrNull(index)
        val label = stat?.dayOfWeek?.firstOrNull()?.toString() ?: dayLabels[index]
        DayReadingStat(
            dayLabel = label,
            pagesRead = stat?.pagesRead ?: 0
        )
    }
    WeeklyReadingChart(
        weeklyCounts = mapped,
        modifier = modifier,
        barColor = barColor,
        textColor = textColor,
        chartHeight = chartHeight
    )
}
