import Connect
import OSLog
import SwiftData
import SwiftUI

struct AcceptInviteView: View {
    @Environment(AccountManager.self) private var account
    @Environment(HouseholdManager.self) private var households
    @Environment(AppStatusStore.self) private var appStatus
    @Environment(\.dismiss) private var dismiss
    @Query private var memberships: [Membership]

    @State private var link: String
    @State private var failure: LocalizedStringResource?

    private let logger = Logger(subsystem: "com.locatedo.LocateDo", category: "sharing")

    init(token: String? = nil) {
        var initial = ""
        if let token, let url = InviteLink.url(for: token) {
            initial = url.absoluteString
        }
        _link = State(initialValue: initial)
    }

    var body: some View {
        Form {
            Section {
                HStack {
                    TextField(text: $link) {
                        Text(.inviteLinkPlaceholder)
                    }
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    PasteButton(payloadType: String.self) { strings in
                        if let first = strings.first {
                            link = first
                        }
                    }
                    .labelStyle(.iconOnly)
                    .buttonBorderShape(.capsule)
                }
            } footer: {
                VStack(alignment: .leading, spacing: 8) {
                    Text(.inviteMessage)
                    if let failure {
                        Text(failure)
                            .foregroundStyle(.red)
                    }
                }
            }
            Section {
                if account.isSignedIn {
                    Button {
                        Task {
                            await join()
                        }
                    } label: {
                        HStack {
                            Text(.inviteJoin)
                            Spacer()
                            if households.isWorking {
                                ProgressView()
                            }
                        }
                    }
                    .disabled(!canJoin)
                } else {
                    VStack(spacing: 12) {
                        Text(.sharingSignInMessage)
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                        SignInButtons()
                    }
                    .listRowBackground(Color.clear)
                }
            } footer: {
                if account.isSignedIn {
                    MaintenanceNote()
                }
            }
        }
        .trackScreen(.acceptInvite)
        .navigationTitle(Text(.inviteTitle))
        .navigationBarTitleDisplayMode(.inline)
    }

    private var canJoin: Bool {
        InviteLink.token(from: link) != nil && !households.isWorking && appStatus.activeMaintenance == nil
    }

    private func join() async {
        failure = nil
        guard let token = InviteLink.token(from: link) else {
            failure = .inviteInvalid
            return
        }
        if memberships.count > 1 {
            failure = .inviteLeaveFirst
            return
        }
        do {
            try await households.accept(token: token)
            dismiss()
        } catch let error as ConnectError {
            logger.notice("Accepting an invite failed: \(error, privacy: .public)")
            failure = Self.message(for: error.code)
        } catch {
            logger.error("Accepting an invite failed: \(error, privacy: .public)")
            failure = .inviteFailed
        }
    }

    private static func message(for code: Code) -> LocalizedStringResource {
        switch code {
        case .failedPrecondition, .notFound:
            .inviteUnavailable
        case .alreadyExists:
            .inviteAlreadyMember
        default:
            .inviteFailed
        }
    }
}
