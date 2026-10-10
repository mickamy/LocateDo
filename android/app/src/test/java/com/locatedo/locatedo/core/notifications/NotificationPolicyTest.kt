package com.locatedo.locatedo.core.notifications

import com.locatedo.locatedo.core.common.uuidV7
import com.locatedo.locatedo.core.model.Todo
import java.time.Duration
import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationPolicyTest {
    private val now = Instant.ofEpochSecond(100_000)

    @Test
    fun notifiesTheFirstTime() {
        assertNull(suppression())
    }

    @Test
    fun eachReasonOnItsOwn() {
        assertEquals(ArrivalSuppression.NO_OPEN_TODOS, suppression(open = 0, notifiable = 0))
        assertEquals(ArrivalSuppression.ASSIGNED_TO_OTHERS, suppression(open = 2, notifiable = 0))
        assertEquals(ArrivalSuppression.RECENTLY_NOTIFIED, suppression(lastNotifiedAt = now.minus(Duration.ofMinutes(10))))
        assertEquals(ArrivalSuppression.NOTIFICATIONS_OFF, suppression(allowed = false))
    }

    @Test
    fun theCooldownEndsAfterThirtyMinutes() {
        assertNull(suppression(lastNotifiedAt = now.minus(Duration.ofMinutes(30))))
    }

    @Test
    fun overlappingReasonsReportTheFirstInOrder() {
        val recent = now.minus(Duration.ofMinutes(10))
        assertEquals(
            ArrivalSuppression.NO_OPEN_TODOS,
            suppression(open = 0, notifiable = 0, lastNotifiedAt = recent, allowed = false),
        )
        assertEquals(
            ArrivalSuppression.ASSIGNED_TO_OTHERS,
            suppression(open = 1, notifiable = 0, lastNotifiedAt = recent, allowed = false),
        )
        assertEquals(ArrivalSuppression.RECENTLY_NOTIFIED, suppression(lastNotifiedAt = recent, allowed = false))
    }

    @Test
    fun keysMatchIos() {
        assertEquals(
            listOf("no_open_todos", "assigned_to_others", "recently_notified", "notifications_off", "short_stay", "schedule_failed"),
            ArrivalSuppression.entries.map { it.key },
        )
    }

    private fun suppression(
        open: Int = 1,
        notifiable: Int = 1,
        lastNotifiedAt: Instant? = null,
        allowed: Boolean = true,
    ): ArrivalSuppression? = NotificationPolicy.suppression(open, notifiable, lastNotifiedAt, allowed, now)

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
