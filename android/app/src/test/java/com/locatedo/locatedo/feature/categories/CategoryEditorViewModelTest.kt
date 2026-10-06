package com.locatedo.locatedo.feature.categories

import com.locatedo.locatedo.core.common.uuidV7
import com.locatedo.locatedo.core.model.BuiltinCategory
import com.locatedo.locatedo.core.model.Category
import com.locatedo.locatedo.testing.FakeCategoryRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CategoryEditorViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val now = Instant.parse("2026-10-06T00:00:00Z")
    private val categories = FakeCategoryRepository()
    private val shopping = Category(
        id = uuidV7(now),
        builtin = BuiltinCategory.SHOPPING,
        icon = "cart",
        color = "green",
        sortOrder = 0,
        updatedAt = now,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun aNewCategoryStartsBlankAndIsAddedWithItsTrimmedName() = runTest(dispatcher) {
        val viewModel = viewModel()
        val events = events(viewModel)
        viewModel.start(category = null, builtinTitle = null)

        assertFalse(viewModel.draft.value.isEditing)
        assertFalse(viewModel.draft.value.canSave)
        viewModel.setName("  Pharmacy ")
        viewModel.setIcon("cross")
        viewModel.setColor("red")
        viewModel.save()

        val saved = categories.added.single()
        assertEquals("Pharmacy", saved.name)
        assertEquals("cross", saved.icon)
        assertEquals("red", saved.color)
        assertEquals(now, saved.updatedAt)
        assertEquals(listOf(CategoryEditorEvent.Saved), events)
    }

    @Test
    fun aBuiltinShowsItsTranslatedTitleAndKeepsNoNameWhenSavedAsIs() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.start(shopping, builtinTitle = "Shopping")

        assertEquals("Shopping", viewModel.draft.value.name)
        assertTrue(viewModel.draft.value.isEditing)
        viewModel.setColor("teal")
        viewModel.save()

        val saved = categories.updated.single()
        assertNull(saved.name)
        assertEquals("teal", saved.color)
        assertEquals(shopping.id, saved.id)
    }

    @Test
    fun aRenamedBuiltinKeepsItsNameUntilItIsTypedBackToTheTitle() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.start(shopping.copy(name = "Groceries"), builtinTitle = "Shopping")
        assertEquals("Groceries", viewModel.draft.value.name)

        viewModel.setName("Errands")
        viewModel.save()
        assertEquals("Errands", categories.updated.last().name)

        viewModel.setName("Shopping")
        viewModel.save()
        assertNull(categories.updated.last().name)
    }

    @Test
    fun theNameIsCappedAtFiftyCharacters() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.start(category = null, builtinTitle = null)

        viewModel.setName("x".repeat(60))

        assertEquals(50, viewModel.draft.value.name.length)
    }

    @Test
    fun aBlankNameIsNotSaved() = runTest(dispatcher) {
        val viewModel = viewModel()
        val events = events(viewModel)
        viewModel.start(category = null, builtinTitle = null)
        viewModel.setName("   ")

        viewModel.save()

        assertTrue(categories.added.isEmpty())
        assertTrue(events.isEmpty())
    }

    private fun viewModel() = CategoryEditorViewModel(categories, Clock.fixed(now, ZoneOffset.UTC))

    private fun TestScope.events(viewModel: CategoryEditorViewModel): List<CategoryEditorEvent> {
        val events = mutableListOf<CategoryEditorEvent>()
        backgroundScope.launch { viewModel.events.collect { events += it } }
        return events
    }
}
