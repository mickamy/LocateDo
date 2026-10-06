package com.locatedo.locatedo.core.data

import androidx.room.withTransaction
import com.locatedo.locatedo.core.database.CategoryDao
import com.locatedo.locatedo.core.database.LocateDoDatabase
import com.locatedo.locatedo.core.database.PlaceDao
import com.locatedo.locatedo.core.database.SyncStateDao
import com.locatedo.locatedo.core.database.TodoDao
import javax.inject.Inject
import javax.inject.Singleton

// Wipes what the server owns; onboarding and permissions live elsewhere and stay.
@Singleton
class LocalData @Inject constructor(
    private val database: LocateDoDatabase,
    private val placeDao: PlaceDao,
    private val todoDao: TodoDao,
    private val categoryDao: CategoryDao,
    private val syncStateDao: SyncStateDao,
    private val categories: CategoryRepository,
) {
    // Anything beyond the four built-ins is the user's own and worth a confirmation before it is replaced.
    suspend fun hasUserData(): Boolean = placeDao.count() > 0 || categoryDao.countCustom() > 0

    suspend fun deleteSynced() = database.withTransaction {
        todoDao.deleteAll()
        placeDao.deleteAll()
        categoryDao.deleteAll()
    }

    suspend fun reset() = database.withTransaction {
        deleteSynced()
        syncStateDao.clear()
        categories.ensureBuiltins()
    }
}
