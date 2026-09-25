package fr.smarthomeworld.wealth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.smarthomeworld.wealth.data.Api
import fr.smarthomeworld.wealth.data.Repo
import fr.smarthomeworld.wealth.data.Snapshot
import fr.smarthomeworld.wealth.data.Store
import fr.smarthomeworld.wealth.data.Allocation
import fr.smarthomeworld.wealth.data.Cashflow
import fr.smarthomeworld.wealth.data.Category
import fr.smarthomeworld.wealth.data.History
import fr.smarthomeworld.wealth.data.Holding
import fr.smarthomeworld.wealth.data.Returns
import fr.smarthomeworld.wealth.data.Person
import fr.smarthomeworld.wealth.data.TransactionPage
import fr.smarthomeworld.wealth.data.Verdict
import fr.smarthomeworld.wealth.data.Waiting1
import fr.smarthomeworld.wealth.widget.refreshWidgets
import fr.smarthomeworld.wealth.work.Watcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the cash-flow page holds. */
data class CashflowState(
    val loading: Boolean = false,
    val months: Int = 13,
    val flow: Cashflow? = null,
    val error: String? = null,
)

data class UiState(
    val paired: Boolean = false,
    val locked: Boolean = false,
    val loading: Boolean = false,
    val snapshot: Snapshot? = null,
    val at: Long = 0L,
    val stale: Boolean = false,
    val error: String? = null,
    val pairing: Boolean = false,
    val serverName: String? = null,
    /** Asset classes the user has unticked — the figure at the top is
     *  the net worth less these. Empty means everything counts. */
    val excluded: Set<String> = emptySet(),
    /** Set while the cheap refresh runs, so the button can say so. */
    val pricing: Boolean = false,
    /** The household as the dashboard knows it; empty hides the switch. */
    val people: List<Person> = emptyList(),
    /** Whose figures are shown: null for everyone. */
    val person: Int? = null,
    val personName: String? = null,
)

data class TriageState(
    val owning: Boolean = false,
    val loading: Boolean = false,
    val rows: List<Waiting1> = emptyList(),
    val remaining: Int = 0,
    val categories: List<Category> = emptyList(),
    val people: List<Person> = emptyList(),
    /** Verdicts taken on this phone that the dashboard has not heard. */
    val waiting: Int = 0,
    val done: Int = 0,
    val error: String? = null,
)

/** The portfolio page: what is held, where it sits, how it has done,
 *  and the line of the securities over the chosen window. */
