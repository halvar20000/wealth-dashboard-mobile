import Foundation
import Security

/// Where the token and the last figures live (contract rule 4).
///
/// The address, the token and the server's name go to the Keychain,
/// readable only on this device and only after it has been unlocked once
/// since boot — so a background round can still read them later. The
/// last snapshot is the household's money too, so it is a file with
/// data protection on, kept out of backups.
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
        get { Set(defaults.stringArray(forKey: "excluded_classes") ?? []) }
        set { defaults.set(newValue.sorted(), forKey: "excluded_classes") }
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
        defaults.removeObject(forKey: "excluded_classes")
    }

    func api() -> Api { Api(baseURL: baseURL ?? "", token: token) }

    private func read(_ key: String) -> String? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: key,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne,
        ]
        var item: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &item) == errSecSuccess,
              let data = item as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }

    private func write(_ key: String, _ value: String?) {
        let match: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: key,
        ]
        SecItemDelete(match as CFDictionary)
        guard let value, let data = value.data(using: .utf8) else { return }
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
    }

    private var cacheFile: URL { directory.appendingPathComponent("snapshot.json") }

    func cache(_ snapshot: Snapshot, at: Date = Date()) {
        do {
            try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
            let data = try JSONEncoder().encode(Cached(at: at, snapshot: snapshot))
            try data.write(to: cacheFile, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
            var file = cacheFile
            var values = URLResourceValues()
            values.isExcludedFromBackup = true
            try? file.setResourceValues(values)
        } catch {
            // A cache that cannot be written costs a spinner on the next
            // cold start, nothing more.
        }
    }

    func cached() -> Cached? {
        guard let data = try? Data(contentsOf: cacheFile) else { return nil }
        return try? Api.decoder.decode(Cached.self, from: data)
    }
}
