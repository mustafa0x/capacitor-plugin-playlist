package org.dwbn.plugins.playlist.manager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
