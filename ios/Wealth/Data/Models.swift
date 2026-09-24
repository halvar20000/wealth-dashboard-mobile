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
struct Market: Codable {
    struct Prices: Codable { var priced: Int?; var failed: Int?; var asOf: String? }
    struct Rates: Codable { var latest: String?; var currencies: Int?; var error: String? }
    var prices: Prices?
    var rates: Rates?
    var netWorth: NetWorth?
}

// MARK: What counts towards the figure at the top (contract rule 8)

extension Snapshot {
    /// The net worth less the unticked classes. With nothing unticked it
    /// is the dashboard's own figure, not a sum of the classes: the two
    /// can differ by a rounding, and the number on the phone must match
    /// the number in the browser.
    func figure(excluding excluded: Set<String>) -> Double? {
        let classes = netWorth?.byClass ?? []
        guard classes.contains(where: { excluded.contains($0.name ?? "") }) else { return netWorth?.total }
        return classes.filter { !excluded.contains($0.name ?? "") }.reduce(0) { $0 + ($1.value ?? 0) }
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
