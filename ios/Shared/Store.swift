import Foundation
import Security

/// Where the token and the last figures live (contract rule 4).
///
/// The address, the token and the server's name go to the Keychain,
/// readable only on this device and only after it has been unlocked once
/// since boot — so a background round can still read them later. The
/// last snapshot is the household's money too, so it is a file with
/// data protection on, kept out of backups.
///
/// The widget and the share extension read the same Keychain: all three
/// targets list one shared keychain access group first in their
/// entitlements, which makes it where a new item goes. The widget needs
/// the figures without fetching (contract rule 1), so the snapshot and
/// the unticked classes are copied there too. No App Group is needed —
/// and so nothing has to be registered in the developer portal.
final class Store {
    private let service: String
    private let directory: URL
    private let defaults: UserDefaults

    init(service: String = "fr.smarthomeworld.wealth", directory: URL? = nil, defaults: UserDefaults = .standard) {
        self.service = service
        self.defaults = defaults
        self.directory = directory ?? FileManager.default
            .urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("Wealth", isDirectory: true)
    }

    // MARK: Keychain

    var baseURL: String? {
        get { read("base_url") }
        set { write("base_url", newValue) }
    }

    var token: String? {
        get { read("token") }
        set { write("token", newValue) }
    }

    var serverName: String? {
        get { read("server_name") }
        set { write("server_name", newValue) }
    }

    /// What the dashboard said it was, at pairing or in the last
    /// snapshot — the floor a feature checks before it shows itself.
    var serverVersion: String? {
        get { read("server_version") }
        set { write("server_version", newValue) }
    }

    /// The asset classes left out of the figure at the top (contract
    /// rule 8). A way of looking rather than a fact about the money, so
    /// it is the phone's alone — and it survives a restart, because
    /// unticking the house every morning would be worse than not being
    /// able to.
    var excludedClasses: Set<String> {
        get {
            if let list = defaults.stringArray(forKey: "excluded_classes") { return Set(list) }
            // The widget has no defaults of the app's: the Keychain copy.
            return Set((readData("excluded_classes").flatMap { try? JSONDecoder().decode([String].self, from: $0) }) ?? [])
        }
        set {
            defaults.set(newValue.sorted(), forKey: "excluded_classes")
            writeData("excluded_classes", try? JSONEncoder().encode(newValue.sorted()))
        }
    }

    /// Whose figures the app shows: nil for everyone, else a person's id
    /// as `people` gives it. In the Keychain, like the unticked classes,
    /// because the widget has to know whose cached figure it is drawing.
    /// The name is kept beside it so the switch can say whose picture
    /// this is before the dashboard has answered.
    var person: Int? {
        get { read("person").flatMap { Int($0) } }
        set { write("person", newValue.map(String.init)) }
    }

    var personName: String? {
        get { read("person_name") }
        set { write("person_name", newValue) }
    }

    /// Whether the background round runs, and the last thing it said —
    /// so that it does not say the same thing every few hours.
    var watch: Bool {
        get { defaults.bool(forKey: "watch") }
        set { defaults.set(newValue, forKey: "watch") }
    }

    var lastNotice: String? {
        get { defaults.string(forKey: "last_notice") }
        set { defaults.set(newValue, forKey: "last_notice") }
    }

    /// Ask for Face ID, Touch ID or the passcode before the figures show.
    var lockEnabled: Bool {
        get { defaults.bool(forKey: "lock") }
        set { defaults.set(newValue, forKey: "lock") }
    }

    var paired: Bool { !(baseURL ?? "").isEmpty && !(token ?? "").isEmpty }

    func pair(url: String, reply: Paired) {
        baseURL = Api.normalise(url)
        token = reply.token
        serverName = reply.server?.name
        serverVersion = reply.server?.version
    }

    /// "Forget this dashboard": the token, the address and every figure.
    func forget() {
        let query: [String: Any] = [kSecClass as String: kSecClassGenericPassword,
                                    kSecAttrService as String: service]
        SecItemDelete(query as CFDictionary)
        try? FileManager.default.removeItem(at: directory)
        for key in ["excluded_classes", "lock", "watch", "last_notice"] {
            defaults.removeObject(forKey: key)
        }
    }

