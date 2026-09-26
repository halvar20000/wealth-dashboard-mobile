import SwiftUI

/// One `## x.y.z` of CHANGELOG.md and the lines under it.
struct Release: Identifiable {
    let version: String
    let notes: [String]
    var id: String { version }

    /// CHANGELOG.md from the app bundle: the repository's own file,
    /// shared with Android and TestFlight. Whatever stands before the
    /// first heading is for whoever edits the file, not for the app.
    static func changelog(in bundle: Bundle = .main) -> [Release] {
        guard let url = bundle.url(forResource: "CHANGELOG", withExtension: "md"),
              let text = try? String(contentsOf: url, encoding: .utf8) else { return [] }
        return parse(text)
    }

    static func parse(_ text: String) -> [Release] {
        var releases: [Release] = []
        var version: String?
        var notes: [String] = []
        for line in text.components(separatedBy: .newlines) {
            let trimmed = line.trimmingCharacters(in: .whitespaces)
            if trimmed.hasPrefix("## ") {
                if let version { releases.append(Release(version: version, notes: notes)) }
                version = String(trimmed.dropFirst(3)).trimmingCharacters(in: .whitespaces)
                notes = []
            } else if version != nil, !trimmed.isEmpty {
                let note = trimmed.hasPrefix("- ") ? String(trimmed.dropFirst(2)) : trimmed
                notes.append(note.trimmingCharacters(in: .whitespaces))
            }
        }
        if let version { releases.append(Release(version: version, notes: notes)) }
        return releases
    }
}

/// Every release, newest first.
struct WhatsNewView: View {
    private let releases = Release.changelog()

    var body: some View {
        List(releases) { release in
            Section(release.version) {
                ForEach(release.notes, id: \.self) { note in
                    Text(note)
                }
            }
        }
        .navigationTitle("What's new")
    }
}
