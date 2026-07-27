package org.dwbn.plugins.playlist.manager

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regression coverage for the currentPosition arithmetic used by [PlaylistManager.moveItem].
 * Verified against a brute-force ground truth (simulate removeAt(from)+add(to,item) on a real
 * list and look up the moved-tracked item's resulting index) for lists of size 2..6 and every
 * valid (from, to, currentIndex) combination.
 */
class PlaylistManagerMoveTest {

    private val invalid = -1

    @Test
    fun currentItem_isTheOneMoved_returnsDestinationIndex() {
        assertEquals(2, PlaylistManager.adjustCurrentIndexForMove(0, 0, 2, invalid))
        assertEquals(0, PlaylistManager.adjustCurrentIndexForMove(3, 3, 0, invalid))
    }

    @Test
    fun invalidPosition_isReturnedUnchanged() {
        assertEquals(invalid, PlaylistManager.adjustCurrentIndexForMove(invalid, 0, 2, invalid))
    }

    @Test
    fun movingItemFromBeforeCurrent_toAtOrBeforeCurrent_shiftsCurrentLeft() {
        // list [a,b,c,d], current = b (index 1), move a(0) -> 1: [b,a,c,d]; b is now at 0.
        assertEquals(0, PlaylistManager.adjustCurrentIndexForMove(1, 0, 1, invalid))
    }

    @Test
    fun movingItemFromBeforeCurrent_toAfterCurrent_leavesCurrentUnchanged() {
        // list [a,b,c,d], current = b (index 1), move a(0) -> 3: [b,c,d,a]; b stays at 0.
        assertEquals(0, PlaylistManager.adjustCurrentIndexForMove(1, 0, 3, invalid))
    }

    @Test
    fun movingItemFromAfterCurrent_toAtOrBeforeCurrent_shiftsCurrentRight() {
        // list [a,b,c,d], current = a (index 0), move c(2) -> 0: [c,a,b,d]; a is now at 1.
        assertEquals(1, PlaylistManager.adjustCurrentIndexForMove(0, 2, 0, invalid))
    }

    @Test
    fun movingItemFromAfterCurrent_toAfterCurrent_leavesCurrentUnchanged() {
        // list [a,b,c,d], current = a (index 0), move c(2) -> 3: [a,b,d,c]; a stays at 0.
        assertEquals(0, PlaylistManager.adjustCurrentIndexForMove(0, 2, 3, invalid))
    }

    @Test
    fun bruteForce_matchesGroundTruthForAllCombinations() {
        for (n in 2..6) {
            val base = (0 until n).toList()
            for (from in 0 until n) {
                for (to in 0 until n) {
                    if (from == to) continue
                    val expectedArr = base.toMutableList()
                    val moved = expectedArr.removeAt(from)
                    expectedArr.add(to, moved)

                    for (currentIndex in 0 until n) {
                        val trackedId = base[currentIndex]
                        val expectedNewIndex = expectedArr.indexOf(trackedId)
                        val got = PlaylistManager.adjustCurrentIndexForMove(currentIndex, from, to, invalid)
                        assertEquals(
                            "n=$n from=$from to=$to currentIndex=$currentIndex",
                            expectedNewIndex,
                            got
                        )
                    }
                }
            }
        }
    }
}
