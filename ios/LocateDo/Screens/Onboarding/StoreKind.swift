import SwiftUI

// The kinds of store onboarding starts from, the same four the map offers nearby, and any other store.
enum StoreKind: String, CaseIterable, Identifiable {
    case grocery
    case drugstore
    case convenience
    case hardware
    case other

    var id: Self {
        self
    }

    var name: LocalizedStringResource {
        switch self {
        case .grocery: .firstPlaceGroceryName
        case .drugstore: .firstPlaceDrugstoreName
        case .convenience: .firstPlaceConvenienceName
        case .hardware: .firstPlaceHardwareName
        case .other: .firstPlaceOtherName
        }
    }

    var systemImage: String {
        switch self {
        case .grocery: "cart"
        case .drugstore: "cross.case"
        case .convenience: "storefront"
        case .hardware: "hammer"
        case .other: "bag"
        }
    }

    var label: LocalizedStringResource {
        if self == .other {
            return .firstPlaceOtherLabel
        }
        return name
    }

    var searchTerm: LocalizedStringResource? {
        switch self {
        case .grocery: .placePickerNearbyGrocery
        case .drugstore: .placePickerNearbyDrugstore
        case .convenience: .placePickerNearbyConvenience
        case .hardware: .placePickerNearbyHardware
        case .other: nil
        }
    }

    var ideas: [LocalizedStringResource] {
        switch self {
        case .grocery:
            [.firstPlaceGroceryIdea1, .firstPlaceGroceryIdea2, .firstPlaceGroceryIdea3, .firstPlaceGroceryIdea4,
             .firstPlaceGroceryIdea5]
        case .drugstore:
            [.firstPlaceDrugstoreIdea1, .firstPlaceDrugstoreIdea2, .firstPlaceDrugstoreIdea3,
             .firstPlaceDrugstoreIdea4, .firstPlaceDrugstoreIdea5]
        case .convenience:
            [.firstPlaceConvenienceIdea1, .firstPlaceConvenienceIdea2, .firstPlaceConvenienceIdea3,
             .firstPlaceConvenienceIdea4, .firstPlaceConvenienceIdea5]
        case .hardware:
            [.firstPlaceHardwareIdea1, .firstPlaceHardwareIdea2, .firstPlaceHardwareIdea3, .firstPlaceHardwareIdea4,
             .firstPlaceHardwareIdea5]
        case .other:
            []
        }
    }
}

// The store onboarding is about: one of the kinds, or any other store by what was typed for it.
struct FirstStore: Hashable {
    let kind: StoreKind
    var customName: String?

    var name: String {
        customName ?? String(localized: kind.name)
    }

    var todosTitle: LocalizedStringResource {
        switch kind {
        case .grocery: .firstPlaceGroceryTodosTitle
        case .drugstore: .firstPlaceDrugstoreTodosTitle
        case .convenience: .firstPlaceConvenienceTodosTitle
        case .hardware: .firstPlaceHardwareTodosTitle
        case .other: .placeEditorTodosTitle(name)
        }
    }

    var storeTitle: LocalizedStringResource {
        switch kind {
        case .grocery: .firstPlaceGroceryStoreTitle
        case .drugstore: .firstPlaceDrugstoreStoreTitle
        case .convenience: .firstPlaceConvenienceStoreTitle
        case .hardware: .firstPlaceHardwareStoreTitle
        case .other: .firstPlaceOtherStoreTitle(name)
        }
    }

    var searchTerm: String? {
        if let customName {
            return customName
        }
        return kind.searchTerm.map { String(localized: $0) }
    }
}
