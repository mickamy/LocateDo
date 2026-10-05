#if DEBUG || STAGING
import SwiftData
import SwiftUI
import UIKit

struct DebugSection: View {
    @Environment(Authenticator.self) private var authenticator
    @Environment(SyncEngine.self) private var sync
    @Query private var syncStates: [SyncState]
    @Query private var pendingWrites: [PendingWrite]
    @State private var serverStatus: String?
    @State private var copiedLabel: String?

    var body: some View {
        Section {
            debugRow("Server", APIEnvironment.current.baseURL.absoluteString)
            copyableRow("User", authenticator.session?.userID.uuidString.lowercased() ?? "-")
            copyableRow("Household", syncStates.first?.householdID?.uuidString.lowercased() ?? "-")
            debugRow("Cursor", "\(syncStates.first?.cursor ?? 0)")
            debugRow("Queued writes", "\(pendingWrites.count)")
            debugRow("Last pull", sync.lastPullSummary ?? "-")
            Button {
                Task {
                    await sync.sync()
                }
            } label: {
                Text(verbatim: "Sync now")
            }
            Button {
                Task {
                    await checkServer()
                }
            } label: {
                Text(verbatim: "Check connection")
            }
            if let serverStatus {
                Text(verbatim: serverStatus)
                    .foregroundStyle(.secondary)
            }
        } header: {
            Text(verbatim: "Debug")
        }
        .sensoryFeedback(.success, trigger: copiedLabel) { _, label in
            label != nil
        }
    }

    private func debugRow(_ label: String, _ value: String) -> some View {
        LabeledContent {
            Text(verbatim: value)
                .font(.footnote.monospaced())
        } label: {
            Text(verbatim: label)
        }
    }

    private func copyableRow(_ label: String, _ value: String) -> some View {
        HStack {
            VStack(alignment: .leading, spacing: 4) {
                Text(verbatim: label)
                Text(verbatim: value)
                    .font(.footnote.monospaced())
                    .foregroundStyle(.secondary)
            }
            Spacer()
            Button {
                copy(value, from: label)
            } label: {
                if copiedLabel == label {
                    Label {
                        Text(verbatim: "Copied")
                    } icon: {
                        Image(systemName: "checkmark")
                    }
                    .foregroundStyle(.green)
                } else {
                    Label {
                        Text(verbatim: "Copy")
                    } icon: {
                        Image(systemName: "doc.on.doc")
                    }
                }
            }
            .labelStyle(.iconOnly)
            .buttonStyle(.borderless)
        }
    }

    private func copy(_ value: String, from label: String) {
        UIPasteboard.general.string = value
        withAnimation {
            copiedLabel = label
        }
        Task {
            try? await Task.sleep(for: .seconds(1.5))
            if copiedLabel == label {
                withAnimation {
                    copiedLabel = nil
                }
            }
        }
    }

    private func checkServer() async {
        do {
            let code = try await HealthClient(environment: .current).statusCode()
            serverStatus = "HTTP \(code)"
        } catch {
            serverStatus = error.localizedDescription
        }
    }
}
#endif
