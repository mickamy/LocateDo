package com.locatedo.locatedo.core.sync

import com.locatedo.locatedo.core.data.category
import com.locatedo.locatedo.core.data.fixedNow
import com.locatedo.locatedo.core.data.place
import com.locatedo.locatedo.core.data.todo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class WriteTest {
    @Test
    fun roundTripsEveryKind() {
        val gym = category("Gym")
        val store = place("Store").copy(categoryId = gym.id)
        val milk = todo("Milk", store.id).copy(completedAt = fixedNow)
        val writes = listOf(
            Write.put(store),
            Write.deletePlace(store.id),
            Write.put(milk),
            Write.completion(milk.id, milk.completedAt),
            Write.deleteTodo(milk.id),
            Write.put(gym),
            Write.deleteCategory(gym.id),
        )

        for (write in writes) {
            assertEquals(write, Write.decode(write.kind, write.serialized()))
        }
        assertEquals(WriteKind.entries.toList(), writes.map { it.kind })
    }

    @Test
    fun completionOfAnOpenTodoClearsTheTimestamp() {
        val milk = todo("Milk", place("Store").id)

        val write = Write.completion(milk.id, null) as Write.SetTodoCompletion

        assertEquals(milk.id.toString(), write.request.id)
        assertFalse(write.request.hasCompletedAt())
    }

    @Test
    fun kindsKeepTheirStoredKeys() {
        for (kind in WriteKind.entries) {
            assertEquals(kind, WriteKind.fromKey(kind.key))
        }
        assertEquals(null, WriteKind.fromKey("unknown"))
    }
}
