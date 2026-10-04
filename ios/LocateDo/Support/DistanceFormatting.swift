import Foundation

nonisolated enum DistanceFormatting {
    static func string(meters: Double, locale: Locale = .current) -> String {
        Measurement(value: meters, unit: UnitLength.meters)
            .formatted(.measurement(width: .abbreviated, usage: .road).locale(locale))
    }
}
