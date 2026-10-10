import Foundation
import OSLog
import SwiftData
import SwiftProtobuf

struct ChangeApplier {
    struct Outcome: Equatable {
        var placesChanged = false
    }

    private struct Key: Hashable {
        let kind: Locatedo_Sync_V1_EntityKind
        let id: String
    }

    private let context: ModelContext
    private let logger = Logger(subsystem: "com.locatedo.LocateDo", category: "sync")
    private var memberships: [UUID: Membership] = [:]
    private var categories: [UUID: PlaceCategory] = [:]
    private var places: [UUID: Place] = [:]
    private var todos: [UUID: Todo] = [:]
    private var outcome = Outcome()

    static func apply(
        _ changes: [Locatedo_Sync_V1_Change],
        reset: Bool,
        to context: ModelContext
    ) throws -> Outcome {
        var applier = try ChangeApplier(context: context, reset: reset)
        try applier.apply(changes)
        return applier.outcome
    }

    private init(context: ModelContext, reset: Bool) throws {
        self.context = context
        if reset {
            try deleteAll(Todo.self)
            try deleteAll(Place.self)
            try deleteAll(PlaceCategory.self)
            try deleteAll(Membership.self)
            outcome.placesChanged = true
            return
        }
        for membership in try context.fetch(FetchDescriptor<Membership>()) {
            memberships[membership.userID] = membership
        }
        for category in try context.fetch(FetchDescriptor<PlaceCategory>()) {
            categories[category.id] = category
        }
        for place in try context.fetch(FetchDescriptor<Place>()) {
            places[place.id] = place
        }
        for todo in try context.fetch(FetchDescriptor<Todo>()) {
            todos[todo.id] = todo
        }
    }

    private func deleteAll<Model: PersistentModel>(_ type: Model.Type) throws {
        for model in try context.fetch(FetchDescriptor<Model>()) {
            context.delete(model)
        }
    }

    private mutating func apply(_ changes: [Locatedo_Sync_V1_Change]) throws {
        var membershipChanges: [Locatedo_Household_V1_Membership] = []
        var categoryChanges: [Locatedo_Category_V1_Category] = []
        var placeChanges: [Locatedo_Place_V1_Place] = []
        var todoChanges: [Locatedo_Todo_V1_Todo] = []
        var deletions: [Locatedo_Sync_V1_Deletion] = []
        for change in Self.latest(changes) {
            switch change.kind {
            case .membership(let membership): membershipChanges.append(membership)
            case .category(let category): categoryChanges.append(category)
            case .place(let place): placeChanges.append(place)
            case .todo(let todo): todoChanges.append(todo)
            case .deletion(let deletion): deletions.append(deletion)
            case nil: break
            }
        }
        membershipChanges.forEach { upsert($0) }
        categoryChanges.forEach { upsert($0) }
        placeChanges.forEach { upsert($0) }
        todoChanges.forEach { upsert($0) }
        deletions.forEach { delete($0) }
    }

    private static func latest(_ changes: [Locatedo_Sync_V1_Change]) -> [Locatedo_Sync_V1_Change] {
        var latest: [Key: (version: Int64, change: Locatedo_Sync_V1_Change)] = [:]
        for change in changes {
            guard let (key, version) = identity(of: change) else {
                continue
            }
            if let existing = latest[key], existing.version >= version {
                continue
            }
            latest[key] = (version, change)
        }
        return latest.values.map(\.change)
    }

    private static func identity(of change: Locatedo_Sync_V1_Change) -> (Key, Int64)? {
        switch change.kind {
        case .membership(let membership):
            (Key(kind: .membership, id: membership.userID), membership.version)
        case .category(let category):
            (Key(kind: .category, id: category.id), category.version)
        case .place(let place):
            (Key(kind: .place, id: place.id), place.version)
        case .todo(let todo):
            (Key(kind: .todo, id: todo.id), todo.version)
        case .deletion(let deletion):
            (Key(kind: deletion.kind, id: deletion.id), deletion.version)
        case nil:
            nil
        }
    }

    private mutating func upsert(_ proto: Locatedo_Household_V1_Membership) {
        guard let userID = uuid(proto.userID, kind: "membership") else {
            return
        }
        var role = MemberRole.member
        if proto.role == .owner {
            role = .owner
        }
        if let membership = memberships[userID] {
            membership.role = role
            membership.displayName = proto.displayName
            membership.joinedAt = proto.joinedAt.date
            membership.updatedAt = proto.updatedAt.date
            return
        }
        let membership = Membership(
            userID: userID,
            role: role,
            displayName: proto.displayName,
            joinedAt: proto.joinedAt.date,
            updatedAt: proto.updatedAt.date
        )
        context.insert(membership)
        memberships[userID] = membership
    }

