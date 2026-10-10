package com.comicreader.presentation.screens

import com.comicreader.domain.model.DailyReadingStat
import com.comicreader.presentation.components.DayReadingStat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeeklyReadingChartTest {

    @Test
    fun testMaxCount_withAllZeroDays_isCoercedToAtLeast10() {
        val allZero = listOf(
            DayReadingStat("M", 0),
            DayReadingStat("T", 0),
            DayReadingStat("W", 0),
            DayReadingStat("T", 0),
            DayReadingStat("F", 0),
            DayReadingStat("S", 0),
            DayReadingStat("S", 0)
        )

        val maxCount = (allZero.maxOfOrNull { it.pagesRead } ?: 0).coerceAtLeast(10)
        assertEquals(10, maxCount)
    }

    @Test
    fun testMaxCount_withHighDay_preservesMaximum() {
        val stats = listOf(
            DayReadingStat("M", 5),
            DayReadingStat("T", 25),
            DayReadingStat("W", 12),
            DayReadingStat("T", 0),
            DayReadingStat("F", 40),
            DayReadingStat("S", 30),
            DayReadingStat("S", 15)
        )

        val maxCount = (stats.maxOfOrNull { it.pagesRead } ?: 0).coerceAtLeast(10)
        assertEquals(40, maxCount)
    }

    @Test
    fun testNormalization_withEmptyList_creates7Days() {
        val empty = emptyList<DayReadingStat>()
        val defaultDays = listOf("M", "T", "W", "T", "F", "S", "S")

        val normalized = defaultDays.mapIndexed { index, label ->
            DayReadingStat(
                dayLabel = label,
                pagesRead = empty.getOrNull(index)?.pagesRead ?: 0
            )
        }

        assertEquals(7, normalized.size)
        assertEquals(listOf("M", "T", "W", "T", "F", "S", "S"), normalized.map { it.dayLabel })
        assertTrue(normalized.all { it.pagesRead == 0 })
    }

    @Test
    fun testDailyReadingStatMapping_mapsDayNames() {
        val dailyStats = listOf(
            DailyReadingStat("Mon", 10, 300),
            DailyReadingStat("Tue", 15, 450),
            DailyReadingStat("Wed", 20, 600),
            DailyReadingStat("Thu", 0, 0),
            DailyReadingStat("Fri", 5, 120),
            DailyReadingStat("Sat", 30, 900),
            DailyReadingStat("Sun", 8, 240)
        )

        val dayLabels = listOf("M", "T", "W", "T", "F", "S", "S")
        val mapped = (0..6).map { index ->
            val stat = dailyStats.getOrNull(index)
            val label = stat?.dayOfWeek?.firstOrNull()?.toString() ?: dayLabels[index]
            DayReadingStat(
                dayLabel = label,
                pagesRead = stat?.pagesRead ?: 0
            )
        }

        assertEquals(7, mapped.size)
        assertEquals("M", mapped[0].dayLabel)
        assertEquals(10, mapped[0].pagesRead)
        assertEquals("S", mapped[5].dayLabel)
        assertEquals(30, mapped[5].pagesRead)
    }
}