    func api() -> Api { Api(baseURL: baseURL ?? "", token: token, person: person) }

    private func read(_ key: String) -> String? {
        readData(key).flatMap { String(data: $0, encoding: .utf8) }
    }

    private func write(_ key: String, _ value: String?) {
        writeData(key, value?.data(using: .utf8))
    }

    private func readData(_ key: String) -> Data? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: key,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne,
        ]
        var item: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &item) == errSecSuccess else { return nil }
        return item as? Data
    }

    private func writeData(_ key: String, _ value: Data?) {
        let match: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: key,
        ]
        SecItemDelete(match as CFDictionary)
        guard let data = value else { return }
        var add = match
        add[kSecValueData as String] = data
        add[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        SecItemAdd(add as CFDictionary, nil)
    }

    // MARK: The cache

    /// The last answer and when it came, so the app opens on figures
    /// rather than a spinner (contract rule 1).
    struct Cached: Codable {
        var at: Date
        var snapshot: Snapshot
        /// Whose figures these are; nil for the household. One person's
        /// net worth shown under everyone's would be a wrong figure, not
        /// an old one, so a cache for somebody else is not used.
        var person: Int? = nil
    }

    private var cacheFile: URL { directory.appendingPathComponent("snapshot.json") }

    func cache(_ snapshot: Snapshot, at: Date = Date()) {
        // A cache that cannot be written costs a spinner on the next cold
        // start, nothing more.
        guard let data = try? JSONEncoder().encode(Cached(at: at, snapshot: snapshot, person: person)) else { return }
        try? keep(data, in: cacheFile)
        writeData("snapshot", data)
    }

    /// Written with data protection on and kept out of backups: what is
    /// in here is the household's money.
    private func keep(_ data: Data, in file: URL) throws {
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        try data.write(to: file, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
        var file = file
        var values = URLResourceValues()
        values.isExcludedFromBackup = true
        try? file.setResourceValues(values)
    }

    // MARK: Verdicts not yet sent (contract rule 2)

    private var pendingFile: URL { directory.appendingPathComponent("pending.json") }

    /// The decisions taken on the phone that the dashboard has not heard
    /// yet, oldest first. A triage on a train is the point of doing it on
    /// a phone, so a verdict is kept here first and sent when there is a
    /// network — in the order it was taken, because two verdicts on the
    /// same row must land the way the thumb meant them.
    func pending() -> [Verdict] {
        guard let data = try? Data(contentsOf: pendingFile) else { return [] }
        return (try? JSONDecoder().decode([Verdict].self, from: data)) ?? []
    }

    func savePending(_ list: [Verdict]) {
        if list.isEmpty {
            try? FileManager.default.removeItem(at: pendingFile)
        } else if let data = try? JSONEncoder().encode(list) {
            try? keep(data, in: pendingFile)
        }
    }

    func queue(_ verdict: Verdict) { savePending(pending() + [verdict]) }

    /// The app's own file first; the Keychain copy is what the widget
    /// and the share extension, which cannot see that file, read.
    func cached() -> Cached? {
        guard let data = (try? Data(contentsOf: cacheFile)) ?? readData("snapshot"),
              let cached = try? Api.decoder.decode(Cached.self, from: data),
              cached.person == person else { return nil }
        return cached
    }

    /// Items written before the widget existed sit in the app's own
    /// access group, where the extensions cannot see them. Written again,
    /// they land in the shared one. Once per install; harmless twice.
    func moveToSharedKeychain() {
        guard !defaults.bool(forKey: "keychain_shared") else { return }
        for key in ["base_url", "token", "server_name", "server_version"] {
            if let value = readData(key) { writeData(key, value) }
        }
        if let data = try? Data(contentsOf: cacheFile) { writeData("snapshot", data) }
        if let list = defaults.stringArray(forKey: "excluded_classes") {
            writeData("excluded_classes", try? JSONEncoder().encode(list))
        }
        defaults.set(true, forKey: "keychain_shared")
    }
}
