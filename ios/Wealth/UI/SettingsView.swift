import SwiftUI

struct SettingsView: View {
    @Environment(AppModel.self) private var model
    @State private var confirming = false

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    LabeledContent("Paired with", value: model.serverName ?? String(localized: "your dashboard"))
                    if let version = model.snapshot?.server?.version {
                        LabeledContent("Dashboard version", value: version)
                    }
                }
                Section {
                    Button("Forget this dashboard", role: .destructive) { confirming = true }
                } footer: {
                    Text("Removes the address, the token and every figure from this phone. Pairing again costs a new six-digit code.")
                }
            }
            .navigationTitle("Settings")
            .confirmationDialog("Forget this dashboard?", isPresented: $confirming, titleVisibility: .visible) {
                Button("Forget", role: .destructive) { model.forget() }
            }
        }
    }
}
