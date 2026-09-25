package fr.smarthomeworld.wealth.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One place that knows the order of things: read the cache first so a
 * cold start shows figures, then ask the dashboard, then keep what came
 * back. A refresh that fails leaves the cache alone — yesterday's net
 * worth with the day on it beats an empty screen.
 */
class Repo(private val store: Store) {

    /** `person` is whose figures these are: null for the household. */
    data class Loaded(val snapshot: Snapshot, val at: Long, val stale: Boolean, val person: Int? = null)

    fun cached(): Loaded? = store.cached()?.let { (snap, at) -> Loaded(snap, at, true, store.person) }

    suspend fun refresh(days: Int = 30): Loaded = withContext(Dispatchers.IO) {
        // Whose figures are asked for is fixed now: a switch while this
        // is on its way must not file them under the new name.
        val whose = store.person
        val snapshot = store.api().snapshot(days)
        if (whose == store.person) {
            store.cache(Api.json.encodeToString(Snapshot.serializer(), snapshot), whose)
        }
        Loaded(snapshot, System.currentTimeMillis(), false, whose)
    }

    suspend fun transactions(accountId: Int? = null, query: String? = null, limit: Int = 100) =
        withContext(Dispatchers.IO) { store.api().transactions(accountId, query, limit) }

    /** The household, for the switch at the top. A dashboard older than
     *  0.72.4 has no `people`; it answers 404 and the switch stays away. */
    suspend fun people(): List<Person> = withContext(Dispatchers.IO) {
        try {
            store.api().people()
        } catch (e: Api.Failure) {
            if (e.status == 404) emptyList() else throw e
        }
    }

    suspend fun importFiles(accountId: Int, files: List<Api.Upload>): ImportReply =
        withContext(Dispatchers.IO) { store.api().importFiles(accountId, files) }

    /** The accounts as the last snapshot knew them — what the share
     *  sheet offers before it has asked the dashboard anything. */
    fun accounts(): List<Account> = store.cached()?.first?.accounts.orEmpty()

    // ── The portfolio ────────────────────────────────────────────

    data class Portfolio(
        val holdings: List<Holding> = emptyList(),
        val allocation: Allocation = Allocation(),
        val returns: Returns = Returns(),
        val history: History = History(),
        val baseCurrency: String = "EUR",
        val pricesAsOf: String? = null,
    )

    /** One page, four calls. The chart's period is the only thing the
     *  page changes on its own, so it can be fetched by itself. */
    suspend fun portfolio(period: String = "1y"): Portfolio = withContext(Dispatchers.IO) {
        val api = store.api()
        val held = api.holdings()
        Portfolio(
            holdings = held.holdings.sortedByDescending { it.valueBase },
            allocation = runCatching { api.allocation() }.getOrDefault(Allocation()),
            returns = runCatching { api.returns() }.getOrDefault(Returns()),
            history = runCatching { api.history(period) }.getOrDefault(History()),
            baseCurrency = held.baseCurrency,
            pricesAsOf = held.pricesAsOf,
        )
    }

    suspend fun history(period: String): History = withContext(Dispatchers.IO) {
        store.api().history(period)
    }

    /** Take an upload back. Several files in one share make several
     *  imports; all of them go, newest first. */
    suspend fun undoImport(accountId: Int, importIds: List<Int>): Int = withContext(Dispatchers.IO) {
        val api = store.api()
        importIds.sortedDescending().sumOf { api.undoImport(accountId, it) }
    }

    // ── Cash flow, and the cheap refresh ─────────────────────────

    suspend fun cashflow(months: Int = 13): Cashflow = withContext(Dispatchers.IO) {
        store.api().cashflow(months)
    }

    /** Quotes and rates, then a fresh snapshot kept in the cache — the
     *  figures on every screen move together or not at all. Both
     *  answers come back, because what a provider refused to quote is
     *  worth saying and only the first one knows it. */
    suspend fun refreshMarket(): Pair<Market, Loaded> = withContext(Dispatchers.IO) {
        val market = store.api().refreshMarket()
        market to refresh()
    }

    // ── The triage ───────────────────────────────────────────────
    //
    // Two queues, one shape: a row waiting for a category, and a row
    // waiting for a person. A verdict is written down before it is
    // sent, so the thumb never waits for the network and nothing is
    // lost when there is none.

    data class Triage(
        val rows: List<Waiting1> = emptyList(),
        val remaining: Int = 0,
        val categories: List<Category> = emptyList(),
        val people: List<Person> = emptyList(),
    )

    suspend fun categorisingQueue(limit: Int = 60): Triage = withContext(Dispatchers.IO) {
        val api = store.api()
        val queue = api.uncategorised(limit)
        Triage(queue.transactions, queue.remaining, categories = api.categories())
    }

    suspend fun owningQueue(limit: Int = 60): Triage = withContext(Dispatchers.IO) {
        val api = store.api()
        val queue = api.unowned(limit)
        Triage(queue.transactions, queue.remaining, people = api.people())
    }

    /** A decision: kept, then sent. Returns how many are still waiting. */
    suspend fun decide(verdict: Verdict): Int = withContext(Dispatchers.IO) {
        store.queue(verdict.copy(at = System.currentTimeMillis()))
        flush()
    }

    /** Send what is waiting, oldest first; stop at the first failure
     *  and keep the rest. Returns how many are still waiting. */
    suspend fun flush(): Int = withContext(Dispatchers.IO) {
        var left = store.pending()
        if (left.isEmpty()) return@withContext 0
        val api = store.api()
        while (left.isNotEmpty()) {
            val next = left.first()
            try {
                api.send(next)
            } catch (e: Api.Failure) {
                // The dashboard refused this one — a category that no
                // longer exists, a row somebody deleted. Dropping it is
                // right: retrying forever would block the queue.
                if (e.status !in 400..499) break
            } catch (e: Exception) {
                break
            }
            left = left.drop(1)
            store.savePending(left)
        }
        left.size
    }

    fun waiting(): Int = store.pending().size

    /** Drop the last verdict, if it has not gone out yet. Returns how
     *  many are waiting afterwards. */
    fun undoLast(): Int {
        val left = store.pending()
        if (left.isEmpty()) return 0
        store.savePending(left.dropLast(1))
        return left.size - 1
    }

    suspend fun pair(url: String, code: String): Paired = withContext(Dispatchers.IO) {
        val reply = Api.pair(url, code)
        store.pair(url, reply)
        reply
    }
}
