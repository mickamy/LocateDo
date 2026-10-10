package com.locatedo.locatedo.core.places

import com.locatedo.locatedo.core.model.BuiltinCategory

// The built-in category a picked place most likely belongs to, from its Places types in order (the primary type
// first). Work is never guessed: no kind of place says so.
object CategoryGuess {
    private val shopping = setOf(
        "supermarket", "grocery_store", "convenience_store", "shopping_mall", "drugstore", "pharmacy", "bakery",
        "market", "store",
    )

    private val life = setOf(
        "bank", "atm", "post_office", "hospital", "doctor", "dentist", "laundry", "library", "school", "university",
        "gym", "gas_station", "electric_vehicle_charging_station", "city_hall", "park", "beauty_salon", "hair_care",
    )

    fun category(types: List<String>): BuiltinCategory? {
        for (type in types) {
            if (type in life) {
                return BuiltinCategory.LIFE
            }
            if (type in shopping || type.endsWith("_store")) {
                return BuiltinCategory.SHOPPING
            }
        }
        return null
    }
}
