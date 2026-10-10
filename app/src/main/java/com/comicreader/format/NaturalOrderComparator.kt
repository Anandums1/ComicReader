package com.comicreader.format

import java.math.BigInteger

/**
 * Natural order comparator for sorting filenames numerically and alphabetically.
 * Ensures that e.g. "page2.jpg" is sorted before "page10.jpg".
 */
object NaturalOrderComparator : Comparator<String> {
    private val CHUNK_REGEX = Regex("(\\d+)|(\\D+)")

    override fun compare(a: String, b: String): Int {
        if (a == b) return 0

        val aChunks = CHUNK_REGEX.findAll(a).map { it.value }.toList()
        val bChunks = CHUNK_REGEX.findAll(b).map { it.value }.toList()

        val minLen = minOf(aChunks.size, bChunks.size)
        for (i in 0 until minLen) {
            val aChunk = aChunks[i]
            val bChunk = bChunks[i]
            val aIsNum = aChunk.first().isDigit()
            val bIsNum = bChunk.first().isDigit()

            if (aIsNum && bIsNum) {
                val aNum = aChunk.toBigIntegerOrNull() ?: BigInteger.ZERO
                val bNum = bChunk.toBigIntegerOrNull() ?: BigInteger.ZERO
                val numCmp = aNum.compareTo(bNum)
                if (numCmp != 0) return numCmp

                // When numeric values match (e.g. "01" vs "1"), compare length
                val lenCmp = aChunk.length.compareTo(bChunk.length)
                if (lenCmp != 0) return lenCmp
            } else {
                val strCmp = aChunk.compareTo(bChunk, ignoreCase = true)
                if (strCmp != 0) return strCmp
            }
        }

        val sizeCmp = aChunks.size.compareTo(bChunks.size)
        if (sizeCmp != 0) return sizeCmp

        return a.compareTo(b)
    }
}
