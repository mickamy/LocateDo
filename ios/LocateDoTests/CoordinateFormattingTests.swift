import CoreLocation
import Foundation
import Testing

@testable import LocateDo

struct CoordinateFormattingTests {
    private let locale = Locale(identifier: "en_US")

    @Test func usesFiveFractionDigits() {
        let coordinate = CLLocationCoordinate2D(latitude: 35.6812, longitude: 139.7671)
        #expect(CoordinateFormatting.string(coordinate, locale: locale) == "35.68120, 139.76710")
    }

    @Test func keepsNegativeSigns() {
        let coordinate = CLLocationCoordinate2D(latitude: -33.8688, longitude: -70.6693)
        #expect(CoordinateFormatting.string(coordinate, locale: locale) == "-33.86880, -70.66930")
    }
}
