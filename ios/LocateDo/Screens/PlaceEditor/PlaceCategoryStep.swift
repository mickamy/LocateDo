import SwiftData
import SwiftUI

// Adding a place, second screen: the category as colored tiles, preselected from the picked store when possible.
struct PlaceCategoryStep: View {
    private static let columns = [GridItem(.flexible(), spacing: 12), GridItem(.flexible(), spacing: 12)]

    @Query(sort: \PlaceCategory.sortOrder) private var categories: [PlaceCategory]
    @Binding var draft: PlaceDraft
    let isLastStep: Bool
    let onNext: () -> Void

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                if draft.isCategorySuggested {
                    Text(.placeEditorCategorySuggested)
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
                LazyVGrid(columns: Self.columns, spacing: 12) {
                    ForEach(categories) { candidate in
                        tile(candidate)
                    }
                }
                NavigationLink {
                    CategoriesScreen()
                } label: {
                    Label(.categoryManage, systemImage: "slider.horizontal.3")
                }
            }
            .padding()
        }
        .background(Color(.systemGroupedBackground))
        .navigationTitle(Text(.placeEditorCategoryTitle))
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .confirmationAction) {
                if isLastStep {
                    Button(.commonSave, action: onNext)
                } else {
                    Button(.placeEditorNext, action: onNext)
                }
            }
        }
        .trackScreen(.placeEditor, parameters: [.mode: EditorMode.new.rawValue, .step: "category"])
    }

    private func tile(_ candidate: PlaceCategory) -> some View {
        let style = CategoryStyle(candidate)
        let isSelected = draft.category == candidate
        return Button {
            draft.choose(candidate)
        } label: {
            VStack(spacing: 8) {
                Image(systemName: style.systemImage)
                    .font(.title2)
                    .foregroundStyle(.white)
                    .frame(width: 52, height: 52)
                    .background(style.tint, in: Circle())
                Text(style.name)
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(.primary)
                    .lineLimit(1)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 16)
            .background(isSelected ? style.tint.opacity(0.15) : Color(.secondarySystemGroupedBackground),
                        in: RoundedRectangle(cornerRadius: 16))
            .overlay {
                RoundedRectangle(cornerRadius: 16)
                    .strokeBorder(isSelected ? style.tint : .clear, lineWidth: 2)
            }
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }
}
