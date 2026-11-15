package dev.reflux.ui

import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import dev.reflux.design.BrandMark
import dev.reflux.ui.components.LockupMarkRight
import dev.reflux.ui.components.lockupMarkWidth
import kotlin.test.Test
import kotlin.test.assertTrue

/** The television's closed rail shows only the lockup's mark, clipped to [lockupMarkWidth]: all of it, and none of the wordmark. */
class BrandLockupTest {
    private val parts = BrandMark.LOCKUP.split('Z').map { it.trim() }.filter { it.isNotEmpty() }
        .map { PathParser().parsePathString("$it Z").toPath().getBounds() }

    @Test
    fun theMarkOnlyWidthHoldsTheWholeMarkAndNoneOfTheWord() {
        val height = 20.dp
        val clip = lockupMarkWidth(height).value / height.value * BrandMark.LOCKUP_HEIGHT
        val mark = parts.filter { it.right <= LockupMarkRight }
        val word = parts.filter { it.right > LockupMarkRight }
        assertTrue(mark.size >= 2, "the loop and the tail are the mark's outlines: $mark")
        assertTrue(word.isNotEmpty(), "the wordmark's outlines lie beyond the mark")
        assertTrue(mark.all { it.right <= clip }, "the clip ($clip) cuts the mark, whose right edge is ${mark.maxOf { it.right }}")
        assertTrue(word.all { it.left > clip }, "the clip ($clip) shows part of the word, which starts at ${word.minOf { it.left }}")
        // The cut this replaced: the mark's own 100-unit square, which ends inside the loop.
        assertTrue(mark.maxOf { it.right } > BrandMark.VIEWPORT, "the lockup draws the mark wider than its square")
    }
}
