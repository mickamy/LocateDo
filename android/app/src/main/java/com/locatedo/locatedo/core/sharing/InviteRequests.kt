package com.locatedo.locatedo.core.sharing

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// An invite link opened from outside waits here until the tabs are on screen and can show the join screen.
@Singleton
class InviteRequests @Inject constructor() {
    private val _pending = MutableStateFlow<String?>(null)

    val pending: StateFlow<String?> = _pending

    fun request(token: String) {
        _pending.value = token
    }

    fun consume(token: String) {
        _pending.compareAndSet(token, null)
    }
}
