package com.locatedo.locatedo.core.auth

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class DataStoreSessionStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun roundTripsAndClears() = runTest {
        val store = store()
        val session = Session(
            userId = UUID.fromString("0199bd00-0000-7000-8000-000000000001"),
            accessToken = "access",
            accessTokenExpiresAt = Instant.parse("2026-10-06T01:00:00Z"),
            refreshToken = "refresh",
        )
        assertNull(store.load())

        store.save(session)
        assertEquals(session, store.load())

        store.clear()
        assertNull(store.load())
    }

    private fun TestScope.store(): DataStoreSessionStore {
        val dataStore = PreferenceDataStoreFactory.create(scope = TestScope(UnconfinedTestDispatcher(testScheduler))) {
            File(folder.root, "session.preferences_pb")
        }
        return DataStoreSessionStore(dataStore)
    }
}
