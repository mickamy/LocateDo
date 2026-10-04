import Foundation
import OSLog
import SwiftProtobuf

final class DeviceRegistration {
    private(set) var pushToken: String?

    private let devices: any Locatedo_Device_V1_DeviceServiceClientInterface
    private let authenticator: Authenticator
    private let logger = Logger(subsystem: "com.locatedo.LocateDo", category: "push")

    init(devices: any Locatedo_Device_V1_DeviceServiceClientInterface, authenticator: Authenticator) {
        self.devices = devices
        self.authenticator = authenticator
    }

    func received(deviceToken: Data) async {
        pushToken = deviceToken.map { String(format: "%02x", $0) }.joined()
        await registerIfSignedIn()
    }

    func registerIfSignedIn() async {
        guard authenticator.isSignedIn, let pushToken else {
            return
        }
        let request = Locatedo_Device_V1_RegisterDeviceRequest.with {
            $0.platform = .ios
            $0.pushToken = pushToken
        }
        let client = devices
        do {
            _ = try await authenticator.authorized { await client.registerDevice(request: request, headers: [:]) }
            logger.notice("Registered the push token")
        } catch {
            logger.notice("Could not register the push token: \(error, privacy: .public)")
        }
    }
}
