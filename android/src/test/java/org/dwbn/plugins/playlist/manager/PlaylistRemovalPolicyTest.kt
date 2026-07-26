package org.dwbn.plugins.playlist.manager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistRemovalPolicyTest {

    @Test
    fun removingCurrentTerminalTrackHasNoSuccessor() {
        assertNull(resolvePostRemovalPosition(2, setOf(2), remainingCount = 2))
    }

    @Test
    fun removingCurrentMiddleTrackSelectsItsSuccessor() {
        assertEquals(1, resolvePostRemovalPosition(1, setOf(1), remainingCount = 2))
    }

    @Test
    fun removingEarlierTracksShiftsCurrentPosition() {
        assertEquals(1, resolvePostRemovalPosition(3, setOf(0, 2), remainingCount = 2))
    }

    @Test
    fun terminalCurrentRemovalClearsCurrentItem() {
        assertTrue(shouldClearCurrentItemAfterRemoval(removingCurrent = true, selectedPosition = null))
    }

    @Test
    fun removalWithSuccessorKeepsCurrentItem() {
        assertFalse(shouldClearCurrentItemAfterRemoval(removingCurrent = true, selectedPosition = 1))
    }

    @Test
    fun unrelatedRemovalDoesNotClearCurrentItem() {
        assertFalse(shouldClearCurrentItemAfterRemoval(removingCurrent = false, selectedPosition = null))
    }

    @Test
    fun resetWithCurrentItemClearsCurrentItem() {
        assertTrue(shouldEmitCurrentItemCleared(hasCurrentItem = true, leavesNoCurrentItem = true))
    }

    @Test
    fun resetWithoutCurrentItemDoesNotEmitDuplicateClear() {
        assertFalse(shouldEmitCurrentItemCleared(hasCurrentItem = false, leavesNoCurrentItem = true))
    }

    @Test
    fun nonEmptyPlaylistReplacementDoesNotEmitTransientClear() {
        assertFalse(shouldEmitCurrentItemCleared(hasCurrentItem = true, leavesNoCurrentItem = false))
    }
}
