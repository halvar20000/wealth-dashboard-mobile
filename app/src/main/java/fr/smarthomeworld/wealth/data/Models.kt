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

