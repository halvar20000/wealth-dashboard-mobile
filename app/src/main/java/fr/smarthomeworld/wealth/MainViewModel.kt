package fr.smarthomeworld.wealth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.smarthomeworld.wealth.data.Api
import fr.smarthomeworld.wealth.data.Repo
import fr.smarthomeworld.wealth.data.Snapshot
import fr.smarthomeworld.wealth.data.Store
import fr.smarthomeworld.wealth.data.Category
import fr.smarthomeworld.wealth.data.Person
import fr.smarthomeworld.wealth.data.TransactionPage
import fr.smarthomeworld.wealth.data.Verdict
import fr.smarthomeworld.wealth.data.Waiting1
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

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val store = Store(app)
    private val repo = Repo(store)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _txns = MutableStateFlow<TransactionPage?>(null)
    val txns: StateFlow<TransactionPage?> = _txns.asStateFlow()

    private val _triage = MutableStateFlow(TriageState())
    val triage: StateFlow<TriageState> = _triage.asStateFlow()

    val lockEnabled: Boolean get() = store.lockEnabled

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
        if (store.paired) refresh()
    }

    fun unlock() {
        _state.value = _state.value.copy(locked = false)
        if (_state.value.snapshot == null) refresh()
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
