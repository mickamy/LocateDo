import Connect
import Foundation
import Synchronization
import Testing

@testable import LocateDo

struct DeviceRegistrationTests {
    private static let token = Data([0x0a, 0xbc, 0xff, 0x01])

    @Test func registersTheHexTokenWhenSignedIn() async {
        let devices = FakeDeviceService()
        let registration = DeviceRegistration(
            devices: devices,
            authenticator: Self.authenticator(signedIn: true),
            apnsEnvironment: .sandbox
        )

        await registration.received(deviceToken: Self.token)

        #expect(registration.pushToken == "0abcff01")
        #expect(devices.registered.map(\.pushToken) == ["0abcff01"])
        #expect(devices.registered.map(\.platform) == [.ios])
        #expect(devices.registered.map(\.apnsEnvironment) == [.sandbox])
    }

    @Test func sendsTheEnvironmentItWasGiven() async {
        let devices = FakeDeviceService()
        let registration = DeviceRegistration(
            devices: devices,
            authenticator: Self.authenticator(signedIn: true),
            apnsEnvironment: .production
        )

        await registration.received(deviceToken: Self.token)

        #expect(devices.registered.map(\.apnsEnvironment) == [.production])
    }

    @Test func onlyAProductionDeviceBuildUsesProduction() {
        typealias Environment = Locatedo_Device_V1_ApnsEnvironment
        #expect(Environment.resolve(apsEnvironment: "production", isSimulator: false) == .production)
        #expect(Environment.resolve(apsEnvironment: "development", isSimulator: false) == .sandbox)
        #expect(Environment.resolve(apsEnvironment: nil, isSimulator: false) == .sandbox)
        #expect(Environment.resolve(apsEnvironment: "production", isSimulator: true) == .sandbox)
    }

    @Test func keepsTheTokenUntilSignIn() async throws {
        let devices = FakeDeviceService()
        let authenticator = Self.authenticator(signedIn: false)
        let registration = DeviceRegistration(devices: devices, authenticator: authenticator)

        await registration.received(deviceToken: Self.token)
        #expect(devices.registered.isEmpty)

        try authenticator.signIn(Self.session)
        await registration.register()
        #expect(devices.registered.map(\.pushToken) == ["0abcff01"])
    }

    @Test func doesNothingBeforeATokenArrives() async {
        let devices = FakeDeviceService()
        let registration = DeviceRegistration(devices: devices, authenticator: Self.authenticator(signedIn: true))

        await registration.register()

        #expect(devices.registered.isEmpty)
    }

    @Test func sendsTheLanguageAndConsent() async {
        let devices = FakeDeviceService()
        let registration = DeviceRegistration(
            devices: devices,
            authenticator: Self.authenticator(signedIn: true),
            apnsEnvironment: .sandbox,
            language: "ja"
        ) { true }

        await registration.received(deviceToken: Self.token)

        #expect(devices.registered.map(\.language) == ["ja"])
        #expect(devices.registered.map(\.promotionsConsent) == [true])
    }

    @Test func sendsTheCompletionNoticeSetting() async {
        let devices = FakeDeviceService()
        let registration = DeviceRegistration(
            devices: devices,
            authenticator: Self.authenticator(signedIn: true),
            apnsEnvironment: .sandbox,
            completionNotices: { false }
        )

        await registration.received(deviceToken: Self.token)

        #expect(devices.registered.map(\.hasCompletionNotices) == [true])
        #expect(devices.registered.map(\.completionNotices) == [false])
    }

    @Test func registersASignedOutDeviceThatConsents() async {
        let devices = FakeDeviceService()
        let registration = DeviceRegistration(
            devices: devices,
            authenticator: Self.authenticator(signedIn: false),
            apnsEnvironment: .sandbox
        ) { true }

        await registration.received(deviceToken: Self.token)

        #expect(devices.registered.map(\.pushToken) == ["0abcff01"])
        #expect(devices.registered.map(\.promotionsConsent) == [true])
    }

    @Test func turningConsentOffIsSentEvenWhenSignedOut() async {
        let devices = FakeDeviceService()
        var consent = true
        let registration = DeviceRegistration(
            devices: devices,
            authenticator: Self.authenticator(signedIn: false),
            apnsEnvironment: .sandbox
        ) { consent }
        await registration.received(deviceToken: Self.token)

        consent = false
        await registration.promotionsConsentChanged()

        #expect(devices.registered.map(\.promotionsConsent) == [true, false])
    }

    @Test func aSignedOutDeviceWithoutConsentWaitsForAChange() async {
        let devices = FakeDeviceService()
        let registration = DeviceRegistration(
            devices: devices,
            authenticator: Self.authenticator(signedIn: false),
            apnsEnvironment: .sandbox
        )

        await registration.received(deviceToken: Self.token)
        await registration.register()

        #expect(devices.registered.isEmpty)
    }

    @Test func aConsentChangeBeforeATokenSendsNothing() async {
        let devices = FakeDeviceService()
        let registration = DeviceRegistration(
            devices: devices,
            authenticator: Self.authenticator(signedIn: false)
        ) { true }

        await registration.promotionsConsentChanged()

        #expect(devices.registered.isEmpty)
    }

    @Test func japaneseIsSentOnlyWhenTheAppShowsJapanese() {
        #expect(DeviceRegistration.language(preferredLocalizations: ["ja"]) == "ja")
        #expect(DeviceRegistration.language(preferredLocalizations: ["en"]) == "en")
        #expect(DeviceRegistration.language(preferredLocalizations: ["Base"]) == "en")
        #expect(DeviceRegistration.language(preferredLocalizations: []) == "en")
    }

    private static let session = Session(
        userID: UUID(uuidString: "0199bd00-0000-7000-8000-000000000001")!,
        accessToken: "access",
        accessTokenExpiresAt: Date(timeIntervalSinceNow: 3_600),
        refreshToken: "refresh"
    )

    private static func authenticator(signedIn: Bool) -> Authenticator {
        var stored: Session?
        if signedIn {
            stored = session
        }
        return Authenticator(
            store: InMemorySessionStore(stored),
            account: FakeAccountService(),
            tokens: AccessTokenStore()
        )
    }
}

nonisolated final class FakeDeviceService: Locatedo_Device_V1_DeviceServiceClientInterface {
    private let requests = Mutex<[Locatedo_Device_V1_RegisterDeviceRequest]>([])

    var registered: [Locatedo_Device_V1_RegisterDeviceRequest] {
        requests.withLock { $0 }
    }

    func registerDevice(
        request: Locatedo_Device_V1_RegisterDeviceRequest,
        headers: Connect.Headers
    ) async -> ResponseMessage<Locatedo_Device_V1_RegisterDeviceResponse> {
        requests.withLock { $0.append(request) }
        return ResponseMessage(result: .success(Locatedo_Device_V1_RegisterDeviceResponse()))
    }
}
