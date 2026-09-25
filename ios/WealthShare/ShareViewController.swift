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
        state.serverVersion = store.serverVersion
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
