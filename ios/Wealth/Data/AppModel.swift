import Foundation
import Observation

/// One place that knows the order of things: read the cache first so a
/// cold start shows figures, then ask the dashboard, then keep what came
/// back. A refresh that fails leaves the cache alone — yesterday's net
/// worth with the day on it beats an empty screen.
@MainActor
@Observable
final class AppModel {
    private let store: Store

    private(set) var paired: Bool
    private(set) var serverName: String?
    private(set) var snapshot: Snapshot?
    /// When the figures on screen were read.
    private(set) var readAt: Date?
    /// True while the figures are the cached ones, not this session's.
    private(set) var stale = true
    private(set) var refreshing = false
    private(set) var pairing = false
    /// Set while the cheap refresh runs, so its button can say so.
    private(set) var pricing = false
    /// Asset classes the viewer has unticked; the figure leaves them out.
    private(set) var excluded: Set<String>
    private(set) var serverVersion: String?
    var error: String?

    init(store: Store = Store()) {
        self.store = store
        paired = store.paired
        serverName = store.serverName
        serverVersion = store.serverVersion
        excluded = store.excludedClasses
        if let cached = store.cached() {
            snapshot = cached.snapshot
            readAt = cached.at
        }
    }

    /// Whether the paired dashboard is at least `floor`. Unknown counts as
    /// yes: a call it does not know answers 404, and the screen that made
    /// it degrades then (contract rule 6).
    func supports(_ floor: String) -> Bool {
        guard let serverVersion, !serverVersion.isEmpty else { return true }
        return DashboardVersion.isAtLeast(serverVersion, floor)
    }

    var accounts: [Account] { snapshot?.accounts ?? [] }
    var currency: String { snapshot?.currency ?? "EUR" }

    func pair(url: String, code: String) async {
        pairing = true
        error = nil
        defer { pairing = false }
        do {
            let reply = try await Api.pair(baseURL: url, code: code)
            store.pair(url: url, reply: reply)
            serverName = store.serverName
            serverVersion = store.serverVersion
            paired = true
            await refresh()
        } catch {
            self.error = Self.sentence(for: error)
        }
    }

    func refresh() async {
        guard paired, !refreshing else { return }
        refreshing = true
        defer { refreshing = false }
        do {
            let fresh = try await store.api().snapshot()
            let now = Date()
            store.cache(fresh, at: now)
            snapshot = fresh
            readAt = now
            if let version = fresh.server?.version, version != serverVersion {
                store.serverVersion = version
                serverVersion = version
            }
            stale = false
            error = nil
        } catch let failure as Api.Failure where failure.status == 401 {
            // The token is gone on the dashboard's side. The figures stay
            // until the person decides to pair again.
            error = failure.message
        } catch {
            self.error = Self.sentence(for: error)
        }
    }

    /// Quotes and rates, no bank touched (contract rule 9), then a fresh
    /// snapshot — the figures on every screen move together or not at all.
    func refreshMarket() async {
        guard paired, !pricing else { return }
        pricing = true
        defer { pricing = false }
        do {
            _ = try await store.api().refreshMarket()
            await refresh()
        } catch let failure as Api.Failure where failure.status == 404 {
            // The dashboard is older than the button; stop offering it.
            serverVersion = serverVersion ?? "0"
            error = String(localized: "This dashboard cannot refresh quotes on its own yet.")
        } catch {
            self.error = Self.sentence(for: error)
        }
    }

    func toggleClass(_ name: String) {
        if excluded.contains(name) { excluded.remove(name) } else { excluded.insert(name) }
        store.excludedClasses = excluded
    }

    func includeEverything() {
        excluded = []
        store.excludedClasses = []
    }

    func cashflow(months: Int) async throws -> Cashflow {
        try await store.api().cashflow(months: months)
    }

    func transactions(accountId: Int? = nil, query: String? = nil) async throws -> TransactionPage {
        try await store.api().transactions(accountId: accountId, query: query)
    }

    func forget() {
        store.forget()
        paired = false
        serverName = nil
        serverVersion = nil
        excluded = []
        snapshot = nil
        readAt = nil
        stale = true
        error = nil
    }

    /// The dashboard's own words where there are some, the system's
    /// otherwise.
    static func sentence(for error: Error) -> String {
        if let failure = error as? Api.Failure { return failure.message }
        if let url = error as? URLError {
            switch url.code {
            case .notConnectedToInternet, .networkConnectionLost:
                return "No network — the figures are the last ones read."
            case .cannotFindHost, .cannotConnectToHost, .timedOut:
                return "The dashboard did not answer at that address."
            case .appTransportSecurityRequiresSecureConnection:
                return "This address needs https."
            default: break
            }
        }
        return error.localizedDescription
    }
}
