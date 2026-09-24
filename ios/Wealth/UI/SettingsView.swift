import SwiftUI

struct SettingsView: View {
    @Environment(AppModel.self) private var model
    @State private var confirming = false
    @State private var watching = false

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
                    Toggle("Ask to unlock", isOn: Binding(get: { model.lockEnabled },
                                                          set: { model.setLock($0) }))
                } footer: {
                    Text("Face ID, Touch ID or the passcode before the figures show — the phone's own lock, nothing of the app's.")
                }
                Section {
                    Toggle("Watch in the background", isOn: Binding(get: { watching }, set: { on in
                        watching = on
                        model.setWatch(on)
                        if on { Task { await Round.askToNotify() } }
                    }))
                } footer: {
                    Text("Every few hours, as iOS allows: send what you decided offline, refresh the figures and the widget, and say something when an account is about to run out, a bank link has stopped, or the queue has grown.")
                }
                Section {
                    Button("Forget this dashboard", role: .destructive) { confirming = true }
                } footer: {
                    Text("Removes the address, the token and every figure from this phone. Pairing again costs a new six-digit code.")
                }
            }
            .navigationTitle("Settings")
            .onAppear { watching = model.watchEnabled }
            .confirmationDialog("Forget this dashboard?", isPresented: $confirming, titleVisibility: .visible) {
                Button("Forget", role: .destructive) { model.forget() }
            }
        }
    }
}
