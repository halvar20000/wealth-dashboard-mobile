import SwiftUI

/// The colours offered for a category — the dashboard's own range, so a
/// category made on the phone sits well beside the built-in ones.
let categoryPalette = [
    "#f87171", "#fb923c", "#fbbf24", "#a3e635", "#34d399", "#2dd4bf",
    "#38bdf8", "#5b9dff", "#818cf8", "#a78bfa", "#e879f9", "#f472b6",
    "#94a3b8", "#64748b",
]

private let groups = ["spending", "income", "non_spending"]

/// The categories the dashboard files rows under — make one, rename it,
/// give it another colour, say what it counts as, or remove it. Each
/// change goes straight to the dashboard: this is its list, not a copy.
struct CategoriesView: View {
    @Environment(AppModel.self) private var model
    @State private var editing: Category?
    @State private var error: String?
    @State private var loaded = false

    var body: some View {
        List {
            Section {
                Text("Create, rename and recolour the categories every row is filed under — the same list as on the dashboard.")
                    .font(.footnote).foregroundStyle(.secondary)
                if !model.canEditCategories {
                    Text("Editing categories needs dashboard 0.79.2 or newer.")
                        .font(.footnote).foregroundStyle(Color.loss)
                }
                if let error { Text(error).font(.footnote).foregroundStyle(Color.loss) }
            }
            ForEach(groups, id: \.self) { g in
                let rows = model.categories.filter { ($0.group ?? "spending") == g }
                if !rows.isEmpty {
                    Section(groupLabel(g)) {
                        ForEach(rows) { c in
                            Button { editing = c } label: {
                                HStack {
                                    Circle().fill(Color(hex: c.colour) ?? .secondary).frame(width: 12, height: 12)
                                    Text(c.title).foregroundStyle(Color.primary)
                                    Spacer()
                                    Text("\(c.transactions ?? 0) rows").font(.caption).foregroundStyle(.secondary)
                                }
                            }
                            .disabled(!model.canEditCategories)
                        }
                    }
                }
            }
        }
        .overlay { if !loaded && model.categories.isEmpty { ProgressView() } }
        .navigationTitle("Categories")
        .toolbar {
            if model.canEditCategories {
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        editing = Category(slug: "", label: "", group: "spending", colour: categoryPalette[9])
                    } label: {
                        Label("New category", systemImage: "plus")
                    }
                }
            }
        }
        .task { await load() }
        .refreshable { await load() }
        .sheet(item: $editing) { c in
            CategoryEditor(category: c)
        }
    }

    private func load() async {
        do {
            try await model.loadCategories()
            error = nil
        } catch is CancellationError {
        } catch let url as URLError where url.code == .cancelled {
        } catch {
            self.error = AppModel.sentence(for: error)
        }
        loaded = true
    }
}

private struct CategoryEditor: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    let category: Category

    @State private var label = ""
    @State private var colour = categoryPalette[9]
    @State private var group = "spending"
    @State private var saving = false
    @State private var confirming = false
    @State private var error: String?

    private var isNew: Bool { category.slug.isEmpty }
    private var locked: Bool { category.groupLocked == true }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("Name", text: $label)
                        .onChange(of: label) { if label.count > 40 { label = String(label.prefix(40)) } }
                }
                Section("Colour") {
                    LazyVGrid(columns: Array(repeating: GridItem(.flexible()), count: 7), spacing: 10) {
                        ForEach(categoryPalette, id: \.self) { hex in
                            Circle()
                                .fill(Color(hex: hex) ?? .secondary)
                                .frame(width: 30, height: 30)
                                .overlay {
                                    if hex == colour { Circle().stroke(Color.primary, lineWidth: 3) }
                                }
                                .onTapGesture { colour = hex }
                        }
                    }
                    .padding(.vertical, 4)
                }
                Section {
                    if locked {
                        LabeledContent("Counts as", value: groupLabel(group))
                    } else {
                        Picker("Counts as", selection: $group) {
                            ForEach(groups, id: \.self) { g in Text(groupLabel(g)).tag(g) }
                        }
                    }
                } footer: {
                    if locked { Text("The dashboard fixes what this one counts as.") }
                }
                if !isNew {
                    Section {
                        if category.deletable != false {
                            Button("Delete", role: .destructive) { confirming = true }
                                .disabled(saving)
                        } else {
                            Text("Built in: it can be renamed and recoloured, not deleted.")
                                .font(.footnote).foregroundStyle(.secondary)
                        }
                    }
                }
                if let error {
                    Section { Text(error).foregroundStyle(Color.loss) }
                }
            }
            .navigationTitle(isNew ? Text("New category") : Text("Edit category"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    if saving { ProgressView() } else {
                        Button("Save") { Task { await save() } }
                            .disabled(label.trimmingCharacters(in: .whitespaces).isEmpty)
                    }
                }
            }
            .confirmationDialog(String(localized: "Delete “\(category.title)”?"),
                                isPresented: $confirming, titleVisibility: .visible) {
                Button("Delete", role: .destructive) { Task { await delete() } }
            } message: {
                Text("Its \(category.transactions ?? 0) transactions move to “Other”, and its \(category.rules ?? 0) rules are removed with it.")
            }
        }
        .onAppear {
            label = category.label ?? ""
            colour = category.colour?.lowercased() ?? categoryPalette[9]
            group = category.group ?? "spending"
        }
    }

    private func save() async {
        saving = true
        defer { saving = false }
        do {
            try await model.saveCategory(slug: isNew ? nil : category.slug,
                                         label: label.trimmingCharacters(in: .whitespaces),
                                         colour: colour, group: locked ? nil : group)
            dismiss()
        } catch {
            self.error = AppModel.sentence(for: error)
        }
    }

    private func delete() async {
        saving = true
        defer { saving = false }
        do {
            _ = try await model.deleteCategory(category.slug)
            dismiss()
        } catch {
            self.error = AppModel.sentence(for: error)
        }
    }
}
