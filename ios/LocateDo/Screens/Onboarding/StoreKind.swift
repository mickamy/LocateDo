import SwiftUI

// The kinds of store onboarding starts from, the same four the map offers nearby.
enum StoreKind: String, CaseIterable, Identifiable {
    case grocery
    case drugstore
    case convenience
    case hardware

    var id: Self {
        self
    }

    var name: LocalizedStringResource {
        switch self {
        case .grocery: .firstPlaceGroceryName
        case .drugstore: .firstPlaceDrugstoreName
        case .convenience: .firstPlaceConvenienceName
        case .hardware: .firstPlaceHardwareName
        }
    }

    var systemImage: String {
        switch self {
        case .grocery: "cart"
        case .drugstore: "cross.case"
        case .convenience: "storefront"
        case .hardware: "hammer"
        }
    }

    var todosTitle: LocalizedStringResource {
        switch self {
        case .grocery: .firstPlaceGroceryTodosTitle
        case .drugstore: .firstPlaceDrugstoreTodosTitle
        case .convenience: .firstPlaceConvenienceTodosTitle
        case .hardware: .firstPlaceHardwareTodosTitle
        }
    }

    var storeTitle: LocalizedStringResource {
        switch self {
        case .grocery: .firstPlaceGroceryStoreTitle
        case .drugstore: .firstPlaceDrugstoreStoreTitle
        case .convenience: .firstPlaceConvenienceStoreTitle
        case .hardware: .firstPlaceHardwareStoreTitle
        }
    }

    var searchTerm: LocalizedStringResource {
        switch self {
        case .grocery: .placePickerNearbyGrocery
        case .drugstore: .placePickerNearbyDrugstore
        case .convenience: .placePickerNearbyConvenience
        case .hardware: .placePickerNearbyHardware
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
        }
    }
}
