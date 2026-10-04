import Foundation
import OSLog
import SwiftProtobuf

final class DeviceRegistration {
    private(set) var pushToken: String?

    private let devices: any Locatedo_Device_V1_DeviceServiceClientInterface
    private let authenticator: Authenticator
    private let apnsEnvironment: Locatedo_Device_V1_ApnsEnvironment
    private let logger = Logger(subsystem: "com.locatedo.LocateDo", category: "push")

    init(
        devices: any Locatedo_Device_V1_DeviceServiceClientInterface,
        authenticator: Authenticator,
        apnsEnvironment: Locatedo_Device_V1_ApnsEnvironment = .current
    ) {
        self.devices = devices
        self.authenticator = authenticator
        self.apnsEnvironment = apnsEnvironment
    }

    func received(deviceToken: Data) async {
        pushToken = deviceToken.map { String(format: "%02x", $0) }.joined()
        await registerIfSignedIn()
    }

    func registerIfSignedIn() async {
        guard authenticator.isSignedIn, let pushToken else {
            return
        }
        let request = Locatedo_Device_V1_RegisterDeviceRequest.with { [apnsEnvironment] in
            $0.platform = .ios
            $0.pushToken = pushToken
            $0.apnsEnvironment = apnsEnvironment
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

extension Locatedo_Device_V1_ApnsEnvironment {
    static var current: Self {
        #if targetEnvironment(simulator)
        let isSimulator = true
        #else
        let isSimulator = false
        #endif
        let apsEnvironment = Bundle.main.object(forInfoDictionaryKey: "LocateDoAPSEnvironment") as? String
        return resolve(apsEnvironment: apsEnvironment, isSimulator: isSimulator)
    }

    // Simulator tokens are always issued by the sandbox, whatever the entitlement says.
    static func resolve(apsEnvironment: String?, isSimulator: Bool) -> Self {
        if !isSimulator && apsEnvironment == "production" {
            return .production
        }
        return .sandbox
    }
}
