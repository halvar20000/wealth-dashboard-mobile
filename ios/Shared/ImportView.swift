import SwiftUI

// A statement into an account — one screen for two doors. The share
// extension shows it for a file shared from another app; the app shows
// it for a file opened with "Open in Wealth" from Safari's downloads or
// the Files app. Both are the same question (which account?) and the
// same answer (what the dashboard made of it), so they share the code.

@Observable
final class ShareState {
    var paired = false
    var accounts: [Account] = []
    var files: [Api.Upload] = []
    var reading = true
    var sending = false
    var reply: ImportReply?
    var error: String?
    /// While the Undo after an import runs, and how many rows it took back.
    var undoing = false
    var undone: Int?
    var api = Api(baseURL: "", token: nil)
    /// What the dashboard said it was — Undo needs 0.74.0.
    var serverVersion: String?
    var close: () -> Void = {}

    @MainActor
    func send(to account: Account) async {
        sending = true
        error = nil
        defer { sending = false }
        do {
            reply = try await api.importFiles(accountId: account.id, files: files)
        } catch {
            self.error = (error as? Api.Failure)?.message ?? error.localizedDescription
        }
    }

    /// The imports Undo would take back — none when the file brought
    /// nothing new, or the dashboard is too old to take one back. After
    /// "0 new, 12 already there" there is nothing to undo, and a button
    /// that does nothing is worse than none.
    var undoable: (account: Int, imports: [Int])? {
        guard let reply, let account = reply.account?.id,
              let imports = reply.result?.imports, !imports.isEmpty,
              (reply.result?.inserted ?? 0) > 0,
              DashboardVersion.isAtLeast(serverVersion ?? "0", "0.74.0") else { return nil }
        return (account, imports)
    }

    /// Put back exactly what this share brought. Several files in one
    /// share are several imports; they go newest first.
    @MainActor
    func undo() async {
        guard let target = undoable, !undoing else { return }
        undoing = true
        error = nil
        defer { undoing = false }
        var removed = 0
        do {
            for id in target.imports.sorted(by: >) {
                removed += try await api.undoImport(accountId: target.account, importId: id)
            }
            undone = removed
        } catch {
            // What went before the failure is gone all the same; say both.
            if removed > 0 { undone = removed }
            self.error = (error as? Api.Failure)?.message ?? error.localizedDescription
        }
    }
}

/// One sheet per file opened, told apart by identity.
extension ShareState: Identifiable {}

/// Which account does this statement belong to? — and then what the
/// dashboard made of it. Three states, no more: pick, sending, done.
struct ShareView: View {
    @Bindable var state: ShareState

    var body: some View {
        NavigationStack {
            List {
                Section {
                    Text(state.files.map(\.name).joined(separator: ", ").isEmpty
                         ? (state.reading ? "…" : String(localized: "Nothing was shared."))
                         : state.files.map(\.name).joined(separator: ", "))
                        .font(.footnote).foregroundStyle(.secondary)
                }
                content
            }
            .navigationTitle(state.reply == nil ? "Import into…" : "Imported")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: state.reply == nil ? .cancellationAction : .confirmationAction) {
                    Button(state.reply == nil ? "Cancel" : "Done") { state.close() }
                }
            }
        }
    }

    @ViewBuilder private var content: some View {
        if !state.paired {
            Text("Pair this phone with your dashboard first — open the app and enter the six-digit code from Settings → Assistants.")
        } else if state.reading {
            ProgressView()
        } else if state.files.isEmpty {
            Text("The app that shared this sent no file.")
        } else if let reply = state.reply {
            report(reply)
            undo
        } else if state.sending {
            HStack(spacing: 12) {
                ProgressView()
                Text("Sending to the dashboard…")
            }
        } else if state.accounts.isEmpty {
            Text("No accounts yet — open the app once so it knows them.")
        } else {
            if let error = state.error {
                Section {
                    Text(error).foregroundStyle(.red)
                }
            }
            Section("Account") {
                ForEach(state.accounts) { a in
                    Button {
                        Task { await state.send(to: a) }
                    } label: {
                        VStack(alignment: .leading) {
                            Text(a.title).foregroundStyle(Color.primary)
                            let detail = [a.type, a.bank].compactMap { $0 }.filter { !$0.isEmpty }.joined(separator: " · ")
                            if !detail.isEmpty {
                                Text(detail).font(.caption).foregroundStyle(.secondary)
                            }
                        }
                    }
                }
            }
        }
    }

    /// What came back, in the dashboard's own words. A file that brought
    /// nothing new is not a failure — a statement overlapping the last
    /// one is the normal case — so the count is stated plainly and the
    /// server's notes are repeated underneath.
    private func report(_ reply: ImportReply) -> some View {
        let r = reply.result
        return Section {
            Text(r?.label ?? String(localized: "Read")).fontWeight(.semibold)
            Text("\(r?.inserted ?? 0) new, \(r?.duplicates ?? 0) already there")
            if let name = reply.account?.name { Text(name).foregroundStyle(.secondary) }
            ForEach(Array(((r?.notes ?? []) + (r?.problems ?? [])).prefix(4).enumerated()), id: \.offset) { _, line in
                Text(line).font(.footnote).foregroundStyle(.secondary)
            }
        }
    }

    /// The wrong account, noticed in time: the Undo the account page in
    /// the browser has always had, without the trip to a laptop. The
    /// balance the file read stays — a balance is a reading, not a booking.
    @ViewBuilder private var undo: some View {
        if let undone = state.undone {
            Section {
                Text("Taken back — \(undone) row(s) removed again.")
                    .font(.footnote).foregroundStyle(.secondary)
                if let error = state.error {
                    Text(error).font(.footnote).foregroundStyle(.red)
                }
            }
        } else if state.undoable != nil {
            Section {
                Button(role: .destructive) {
                    Task { await state.undo() }
                } label: {
                    if state.undoing {
                        HStack(spacing: 12) {
                            ProgressView()
                            Text("Taking it back…")
                        }
                    } else {
                        Text("Undo this import")
                    }
                }
                .disabled(state.undoing)
                if let error = state.error {
                    Text(error).font(.footnote).foregroundStyle(.red)
                }
            }
        }
    }
}
