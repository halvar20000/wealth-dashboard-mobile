import Foundation

// What the dashboard answers with. Every field is optional on purpose:
// this app and the server it talks to are updated separately, and a
// field added or dropped on one side must not make the other refuse to
// start (contract rule 5). Unknown keys are ignored by `Codable` anyway;
// the defaults live in the computed accessors next to each type.
//
// The names are the dashboard's own in camelCase — `Api.decoder`
// converts from snake_case, and the cache is written in camelCase,
// which the same decoder reads back unchanged.

struct Paired: Codable {
    var ok: Bool?
    var token: String?
    var server: ServerInfo?
    var baseCurrency: String?
    var error: String?
}

struct ServerInfo: Codable, Equatable {
    var name: String?
    var version: String?
}

/// The envelope every tool answers in.
struct ToolReply<T: Decodable>: Decodable {
    var ok: Bool?
    var result: T?
    var error: String?
}

struct Snapshot: Codable {
    var at: String?
    var baseCurrency: String?
    var netWorth: NetWorth?
    var performance: [String: Period]?
    var accounts: [Account]?
    var upcoming: Upcoming?
    var events: [Event]?
    var waiting: Waiting?
    var sync: SyncHealth?
    var server: ServerInfo?

    var currency: String { baseCurrency ?? "EUR" }
}

struct NetWorth: Codable {
    /// The dashboard calls the total `net_worth` inside `net_worth`.
    var netWorth: Double?
    var cash: Double?
    var securities: Double?
    var assets: Double?
    var debt: Double?
    var fxAsOf: String?
    var pricesAsOf: String?
    var byClass: [ClassValue]?

    var total: Double? { netWorth }
}

struct ClassValue: Codable, Hashable {
    var name: String?
    var value: Double?
}

/// One window of the return: the percentage, and the gain in money.
struct Period: Codable {
    var name: String?
    var since: String?
    var twr: Double?
    var twrAnnual: Double?
    var mwr: Double?
    var pnl: Double?
    var days: Int?
}

struct Account: Codable, Identifiable, Hashable {
    var id: Int
    var name: String?
    var type: String?
    var currency: String?
    var bank: String?
    var balance: Double?
    var balanceBase: Double?
    var asOf: String?

    var title: String { name ?? "Account \(id)" }
}

struct Upcoming: Codable {
    var days: Int?
    var starting: Double?
    var ending: Double?
    var totalIn: Double?
    var totalOut: Double?
    var lowest: Event?
    var belowZero: Event?
}

struct Event: Codable, Hashable {
    var date: String?
    var kind: String?
    var name: String?
    var amount: Double?
    var account: String?
    var running: Double?
    var estimate: Bool?
    var overdue: Bool?
}

struct Waiting: Codable {
    var uncategorised: Int?
    var unassignedSpending: Int?
}

struct SyncHealth: Codable {
    var links: Int?
    var red: Int?
    var yellow: Int?
    var lastSyncAt: String?
}

struct TransactionPage: Codable {
    var matched: Int?
    var returned: Int?
    var transactions: [Txn]?
}

struct Txn: Codable, Identifiable, Hashable {
    var id: Int
    var accountId: Int?
    var account: String?
    var txnDate: String?
    var description: String?
    var counterparty: String?
    var amount: Double?
    var currency: String?
    var kind: String?
    var category: String?
    var isin: String?
    var securityName: String?
    var quantity: Double?
    var price: Double?
}

// MARK: Cash flow (dashboard ≥ 0.73.0)
//
// What came in and what went out, per calendar month. The dashboard does
// the arithmetic — every currency at the rate of its month, transfers
// between one's own accounts left out — so the app only draws it.

struct MonthFlow: Codable, Hashable {
    var month: String?
    var income: Double?
    var spending: Double?
    var investment: Double?
    var net: Double?
}

struct CategoryFlow: Codable, Hashable {
    var category: String?
    var label: String?
    var colour: String?
    var total: Double?
    var perMonth: Double?

    var title: String {
        if let label, !label.isEmpty { return label }
        return category ?? "—"
    }
}

