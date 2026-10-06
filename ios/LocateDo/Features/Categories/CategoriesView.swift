import SwiftData
import SwiftUI

struct CategoriesView: View {
    @Environment(LocalWrites.self) private var writes
    @Query(sort: \PlaceCategory.sortOrder) private var categories: [PlaceCategory]

    @State private var editing: PlaceCategory?
    @State private var isAdding = false
    @State private var pendingDelete: PlaceCategory?
    @State private var failure: LocalizedStringResource?

    var body: some View {
        List {
            Section {
                ForEach(categories) { category in
                    Button {
                        editing = category
                    } label: {
                        row(for: category)
                    }
                    .foregroundStyle(.primary)
                }
                .onMove(perform: move)
                .onDelete(perform: requestDelete)
            } footer: {
                if let failure {
                    Text(failure)
                        .foregroundStyle(.red)
                }
            }
            Section {
                Button {
                    isAdding = true
                } label: {
                    Label(.categoryAdd, systemImage: "plus")
                }
            }
        }
        .trackScreen(.categories)
        .navigationTitle(Text(.categoryTitle))
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            EditButton()
        }
        .sheet(item: $editing) { category in
            CategoryEditorView(category: category)
        }
        .sheet(isPresented: $isAdding) {
            CategoryEditorView(category: nil)
        }
        .confirmationDialog(
            Text(.categoryDeleteConfirmTitle(pendingDelete?.displayName ?? "")),
            isPresented: isConfirmingDelete,
            titleVisibility: .visible,
            presenting: pendingDelete
        ) { category in
            Button(.commonDelete, role: .destructive) {
                delete(category)
            }
        } message: { category in
            Text(.categoryDeleteConfirmMessage(category.places.count))
        }
    }

    private func row(for category: PlaceCategory) -> some View {
        let style = CategoryStyle(category)
        return HStack(spacing: 12) {
            Image(systemName: style.systemImage)
                .font(.body)
                .foregroundStyle(.white)
                .frame(width: 32, height: 32)
                .background(style.tint, in: .circle)
                .accessibilityHidden(true)
            Text(style.name)
            Spacer()
            Text(.categoryPlaceCount(category.places.count))
                .font(.footnote)
                .foregroundStyle(.secondary)
        }
    }

    private var isConfirmingDelete: Binding<Bool> {
        Binding {
            pendingDelete != nil
        } set: { isPresented in
            if !isPresented {
                pendingDelete = nil
            }
        }
    }

    private func move(from source: IndexSet, to destination: Int) {
        var ordered = categories
        ordered.move(fromOffsets: source, toOffset: destination)
        writes.reorder(ordered)
    }

    private func requestDelete(at offsets: IndexSet) {
        guard let index = offsets.first else {
            return
        }
        let category = categories[index]
        if category.places.isEmpty {
            delete(category)
        } else {
            pendingDelete = category
        }
    }

    private func delete(_ category: PlaceCategory) {
        failure = nil
        if !writes.delete(category) {
            failure = .categoryLastOne
        }
    }
}
