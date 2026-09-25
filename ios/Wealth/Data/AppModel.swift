import Foundation
import Observation
import WidgetKit

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
    /// The net worth over the last year, for the line under the figure.
    /// Empty until it has been fetched — the overview works without it.
    private(set) var netWorthLine: [Double] = []
    /// The day of each value in `netWorthLine`, for the tooltip.
    private(set) var netWorthDates: [String] = []
    /// Asset classes the viewer has unticked; the figure leaves them out.
    private(set) var excluded: Set<String>
    private(set) var serverVersion: String?
    /// Verdicts taken on this phone that the dashboard has not heard.
    private(set) var waiting = 0
    /// The household as the dashboard knows it; empty hides the switch.
    private(set) var people: [Person] = []
    /// Whose figures are shown: nil for everyone.
    private(set) var person: Int?
    private(set) var personName: String?
    /// Whether the figures hide behind the device's own lock.
    private(set) var lockEnabled: Bool
    /// True while the lock is up.
    var locked: Bool
    var error: String?

    init(store: Store = Store()) {
        self.store = store
        store.moveToSharedKeychain()
        paired = store.paired
        serverName = store.serverName
        serverVersion = store.serverVersion
        excluded = store.excludedClasses
        waiting = store.pending().count
        lockEnabled = store.lockEnabled
        locked = store.paired && store.lockEnabled
        person = store.person
        personName = store.personName
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

    /// Set when a refresh is asked for while one runs under a lens that
    /// has since changed: the running one goes round once more.
    private var again = false

    func refresh() async {
        guard paired else { return }
        if refreshing { again = true; return }
        refreshing = true
        defer { refreshing = false }
        // Who the dashboard knows, beside every refresh: a person added
        // or removed in the browser should show here too.
        Task { await loadPeople() }
        repeat {
            again = false
            await fetchSnapshot()
        } while again
    }

    private func fetchSnapshot() async {
        let asked = person
        do {
            let fresh = try await store.api().snapshot()
            // Somebody else's figures by now: the switch moved while this
            // was on its way, and `again` fetches the right ones.
            guard asked == person else { return }
            let now = Date()
            store.cache(fresh, at: now)
            // The Home screen draws the same cache; redraw it now, so the
            // two never disagree about today's figure.
            WidgetCenter.shared.reloadAllTimelines()
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
            return
        } catch {
            self.error = Self.sentence(for: error)
            return
        }
        // The line is an extra: a dashboard that cannot draw it still
        // gives the figure, and a failure here keeps yesterday's line.
        if let history = try? await store.api().history(period: "1y") {
            netWorthLine = history.line
            netWorthDates = history.lineDates
        }
    }

    /// Quotes and rates, no bank touched (contract rule 9), then a fresh
    /// snapshot — the figures on every screen move together or not at all.
    func refreshMarket() async {
        guard paired, !pricing else { return }
        pricing = true
        defer { pricing = false }
        do {
            let market = try await store.api().refreshMarket()
            await refresh()
            // A quote the provider refused is said beside the figures,
            // with its reason; the snapshot's own failure comes first.
            if error == nil { error = market.note }
        } catch let failure as Api.Failure where failure.status == 404 {
            // The dashboard is older than the button; stop offering it.
            serverVersion = serverVersion ?? "0"
            error = String(localized: "This dashboard cannot refresh quotes on its own yet.")
        } catch {
            self.error = Self.sentence(for: error)
        }
    }

    // MARK: Whose figures

    /// Who the dashboard knows. A dashboard older than 0.72.4 has no
    /// `people`; it answers 404 and the switch stays away.
    private func loadPeople() async {
        let known: [Person]
        do {
            known = try await store.api().people()
        } catch let failure as Api.Failure where failure.status == 404 {
            known = []
        } catch {
            return
        }
        people = known
        // The person this phone was showing is gone from the dashboard:
        // fall back to everyone rather than ask for somebody it no
        // longer knows.
        if let person, !known.contains(where: { $0.id == person }) { await choose(nil) }
    }

    /// Show one person's accounts, or everyone's (nil). The figures on
    /// screen go at once, since they were somebody else's; the pages
    /// that watch `person` ask again by themselves.
    func choose(_ who: Person?) async {
        guard who?.id != person else { return }
        store.person = who?.id
        store.personName = who?.name
        person = who?.id
        personName = who?.name
        let cached = store.cached()
        snapshot = cached?.snapshot
        readAt = cached?.at
        stale = true
        WidgetCenter.shared.reloadAllTimelines()
        await refresh()
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

    // MARK: The portfolio

    func holdings() async throws -> Holdings { try await store.api().holdings() }
    func history(period: String) async throws -> History { try await store.api().history(period: period) }
    /// The ring and the returns are extras: a dashboard that cannot give
    /// them still shows the holdings.
    func allocation() async -> Allocation? { try? await store.api().allocation() }
    func returns() async -> Returns? { try? await store.api().returns() }

    // MARK: The triage

    struct Triage {
        var rows: [QueueRow] = []
        var remaining = 0
        var categories: [Category] = []
        var people: [Person] = []
    }

    /// One of the two queues. What was decided and not yet sent goes out
    /// first, so the queue does not hand back a row that was dealt with
    /// on the train.
    func triage(owning: Bool) async throws -> Triage {
        await flush()
        let api = store.api()
        if owning {
            async let queue = api.unowned()
            async let people = api.people()
            let (q, who) = try await (queue, people)
            return Triage(rows: q.transactions ?? [], remaining: q.remaining ?? 0, people: who)
        } else {
            async let queue = api.uncategorised()
            async let categories = api.categories()
            let (q, choices) = try await (queue, categories)
            return Triage(rows: q.transactions ?? [], remaining: q.remaining ?? 0, categories: choices)
        }
    }

    /// A verdict: written down at once, sent when the network allows
    /// (contract rule 2). The card moves on either way.
    func decide(_ verdict: Verdict) {
        store.queue(verdict)
        waiting = store.pending().count
        Task { await flush() }
    }

    private var flushing = false

    /// Send what is waiting, oldest first; stop at the first failure that
    /// is not the dashboard's refusal and keep the rest.
    func flush() async {
        guard paired, !flushing else { return }
        flushing = true
        defer { flushing = false; waiting = store.pending().count }
        await Self.flush(store)
    }

    /// The same round, for the app and for the background: oldest first,
    /// and a failure that is not the dashboard's refusal stops it.
    nonisolated static func flush(_ store: Store) async {
        let api = store.api()
        while let next = store.pending().first {
            do {
                try await api.deliver(next)
            } catch let failure as Api.Failure where (400..<500).contains(failure.status) && failure.status != 401 {
                // A category that no longer exists, a row somebody
                // deleted: dropping it is right, retrying forever would
                // block the queue. A 401 is not about the verdict.
            } catch {
                return
            }
            // Only this one: another may have been queued meanwhile.
            var left = store.pending()
            if left.first == next { left.removeFirst() }
            store.savePending(left)
        }
    }

    /// Take the last verdict back while it is still on the phone. Once
    /// the dashboard has it, the place to change it is the dashboard.
    /// Returns whether there was one to take back.
    @discardableResult
    func undoLast() -> Bool {
        var left = store.pending()
        guard !left.isEmpty else { return false }
        left.removeLast()
        store.savePending(left)
        waiting = left.count
        return true
    }

    // MARK: A file opened with "Open in Wealth"

    /// The import sheet for a file opened from Safari's downloads or the
    /// Files app — the share extension's screen, shown by the app.
    var opened: ShareState?

    /// A downloaded statement arrives as a URL rather than a share. It is
    /// read at once; several files opened together join one sheet, as
    /// several shared at once would.
    func open(_ url: URL) {
        guard url.isFileURL else { return }
        let upload = Api.Upload.read(url)
        // iOS copied it into Documents/Inbox for us; the dashboard keeps
        // the statement, the phone need not.
        if url.path.contains("/Documents/Inbox/") { try? FileManager.default.removeItem(at: url) }
        if let sheet = opened, sheet.reply == nil, !sheet.sending {
            if let upload { sheet.files.append(upload) }
            return
        }
        let sheet = ShareState()
        sheet.paired = paired
        sheet.accounts = accounts
        sheet.api = store.api()
        sheet.serverVersion = serverVersion
        sheet.files = upload.map { [$0] } ?? []
        sheet.reading = false
        sheet.close = { [weak self] in self?.opened = nil }
        opened = sheet
    }

    // MARK: The background round

    var watchEnabled: Bool { store.watch }

    func setWatch(_ on: Bool) {
        store.watch = on
        if !on { store.lastNotice = nil }
        Round.schedule(on && paired)
    }

    // MARK: The lock

    func setLock(_ on: Bool) {
        store.lockEnabled = on
        lockEnabled = on
    }

    /// Called when the app goes to the background: the next person to
    /// pick up the phone meets the lock.
    func lockIfEnabled() {
        if paired && lockEnabled { locked = true }
    }

    func forget() {
        store.forget()
        Round.schedule(false)
        WidgetCenter.shared.reloadAllTimelines()
        waiting = 0
        lockEnabled = false
        locked = false
        paired = false
        serverName = nil
        serverVersion = nil
        excluded = []
        people = []
        person = nil
        personName = nil
        snapshot = nil
        netWorthLine = []
        netWorthDates = []
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
                return String(localized: "No network — the figures are the last ones read.")
            case .cannotFindHost, .cannotConnectToHost, .timedOut:
                return String(localized: "The dashboard did not answer at that address.")
            case .appTransportSecurityRequiresSecureConnection:
                return String(localized: "This address needs https.")
            default: break
            }
        }
        return error.localizedDescription
    }
}
