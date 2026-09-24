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
