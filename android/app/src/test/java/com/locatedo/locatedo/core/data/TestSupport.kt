package com.locatedo.locatedo.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.locatedo.locatedo.core.common.uuidV7
import com.locatedo.locatedo.core.database.LocateDoDatabase
import com.locatedo.locatedo.core.model.Category
import com.locatedo.locatedo.core.model.Place
import com.locatedo.locatedo.core.model.Todo
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

val fixedNow: Instant = Instant.parse("2026-10-06T00:00:00Z")
val fixedClock: Clock = Clock.fixed(fixedNow, ZoneOffset.UTC)

class FakeProStatus(var pro: Boolean = false) : ProStatus {
    override fun isPro(): Boolean = pro
}

fun inMemoryDatabase(): LocateDoDatabase =
    Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), LocateDoDatabase::class.java).build()

fun place(name: String, createdAt: Instant = fixedNow) =
    Place(id = uuidV7(createdAt), name = name, latitude = 35.0, longitude = 139.0, createdAt = createdAt)

fun todo(title: String, placeId: UUID, createdAt: Instant = fixedNow) =
    Todo(id = uuidV7(createdAt), title = title, placeId = placeId, createdAt = createdAt)

fun category(name: String) =
    Category(id = uuidV7(fixedNow), name = name, icon = "cart", color = "green", sortOrder = 0, updatedAt = fixedNow)
