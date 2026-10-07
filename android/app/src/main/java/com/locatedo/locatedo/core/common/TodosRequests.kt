package com.locatedo.locatedo.core.common

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// Lets a notification tap ask the app shell for the to-do tab.
@Singleton
class TodosRequests @Inject constructor() {
    private val _pending = MutableStateFlow(false)

    val pending: StateFlow<Boolean> = _pending

    fun request() {
        _pending.value = true
    }

    fun consume() {
        _pending.value = false
    }
}
