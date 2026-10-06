package com.locatedo.locatedo.core.sync

import com.locatedo.category.v1.BuiltinCategory as ProtoBuiltin
import com.locatedo.locatedo.core.common.uuidV7
import com.locatedo.locatedo.core.model.BuiltinCategory
import com.locatedo.locatedo.core.model.Category
import com.locatedo.locatedo.core.model.Place
import com.locatedo.locatedo.core.model.Todo
import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtoInputTest {
    private val now = Instant.parse("2026-10-06T00:00:00Z")
    private val shopping = Category(id = uuidV7(now), builtin = BuiltinCategory.SHOPPING, icon = "cart", color = "green", sortOrder = 0, updatedAt = now)
    private val gym = Category(id = uuidV7(now), name = "Gym", icon = "dumbbell", color = "teal", sortOrder = 4, updatedAt = now)
    private val store = Place(id = uuidV7(now), name = "Store", latitude = 35.0, longitude = 139.0, radiusMeters = 149.6, categoryId = shopping.id, sortOrder = 2, createdAt = now)

    @Test
    fun aBuiltinCarriesItsKindAndNoName() {
        val input = ProtoInput.category(shopping)

        assertEquals(shopping.id.toString(), input.id)
        assertEquals(ProtoBuiltin.BUILTIN_CATEGORY_SHOPPING, input.builtin)
        assertFalse(input.hasName())
        assertEquals("cart", input.icon)
    }

    @Test
    fun aCustomCategoryCarriesItsName() {
        val input = ProtoInput.category(gym)

        assertEquals(ProtoBuiltin.BUILTIN_CATEGORY_UNSPECIFIED, input.builtin)
        assertTrue(input.hasName())
        assertEquals("Gym", input.name)
        assertEquals(4, input.sortOrder)
    }

    @Test
    fun aPlaceRoundsItsRadiusAndKeepsAnOptionalCategory() {
        val input = ProtoInput.place(store)

        assertEquals(150, input.radiusM)
        assertEquals(35.0, input.lat, 0.0)
        assertEquals(139.0, input.lng, 0.0)
        assertEquals(shopping.id.toString(), input.categoryId)
        assertEquals(2, input.sortOrder)
        assertFalse(ProtoInput.place(store.copy(categoryId = null)).hasCategoryId())
    }

    @Test
    fun aTodoKeepsAnOptionalAssignee() {
        val assignee = UUID.fromString("0199bd00-0000-7000-8000-000000000002")
        val todo = Todo(id = uuidV7(now), title = "Milk", placeId = store.id, assigneeId = assignee, createdAt = now)

        val input = ProtoInput.todo(todo)

        assertEquals(store.id.toString(), input.placeId)
        assertEquals("Milk", input.title)
        assertEquals(assignee.toString(), input.assigneeId)
        assertFalse(ProtoInput.todo(todo.copy(assigneeId = null)).hasAssigneeId())
    }

    @Test
    fun theInitialUploadCarriesCompletionTimes() {
        val householdId = uuidV7(now)
        val open = Todo(id = uuidV7(now), title = "Milk", placeId = store.id, createdAt = now)
        val done = Todo(id = uuidV7(now), title = "Bread", placeId = store.id, completedAt = now.plusSeconds(30), createdAt = now)

        val request = InitialUpload.request(householdId, listOf(shopping, gym), listOf(store), listOf(open, done))

        assertEquals(householdId.toString(), request.id)
        assertEquals(listOf(shopping.id, gym.id).map { it.toString() }, request.categoriesList.map { it.id })
        assertEquals(listOf("Store"), request.placesList.map { it.name })
        assertEquals(listOf("Milk", "Bread"), request.todosList.map { it.todo.title })
        assertFalse(request.todosList[0].hasCompletedAt())
        assertEquals(now.plusSeconds(30), request.todosList[1].completedAt.toInstant())
    }

    @Test
    fun timestampsRoundTrip() {
        val instant = Instant.parse("2026-10-06T12:34:56.789Z")

        assertEquals(instant, instant.toTimestamp().toInstant())
    }
}