struct Unconverted: Codable, Hashable {
    var currency: String?
    var amount: Double?
}

struct Cashflow: Codable {
    var months: [MonthFlow]?
    var byCategory: [CategoryFlow]?
    var incomeByCategory: [CategoryFlow]?
    var totalIncome: Double?
    var totalSpending: Double?
    var totalInvestment: Double?
    var averageSpending: Double?
    var averageIncome: Double?
    var monthsCovered: Int?
    var baseCurrency: String?
    var unconverted: [Unconverted]?

    /// What is left over in an average month — the figure the page is
    /// really about.
    var averageNet: Double { (averageIncome ?? 0) - (averageSpending ?? 0) }

    /// What goes into investments in an average month.
    var averageInvestment: Double { (totalInvestment ?? 0) / Double(max(monthsCovered ?? 0, 1)) }
}

/// The cheap refresh's answer: quotes and rates, no bank touched.
/// The phone only needs to know that it worked — the figures come from
/// the snapshot read straight after — so every field here reads loosely:
/// one of an unexpected shape is left out rather than failing the reply.
struct Market: Decodable {
    struct Prices: Decodable {
        /// A security the quote server could not price, and why.
        struct Miss: Decodable { var isin: String?; var error: String? }
        var priced: Int?; var held: Int?
        /// A list of misses, not a count — the shape that made the
        /// first version of this button fail.
        var failed: [Miss]?
        init(from decoder: Decoder) throws {
            let c = try decoder.container(keyedBy: CodingKeys.self)
            priced = c.loose(Int.self, .priced)
            held = c.loose(Int.self, .held)
            failed = c.loose([Miss].self, .failed)
        }
        private enum CodingKeys: String, CodingKey { case priced, held, failed }
    }
    struct Rates: Decodable {
        var latest: String?; var currencies: Int?; var error: String?
        init(from decoder: Decoder) throws {
            let c = try decoder.container(keyedBy: CodingKeys.self)
            latest = c.loose(String.self, .latest)
            currencies = c.loose(Int.self, .currencies)
            error = c.loose(String.self, .error)
        }
        private enum CodingKeys: String, CodingKey { case latest, currencies, error }
    }
    var prices: Prices?
    var rates: Rates?
    var netWorth: NetWorth?

    init(from decoder: Decoder) throws {
        // Not even an object is still a market refresh that went through.
        guard let c = try? decoder.container(keyedBy: CodingKeys.self) else { return }
        prices = c.loose(Prices.self, .prices)
        rates = c.loose(Rates.self, .rates)
        netWorth = c.loose(NetWorth.self, .netWorth)
    }
    private enum CodingKeys: String, CodingKey { case prices, rates, netWorth }
}

extension KeyedDecodingContainer {
    /// The field if it is there and of the expected shape, nil otherwise.
    func loose<T: Decodable>(_ type: T.Type, _ key: Key) -> T? {
        (try? decodeIfPresent(type, forKey: key)) ?? nil
    }
}

// MARK: What counts towards the figure at the top (contract rule 8)

extension Snapshot {
    /// The name the debt goes by among the classes — and so in the set
    /// of unticked ones.
    static let debtClass = "Debt"

    /// What can be ticked: the classes, and the debt as a line of its own.
    ///
    /// `by_class` carries what is owned and nothing that is owed — the
    /// dashboard draws it as a ring, and a ring has no negative slice.
    /// The net worth it reports does subtract the debt, so a sum of the
    /// classes alone is too high by every mortgage in the house. The
    /// debt goes back in here, negative, and can be unticked like the rest.
    var classes: [ClassValue] {
        let owned = netWorth?.byClass ?? []
        guard let debt = netWorth?.debt, debt > 0 else { return owned }
        return owned + [ClassValue(name: Snapshot.debtClass, value: -debt)]
    }

    /// The net worth less the unticked classes. With nothing unticked it
    /// is the dashboard's own figure, not a sum of the classes: the two
    /// can differ by a rounding, and the number on the phone must match
    /// the number in the browser.
    func figure(excluding excluded: Set<String>) -> Double? {
        let classes = self.classes
        guard classes.contains(where: { excluded.contains($0.name ?? "") }) else { return netWorth?.total }
        return classes.filter { !excluded.contains($0.name ?? "") }.reduce(0) { $0 + ($1.value ?? 0) }
    }
}

