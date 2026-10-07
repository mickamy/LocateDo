import GoogleSignIn
import Testing

@testable import LocateDo

struct GoogleSignInSetupTests {
    @Test func readsTheClientAndServerClientIDs() throws {
        let configuration = try #require(GoogleSignInSetup.configuration(info: [
            "LocateDoGoogleClientID": "ios.apps.googleusercontent.com",
            "LocateDoGoogleServerClientID": "web.apps.googleusercontent.com"
        ]))

        #expect(configuration.clientID == "ios.apps.googleusercontent.com")
        #expect(configuration.serverClientID == "web.apps.googleusercontent.com")
    }

    @Test func staysOffWithoutBothIDs() {
        #expect(GoogleSignInSetup.configuration(info: [:]) == nil)
        #expect(GoogleSignInSetup.configuration(info: ["LocateDoGoogleClientID": "ios"]) == nil)
        #expect(GoogleSignInSetup.configuration(info: [
            "LocateDoGoogleClientID": "ios",
            "LocateDoGoogleServerClientID": ""
        ]) == nil)
    }
}
