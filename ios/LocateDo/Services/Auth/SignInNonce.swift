import CryptoKit
import Foundation
import Security

nonisolated enum SignInNonce {
    static func make(byteCount: Int = 32) -> String {
        var bytes = [UInt8](repeating: 0, count: byteCount)
        let status = SecRandomCopyBytes(kSecRandomDefault, byteCount, &bytes)
        precondition(status == errSecSuccess, "SecRandomCopyBytes failed: \(status)")
        return hex(bytes)
    }

    static func sha256(_ nonce: String) -> String {
        hex(Array(SHA256.hash(data: Data(nonce.utf8))))
    }

    private static func hex(_ bytes: [UInt8]) -> String {
        bytes.map { String(format: "%02x", $0) }.joined()
    }
}
