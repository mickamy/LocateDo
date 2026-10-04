import Foundation
import Testing

@testable import LocateDo

struct APIEnvironmentTests {
    @Test func readsTheBaseURLFromTheInfoDictionary() {
        let environment = APIEnvironment(
            infoDictionary: ["LocateDoAPIBaseURL": "https://api.example.com"],
            arguments: []
        )
        #expect(environment?.baseURL == URL(string: "https://api.example.com"))
    }

    @Test func launchArgumentOverridesTheInfoDictionary() {
        let environment = APIEnvironment(
            infoDictionary: ["LocateDoAPIBaseURL": "https://api.example.com"],
            arguments: ["-apiBaseURL", "http://192.168.1.10:8080"]
        )
        #expect(environment?.baseURL == URL(string: "http://192.168.1.10:8080"))
    }

    @Test func missingValueFails() {
        #expect(APIEnvironment(infoDictionary: [:], arguments: []) == nil)
        #expect(APIEnvironment(infoDictionary: ["LocateDoAPIBaseURL": ""], arguments: []) == nil)
    }
}
