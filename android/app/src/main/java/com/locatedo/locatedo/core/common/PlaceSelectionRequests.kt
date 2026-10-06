package com.locatedo.locatedo.core.common

import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// Lets other screens (the to-do tab, later a notification tap) ask the home map to show a place.
@Singleton
class PlaceSelectionRequests @Inject constructor() {
    private val _pending = MutableStateFlow<UUID?>(null)

    val pending: StateFlow<UUID?> = _pending

    fun request(placeId: UUID) {
        _pending.value = placeId
    }

    fun consume(placeId: UUID) {
        _pending.compareAndSet(placeId, null)
    }
}
