package com.locatedo.locatedo.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

// Until release the schema is edited in place: a changed model means reinstalling the app, as on iOS.
@Database(
    entities = [
        CategoryEntity::class,
        PlaceEntity::class,
        TodoEntity::class,
        SyncStateEntity::class,
        MembershipEntity::class,
        PendingWriteEntity::class,
    ],
    version = 1,
)
abstract class LocateDoDatabase : RoomDatabase() {
    abstract fun placeDao(): PlaceDao
    abstract fun todoDao(): TodoDao
    abstract fun categoryDao(): CategoryDao
    abstract fun syncStateDao(): SyncStateDao
    abstract fun membershipDao(): MembershipDao
    abstract fun pendingWriteDao(): PendingWriteDao
}
