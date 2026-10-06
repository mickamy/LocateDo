package com.locatedo.locatedo.core.notifications

import com.locatedo.locatedo.core.common.uuidV7
import com.locatedo.locatedo.core.model.Todo
import java.time.Duration
import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationPolicyTest {
    private val now = Instant.ofEpochSecond(100_000)

    @Test
    fun staysQuietWithoutOpenTodos() {
        assertFalse(NotificationPolicy.shouldNotify(openTodoCount = 0, lastNotifiedAt = null, now = now))
    }

    @Test
    fun notifiesTheFirstTime() {
        assertTrue(NotificationPolicy.shouldNotify(openTodoCount = 1, lastNotifiedAt = null, now = now))
    }

    @Test
    fun respectsTheCooldown() {
        val tenMinutesAgo = now.minus(Duration.ofMinutes(10))
        assertFalse(NotificationPolicy.shouldNotify(openTodoCount = 2, lastNotifiedAt = tenMinutesAgo, now = now))

        val thirtyOneMinutesAgo = now.minus(Duration.ofMinutes(31))
        assertTrue(NotificationPolicy.shouldNotify(openTodoCount = 2, lastNotifiedAt = thirtyOneMinutesAgo, now = now))
    }

    @Test
    fun bodyListsUpToThreeTitles() {
        assertEquals(NotificationBody(listOf("Milk", "Bread"), more = 0), NotificationPolicy.body(listOf("Milk", "Bread")))
    }

    @Test
    fun bodyCountsTheRest() {
        val body = NotificationPolicy.body(listOf("Milk", "Bread", "Eggs", "Butter", "Jam"))

        assertEquals(listOf("Milk", "Bread", "Eggs"), body.titles)
        assertEquals(2, body.more)
    }

    @Test
    fun notifiesOnlyForTodosAssignedToAnyoneOrToTheUser() {
        val me = UUID.randomUUID()
        val partner = UUID.randomUUID()
        val placeId = uuidV7(now)
        val anyone = Todo(id = uuidV7(now), title = "Milk", placeId = placeId, createdAt = now)
        val mine = Todo(id = uuidV7(now), title = "Bread", placeId = placeId, assigneeId = me, createdAt = now)
        val theirs = Todo(id = uuidV7(now), title = "Eggs", placeId = placeId, assigneeId = partner, createdAt = now)
        val todos = listOf(anyone, mine, theirs)

        assertEquals(listOf("Milk", "Bread"), NotificationPolicy.notifiableTodos(todos, me).map { it.title })
        assertEquals(listOf("Milk", "Eggs"), NotificationPolicy.notifiableTodos(todos, partner).map { it.title })
        assertEquals(listOf("Milk", "Bread", "Eggs"), NotificationPolicy.notifiableTodos(todos, null).map { it.title })
    }
}
