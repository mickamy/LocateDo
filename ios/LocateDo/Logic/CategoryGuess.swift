import MapKit

// The built-in category a picked store most likely belongs to. Work is never guessed: no kind of store says so.
nonisolated enum CategoryGuess {
    private static let shopping: Set<MKPointOfInterestCategory> = [.foodMarket, .store, .pharmacy, .bakery]

    private static let life: Set<MKPointOfInterestCategory> = [
        .bank, .atm, .postOffice, .hospital, .laundry, .library, .school, .university,
        .fitnessCenter, .gasStation, .evCharger, .park, .beauty
    ]

    static func category(for kind: MKPointOfInterestCategory?) -> BuiltinCategory? {
        guard let kind else {
            return nil
        }
        if shopping.contains(kind) {
            return .shopping
        }
        if life.contains(kind) {
            return .life
        }
        return nil
    }
}