data class PortfolioState(
    val loading: Boolean = false,
    val period: String = "1y",
    val holdings: List<Holding> = emptyList(),
    val allocation: Allocation = Allocation(),
    val returns: Returns = Returns(),
    val history: History = History(),
    val baseCurrency: String = "EUR",
    /** What the depots hold now, for the figure before the line loads. */
    val securities: Double? = null,
    val error: String? = null,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private fun say(id: Int, vararg args: Any): String =
        getApplication<Application>().getString(id, *args)

    private val store = Store(app)
    private val repo = Repo(store)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _txns = MutableStateFlow<TransactionPage?>(null)
    val txns: StateFlow<TransactionPage?> = _txns.asStateFlow()

    private val _cashflow = MutableStateFlow(CashflowState())
    val cashflow: StateFlow<CashflowState> = _cashflow.asStateFlow()

    private val _portfolio = MutableStateFlow(PortfolioState())
    val portfolio: StateFlow<PortfolioState> = _portfolio.asStateFlow()

    private val _triage = MutableStateFlow(TriageState())
    val triage: StateFlow<TriageState> = _triage.asStateFlow()

    val lockEnabled: Boolean get() = store.lockEnabled
    val watchEnabled: Boolean get() = store.watch

    init {
        val cached = repo.cached()
        _state.value = UiState(
            paired = store.paired,
            locked = store.paired && store.lockEnabled,
            snapshot = cached?.snapshot,
            at = cached?.at ?: 0L,
            stale = cached != null,
            serverName = store.serverName,
            excluded = store.excludedClasses,
            person = store.person,
            personName = store.personName,
        )
        if (store.paired) { refresh(); loadHistory() }
        // A round that was switched on survives a reboot and an update;
        // one that was switched off leaves nothing behind.
        Watcher.schedule(app, store.watch && store.paired)
    }

    fun unlock() {
        _state.value = _state.value.copy(locked = false)
        if (_state.value.snapshot == null) refresh()
    }

    /** The background round, and with it the notices. */
    fun setWatch(on: Boolean) {
        store.watch = on
        if (!on) store.lastNotice = null
        Watcher.schedule(getApplication<Application>(), on && store.paired)
        _state.value = _state.value.copy()
    }

    fun setLock(on: Boolean) {
        store.lockEnabled = on
        _state.value = _state.value.copy()
    }

    fun refresh() {
        if (!store.paired) return
        _state.value = _state.value.copy(loading = true, error = null)
        loadPeople()
        viewModelScope.launch {
            runCatching { repo.refresh() }
                .onSuccess {
                    // Somebody else's by now: the switch moved while this
                    // was on its way, and its own refresh is coming.
                    if (it.person != store.person) return@onSuccess
                    _state.value = _state.value.copy(
                        loading = false, snapshot = it.snapshot, at = it.at, stale = false, error = null)
                    // The home screen draws the same cache; redraw it now,
                    // so the two never disagree about today's figure.
                    refreshWidgets(getApplication<Application>())
                }
                .onFailure { e ->
                    // A refresh that fails keeps what was on screen: the
                    // figures are still true as of when they were fetched.
                    _state.value = _state.value.copy(
                        loading = false, stale = true,
                        error = (e as? Api.Failure)?.message ?: e.message ?: say(R.string.error_unreachable))
                }
        }
    }

    fun pair(url: String, code: String) {
        _state.value = _state.value.copy(pairing = true, error = null)
        viewModelScope.launch {
            runCatching { repo.pair(url, code) }
                .onSuccess {
                    _state.value = _state.value.copy(
                        pairing = false, paired = true, locked = false, error = null,
                        serverName = it.server?.name)
                    refresh()
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        pairing = false,
                        error = (e as? Api.Failure)?.message ?: e.message ?: say(R.string.error_pairing))
                }
        }
    }

    fun forget() {
        store.forget()
        _txns.value = null
        _state.value = UiState()
    }

    // ── Whose figures ────────────────────────────────────────────

    /** Who the dashboard knows. Asked beside every refresh, because a
     *  person added or removed in the browser should show here too. */
    private fun loadPeople() {
        viewModelScope.launch {
            val people = runCatching { repo.people() }.getOrElse { return@launch }
            _state.value = _state.value.copy(people = people)
            // The person this phone was showing is gone from the
            // dashboard: fall back to everyone rather than ask forever
            // for somebody it no longer knows.
            val chosen = store.person
            if (chosen != null && people.none { it.id == chosen }) setPerson(null)
        }
    }

    /** Show one person's accounts, or everyone's (null). Every page
     *  that has been loaded is asked again under the new lens; the old
     *  figures go, since they were somebody else's. */
    fun setPerson(person: Person?) {
        if (person?.id == store.person) return
        store.person = person?.id
        store.personName = person?.name
        _state.value = _state.value.copy(
            person = person?.id, personName = person?.name,
            snapshot = repo.cached()?.snapshot, at = repo.cached()?.at ?: 0L)
        val hadPortfolio = _portfolio.value.holdings.isNotEmpty()
        val hadCashflow = _cashflow.value.flow != null
        val hadTriage = _triage.value.rows.isNotEmpty()
        _portfolio.value = PortfolioState(period = _portfolio.value.period)
        _cashflow.value = CashflowState(months = _cashflow.value.months)
        refresh()
        loadHistory()
        if (hadPortfolio) loadPortfolio()
        if (hadCashflow) loadCashflow()
        if (hadTriage) loadTriage(_triage.value.owning)
        if (_txns.value != null) loadTransactions(lastTxns.first, lastTxns.second)
    }

    // ── Cash flow ────────────────────────────────────────────────

    fun loadCashflow(months: Int = _cashflow.value.months) {
        if (!store.paired) return
        _cashflow.value = _cashflow.value.copy(loading = true, months = months, error = null)
        viewModelScope.launch {
            runCatching { repo.cashflow(months) }
                .onSuccess { _cashflow.value = CashflowState(loading = false, months = months, flow = it) }
                .onFailure { e ->
                    _cashflow.value = _cashflow.value.copy(
                        loading = false,
                        error = (e as? Api.Failure)?.message ?: e.message
                            ?: say(R.string.error_cashflow))
                }
        }
    }

    // ── What counts towards the figure at the top ────────────────

    /** Tick or untick one asset class. Nothing is fetched: the snapshot
     *  already carries every class, so the sum is the phone's to make. */
    fun toggleClass(name: String) {
        val next = _state.value.excluded.toMutableSet()
        if (!next.remove(name)) next.add(name)
        store.excludedClasses = next
        _state.value = _state.value.copy(excluded = next)
    }

    /** Quotes and rates, no bank touched — the refresh somebody presses
     *  to see whether the markets moved. */
    fun refreshMarket() {
        if (!store.paired) return
        _state.value = _state.value.copy(pricing = true, error = null)
        viewModelScope.launch {
            runCatching { repo.refreshMarket() }
                .onSuccess { (market, loaded) ->
                    // A provider refusing one quote is not a failed
                    // refresh: the other prices arrived. Say which ones
                    // are stale and why, and keep the figures that came.
                    val misses = market.prices.failed
                    val head = misses.take(2).joinToString("; ") {
                        it.isin + (it.error?.let { e -> ": $e" } ?: "")
                    }
                    val note = listOfNotNull(
                        head.ifBlank { null }?.let {
                            if (misses.size > 2) say(R.string.quotes_missed_more, it, misses.size - 2) else it
                        },
                        market.rates?.error?.let { say(R.string.quotes_fx_error, it) },
                    ).joinToString(" · ").ifBlank { null }
                    _state.value = _state.value.copy(
                        pricing = false, snapshot = loaded.snapshot, at = loaded.at,
                        stale = false, error = note)
                    refreshWidgets(getApplication<Application>())
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        pricing = false,
                        error = (e as? Api.Failure)?.message ?: e.message
                            ?: say(R.string.error_quotes))
                }
        }
    }

    // ── The portfolio ────────────────────────────────────────────

    /** The whole page. Called when the tab is opened, and again by the
     *  refresh button — not on every visit, because four calls to a
     *  Raspberry Pi is a thing to ask for, not to assume. */
    fun loadPortfolio(period: String = _portfolio.value.period) {
        if (!store.paired) return
        _portfolio.value = _portfolio.value.copy(loading = true, error = null, period = period)
        viewModelScope.launch {
            runCatching { repo.portfolio(period) }
                .onSuccess { p ->
                    _portfolio.value = PortfolioState(
                        loading = false, period = period, holdings = p.holdings,
                        allocation = p.allocation, returns = p.returns, history = p.history,
                        baseCurrency = p.baseCurrency,
                        securities = _state.value.snapshot?.netWorth?.securities)
                }
                .onFailure { e ->
                    _portfolio.value = _portfolio.value.copy(
                        loading = false,
                        error = (e as? Api.Failure)?.message ?: e.message
                            ?: say(R.string.error_unreachable))
                }
        }
    }

    /** Only the chart's window changed — one call, not four. The same
     *  call fills the line under the overview's figure at start-up. */
    fun loadHistory(period: String = _portfolio.value.period) {
        if (!store.paired) return
        _portfolio.value = _portfolio.value.copy(loading = true, period = period, error = null)
        viewModelScope.launch {
            runCatching { repo.history(period) }
                .onSuccess { _portfolio.value = _portfolio.value.copy(loading = false, history = it) }
                .onFailure { e ->
                    _portfolio.value = _portfolio.value.copy(
                        loading = false, error = e.message ?: say(R.string.error_history))
                }
        }
    }

    fun setPeriod(period: String) = loadHistory(period)

    // ── The triage ───────────────────────────────────────────────

    /** Load one of the two queues. The decisions already taken and not
     *  yet sent go out first, so the queue does not hand back a row
     *  that was dealt with on the train. */
    fun loadTriage(owning: Boolean) {
        _triage.value = _triage.value.copy(loading = true, error = null, owning = owning)
        viewModelScope.launch {
            runCatching {
                repo.flush()
                if (owning) repo.owningQueue() else repo.categorisingQueue()
            }.onSuccess { t ->
                _triage.value = TriageState(
                    owning = owning, rows = t.rows, remaining = t.remaining,
                    categories = t.categories, people = t.people, waiting = repo.waiting())
            }.onFailure { e ->
                _triage.value = _triage.value.copy(
                    loading = false, waiting = repo.waiting(),
                    error = (e as? Api.Failure)?.message ?: e.message ?: say(R.string.error_queue))
            }
        }
    }

    /** A verdict: written down at once, sent when the network allows.
     *  The card moves on either way — that is the whole point. */
    fun decide(row: Waiting1, category: String?, owner: String?,
               pattern: String? = row.pattern, remember: Boolean = true) {
        _triage.value = _triage.value.copy(waiting = _triage.value.waiting + 1, done = _triage.value.done + 1)
        viewModelScope.launch {
            val left = runCatching {
                repo.decide(Verdict(txnId = row.id, category = category,
                                    pattern = pattern, remember = remember, owner = owner))
            }.getOrElse { repo.waiting() }
            _triage.value = _triage.value.copy(waiting = left)
        }
    }

    /** Take the last verdict back — while it is still on the phone.
     *  Once the dashboard has it, the place to change it is the
     *  dashboard, and saying so is better than pretending. */
    fun undoLast() {
        viewModelScope.launch {
            val left = repo.undoLast()
            _triage.value = _triage.value.copy(
                waiting = left,
                error = if (left == _triage.value.waiting)
                    say(R.string.error_undo_sent) else null)
        }
    }

    /** What the rows tab last asked for, to ask again after a switch. */
    private var lastTxns: Pair<Int?, String?> = null to null

    fun loadTransactions(accountId: Int? = null, query: String? = null) {
        lastTxns = accountId to query
        _txns.value = null
        viewModelScope.launch {
            runCatching { repo.transactions(accountId, query) }
                .onSuccess { _txns.value = it }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        error = (e as? Api.Failure)?.message ?: e.message ?: say(R.string.error_transactions))
                }
        }
    }
}
