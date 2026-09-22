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

    data class Loaded(val snapshot: Snapshot, val at: Long, val stale: Boolean)

    fun cached(): Loaded? = store.cached()?.let { (snap, at) -> Loaded(snap, at, true) }

    suspend fun refresh(days: Int = 30): Loaded = withContext(Dispatchers.IO) {
        val api = store.api()
        val snapshot = api.snapshot(days)
        store.cache(Api.json.encodeToString(Snapshot.serializer(), snapshot))
        Loaded(snapshot, System.currentTimeMillis(), false)
    }

    suspend fun transactions(accountId: Int? = null, query: String? = null, limit: Int = 100) =
        withContext(Dispatchers.IO) { store.api().transactions(accountId, query, limit) }

    suspend fun pair(url: String, code: String): Paired = withContext(Dispatchers.IO) {
        val reply = Api.pair(url, code)
        store.pair(url, reply)
        reply
    }
}
