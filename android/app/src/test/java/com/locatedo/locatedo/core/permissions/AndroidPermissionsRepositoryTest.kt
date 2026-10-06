package com.locatedo.locatedo.core.permissions

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import com.locatedo.locatedo.testing.testPreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AndroidPermissionsRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val application: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun locationStartsNotDeterminedAndIsDeniedOnceAsked() = runTest {
        val repository = repository()

        assertEquals(LocationAuth.NOT_DETERMINED, repository.observe().first().location)

        repository.markLocationRequested()

        assertEquals(LocationAuth.DENIED, repository.observe().first().location)
    }

    @Test
    fun aForegroundGrantIsWhenInUseAndABackgroundGrantIsAlways() = runTest {
        val repository = repository()

        shadowOf(application).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
        assertEquals(LocationAuth.WHEN_IN_USE, repository.observe().first().location)

        shadowOf(application).grantPermissions(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        assertEquals(LocationAuth.ALWAYS, repository.observe().first().location)
    }

    @Test
    fun notificationsFollowTheSystemToggleAndWhetherTheAppAsked() = runTest {
        val notificationManager = application.getSystemService(NotificationManager::class.java)
        shadowOf(notificationManager).setNotificationsEnabled(false)
        val repository = repository()

        assertEquals(NotificationAuth.NOT_DETERMINED, repository.observe().first().notifications)

        repository.markNotificationsRequested()
        assertEquals(NotificationAuth.DENIED, repository.observe().first().notifications)

        shadowOf(notificationManager).setNotificationsEnabled(true)
        assertEquals(NotificationAuth.AUTHORIZED, repository.observe().first().notifications)
    }

    @Test
    fun refreshRecomputesForAnOpenCollector() = runTest {
        val repository = repository()
        val state = repository.observe().stateIn(backgroundScope)
        assertEquals(LocationAuth.NOT_DETERMINED, state.value.location)

        shadowOf(application).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        repository.refresh()

        assertEquals(LocationAuth.WHEN_IN_USE, state.first { it.location != LocationAuth.NOT_DETERMINED }.location)
    }

    private fun TestScope.repository(): AndroidPermissionsRepository {
        val preferences = testPreferences(folder.root, TestScope(UnconfinedTestDispatcher(testScheduler)))
        return AndroidPermissionsRepository(application, preferences)
    }
}
