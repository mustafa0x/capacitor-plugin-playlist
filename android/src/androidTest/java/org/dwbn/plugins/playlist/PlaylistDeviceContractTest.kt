package org.dwbn.plugins.playlist

import android.app.Application
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.dwbn.plugins.playlist.manager.PlaylistManager
import org.dwbn.plugins.playlist.service.MediaService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaylistDeviceContractTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Suppress("DEPRECATION")
    @Test
    fun mediaPlaybackServiceIsMergedIntoTheHostManifest() {
        val services = context.packageManager
            .getPackageInfo(context.packageName, PackageManager.GET_SERVICES)
            .services
            .orEmpty()

        assertTrue(services.any { it.name == MediaService::class.java.name })
    }

    @Test
    fun rateAndVolumePersistBeforeAPlayerIsCreated() {
        val manager = PlaylistManager(context.applicationContext as Application)

        manager.setPlaybackSpeed(2f)
        manager.setPlaybackSpeed(0f)
        manager.setVolume(0.4f, 0.6f)

        assertEquals(2f, manager.getPlaybackSpeed())
        assertEquals(0.4f, manager.getVolumeLeft())
        assertEquals(0.6f, manager.getVolumeRight())
    }
}
