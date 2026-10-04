import Foundation

nonisolated enum DistanceFormatting {
    static func string(meters: Double, locale: Locale = .current) -> String {
        Measurement(value: meters, unit: UnitLength.meters)
            .formatted(.measurement(width: .abbreviated, usage: .road).locale(locale))
    }

    static func placeholder(locale: Locale = .current) -> String {
        string(meters: 500, locale: locale)
    }
}
