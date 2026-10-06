import Testing

@testable import LocateDo

struct CrashReportingTests {
    @Test(arguments: [
        ("Debug", false),
        ("Staging", true),
        ("Release", true)
    ])
    func collectsOutsideDebug(configuration: String, expected: Bool) {
        #expect(CrashReporting.isCollectionEnabled(for: configuration) == expected)
    }
}
