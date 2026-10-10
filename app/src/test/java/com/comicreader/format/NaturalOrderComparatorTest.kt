package com.comicreader.format

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NaturalOrderComparatorTest {

    @Test
    fun sortsUnpaddedNumericFilenamesNaturally() {
        val input = listOf("page10.jpg", "page1.jpg", "page2.jpg", "page20.jpg", "page3.jpg")
        val sorted = input.sortedWith(NaturalOrderComparator)

        assertThat(sorted).containsExactly(
            "page1.jpg",
            "page2.jpg",
            "page3.jpg",
            "page10.jpg",
            "page20.jpg"
        ).inOrder()
    }

    @Test
    fun sortsZeroPaddedFilenamesCorrectly() {
        val input = listOf("page010.jpg", "page001.jpg", "page002.jpg")
        val sorted = input.sortedWith(NaturalOrderComparator)

        assertThat(sorted).containsExactly(
            "page001.jpg",
            "page002.jpg",
            "page010.jpg"
        ).inOrder()
    }

    @Test
    fun handlesMultiSegmentAlphanumericNames() {
        val input = listOf("ch2_p1.png", "ch1_p10.png", "ch1_p2.png", "ch1_p1.png")
        val sorted = input.sortedWith(NaturalOrderComparator)

        assertThat(sorted).containsExactly(
            "ch1_p1.png",
            "ch1_p2.png",
            "ch1_p10.png",
            "ch2_p1.png"
        ).inOrder()
    }

    @Test
    fun handlesCaseInsensitiveAlphanumericComparison() {
        val input = listOf("PAGE2.jpg", "page1.jpg", "Page10.jpg")
        val sorted = input.sortedWith(NaturalOrderComparator)

        assertThat(sorted).containsExactly(
            "page1.jpg",
            "PAGE2.jpg",
            "Page10.jpg"
        ).inOrder()
    }

    @Test
    fun handlesIdenticalStrings() {
        assertThat(NaturalOrderComparator.compare("same.jpg", "same.jpg")).isEqualTo(0)
    }

    @Test
    fun handlesLargeNumbersWithoutOverflow() {
        val num1 = "file9999999999999999999999999999.png"
        val num2 = "file10000000000000000000000000000.png"
        val sorted = listOf(num2, num1).sortedWith(NaturalOrderComparator)

        assertThat(sorted).containsExactly(num1, num2).inOrder()
    }
}