extension Market {
    /// What the provider would not quote, and why — nil when everything
    /// came. A refused quote is not a failed refresh: the other prices
    /// arrived and the net worth with them, so this is said beside the
    /// figures rather than instead of them.
    var note: String? {
        var parts: [String] = []
        let misses = prices?.failed ?? []
        if !misses.isEmpty {
            var head = misses.prefix(2)
                .map { m in (m.isin ?? "?") + (m.error.map { ": \($0)" } ?? "") }
                .joined(separator: "; ")
            if misses.count > 2 { head += " " + String(localized: "and \(misses.count - 2) more") }
            parts.append(String(localized: "Not quoted: \(head)"))
        }
        if let error = rates?.error, !error.isEmpty {
            parts.append(String(localized: "Exchange rates: \(error)"))
        }
        return parts.isEmpty ? nil : parts.joined(separator: " · ")
    }
}

/// "0.72.7" against a floor such as "0.73.0", number by number.
enum DashboardVersion {
    static func isAtLeast(_ version: String, _ floor: String) -> Bool {
        func parts(_ s: String) -> [Int] {
            s.split(whereSeparator: { !$0.isNumber }).compactMap { Int($0) }
        }
        let a = parts(version), b = parts(floor)
        for i in 0..<max(a.count, b.count) {
            let x = i < a.count ? a[i] : 0, y = i < b.count ? b[i] : 0
            if x != y { return x > y }
        }
        return true
    }
}

// MARK: The portfolio
//
// Four calls make the page: what is held, where the money sits, how it
// has done, and the line of the net worth over time.

/// One security, as the trades add up to it.
struct Holding: Codable, Identifiable, Hashable {
    var isin: String?
    var name: String?
    var symbol: String?
    var currency: String?
    var quantity: Double?
    var netInvested: Double?
    var price: Double?
    var priceAsOf: String?
    /// "market" for a quote, "trade" when the last trade's price stands in.
    var priceKind: String?
    var lastTrade: String?
    var value: Double?
    var valueBase: Double?
    var accounts: [String]?
    var incompleteHistory: Bool?

    var id: String { isin ?? name ?? "" }
    var title: String { name ?? isin ?? "—" }
    /// What it is worth now less what went in — the money answer, next
    /// to the percentage one the return gives.
    var gain: Double { (valueBase ?? 0) - (netInvested ?? 0) }
}

struct Holdings: Codable {
    var baseCurrency: String?
    var pricesAsOf: String?
    var holdings: [Holding]?
}

struct HistoryPoint: Codable, Hashable {
    var date: String?
    var netWorth: Double?
    /// What the depots hold that day, without cash or anything else.
    /// Nil on days rebuilt from another app's totals, which never split
    /// the figure.
    var securities: Double?
}

struct History: Codable {
    var baseCurrency: String?
    var period: String?
    var firstDate: String?
    var points: [HistoryPoint]?

    /// The net worth, without the days nothing was recorded for.
    var line: [Double] { (points ?? []).compactMap(\.netWorth) }
    /// The day of each value in `line`, in the same order.
    var lineDates: [String] { (points ?? []).filter { $0.netWorth != nil }.map { $0.date ?? "" } }
    /// The securities alone, for the portfolio's line.
    var securities: [Double] { (points ?? []).compactMap(\.securities) }
    /// The day of each value in `securities`, in the same order.
    var securitiesDates: [String] { (points ?? []).filter { $0.securities != nil }.map { $0.date ?? "" } }
}

struct AllocationRow: Codable, Hashable {
    var key: String?
    var value: Double?
    /// A percentage, 0 to 100 — not a fraction.
    var share: Double?
    var target: Double?
    var drift: Double?
}

struct Dimension: Codable {
    var hasTargets: Bool?
    var rows: [AllocationRow]?
}