    private mutating func upsert(_ proto: Locatedo_Category_V1_Category) {
        guard let id = uuid(proto.id, kind: "category") else {
            return
        }
        let category: PlaceCategory
        if let existing = categories[id] {
            category = existing
        } else {
            category = PlaceCategory(id: id, icon: proto.icon, color: proto.color, sortOrder: Int(proto.sortOrder))
            context.insert(category)
            categories[id] = category
        }
        category.builtin = BuiltinCategory(proto.builtin)
        category.name = nil
        if proto.hasName {
            category.name = proto.name
        }
        category.icon = proto.icon
        category.color = proto.color
        category.sortOrder = Int(proto.sortOrder)
        category.updatedAt = proto.updatedAt.date
    }

    private mutating func upsert(_ proto: Locatedo_Place_V1_Place) {
        guard let id = uuid(proto.id, kind: "place") else {
            return
        }
        let place: Place
        if let existing = places[id] {
            place = existing
        } else {
            place = Place(
                id: id,
                name: proto.name,
                latitude: proto.lat,
                longitude: proto.lng,
                now: id.v7Date ?? proto.updatedAt.date
            )
            context.insert(place)
            places[id] = place
        }
        place.name = proto.name
        place.latitude = proto.lat
        place.longitude = proto.lng
        place.radiusMeters = Double(proto.radiusM)
        place.category = nil
        if proto.hasCategoryID, let categoryID = UUID(uuidString: proto.categoryID) {
            place.category = categories[categoryID]
        }
        place.sortOrder = Int(proto.sortOrder)
        place.updatedAt = proto.updatedAt.date
        outcome.placesChanged = true
    }

    private mutating func upsert(_ proto: Locatedo_Todo_V1_Todo) {
        guard let id = uuid(proto.id, kind: "todo") else {
            return
        }
        guard let place = UUID(uuidString: proto.placeID).flatMap({ places[$0] }) else {
            logger.error("Skipping todo \(proto.id, privacy: .public) for a missing place")
            return
        }
        guard let placeEvent = PlaceEvent(proto.trigger.event) else {
            logger.error("Skipping todo \(proto.id, privacy: .public) without a place event")
            return
        }
        let todo: Todo
        if let existing = todos[id] {
            todo = existing
            todo.place = place
        } else {
            todo = Todo(id: id, title: proto.title, place: place, now: id.v7Date ?? proto.updatedAt.date)
            context.insert(todo)
            place.todos.append(todo)
            todos[id] = todo
        }
        todo.title = proto.title
        todo.assigneeID = optionalUUID(proto.assigneeID, isSet: proto.hasAssigneeID)
        todo.creatorID = optionalUUID(proto.creatorID, isSet: proto.hasCreatorID)
        todo.completerID = optionalUUID(proto.completerID, isSet: proto.hasCompleterID)
        todo.placeEvent = placeEvent
        todo.completedAt = nil
        if proto.hasCompletedAt {
            todo.completedAt = proto.completedAt.date
        }
        todo.updatedAt = proto.updatedAt.date
    }

    private mutating func delete(_ deletion: Locatedo_Sync_V1_Deletion) {
        guard let id = UUID(uuidString: deletion.id) else {
            return
        }
        switch deletion.kind {
        case .membership:
            if let membership = memberships.removeValue(forKey: id) {
                context.delete(membership)
            }
        case .category:
            if let category = categories.removeValue(forKey: id) {
                context.delete(category)
            }
        case .place:
            deletePlace(id)
        case .todo:
            if let todo = todos.removeValue(forKey: id) {
                context.delete(todo)
            }
        case .unspecified, .UNRECOGNIZED:
            break
        }
    }

    private mutating func deletePlace(_ id: UUID) {
        guard let place = places.removeValue(forKey: id) else {
            return
        }
        for todo in place.todos {
            todos.removeValue(forKey: todo.id)
        }
        context.delete(place)
        outcome.placesChanged = true
    }
}

private extension ChangeApplier {
    func uuid(_ string: String, kind: String) -> UUID? {
        guard let id = UUID(uuidString: string) else {
            logger.error("Skipping a \(kind, privacy: .public) with an invalid id \(string, privacy: .public)")
            return nil
        }
        return id
    }
}

private extension BuiltinCategory {
    nonisolated init?(_ proto: Locatedo_Category_V1_BuiltinCategory) {
        switch proto {
        case .shopping: self = .shopping
        case .work: self = .work
        case .life: self = .life
        case .other: self = .other
        case .unspecified, .UNRECOGNIZED: return nil
        }
    }
}

private extension PlaceEvent {
    nonisolated init?(_ proto: Locatedo_Todo_V1_PlaceEvent) {
        switch proto {
        case .arrival: self = .arrival
        case .departure: self = .departure
        case .unspecified, .UNRECOGNIZED: return nil
        }
    }
}

private nonisolated func optionalUUID(_ raw: String, isSet: Bool) -> UUID? {
    guard isSet else {
        return nil
    }
    return UUID(uuidString: raw)
}
