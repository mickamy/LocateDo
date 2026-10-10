import SwiftUI

// A destructive action asked about first, shown over whatever is on top.
struct Confirmation: Identifiable {
    let id = UUID()
    let title: String
    let message: String?
    let actionTitle: LocalizedStringResource
    let action: () -> Void
}
