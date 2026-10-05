import Foundation
import Testing

@testable import LocateDo

struct InviteLinkTests {
    private static let token = "example_Invite-Link"

    @Test func buildsTheLinkOnTheOwnedDomain() {
        #expect(InviteLink.url(for: Self.token)?.absoluteString == "https://locatedo.com/i/\(Self.token)")
    }

    @Test func readsTheTokenFromALinkOrOnItsOwn() {
        #expect(InviteLink.token(from: "https://locatedo.com/i/\(Self.token)") == Self.token)
        #expect(InviteLink.token(from: "  https://www.locatedo.com/i/\(Self.token)\n") == Self.token)
        #expect(InviteLink.token(from: Self.token) == Self.token)
    }

    @Test func rejectsAnythingElse() {
        #expect(InviteLink.token(from: "https://example.com/i/\(Self.token)") == nil)
        #expect(InviteLink.token(from: "https://locatedo.com/privacy") == nil)
        #expect(InviteLink.token(from: "https://locatedo.com/i/\(Self.token)/extra") == nil)
        #expect(InviteLink.token(from: "short") == nil)
        #expect(InviteLink.token(from: "has spaces in the middle of it") == nil)
    }
}
