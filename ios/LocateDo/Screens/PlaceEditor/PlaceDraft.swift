import CoreLocation
import Foundation

// What is being typed and picked for a place, before it is saved.
struct PlaceDraft {
    var name: String
    var coordinate: CLLocationCoordinate2D?
    var source: PlaceSource?
    var radiusMeters: Double
    var category: PlaceCategory?
    var suggestion: BuiltinCategory?
    var hasChosenCategory = false
    private var pickedName: String?

    init(radiusMeters: Double) {
        name = ""
        self.radiusMeters = radiusMeters
    }

    init(editing place: Place) {
        name = place.name
        coordinate = place.coordinate
        radiusMeters = place.radiusMeters
        category = place.category
    }

    var trimmedName: String {
        name.trimmingCharacters(in: .whitespaces)
    }

    var canSave: Bool {
        !trimmedName.isEmpty && coordinate != nil
    }

    var isCategorySuggested: Bool {
        suggestion != nil && !hasChosenCategory
    }

    mutating func apply(_ pick: PlacePick, categories: [PlaceCategory]) {
        coordinate = pick.coordinate
        source = pick.source
        suggest(pick.suggestion, from: categories)
        // A name typed by hand stays; one that came from the last pick follows the new one.
        if name.isEmpty || name == pickedName {
            name = pick.name ?? ""
            pickedName = pick.name
        }
    }

    mutating func choose(_ category: PlaceCategory) {
        self.category = category
        hasChosenCategory = true
    }

    // A new pick guesses again, unless the category was already chosen by hand.
    private mutating func suggest(_ guessed: BuiltinCategory?, from categories: [PlaceCategory]) {
        suggestion = guessed
        if hasChosenCategory {
            return
        }
        category = categories.first { $0.builtin == guessed && guessed != nil }
    }
}
