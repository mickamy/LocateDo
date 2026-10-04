import Foundation

extension UUID {
    nonisolated static func v7(now: Date = .now) -> UUID {
        let millis = UInt64((now.timeIntervalSince1970 * 1000).rounded())
        var bytes = [UInt8](repeating: 0, count: 16)
        for index in 0..<6 {
            bytes[index] = UInt8(truncatingIfNeeded: millis >> (8 * UInt64(5 - index)))
        }
        for index in 6..<16 {
            bytes[index] = UInt8.random(in: .min ... .max)
        }
        bytes[6] = (bytes[6] & 0x0F) | 0x70
        bytes[8] = (bytes[8] & 0x3F) | 0x80
        return UUID(uuid: (
            bytes[0], bytes[1], bytes[2], bytes[3],
            bytes[4], bytes[5], bytes[6], bytes[7],
            bytes[8], bytes[9], bytes[10], bytes[11],
            bytes[12], bytes[13], bytes[14], bytes[15]
        ))
    }

    nonisolated var v7Date: Date? {
        let bytes = uuid
        guard bytes.6 >> 4 == 0x7 else {
            return nil
        }
        let millis = [bytes.0, bytes.1, bytes.2, bytes.3, bytes.4, bytes.5]
            .reduce(UInt64(0)) { $0 << 8 | UInt64($1) }
        return Date(timeIntervalSince1970: Double(millis) / 1000)
    }
}
