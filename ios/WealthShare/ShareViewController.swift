import SwiftUI
import UIKit
import UniformTypeIdentifiers

/// The share sheet: a statement from the bank's own app, from Files or
/// from Mail, straight into an account.
///
/// The files are read here and now rather than remembered as links —
/// what the sending app hands over is readable only while this sheet
/// lives, and a statement is a few hundred kilobytes.
///
/// The dashboard does the reading. This sheet only asks which account
/// the file belongs to and repeats what came back, including the
/// sentences the server says when a file is a scan or rows were kept
/// out: the app inventing its own wording would be a second place to
/// keep those explanations right.
final class ShareViewController: UIViewController {
    private let state = ShareState()

    override func viewDidLoad() {
        super.viewDidLoad()
        let store = Store()
        state.paired = store.paired
        state.accounts = store.cached()?.snapshot.accounts ?? []
        state.api = store.api()
        state.close = { [weak self] in
            self?.extensionContext?.completeRequest(returningItems: nil)
        }

        let host = UIHostingController(rootView: ShareView(state: state))
        addChild(host)
        host.view.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(host.view)
        NSLayoutConstraint.activate([
            host.view.topAnchor.constraint(equalTo: view.topAnchor),
            host.view.bottomAnchor.constraint(equalTo: view.bottomAnchor),
            host.view.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            host.view.trailingAnchor.constraint(equalTo: view.trailingAnchor),
        ])
        host.didMove(toParent: self)

        let providers = (extensionContext?.inputItems as? [NSExtensionItem] ?? [])
            .flatMap { $0.attachments ?? [] }
        Task { @MainActor in
            var files: [Api.Upload] = []
            for provider in providers {
                if let file = await Self.read(provider) { files.append(file) }
            }
            state.files = files
            state.reading = false
        }
    }

    /// One attachment as bytes, with the name the sending app gave it.
    private static func read(_ provider: NSItemProvider) async -> Api.Upload? {
        let type = provider.registeredTypeIdentifiers
            .compactMap { UTType($0) }
            .first { $0.conforms(to: .data) } ?? .data
        let suggested = provider.suggestedName
        return await withCheckedContinuation { done in
            _ = provider.loadFileRepresentation(forTypeIdentifier: type.identifier) { url, _ in
                // The URL is gone when this closure returns: read it now.
                guard let url, let data = try? Data(contentsOf: url) else { return done.resume(returning: nil) }
                let name = suggested.map { name in
                    url.pathExtension.isEmpty || name.hasSuffix("." + url.pathExtension)
                        ? name : name + "." + url.pathExtension
                } ?? url.lastPathComponent
                done.resume(returning: Api.Upload(name: name, mime: type.preferredMIMEType, data: data))
            }
        }
    }
}

@Observable
final class ShareState {
    var paired = false
    var accounts: [Account] = []
    var files: [Api.Upload] = []
    var reading = true
    var sending = false
    var reply: ImportReply?
    var error: String?
    var api = Api(baseURL: "", token: nil)
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
}

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
}
