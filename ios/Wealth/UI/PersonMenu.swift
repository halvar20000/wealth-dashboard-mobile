import SwiftUI

/// Whose picture: everyone's, or one person's accounts — the switch the
/// dashboard has at the top of every page, as a menu at the top of every
/// tab. Hidden on a dashboard that knows nobody, or is too old to say.
struct PersonMenu: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        Menu {
            Picker("Whose accounts", selection: choice) {
                Text("Everyone").tag(Int?.none)
                ForEach(model.people) { p in
                    Text(p.name ?? "#\(p.id)").tag(Int?.some(p.id))
                }
            }
        } label: {
            Label(title, systemImage: model.person == nil ? "person.2" : "person")
                .labelStyle(.titleAndIcon)
        }
    }

    private var title: String {
        guard let person = model.person else { return String(localized: "Everyone") }
        return model.people.first { $0.id == person }?.name ?? model.personName ?? "#\(person)"
    }

    private var choice: Binding<Int?> {
        Binding(
            get: { model.person },
            set: { id in
                let who = model.people.first { $0.id == id }
                Task { await model.choose(who) }
            })
    }
}

extension View {
    /// The person switch in the navigation bar's leading corner.
    func personMenu() -> some View { modifier(PersonMenuItem()) }
}

private struct PersonMenuItem: ViewModifier {
    @Environment(AppModel.self) private var model

    func body(content: Content) -> some View {
        content.toolbar {
            if !model.people.isEmpty {
                ToolbarItem(placement: .topBarLeading) { PersonMenu() }
            }
        }
    }
}
