package fr.smarthomeworld.wealth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.smarthomeworld.wealth.data.Api
import fr.smarthomeworld.wealth.data.Repo
import fr.smarthomeworld.wealth.data.Snapshot
import fr.smarthomeworld.wealth.data.Store
import fr.smarthomeworld.wealth.data.Allocation
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
 *  and the line of the net worth over the chosen window. */
data class PortfolioState(
    val loading: Boolean = false,
    val period: String = "1y",
    val holdings: List<Holding> = emptyList(),
    val allocation: Allocation = Allocation(),
    val returns: Returns = Returns(),
    val history: History = History(),
    val baseCurrency: String = "EUR",
    val total: Double? = null,
    val error: String? = null,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val store = Store(app)
    private val repo = Repo(store)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _txns = MutableStateFlow<TransactionPage?>(null)
    val txns: StateFlow<TransactionPage?> = _txns.asStateFlow()

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
        viewModelScope.launch {
            runCatching { repo.refresh() }
                .onSuccess {
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
                        error = (e as? Api.Failure)?.message ?: e.message ?: "The dashboard could not be reached.")
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
                        error = (e as? Api.Failure)?.message ?: e.message ?: "Pairing failed.")
                }
        }
    }

    fun forget() {
        store.forget()
        _txns.value = null
        _state.value = UiState()
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
                        total = _state.value.snapshot?.netWorth?.total)
                }
                .onFailure { e ->
                    _portfolio.value = _portfolio.value.copy(
                        loading = false,
                        error = (e as? Api.Failure)?.message ?: e.message
                            ?: "Das Dashboard war nicht erreichbar.")
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
                        loading = false, error = e.message ?: "Der Verlauf kam nicht an.")
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
                    error = (e as? Api.Failure)?.message ?: e.message ?: "Could not fetch the queue.")
            }
        }
    }

    /** A verdict: written down at once, sent when the network allows.
     *  The card moves on either way — that is the whole point. */
    fun decide(row: Waiting1, category: String?, owner: String?) {
        _triage.value = _triage.value.copy(waiting = _triage.value.waiting + 1, done = _triage.value.done + 1)
        viewModelScope.launch {
            val left = runCatching {
                repo.decide(Verdict(txnId = row.id, category = category,
                                    pattern = row.pattern, remember = true, owner = owner))
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
                    "That one is already with the dashboard — change it there." else null)
        }
    }

    fun loadTransactions(accountId: Int? = null, query: String? = null) {
        _txns.value = null
        viewModelScope.launch {
            runCatching { repo.transactions(accountId, query) }
                .onSuccess { _txns.value = it }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        error = (e as? Api.Failure)?.message ?: e.message ?: "Could not load transactions.")
                }
        }
    }
}
