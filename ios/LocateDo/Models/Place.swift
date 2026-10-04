import CoreLocation
import Foundation
import SwiftData

@Model
final class Place {
    static let defaultRadiusMeters: Double = 100
    static let radiusRange: ClosedRange<Double> = 50...500

    @Attribute(.unique) var id: UUID
    var name: String
    var latitude: Double
    var longitude: Double
    var radiusMeters: Double
    var category: PlaceCategory?
    var sortOrder: Int
    var lastNotifiedAt: Date?
    var createdAt: Date
    var updatedAt: Date

    @Relationship(deleteRule: .cascade, inverse: \Todo.place)
    var todos: [Todo] = []

    init(
        id: UUID = .v7(),
        name: String,
        latitude: Double,
        longitude: Double,
        radiusMeters: Double = Place.defaultRadiusMeters,
        category: PlaceCategory? = nil,
        sortOrder: Int = 0,
        now: Date = .now
    ) {
        self.id = id
        self.name = name
        self.latitude = latitude
        self.longitude = longitude
        self.radiusMeters = radiusMeters
        self.category = category
        self.sortOrder = sortOrder
        createdAt = now
        updatedAt = now
    }

    var coordinate: CLLocationCoordinate2D {
        CLLocationCoordinate2D(latitude: latitude, longitude: longitude)
    }

    var openTodos: [Todo] {
        todos.open
    }

    var completedTodos: [Todo] {
        todos.completedNewestFirst
    }
}
