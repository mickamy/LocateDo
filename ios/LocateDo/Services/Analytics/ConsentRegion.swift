import Foundation
import StoreKit

// The EEA and the UK, where reading the app instance ID for analytics needs consent first.
nonisolated enum ConsentRegion {
    // StoreKit gives the storefront as ISO 3166-1 alpha-3.
    private static let storefronts: Set<String> = [
        "AUT", "BEL", "BGR", "HRV", "CYP", "CZE", "DNK", "EST", "FIN", "FRA", "DEU", "GRC", "HUN", "IRL",
        "ITA", "LVA", "LTU", "LUX", "MLT", "NLD", "POL", "PRT", "ROU", "SVK", "SVN", "ESP", "SWE",
        "ISL", "LIE", "NOR", "GBR"
    ]

    private static let regions: Set<String> = [
        "AT", "BE", "BG", "HR", "CY", "CZ", "DK", "EE", "FI", "FR", "DE", "GR", "HU", "IE",
        "IT", "LV", "LT", "LU", "MT", "NL", "PL", "PT", "RO", "SK", "SI", "ES", "SE",
        "IS", "LI", "NO", "GB"
    ]

    static func requiresConsent(storefront: String?, region: String?) -> Bool {
        if let storefront {
            return storefronts.contains(storefront.uppercased())
        }
        if let region {
            return regions.contains(region.uppercased())
        }
        return true
    }

    static let overrideArgument = "-consentStorefront"

    static func currentStorefront(arguments: [String] = ProcessInfo.processInfo.arguments) async -> String? {
        #if DEBUG
        if let index = arguments.firstIndex(of: overrideArgument), index + 1 < arguments.count {
            return arguments[index + 1]
        }
        #endif
        return await Storefront.current?.countryCode
    }

    static var currentRegion: String? {
        Locale.current.region?.identifier
    }
}