struct Allocation: Codable {
    var total: Double?
    var cash: Double?
    var dimensions: [String: Dimension]?

    /// By asset class. The decoder turns dictionary keys to camelCase
    /// too, so the dashboard's `asset_class` arrives as `assetClass`.
    var byClass: [AllocationRow] {
        (dimensions?["assetClass"] ?? dimensions?["asset_class"])?.rows ?? []
    }
}

/// The return tool: the whole portfolio over three windows, and one
/// entry per holding under its ISIN.
struct Returns: Codable {
    var all: Period?
    var ytd: Period?
    var year: Period?
    var holdings: [String: Period]?

    private enum CodingKeys: String, CodingKey {
        case all, ytd, year = "1y", holdings
    }
}

// MARK: The triage
//
// Two queues, one shape: a row waiting for a category, and a row waiting
// for a person.

struct Queue: Codable {
    var remaining: Int?
    var transactions: [QueueRow]?
}

struct QueueRow: Codable, Identifiable, Hashable {
    var id: Int
    var accountId: Int?
    var accountName: String?
    var txnDate: String?
    var description: String?
    var counterparty: String?
    var amount: Double?
    var currency: String?
    var kind: String?
    /// What the dashboard would file it under, if it had to guess.
    var suggestion: String?
    /// The words a rule would remember it by — shown, because a rule
    /// made from the wrong words is the mistake that repeats itself.
    var pattern: String?
    /// In the "whose" queue: the category it already has.
    var category: String?
    var label: String?

    var headline: String {
        let text = (description ?? "").trimmingCharacters(in: .whitespaces)
        if !text.isEmpty { return String(text.prefix(90)) }
        return counterparty ?? "—"
    }
}

/// A category as the dashboard knows it, for the sheet of choices.
struct Category: Codable, Identifiable, Hashable {
    var slug: String
    var label: String?
    var group: String?
    var colour: String?
    /// How many rows carry it — the sheet puts the used ones first.
    var transactions: Int?

    var id: String { slug }
    var title: String { label ?? slug }
}

/// One of the household, for "whose spending is this".
struct Person: Codable, Identifiable, Hashable {
    var id: Int
    var name: String?
}

struct PeopleList: Codable {
    var people: [Person]?
}

/// A decision taken on the phone, kept until the dashboard has it
/// (contract rule 2). `owner` is a person's id as text, or "shared".
struct Verdict: Codable, Equatable {
    var txnId: Int
    var category: String?
    var pattern: String?
    var remember: Bool = true
    var owner: String?
    var at: Date = Date()
}

// MARK: A statement into an account

/// What an import answered: the account it went into, and what the
/// dashboard's readers made of the file.
struct ImportReply: Decodable {
    struct Target: Decodable { var id: Int?; var name: String? }
    struct Report: Decodable {
        var label: String?
        var inserted: Int?
        var duplicates: Int?
        var skipped: Int?
        var parsed: Int?
        /// The dashboard's own sentences — a scan, rows kept out. Read
        /// loosely: they are shown, never relied on.
        var problems: [String]?
        var notes: [String]?
        /// The import ids this upload created, one per file — what
        /// `undo_import` takes to put the file back.
        var imports: [Int]?

        init(from decoder: Decoder) throws {
            let c = try decoder.container(keyedBy: CodingKeys.self)
            label = c.loose(String.self, .label)
            inserted = c.loose(Int.self, .inserted)
            duplicates = c.loose(Int.self, .duplicates)
            skipped = c.loose(Int.self, .skipped)
            parsed = c.loose(Int.self, .parsed)
            problems = c.loose([String].self, .problems)
            notes = c.loose([String].self, .notes)
            imports = c.loose([Int].self, .imports)
        }
        private enum CodingKeys: String, CodingKey {
            case label, inserted, duplicates, skipped, parsed, problems, notes, imports
        }
    }
    var ok: Bool?
    var account: Target?
    var result: Report?
    var error: String?
}

/// What `undo_import` answered: how many rows went (dashboard ≥ 0.74.0).
struct UndoneImport: Decodable {
    var accountId: Int?
    var importId: Int?
    var removed: Int?
}
