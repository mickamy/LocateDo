import SwiftUI

enum SharingStatus: Equatable {
    case ownerFree
    case ownerAlone
    case ownerSharing(count: Int)
    case member(ownerName: String)
}

struct SharingHeader: View {
    let status: SharingStatus
    let seatsLeft: Int
    let onUpgrade: () -> Void
    let onInvite: () -> Void

    @Environment(AppStatusStore.self) private var appStatus

    var body: some View {
        VStack(spacing: 16) {
            Image(systemName: "person.3.fill")
                .font(.system(size: 30))
                .foregroundStyle(.white)
                .frame(width: 72, height: 72)
                .background(.tint, in: .circle)
                .accessibilityHidden(true)
            VStack(spacing: 8) {
                Text(title)
                    .font(.title3.bold())
                Text(message)
                    .foregroundStyle(.secondary)
            }
            .multilineTextAlignment(.center)
            action
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 8)
    }

    @ViewBuilder
    private var action: some View {
        switch status {
        case .ownerFree:
            VStack(alignment: .leading, spacing: 20) {
                BenefitRow(
                    systemImage: "list.bullet.rectangle",
                    title: .sharingBenefitListsTitle,
                    message: .sharingIosBenefitListsMessage
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
            .padding(.vertical, 8)
            primaryButton(.settingsProUpgrade, action: onUpgrade)
        case .ownerAlone, .ownerSharing:
            VStack(spacing: 8) {
                primaryButton(.sharingInvite, action: onInvite)
                    .disabled(seatsLeft == 0 || appStatus.activeMaintenance != nil)
                Group {
                    if seatsLeft == 0 {
                        Text(.sharingFull)
                    } else {
                        Text(.sharingSeatsLeft(seatsLeft))
                        Text(.sharingInviteExpiry)
                    }
                    MaintenanceNote()
                }
                .font(.footnote)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
            }
        case .member:
            EmptyView()
        }
    }

    private func primaryButton(_ title: LocalizedStringResource, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title)
                .frame(maxWidth: .infinity)
        }
        .buttonStyle(.borderedProminent)
        .controlSize(.large)
    }

    private var title: LocalizedStringResource {
        switch status {
        case .ownerFree:
            .sharingIntroTitle
        case .ownerAlone:
            .sharingInviteHeaderTitle
        case .ownerSharing(let count):
            .sharingSharedHeaderTitle(count)
        case .member(let ownerName):
            .sharingMemberHeaderTitle(ownerName)
        }
    }

    private var message: LocalizedStringResource {
        switch status {
        case .ownerFree:
            .sharingProRequired
        case .ownerAlone:
            .sharingInviteHeaderMessage
        case .ownerSharing:
            .sharingIosSharedHeaderMessage
        case .member:
            .sharingMemberHeaderMessage
        }
    }
}
