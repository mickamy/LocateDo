package com.locatedo.locatedo.core.data

import com.locatedo.locatedo.core.database.LocateDoDatabase
import com.locatedo.locatedo.core.model.Plan
import com.locatedo.locatedo.core.model.SyncState
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoomSyncStateRepositoryTest {
    private lateinit var database: LocateDoDatabase
    private lateinit var repository: RoomSyncStateRepository

    @Before
    fun setUp() {
        database = inMemoryDatabase()
        repository = RoomSyncStateRepository(database.syncStateDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun startsEmptyAndRoundTrips() = runTest {
        assertEquals(SyncState(), repository.get())
        assertEquals(SyncState(), repository.observe().first())

        val state = SyncState(householdId = UUID.fromString("0199bd00-0000-7000-8000-0000000000aa"), cursor = 42, plan = Plan.PRO)
        repository.set(state)

        assertEquals(state, repository.get())
        assertEquals(state, repository.observe().first())

        repository.clear()

        assertEquals(SyncState(), repository.get())
    }
}
