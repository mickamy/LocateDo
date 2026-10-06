package com.locatedo.locatedo.feature.categories

import com.locatedo.locatedo.core.common.uuidV7
import com.locatedo.locatedo.core.model.Category
import com.locatedo.locatedo.core.model.Place
import com.locatedo.locatedo.core.model.PlaceWithTodos
import com.locatedo.locatedo.testing.FakeCategoryRepository
import com.locatedo.locatedo.testing.FakePlaceRepository
import java.time.Instant
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CategoryListViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val now = Instant.parse("2026-10-06T00:00:00Z")
    private val categories = FakeCategoryRepository()
    private val places = FakePlaceRepository()
    private val shopping = category("Shopping", sortOrder = 0)
    private val work = category("Work", sortOrder = 1)
    private val life = category("Life", sortOrder = 2)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        categories.state.value = listOf(shopping, work, life)
        places.state.value = listOf(
            PlaceWithTodos(place("Store", shopping.id), emptyList()),
            PlaceWithTodos(place("Market", shopping.id), emptyList()),
            PlaceWithTodos(place("Office", work.id), emptyList()),
            PlaceWithTodos(place("Park", null), emptyList()),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun countsThePlacesOfEachCategory() = runTest(dispatcher) {
        val viewModel = viewModel()

        val items = viewModel.uiState.value.items
        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(listOf("Shopping", "Work", "Life"), items.map { it.category.name })
        assertEquals(listOf(2, 1, 0), items.map { it.placeCount })
    }

    @Test
    fun movingARowReordersTheListAndDroppingItWritesTheOrder() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.move(from = 0, to = 2)
        assertEquals(listOf("Work", "Life", "Shopping"), viewModel.uiState.value.items.map { it.category.name })
        assertTrue(categories.reordered.isEmpty())

        viewModel.commitOrder()

        assertEquals(listOf(work.id, life.id, shopping.id), categories.reordered.single().map { it.id })
        assertEquals(listOf("Work", "Life", "Shopping"), viewModel.uiState.value.items.map { it.category.name })
    }

    @Test
    fun droppingWithoutMovingWritesNothing() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.move(from = 1, to = 1)
        viewModel.commitOrder()

        assertTrue(categories.reordered.isEmpty())
    }

    @Test
    fun deletingReportsWhetherTheRepositoryAllowedIt() = runTest(dispatcher) {
        val viewModel = viewModel()

        assertTrue(viewModel.delete(life.id))
        assertEquals(listOf("Shopping", "Work"), viewModel.uiState.value.items.map { it.category.name })

        assertTrue(viewModel.delete(work.id))
        assertFalse(viewModel.delete(shopping.id))
        assertEquals(listOf("Shopping"), viewModel.uiState.value.items.map { it.category.name })
    }

    private fun TestScope.viewModel(): CategoryListViewModel {
        val viewModel = CategoryListViewModel(categories, places)
        backgroundScope.launch { viewModel.uiState.collect {} }
        return viewModel
    }

    private fun category(name: String, sortOrder: Int) =
        Category(id = uuidV7(now), name = name, icon = "cart", color = "green", sortOrder = sortOrder, updatedAt = now)

    private fun place(name: String, categoryId: java.util.UUID?) =
        Place(id = uuidV7(now), name = name, latitude = 35.0, longitude = 139.0, categoryId = categoryId, createdAt = now)
}
