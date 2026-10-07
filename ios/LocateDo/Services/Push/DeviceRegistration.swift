import Connect
import Foundation
import OSLog
import SwiftProtobuf

final class DeviceRegistration {
    private(set) var pushToken: String?

    private let devices: any Locatedo_Device_V1_DeviceServiceClientInterface
    private let authenticator: Authenticator
    private let apnsEnvironment: Locatedo_Device_V1_ApnsEnvironment
    private let language: String
    private let promotionsConsent: () -> Bool
    private let completionNotices: () -> Bool
    private let logger = Logger(subsystem: "com.locatedo.LocateDo", category: "push")

    init(
        devices: any Locatedo_Device_V1_DeviceServiceClientInterface,
        authenticator: Authenticator,
        apnsEnvironment: Locatedo_Device_V1_ApnsEnvironment = .current,
        language: String = DeviceRegistration.language(preferredLocalizations: Bundle.main.preferredLocalizations),
        promotionsConsent: @escaping () -> Bool = { false },
        completionNotices: @escaping () -> Bool = { true }
    ) {
        self.devices = devices
        self.authenticator = authenticator
        self.apnsEnvironment = apnsEnvironment
        self.language = language
        self.promotionsConsent = promotionsConsent
        self.completionNotices = completionNotices
    }

    func received(deviceToken: Data) async {
        pushToken = deviceToken.map { String(format: "%02x", $0) }.joined()
        await register()
    }

    func register() async {
        guard authenticator.isSignedIn || promotionsConsent() else {
            return
        }
        await send()
    }

    // Sent even when consent was turned off, so the server can drop an anonymous device.
    func promotionsConsentChanged() async {
        await send()
    }

    static func language(preferredLocalizations: [String]) -> String {
        if preferredLocalizations.first == "ja" {
            return "ja"
        }
        return "en"
    }

    private func send() async {
        guard let pushToken else {
            return
        }
        let request = Locatedo_Device_V1_RegisterDeviceRequest.with { [apnsEnvironment, language] in
            $0.platform = .ios
            $0.pushToken = pushToken
            $0.apnsEnvironment = apnsEnvironment
            $0.language = language
            $0.promotionsConsent = promotionsConsent()
            $0.completionNotices = completionNotices()
        }
        let client = devices
        do {
            if authenticator.isSignedIn {
                _ = try await authenticator.authorized { await client.registerDevice(request: request, headers: [:]) }
            } else {
                _ = try await client.registerDevice(request: request, headers: [:]).result.get()
            }
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
