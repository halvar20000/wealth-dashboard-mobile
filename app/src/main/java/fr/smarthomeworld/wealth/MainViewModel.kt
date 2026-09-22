package fr.smarthomeworld.wealth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.smarthomeworld.wealth.data.Api
import fr.smarthomeworld.wealth.data.Repo
import fr.smarthomeworld.wealth.data.Snapshot
import fr.smarthomeworld.wealth.data.Store
import fr.smarthomeworld.wealth.data.TransactionPage
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

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val store = Store(app)
    private val repo = Repo(store)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _txns = MutableStateFlow<TransactionPage?>(null)
    val txns: StateFlow<TransactionPage?> = _txns.asStateFlow()

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
