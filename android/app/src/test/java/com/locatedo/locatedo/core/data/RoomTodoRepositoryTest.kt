package com.locatedo.locatedo.core.data

import com.locatedo.locatedo.core.analytics.AnalyticsEvent
import com.locatedo.locatedo.core.analytics.AnalyticsUserProperty
import com.locatedo.locatedo.core.analytics.TodoAddVia
import com.locatedo.locatedo.core.analytics.WriteAnalytics
import com.locatedo.locatedo.core.database.LocateDoDatabase
import com.locatedo.locatedo.core.model.FreeLimit
import com.locatedo.locatedo.core.model.Place
import com.locatedo.locatedo.core.model.TodoDeletionVia
import com.locatedo.locatedo.core.sync.Write
import com.locatedo.locatedo.core.sync.WriteQueue
import com.locatedo.locatedo.core.sync.toInstant
import com.locatedo.locatedo.testing.FakeAnalytics
import com.locatedo.locatedo.testing.fakeAuthenticator
import com.locatedo.locatedo.testing.testPreferences
import com.locatedo.locatedo.testing.testSession
import java.time.Duration
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoomTodoRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var database: LocateDoDatabase
    private lateinit var places: RoomPlaceRepository
    private lateinit var repository: RoomTodoRepository
    private lateinit var writeAnalytics: WriteAnalytics
    private val proStatus = FakeProStatus()
    private val authenticator = fakeAuthenticator()
    private val analytics = FakeAnalytics()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val store: Place = place("Store")

    @Before
    fun setUp() = runTest {
        database = inMemoryDatabase()
        val queue = WriteQueue(database.pendingWriteDao(), authenticator, fixedClock)
        writeAnalytics = WriteAnalytics(analytics, testPreferences(folder.root, scope), fixedClock)
        places = RoomPlaceRepository(database, database.placeDao(), proStatus, queue, writeAnalytics, fixedClock)
        repository = RoomTodoRepository(database, database.todoDao(), proStatus, queue, authenticator, writeAnalytics, fixedClock)
        places.add(store)
    }

    @After
    fun tearDown() {
        database.close()
        scope.cancel()
    }

    @Test
    fun addingATodoAttachesItToThePlace() = runTest {
        val milk = todo("Milk", store.id)

        assertNull(repository.add(milk))

        val withTodos = places.observeWithTodos(store.id).first()
        assertEquals(listOf(milk), withTodos?.openTodos)
    }

    @Test
    fun theSixteenthOpenTodoHitsTheFreeLimit() = runTest {
        repeat(FreeLimit.OPEN_TODOS.max) { index ->
            assertNull(repository.add(todo("Todo $index", store.id)))
        }

        assertEquals(FreeLimit.OPEN_TODOS, repository.add(todo("One too many", store.id)))
    }

    @Test
    fun completingFreesASlotAndReopeningTakesItBack() = runTest {
        val first = todo("First", store.id)
        repository.add(first)
        repeat(FreeLimit.OPEN_TODOS.max - 1) { index ->
            repository.add(todo("Todo $index", store.id))
        }

        assertNull(repository.setCompleted(first.id, completed = true))
        assertNull(repository.add(todo("Fits now", store.id)))
        assertEquals(FreeLimit.OPEN_TODOS, repository.setCompleted(first.id, completed = false))

        val stored = repository.observeAll().first().single { it.id == first.id }
        assertTrue(stored.isCompleted)
        assertEquals(fixedNow, stored.completedAt)
    }

    @Test
    fun completingAnUnknownTodoDoesNothing() = runTest {
        assertNull(repository.setCompleted(todo("Ghost", store.id).id, completed = true))

        assertEquals(emptyList<Any>(), repository.observeAll().first())
    }

    @Test
    fun deleteRemovesTheGivenTodos() = runTest {
        val milk = todo("Milk", store.id)
        val eggs = todo("Eggs", store.id)
        repository.add(milk)
        repository.add(eggs)

        repository.delete(listOf(milk.id), TodoDeletionVia.SWIPE)

        assertEquals(listOf(eggs), repository.observeAll().first())
    }

    @Test
    fun undoingADeleteBringsBackTheSameTodo() = runTest {
        authenticator.signIn(testSession)
        val milk = todo("Milk", store.id).copy(assigneeId = UUID.randomUUID())
        repository.add(milk)

        val deleted = repository.delete(listOf(milk.id), TodoDeletionVia.MENU)
        repository.restore(deleted)

        assertEquals(listOf(milk), deleted)
        assertEquals(listOf(milk), repository.observeAll().first())
        val kinds = database.queuedWrites().takeLast(2).map { it::class }
        assertEquals(listOf(Write.DeleteTodo::class, Write.PutTodo::class), kinds)
    }

    @Test
    fun undoingADeleteKeepsItCompleted() = runTest {
        authenticator.signIn(testSession)
        val milk = todo("Milk", store.id)
        repository.add(milk)
        repository.setCompleted(milk.id, true)

        repository.restore(repository.delete(listOf(milk.id), TodoDeletionVia.SWIPE))

        assertEquals(fixedNow, repository.observeAll().first().single().completedAt)
        val kinds = database.queuedWrites().takeLast(3).map { it::class }
        assertEquals(listOf(Write.DeleteTodo::class, Write.PutTodo::class, Write.SetTodoCompletion::class), kinds)
    }

    @Test
    fun undoingADeleteSkipsTodosWhosePlaceIsGone() = runTest {
        val milk = todo("Milk", store.id)
        repository.add(milk)
        val deleted = repository.delete(listOf(milk.id), TodoDeletionVia.SWIPE)
        places.delete(store.id)

        repository.restore(deleted)

        assertEquals(emptyList<Any>(), repository.observeAll().first())
        assertEquals(0, analytics.count(AnalyticsEvent.TODO_DELETE_UNDONE))
    }

    @Test
    fun deletingAndUndoingSayWhereAndHowMany() = runTest {
        val milk = todo("Milk", store.id)
        val eggs = todo("Eggs", store.id)
        repository.add(milk)
        repository.add(eggs)

        repository.restore(repository.delete(listOf(milk.id, eggs.id), TodoDeletionVia.COMPLETED_BULK))

        val deletion = analytics.values(AnalyticsEvent.TODO_DELETED)
        assertEquals("completed_bulk", deletion["via"])
        assertEquals(2L, deletion["count"])
        assertEquals(2L, analytics.values(AnalyticsEvent.TODO_DELETE_UNDONE)["count"])
    }

    @Test
    fun togglingCompletionQueuesTheCompletionEachTime() = runTest {
        authenticator.signIn(testSession)
        val milk = todo("Milk", store.id)
        repository.add(milk)

        repository.setCompleted(milk.id, true)
        repository.setCompleted(milk.id, false)

        val completions = database.queuedWrites().filterIsInstance<Write.SetTodoCompletion>().map { it.request }
        assertEquals(listOf(milk.id.toString(), milk.id.toString()), completions.map { it.id })
        assertEquals(listOf(true, false), completions.map { it.hasCompletedAt() })
        assertEquals(fixedNow, completions[0].completedAt.toInstant())
    }

    @Test
    fun checkingOffNotesTheSignedInUserAndReopeningClearsIt() = runTest {
        authenticator.signIn(testSession)
        val milk = todo("Milk", store.id)
        repository.add(milk)

        repository.setCompleted(milk.id, true)
        assertEquals(testSession.userId, repository.observeAll().first().single().completerId)

        repository.setCompleted(milk.id, false)
        assertNull(repository.observeAll().first().single().completerId)
    }

    @Test
    fun checkingOffFromANotificationQueuesItAndSaysSo() = runTest {
        authenticator.signIn(testSession)
        val milk = todo("Milk", store.id)
        repository.add(milk)

        assertTrue(repository.checkOff(milk.id))

        val checkedOff = repository.observeAll().first().single()
        assertEquals(fixedNow, checkedOff.completedAt)
        assertEquals(testSession.userId, checkedOff.completerId)
        assertEquals(milk.id.toString(), database.queuedWrites().filterIsInstance<Write.SetTodoCompletion>().single().request.id)
        assertEquals("action", analytics.values(AnalyticsEvent.TODO_COMPLETED)["via"])
    }

    @Test
    fun checkingOffWhatIsAlreadyDoneOrGoneChangesNothing() = runTest {
        val milk = todo("Milk", store.id)
        repository.add(milk)
        repository.setCompleted(milk.id, true)

        assertFalse(repository.checkOff(milk.id))
        assertFalse(repository.checkOff(UUID.randomUUID()))
        assertEquals(1, analytics.count(AnalyticsEvent.TODO_COMPLETED))
    }

    @Test
    fun updatingQueuesAPutWithTheAssignee() = runTest {
        authenticator.signIn(testSession)
        val milk = todo("Milk", store.id)
        repository.add(milk)
        val assignee = UUID.randomUUID()

        repository.update(milk.copy(assigneeId = assignee))

        val put = database.queuedWrites().last() as Write.PutTodo
        assertEquals(assignee.toString(), put.input.assigneeId)
    }

    @Test
    fun deletingSeveralTodosQueuesADeleteForEach() = runTest {
        authenticator.signIn(testSession)
        val milk = todo("Milk", store.id)
        val bread = todo("Bread", store.id)
        repository.add(milk)
        repository.add(bread)

        repository.delete(listOf(milk.id, bread.id), TodoDeletionVia.SWIPE)

        val deletes = database.queuedWrites().filterIsInstance<Write.DeleteTodo>().map { it.request.id }
        assertEquals(listOf(milk.id.toString(), bread.id.toString()), deletes)
        assertEquals(listOf(1L, 2L, 3L, 4L), database.pendingWriteDao().all().map { it.sequence })
    }

    @Test
    fun addingATodoLogsTheOpenCounts() = runTest {
        val pharmacy = place("Pharmacy")
        places.add(pharmacy)
        repository.add(todo("Stamps", pharmacy.id))

        repository.add(todo("Milk", store.id).copy(assigneeId = UUID.randomUUID()))

        val values = analytics.values(AnalyticsEvent.TODO_ADDED)
        assertEquals(2L, values["open_todo_count"])
        assertEquals(1L, values["place_open_todos"])
        assertEquals(1L, values["assigned"])
        assertEquals("todo_editor", values["via"])
        assertEquals("2", analytics.userProperties[AnalyticsUserProperty.OPEN_TODO_COUNT])
    }

    @Test
    fun aTodoTypedWithANewPlaceSaysSo() = runTest {
        repository.add(todo("Milk", store.id), TodoAddVia.PLACE_EDITOR)

        assertEquals("place_editor", analytics.values(AnalyticsEvent.TODO_ADDED)["via"])
    }

    @Test
    fun theRemainingRoomCountsOpenTodosOnlyOnTheFreePlan() = runTest {
        repository.add(todo("Milk", store.id))
        assertEquals(FreeLimit.OPEN_TODOS.max - 1, repository.remainingOpen())

        proStatus.pro = true

        assertNull(repository.remainingOpen())
    }

    @Test
    fun reopeningOverTheFreeLimitLogsTheLimit() = runTest {
        val done = todo("Done", store.id)
        repository.add(done)
        repository.setCompleted(done.id, completed = true)
        repeat(FreeLimit.OPEN_TODOS.max) { index ->
            repository.add(todo("Todo $index", store.id))
        }

        repository.setCompleted(done.id, completed = false)

        assertEquals("todo", analytics.values(AnalyticsEvent.LIMIT_REACHED)["kind"])
    }

    @Test
    fun completingAndDeletingTodosKeepTheOpenCountCurrent() = runTest {
        val milk = todo("Milk", store.id)
        val eggs = todo("Eggs", store.id)
        repository.add(milk)
        repository.add(eggs)

        repository.setCompleted(milk.id, completed = true)
        assertEquals("1", analytics.userProperties[AnalyticsUserProperty.OPEN_TODO_COUNT])

        repository.delete(listOf(eggs.id), TodoDeletionVia.SWIPE)
        assertEquals("0", analytics.userProperties[AnalyticsUserProperty.OPEN_TODO_COUNT])
    }

    @Test
    fun completingATodoInTheAppSaysSo() = runTest {
        val milk = todo("Milk", store.id, createdAt = fixedNow.minus(Duration.ofHours(26)))
        repository.add(milk)
        repository.add(todo("Eggs", store.id))

        repository.setCompleted(milk.id, completed = true)

        val values = analytics.values(AnalyticsEvent.TODO_COMPLETED)
        assertEquals("app", values["via"])
        assertEquals(26L, values["age_hours"])
        assertEquals(1L, values["open_todo_count"])
    }

    @Test
    fun completingRightAfterOpeningTheArrivalNotificationCountsForIt() = runTest {
        val milk = todo("Milk", store.id)
        repository.add(milk)

        writeAnalytics.arrivalOpened(store.id, notifiedAt = fixedNow.minusSeconds(10))
        repository.setCompleted(milk.id, completed = true)

        assertEquals(10L, analytics.values(AnalyticsEvent.ARRIVAL_OPENED)["latency_s"])
        assertEquals("notification", analytics.values(AnalyticsEvent.TODO_COMPLETED)["via"])
    }

    @Test
    fun reopeningATodoIsNotACompletion() = runTest {
        val milk = todo("Milk", store.id)
        repository.add(milk)
        repository.setCompleted(milk.id, completed = true)

        repository.setCompleted(milk.id, completed = false)

        assertEquals(1, analytics.count(AnalyticsEvent.TODO_COMPLETED))
    }
}
