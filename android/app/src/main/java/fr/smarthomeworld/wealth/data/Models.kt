package fr.smarthomeworld.wealth.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * What the dashboard answers with. Every field is nullable or has a
 * default on purpose: this app and the server it talks to are updated
 * separately, and a field added or dropped on one side must not make
 * the other refuse to start. The JSON reader ignores what it does not
 * know — see [Api].
 */

@Serializable
data class Paired(
    val ok: Boolean = false,
    val token: String? = null,
    val server: ServerInfo? = null,
    @SerialName("base_currency") val baseCurrency: String? = null,
    @SerialName("mcp_url") val mcpUrl: String? = null,
    val error: String? = null,
)

@Serializable
data class ServerInfo(val name: String? = null, val version: String? = null)

@Serializable
data class ToolReply<T>(val ok: Boolean = false, val result: T? = null, val error: String? = null)

@Serializable
data class Snapshot(
    val at: String? = null,
    @SerialName("base_currency") val baseCurrency: String = "EUR",
    @SerialName("net_worth") val netWorth: NetWorth = NetWorth(),
    val performance: Map<String, Period> = emptyMap(),
    val accounts: List<Account> = emptyList(),
    val upcoming: Upcoming? = null,
    val events: List<Event> = emptyList(),
    val waiting: Waiting = Waiting(),
    val sync: SyncHealth = SyncHealth(),
)

@Serializable
data class NetWorth(
    @SerialName("net_worth") val total: Double? = null,
    val cash: Double? = null,
    val securities: Double? = null,
    val assets: Double? = null,
    val debt: Double? = null,
    @SerialName("fx_as_of") val fxAsOf: String? = null,
    @SerialName("prices_as_of") val pricesAsOf: String? = null,
    @SerialName("by_class") val byClass: List<ClassValue> = emptyList(),
)

@Serializable
data class ClassValue(val name: String = "", val value: Double = 0.0)

/** One window of the return: the percentage, and the gain in money. */
@Serializable
data class Period(
    /** Set only where the figure belongs to one security. */
    val name: String? = null,
    val since: String? = null,
    val twr: Double? = null,
    @SerialName("twr_annual") val twrAnnual: Double? = null,
    val mwr: Double? = null,
    val pnl: Double? = null,
    val days: Int = 0,
)

@Serializable
data class Account(
    val id: Int = 0,
    val name: String = "",
    val type: String? = null,
    val currency: String? = null,
    val bank: String? = null,
    val balance: Double? = null,
    @SerialName("balance_base") val balanceBase: Double? = null,
    @SerialName("as_of") val asOf: String? = null,
)

@Serializable
data class Upcoming(
    val days: Int = 30,
    val starting: Double? = null,
    val ending: Double? = null,
    @SerialName("total_in") val totalIn: Double? = null,
    @SerialName("total_out") val totalOut: Double? = null,
    val lowest: Event? = null,
    @SerialName("below_zero") val belowZero: Event? = null,
)

@Serializable
data class Event(
    val date: String = "",
    val kind: String = "",
    val name: String = "",
    val amount: Double = 0.0,
    val account: String? = null,
    val running: Double? = null,
    val estimate: Boolean = false,
    val overdue: Boolean = false,
)

@Serializable
data class Waiting(
    val uncategorised: Int = 0,
    @SerialName("unassigned_spending") val unassignedSpending: Int = 0,
)

@Serializable
data class SyncHealth(
    val links: Int = 0,
    val red: Int = 0,
    val yellow: Int = 0,
    @SerialName("last_sync_at") val lastSyncAt: String? = null,
)

@Serializable
data class TransactionPage(
    val matched: Int = 0,
    val returned: Int = 0,
    val transactions: List<Txn> = emptyList(),
)

@Serializable
data class Txn(
    val id: Int = 0,
    @SerialName("account_id") val accountId: Int = 0,
    val account: String? = null,
    @SerialName("txn_date") val date: String = "",
    val description: String? = null,
    val counterparty: String? = null,
    val amount: Double = 0.0,
    val currency: String? = null,
    val kind: String? = null,
    val category: String? = null,
    val isin: String? = null,
    @SerialName("security_name") val securityName: String? = null,
    val quantity: Double? = null,
    val price: Double? = null,
)

/** What an import answered: the reader that read it, and what it did. */
@Serializable
data class ImportReply(
    val ok: Boolean = false,
    val account: ImportAccount? = null,
    val result: ImportResult? = null,
    val error: String? = null,
    val files: List<String> = emptyList(),
)

@Serializable
data class ImportAccount(val id: Int = 0, val name: String = "")

@Serializable
data class ImportResult(
    val label: String? = null,
    val inserted: Int = 0,
    val duplicates: Int = 0,
    val skipped: Int = 0,
    val parsed: Int = 0,
    val problems: List<String> = emptyList(),
    val notes: List<String> = emptyList(),
    val unrecognised: List<String> = emptyList(),
)

/** The queue: a row with no category, and the app's own first guess. */
@Serializable
data class Queue(
    val remaining: Int = 0,
    val transactions: List<Waiting1> = emptyList(),
)

@Serializable
data class Waiting1(
    val id: Int = 0,
    @SerialName("account_id") val accountId: Int = 0,
    @SerialName("account_name") val accountName: String? = null,
    @SerialName("txn_date") val date: String = "",
    val description: String? = null,
    val counterparty: String? = null,
    val amount: Double = 0.0,
    val currency: String? = null,
    val kind: String? = null,
    /** What the app would file it under, if it had to guess. */
    val suggestion: String? = null,
    /** The words a rule would remember it by — shown, because a rule
     *  made from the wrong words is the mistake that repeats itself. */
    val pattern: String? = null,
)

