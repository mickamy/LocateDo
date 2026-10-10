import SwiftUI

struct UsageDataSection: View {
    @Environment(AnalyticsConsent.self) private var analyticsConsent
    @State private var isConfirmingOff = false

    var body: some View {
        if analyticsConsent.showsSetting {
            Section {
                Toggle(isOn: binding) {
                    Text(.settingsPrivacyUsageDataTitle)
                }
                .accessibilityIdentifier("settings.usageData")
            } header: {
                Text(.settingsPrivacyTitle)
            } footer: {
                Text(.settingsPrivacyUsageDataFooter)
            }
            // Plain buttons and one tap to confirm, so withdrawing stays as easy as agreeing.
            .alert(Text(.settingsPrivacyUsageDataOffTitle), isPresented: $isConfirmingOff) {
                Button(.settingsPrivacyUsageDataOff) {
                    analyticsConsent.set(false, source: .settings)
                }
                Button(.commonCancel, role: .cancel) {}
            } message: {
                Text(.settingsPrivacyUsageDataOffMessage)
            }
        }
    }

    private var binding: Binding<Bool> {
        Binding {
            analyticsConsent.isSending
        } set: { isOn in
            if isOn {
                analyticsConsent.set(true, source: .settings)
            } else {
                isConfirmingOff = true
            }
        }
    }
}
