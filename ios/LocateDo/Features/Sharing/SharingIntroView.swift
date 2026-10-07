import SwiftUI

struct SharingIntroView: View {
    var body: some View {
        ScrollView {
            VStack(spacing: 32) {
                VStack(spacing: 16) {
                    Image(systemName: "person.3.fill")
                        .font(.system(size: 36))
                        .foregroundStyle(.white)
                        .frame(width: 88, height: 88)
                        .background(.tint, in: .circle)
                        .accessibilityHidden(true)
                    Text(.sharingIntroTitle)
                        .font(.title2.bold())
                    Text(.sharingIntroMessage)
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.center)
                }
                VStack(alignment: .leading, spacing: 24) {
                    BenefitRow(
                        systemImage: "list.bullet.rectangle",
                        title: .sharingBenefitListsTitle,
                        message: .sharingBenefitListsMessage
                    )
                    BenefitRow(
                        systemImage: "location.circle",
                        title: .sharingBenefitAssignTitle,
                        message: .sharingBenefitAssignMessage
                    )
                    BenefitRow(
                        systemImage: "gift",
                        title: .sharingBenefitPlanTitle,
                        message: .sharingBenefitPlanMessage
                    )
                }
            }
            .padding(.horizontal, 32)
            .padding(.vertical, 40)
            .frame(maxWidth: .infinity)
        }
        .trackScreen(.sharingIntro)
        .background(Color(.systemGroupedBackground))
        .safeAreaInset(edge: .bottom) {
            VStack(spacing: 12) {
                Text(.sharingInvitedHint)
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
                SignInButtons()
            }
            .padding(.horizontal, 20)
            .padding(.bottom, 28)
        }
    }
}
