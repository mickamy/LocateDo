import Foundation
import Testing

@testable import LocateDo

struct DistanceFormattingTests {
    @Test func usesFeetAndMilesInTheUS() {
        let locale = Locale(identifier: "en_US")
        #expect(DistanceFormatting.string(meters: 100, locale: locale).hasSuffix("ft"))
        #expect(DistanceFormatting.string(meters: 5_000, locale: locale).hasSuffix("mi"))
    }

    @Test func usesMetersAndKilometersInJapan() {
        let locale = Locale(identifier: "ja_JP")
        #expect(DistanceFormatting.string(meters: 350, locale: locale) == "350 m")
        #expect(DistanceFormatting.string(meters: 1_500, locale: locale) == "1.5 km")
    }
}
