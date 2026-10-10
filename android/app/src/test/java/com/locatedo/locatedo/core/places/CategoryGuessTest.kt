package com.locatedo.locatedo.core.places

import com.locatedo.locatedo.core.model.BuiltinCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CategoryGuessTest {
    @Test
    fun storesAreShopping() {
        for (type in listOf("supermarket", "convenience_store", "pharmacy", "hardware_store", "bakery")) {
            assertEquals(type, BuiltinCategory.SHOPPING, CategoryGuess.category(listOf(type, "point_of_interest")))
        }
    }

    @Test
    fun errandsAreLife() {
        for (type in listOf("bank", "post_office", "hospital", "gas_station", "gym", "hair_care")) {
            assertEquals(type, BuiltinCategory.LIFE, CategoryGuess.category(listOf(type, "establishment")))
        }
    }

    @Test
    fun thePrimaryTypeWins() {
        assertEquals(BuiltinCategory.LIFE, CategoryGuess.category(listOf("gas_station", "convenience_store")))
    }

    @Test
    fun otherKindsAreNotGuessed() {
        assertNull(CategoryGuess.category(listOf("restaurant", "cafe", "point_of_interest")))
        assertNull(CategoryGuess.category(emptyList()))
    }
}
