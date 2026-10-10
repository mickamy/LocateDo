import SwiftUI

// "Somewhere else" says what store it is first, a kind or a name, so the to-dos and the map that follow can use it.
struct StoreNameStep: View {
    let onNext: (String) -> Void

    @State private var name = ""
    @FocusState private var isFocused: Bool

    private var trimmed: String {
        name.trimmingCharacters(in: .whitespaces)
    }

    var body: some View {
        VStack(spacing: 20) {
            Text(.firstPlaceOtherNameTitle)
                .font(.title2.bold())
                .multilineTextAlignment(.center)
                .padding(.top, 40)
            TextField(text: $name, prompt: Text(.firstPlaceOtherNamePlaceholder)) {
                Text(.firstPlaceOtherNameTitle)
            }
            .font(.title3)
            .padding(14)
            .background(Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: 12))
            .focused($isFocused)
            .submitLabel(.next)
            .onSubmit(next)
            .accessibilityIdentifier("onboarding.storeName")
            Spacer()
            Button(action: next) {
                Text(.placeEditorNext)
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .controlSize(.large)
            .disabled(trimmed.isEmpty)
        }
        .padding(32)
        .onAppear {
            isFocused = true
            Analytics.logScreen(.onboarding, parameters: [.step: "store_name"])
        }
    }

    private func next() {
        guard !trimmed.isEmpty else {
            return
        }
        onNext(trimmed)
    }
}
