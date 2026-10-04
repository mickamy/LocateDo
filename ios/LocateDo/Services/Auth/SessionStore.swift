import Foundation
import Security

nonisolated protocol SessionStoring: Sendable {
    func load() throws -> Session?
    func save(_ session: Session) throws
    func clear() throws
}

nonisolated struct KeychainSessionStore: SessionStoring {
    enum Failure: Error {
        case status(OSStatus)
    }

    private let service: String
    private let account = "session"

    init(service: String = Bundle.main.bundleIdentifier ?? "com.locatedo.LocateDo") {
        self.service = service
    }

    func load() throws -> Session? {
        var query = baseQuery
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne
        var item: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &item)
        switch status {
        case errSecSuccess:
            guard let data = item as? Data else {
                return nil
            }
            return try JSONDecoder().decode(Session.self, from: data)
        case errSecItemNotFound:
            return nil
        default:
            throw Failure.status(status)
        }
    }

    func save(_ session: Session) throws {
        let data = try JSONEncoder().encode(session)
        let update: [String: Any] = [kSecValueData as String: data]
        let status = SecItemUpdate(baseQuery as CFDictionary, update as CFDictionary)
        if status == errSecItemNotFound {
            var query = baseQuery
            query[kSecValueData as String] = data
            query[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlock
            let added = SecItemAdd(query as CFDictionary, nil)
            guard added == errSecSuccess else {
                throw Failure.status(added)
            }
            return
        }
        guard status == errSecSuccess else {
            throw Failure.status(status)
        }
    }

    func clear() throws {
        let status = SecItemDelete(baseQuery as CFDictionary)
        guard status == errSecSuccess || status == errSecItemNotFound else {
            throw Failure.status(status)
        }
    }

    private var baseQuery: [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account
        ]
    }
}
