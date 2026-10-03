import Foundation
import Testing

@testable import LocateDo

struct UUIDV7Tests {
    @Test func setsVersionAndVariantBits() {
        let bytes = UUID.v7().uuid
        #expect(bytes.6 >> 4 == 0x7)
        #expect(bytes.8 >> 6 == 0b10)
    }

    @Test func encodesMillisecondTimestamp() {
        let bytes = UUID.v7(now: Date(timeIntervalSince1970: 1_700_000_000.123)).uuid
        let millis = [bytes.0, bytes.1, bytes.2, bytes.3, bytes.4, bytes.5]
            .reduce(UInt64(0)) { $0 << 8 | UInt64($1) }
        #expect(millis == 1_700_000_000_123)
    }

    @Test func sortsByTime() {
        let earlier = UUID.v7(now: Date(timeIntervalSince1970: 1_000))
        let later = UUID.v7(now: Date(timeIntervalSince1970: 1_001))
        #expect(earlier.uuidString < later.uuidString)
    }

    @Test func isUniqueWithinTheSameMillisecond() {
        let now = Date()
        let ids = Set((0..<1_000).map { _ in UUID.v7(now: now) })
        #expect(ids.count == 1_000)
    }
}
