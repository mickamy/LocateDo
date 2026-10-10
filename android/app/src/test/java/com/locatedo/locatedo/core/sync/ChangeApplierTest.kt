package com.locatedo.locatedo.core.sync

import androidx.room.withTransaction
import com.locatedo.category.v1.BuiltinCategory
import com.locatedo.household.v1.Role
import com.locatedo.locatedo.core.common.uuidV7
import com.locatedo.locatedo.core.data.RoomMembershipRepository
import com.locatedo.locatedo.core.data.asEntity
import com.locatedo.locatedo.core.data.fixedNow
import com.locatedo.locatedo.core.data.inMemoryDatabase
import com.locatedo.locatedo.core.data.place
import com.locatedo.locatedo.core.data.todo
import com.locatedo.locatedo.core.database.LocateDoDatabase
import com.locatedo.locatedo.core.database.PendingWriteEntity
import com.locatedo.locatedo.core.model.MemberRole
import com.locatedo.sync.v1.Change
import com.locatedo.sync.v1.EntityKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ChangeApplierTest {
    private lateinit var database: LocateDoDatabase
    private lateinit var applier: ChangeApplier

    @Before
    fun setUp() {
        database = inMemoryDatabase()
        applier = ChangeApplier(database.categoryDao(), database.placeDao(), database.todoDao(), database.membershipDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun todosKeepTheirPlaceEventAndOnesWithoutItAreSkipped() = runTest {
        val placeId = uuidV7(created)
        val departureId = uuidV7(created)
        val unsetId = uuidV7(created)

        apply(
            placeChange(placeId, version = 1),
            todoChange(departureId, placeId, version = 2, event = com.locatedo.todo.v1.PlaceEvent.PLACE_EVENT_DEPARTURE),
            todoChange(unsetId, placeId, version = 3, event = com.locatedo.todo.v1.PlaceEvent.PLACE_EVENT_UNSPECIFIED),
        )

        assertEquals("departure", database.todoDao().get(departureId.toString())?.placeEvent)
        assertNull(database.todoDao().get(unsetId.toString()))
    }

    @Test
    fun insertsRowsEvenWhenAPlaceArrivesBeforeItsCategory() = runTest {
        val categoryId = uuidV7(created)
        val placeId = uuidV7(created)
        val todoId = uuidV7(created)

        apply(
            placeChange(placeId, version = 2, categoryId = categoryId),
            todoChange(todoId, placeId, version = 4),
            categoryChange(categoryId, version = 3, name = "Gym"),
        )

        val place = checkNotNull(database.placeDao().get(placeId.toString()))
        assertEquals("Store", place.name)
        assertEquals(150.0, place.radiusMeters, 0.0)
        assertEquals(categoryId.toString(), place.categoryId)
        assertEquals(created.toEpochMilli(), place.createdAt)
        assertEquals(updated.toEpochMilli(), place.updatedAt)
        val todo = checkNotNull(database.todoDao().get(todoId.toString()))
        assertEquals(placeId.toString(), todo.placeId)
        assertEquals(created.toEpochMilli(), todo.createdAt)
        assertNull(todo.completedAt)
        assertEquals("Gym", database.categoryDao().observeAll().first().single().name)
    }

    @Test
    fun updatesExistingRowsAndKeepsLocalOnlyFields() = runTest {
        val store = place("Store").copy(lastNotifiedAt = created)
        val milk = todo("Milk", store.id)
        database.placeDao().upsert(store.asEntity())
        database.todoDao().upsert(milk.asEntity())

        apply(
            placeChange(store.id, version = 5, name = "Supermarket"),
            todoChange(milk.id, store.id, version = 6, title = "Oat milk", completedAt = updated),
        )

        val place = checkNotNull(database.placeDao().get(store.id.toString()))
        assertEquals("Supermarket", place.name)
        assertEquals(created.toEpochMilli(), place.lastNotifiedAt)
        assertEquals(fixedNow.toEpochMilli(), place.createdAt)
        val todo = checkNotNull(database.todoDao().get(milk.id.toString()))
        assertEquals("Oat milk", todo.title)
        assertEquals(updated.toEpochMilli(), todo.completedAt)
        assertEquals(fixedNow.toEpochMilli(), todo.createdAt)
        assertEquals(1, database.placeDao().count())
    }

    @Test
    fun keepsWhoAddedATodoAndWhoCheckedItOffUntilItIsReopened() = runTest {
        val store = place("Store")
        val milk = todo("Milk", store.id)
        database.placeDao().upsert(store.asEntity())
        val creator = uuidV7(created)
        val completer = uuidV7(created)

        apply(todoChange(milk.id, store.id, version = 2, completedAt = updated, creatorId = creator, completerId = completer))
        val completed = checkNotNull(database.todoDao().get(milk.id.toString()))
        assertEquals(creator.toString(), completed.creatorId)
        assertEquals(completer.toString(), completed.completerId)

        apply(todoChange(milk.id, store.id, version = 3, creatorId = creator))
        val reopened = checkNotNull(database.todoDao().get(milk.id.toString()))
        assertEquals(creator.toString(), reopened.creatorId)
        assertNull(reopened.completerId)
    }

    @Test
    fun unknownCategoryLeavesThePlaceUncategorizedAndOrphanTodosAreSkipped() = runTest {
        val placeId = uuidV7()

        apply(
            placeChange(placeId, version = 1, categoryId = uuidV7()),
            todoChange(uuidV7(), placeId = uuidV7(), version = 2),
        )

        assertNull(checkNotNull(database.placeDao().get(placeId.toString())).categoryId)
        assertTrue(database.todoDao().observeAll().first().isEmpty())
    }

    @Test
    fun theHighestVersionOfARowWins() = runTest {
        val deletedId = uuidV7()
        val recreatedId = uuidV7()

        apply(
            placeChange(deletedId, version = 1),
            deletionChange(EntityKind.ENTITY_KIND_PLACE, deletedId, version = 2),
            deletionChange(EntityKind.ENTITY_KIND_PLACE, recreatedId, version = 3),
            placeChange(recreatedId, version = 4),
        )

        assertEquals(listOf(recreatedId.toString()), database.placeDao().ids())
    }

    @Test
    fun deletingAPlaceRemovesItsTodosAndMissingRowsAreIgnored() = runTest {
        val store = place("Store")
        database.placeDao().upsert(store.asEntity())
        database.todoDao().upsert(todo("Milk", store.id).asEntity())

        apply(
            deletionChange(EntityKind.ENTITY_KIND_PLACE, store.id, version = 7),
            deletionChange(EntityKind.ENTITY_KIND_TODO, uuidV7(), version = 8),
        )

        assertTrue(database.placeDao().ids().isEmpty())
        assertTrue(database.todoDao().observeAll().first().isEmpty())
    }

    @Test
    fun resetReplacesSyncedDataButKeepsTheQueue() = runTest {
        val local = place("Local")
        database.placeDao().upsert(local.asEntity())
        database.todoDao().upsert(todo("Milk", local.id).asEntity())
        database.pendingWriteDao().insert(PendingWriteEntity(kind = "putPlace", payload = Write.put(local).serialized(), createdAt = 0))
        val serverCategory = uuidV7()

        apply(
            categoryChange(serverCategory, version = 1, builtin = BuiltinCategory.BUILTIN_CATEGORY_SHOPPING),
            placeChange(uuidV7(), version = 2, categoryId = serverCategory),
            reset = true,
        )

        val category = database.categoryDao().observeAll().first().single()
        assertEquals(serverCategory.toString(), category.id)
        assertEquals("shopping", category.builtin)
        assertNull(category.name)
        assertEquals(listOf("Store"), database.placeDao().observeAll().first().map { it.name })
        assertTrue(database.todoDao().observeAll().first().isEmpty())
        assertEquals(1, database.pendingWriteDao().count())
    }

    @Test
    fun membershipsAreUpsertedAndDeletedByUserId() = runTest {
        val owner = uuidV7()
        val member = uuidV7()
        val repository = RoomMembershipRepository(database.membershipDao())

        apply(
            membershipChange(owner, Role.ROLE_OWNER, "Taro", version = 1),
            membershipChange(member, Role.ROLE_MEMBER, "Hanako", version = 2),
        )
        assertEquals(listOf("Taro", "Hanako"), repository.observeAll().first().map { it.displayName })
        apply(
            membershipChange(owner, Role.ROLE_OWNER, "Taro Yamada", version = 3),
            deletionChange(EntityKind.ENTITY_KIND_MEMBERSHIP, member, version = 4),
        )

        val memberships = repository.observeAll().first()
        assertEquals(listOf(owner), memberships.map { it.userId })
        assertEquals(MemberRole.OWNER, memberships.single().role)
        assertEquals("Taro Yamada", memberships.single().displayName)
        assertEquals(created, memberships.single().joinedAt)
        assertNotNull(memberships.single().updatedAt)
    }

    @Test
    fun invalidIdsAreSkipped() = runTest {
        apply(
            com.locatedo.sync.v1.change { place = com.locatedo.place.v1.place { id = "not-a-uuid"; name = "Store" } },
        )

        assertTrue(database.placeDao().ids().isEmpty())
    }

    private suspend fun apply(vararg changes: Change, reset: Boolean = false) {
        database.withTransaction { applier.apply(changes.toList(), reset) }
    }
}
