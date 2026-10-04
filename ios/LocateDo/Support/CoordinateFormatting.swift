import CoreLocation
import Foundation

nonisolated enum CoordinateFormatting {
    static func string(_ coordinate: CLLocationCoordinate2D, locale: Locale = .current) -> String {
        let style = FloatingPointFormatStyle<Double>.number
            .precision(.fractionLength(5))
            .locale(locale)
        return "\(coordinate.latitude.formatted(style)), \(coordinate.longitude.formatted(style))"
    }
}
