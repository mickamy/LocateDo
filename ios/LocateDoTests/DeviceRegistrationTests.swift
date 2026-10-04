import Connect
import Foundation
import Synchronization
import Testing

@testable import LocateDo

struct DeviceRegistrationTests {
    private static let token = Data([0x0a, 0xbc, 0xff, 0x01])

    @Test func registersTheHexTokenWhenSignedIn() async {
        let devices = FakeDeviceService()
        let registration = DeviceRegistration(devices: devices, authenticator: Self.authenticator(signedIn: true))

        await registration.received(deviceToken: Self.token)

        #expect(registration.pushToken == "0abcff01")
        #expect(devices.registered.map(\.pushToken) == ["0abcff01"])
        #expect(devices.registered.map(\.platform) == [.ios])
    }

    @Test func keepsTheTokenUntilSignIn() async throws {
        let devices = FakeDeviceService()
        let authenticator = Self.authenticator(signedIn: false)
        let registration = DeviceRegistration(devices: devices, authenticator: authenticator)

        await registration.received(deviceToken: Self.token)
        #expect(devices.registered.isEmpty)

        try authenticator.signIn(Self.session)
        await registration.registerIfSignedIn()
        #expect(devices.registered.map(\.pushToken) == ["0abcff01"])
    }

    @Test func doesNothingBeforeATokenArrives() async {
        let devices = FakeDeviceService()
        let registration = DeviceRegistration(devices: devices, authenticator: Self.authenticator(signedIn: true))

        await registration.registerIfSignedIn()

        #expect(devices.registered.isEmpty)
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
