package com.locatedo.locatedo.core.billing

import com.locatedo.locatedo.core.model.FreeLimit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class PaywallTrigger(val key: String) {
    PLACE_LIMIT("place_limit"),
    TODO_LIMIT("todo_limit"),
    SHARE("share"),
    SETTINGS("settings"),
}

val FreeLimit.paywallTrigger: PaywallTrigger
    get() = when (this) {
        FreeLimit.PLACES -> PaywallTrigger.PLACE_LIMIT
        FreeLimit.OPEN_TODOS -> PaywallTrigger.TODO_LIMIT
    }

// Any screen asks for the paywall here; the app shell opens it, so it looks the same wherever the limit was hit.
@Singleton
class PaywallRequests @Inject constructor() {
    private val _pending = MutableStateFlow<PaywallTrigger?>(null)

    val pending: StateFlow<PaywallTrigger?> = _pending

    fun request(trigger: PaywallTrigger) {
        _pending.value = trigger
    }

    fun consume(trigger: PaywallTrigger) {
        _pending.compareAndSet(trigger, null)
    }
}
