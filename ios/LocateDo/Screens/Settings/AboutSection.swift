import SwiftUI

struct AboutSection: View {
    var body: some View {
        Section {
            LabeledContent {
                Text(Self.version)
            } label: {
                Text(.settingsAboutVersion)
            }
            Link(destination: LegalLinks.privacyPolicy) {
                Label(.settingsAboutPrivacyPolicy, systemImage: "hand.raised")
            }
            ContactSupportButton()
        } header: {
            Text(.settingsAboutTitle)
        }
    }

    private static var version: String {
        let info = Bundle.main.infoDictionary
        let version = info?["CFBundleShortVersionString"] as? String ?? "-"
        let build = info?["CFBundleVersion"] as? String ?? "-"
        return "\(version) (\(build))"
    }
}
