package com.locatedo.locatedo.core.data

import javax.inject.Inject

interface ProStatus {
    fun isPro(): Boolean
}

// Stands in until billing lands and the RevenueCat-backed status replaces it.
class FreeProStatus @Inject constructor() : ProStatus {
    override fun isPro(): Boolean = false
}
