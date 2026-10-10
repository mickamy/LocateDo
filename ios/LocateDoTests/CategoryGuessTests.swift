import MapKit
import Testing

@testable import LocateDo

struct CategoryGuessTests {
    @Test(arguments: [
        MKPointOfInterestCategory.foodMarket, .store, .pharmacy, .bakery
    ])
    func storesAreShopping(kind: MKPointOfInterestCategory) {
        #expect(CategoryGuess.category(for: kind) == .shopping)
    }

    @Test(arguments: [
        MKPointOfInterestCategory.bank, .postOffice, .hospital, .gasStation, .fitnessCenter, .beauty
    ])
    func errandsAreLife(kind: MKPointOfInterestCategory) {
        #expect(CategoryGuess.category(for: kind) == .life)
    }

    @Test(arguments: [MKPointOfInterestCategory.restaurant, .cafe, .museum])
    func otherKindsAreNotGuessed(kind: MKPointOfInterestCategory) {
        #expect(CategoryGuess.category(for: kind) == nil)
    }

    @Test func noKindIsNotGuessed() {
        #expect(CategoryGuess.category(for: nil) == nil)
    }
}
