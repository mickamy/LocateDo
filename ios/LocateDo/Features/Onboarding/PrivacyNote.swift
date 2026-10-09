import SwiftUI

// Said where location is asked for, since that is when people wonder where it goes.
struct PrivacyNote: View {
    let text: LocalizedStringResource

    var body: some View {
        Label {
            Text(text)
        } icon: {
            Image(systemName: "lock.fill")
                .foregroundStyle(.tint)
        }
        .font(.footnote)
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(12)
        .background(.fill.tertiary, in: RoundedRectangle(cornerRadius: 12))
    }
}