/** A category as the dashboard knows it, for the sheet of choices. */
@Serializable
data class Category(
    val slug: String = "",
    val label: String = "",
    val group: String? = null,
    val colour: String? = null,
    /** How many rows carry it — the sheet puts the used ones first. */
    val transactions: Int = 0,
) {
    val spending: Boolean get() = group == "spending"
}

/** One of the household, for "whose spending is this". */
@Serializable
data class Person(val id: Int = 0, val name: String = "")

/** A queue and how much of it is left, for both triages. */
@Serializable
data class Unowned(
    val remaining: Int = 0,
    val transactions: List<Waiting1> = emptyList(),
)

/** A decision taken on the phone, kept until the dashboard has it.
 *  `owner` is a person's id as text, or "shared", or null. */
@Serializable
data class Verdict(
    val txnId: Int,
    val category: String? = null,
    val pattern: String? = null,
    val remember: Boolean = true,
    val owner: String? = null,
    val at: Long = 0L,
)

@Serializable
data class PeopleList(val people: List<Person> = emptyList())


// ─── The portfolio ───────────────────────────────────────────────
//
// Four calls make the page: what is held, where the money sits, how it
// has done, and the line of the net worth over time.

/** One security, as the trades add up to it. */
@Serializable
data class Holding(
    val isin: String = "",
    val name: String = "",
    val symbol: String? = null,
    val currency: String? = null,
    val quantity: Double = 0.0,
    @SerialName("net_invested") val invested: Double = 0.0,
    val price: Double? = null,
    @SerialName("price_as_of") val priceAsOf: String? = null,
    @SerialName("price_kind") val priceKind: String? = null,
    @SerialName("last_trade") val lastTrade: String? = null,
    val value: Double = 0.0,
    @SerialName("value_base") val valueBase: Double = 0.0,
    val accounts: List<String> = emptyList(),
    @SerialName("incomplete_history") val incompleteHistory: Boolean = false,
) {
    /** What it is worth now less what went in — the money answer, next
     *  to the percentage one the return gives. */
    val gain: Double get() = valueBase - invested
}

@Serializable
data class Holdings(
    @SerialName("base_currency") val baseCurrency: String = "EUR",
    @SerialName("prices_as_of") val pricesAsOf: String? = null,
    val holdings: List<Holding> = emptyList(),
)

@Serializable
data class HistoryPoint(
    val date: String = "",
    @SerialName("net_worth") val netWorth: Double? = null,
)

@Serializable
data class History(
    @SerialName("base_currency") val baseCurrency: String = "EUR",
    val period: String = "",
    @SerialName("first_date") val firstDate: String? = null,
    val points: List<HistoryPoint> = emptyList(),
)

@Serializable
data class AllocationRow(
    val key: String = "",
    val value: Double = 0.0,
    val share: Double = 0.0,
    val target: Double? = null,
    val drift: Double? = null,
)

@Serializable
data class Dimension(
    @SerialName("has_targets") val hasTargets: Boolean = false,
    val rows: List<AllocationRow> = emptyList(),
)

@Serializable
data class Allocation(
    val total: Double = 0.0,
    val cash: Double = 0.0,
    val dimensions: Map<String, Dimension> = emptyMap(),
)

/** The return tool: the whole portfolio over three windows, and one
 *  entry per holding under its ISIN. */
@Serializable
data class Returns(
    val all: Period? = null,
    val ytd: Period? = null,
    @SerialName("1y") val year: Period? = null,
    val holdings: Map<String, Period> = emptyMap(),
)

// ─── Cash flow ───────────────────────────────────────────────────
//
// What came in and what went out, per calendar month. The dashboard
// does the arithmetic — every currency at the rate of its month — so
// the app only draws it.

@Serializable
data class MonthFlow(
    val month: String = "",
    val income: Double = 0.0,
    val spending: Double = 0.0,
    val investment: Double = 0.0,
    val net: Double = 0.0,
    val categories: Map<String, Double> = emptyMap(),
)

@Serializable
data class CategoryFlow(
    val category: String = "",
    val label: String = "",
    val colour: String? = null,
    val total: Double = 0.0,
    @SerialName("per_month") val perMonth: Double = 0.0,
)

@Serializable
data class Cashflow(
    val months: List<MonthFlow> = emptyList(),
    @SerialName("by_category") val byCategory: List<CategoryFlow> = emptyList(),
    @SerialName("income_by_category") val incomeByCategory: List<CategoryFlow> = emptyList(),
    @SerialName("total_income") val totalIncome: Double = 0.0,
    @SerialName("total_spending") val totalSpending: Double = 0.0,
    @SerialName("total_investment") val totalInvestment: Double = 0.0,
    @SerialName("average_spending") val averageSpending: Double = 0.0,
    @SerialName("average_income") val averageIncome: Double = 0.0,
    @SerialName("months_covered") val monthsCovered: Int = 0,
    @SerialName("base_currency") val baseCurrency: String = "EUR",
    val unconverted: List<Unconverted> = emptyList(),
) {
    /** What is left over in an average month — the figure the page is
     *  really about. */
    val averageNet: Double get() = averageIncome - averageSpending
}

@Serializable
data class Unconverted(val currency: String = "", val amount: Double = 0.0)

/** The cheap refresh: quotes and rates, no bank touched. */
@Serializable
data class Market(
    val prices: PriceReport = PriceReport(),
    val rates: RateReport? = null,
    @SerialName("net_worth") val netWorth: NetWorth = NetWorth(),
)

@Serializable
data class PriceReport(
    val priced: Int = 0,
    val failed: Int = 0,
    @SerialName("as_of") val asOf: String? = null,
)

@Serializable
data class RateReport(
    val latest: String? = null,
    val currencies: Int = 0,
    val error: String? = null,
)
