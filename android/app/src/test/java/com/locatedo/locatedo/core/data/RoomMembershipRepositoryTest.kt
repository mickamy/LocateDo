package com.locatedo.locatedo.core.data

import com.locatedo.locatedo.core.database.LocateDoDatabase
import com.locatedo.locatedo.core.database.MembershipEntity
import com.locatedo.locatedo.core.model.MemberRole
import java.time.Instant
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
class RoomMembershipRepositoryTest {
    private lateinit var database: LocateDoDatabase
    private lateinit var repository: RoomMembershipRepository

    @Before
    fun setUp() {
        database = inMemoryDatabase()
        repository = RoomMembershipRepository(database.membershipDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun listsMembersInTheOrderTheyJoined() = runTest {
        val owner = UUID.randomUUID()
        val member = UUID.randomUUID()
        database.membershipDao().upsert(MembershipEntity(member.toString(), "member", "Hanako", joinedAt = 2_000, updatedAt = 2_000))
        database.membershipDao().upsert(MembershipEntity(owner.toString(), "owner", "Taro", joinedAt = 1_000, updatedAt = 1_000))

        val memberships = repository.observeAll().first()

        assertEquals(listOf(owner, member), memberships.map { it.userId })
        assertEquals(listOf(MemberRole.OWNER, MemberRole.MEMBER), memberships.map { it.role })
        assertEquals(Instant.ofEpochMilli(1_000), memberships.first().joinedAt)
    }
}
