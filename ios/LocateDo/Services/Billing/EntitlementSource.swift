import Foundation
import RevenueCat

protocol EntitlementSource {
    func logIn(_ appUserID: String) async throws -> Bool
    func logOut() async throws -> Bool
    func refresh() async throws -> Bool
    func updates() -> AsyncStream<Bool>
}

final class RevenueCatEntitlementSource: EntitlementSource {
    static let entitlementID = "locatedo_pro"

    init(apiKey: String) {
        Purchases.configure(withAPIKey: apiKey)
    }

    func logIn(_ appUserID: String) async throws -> Bool {
        let (info, _) = try await Purchases.shared.logIn(appUserID)
        return Self.hasPro(info)
    }

    func logOut() async throws -> Bool {
        if Purchases.shared.isAnonymous {
            return try await refresh()
        }
        return Self.hasPro(try await Purchases.shared.logOut())
    }

    func refresh() async throws -> Bool {
        Self.hasPro(try await Purchases.shared.customerInfo())
    }

    func updates() -> AsyncStream<Bool> {
        AsyncStream { continuation in
            let task = Task {
                for await info in Purchases.shared.customerInfoStream {
                    continuation.yield(Self.hasPro(info))
                }
                continuation.finish()
            }
            continuation.onTermination = { _ in
                task.cancel()
            }
        }
    }

    private static func hasPro(_ info: CustomerInfo) -> Bool {
        info.entitlements[entitlementID]?.isActive == true
    }
}
