package fr.smarthomeworld.wealth.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Where the token and the last figures live: an encrypted preference
 * file, keyed by the phone's hardware keystore. The cached snapshot is
 * in there too — it is the household's money, not a picture of a cat,
 * and a plain file is readable by a backup tool.
 */
class Store(context: Context) {

    private val prefs: SharedPreferences = run {
        val key = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context, "wealth", key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    var baseUrl: String?
        get() = prefs.getString("base_url", null)
        set(value) = prefs.edit().putString("base_url", value).apply()

    var token: String?
        get() = prefs.getString("token", null)
        set(value) = prefs.edit().putString("token", value).apply()

    var serverName: String?
        get() = prefs.getString("server_name", null)
        set(value) = prefs.edit().putString("server_name", value).apply()

    /** Ask for the fingerprint when the app comes back to the front. */
    var lockEnabled: Boolean
        get() = prefs.getBoolean("lock", false)
        set(value) = prefs.edit().putBoolean("lock", value).apply()

    val paired: Boolean get() = !baseUrl.isNullOrBlank() && !token.isNullOrBlank()

    fun pair(url: String, reply: Paired) {
        baseUrl = Api.normalise(url)
        token = reply.token
        serverName = reply.server?.name
    }

    fun forget() {
        prefs.edit().clear().apply()
    }

    /** The last answer, so the app opens on figures rather than a spinner. */
    fun cache(raw: String) = prefs.edit()
        .putString("snapshot", raw)
        .putLong("snapshot_at", System.currentTimeMillis())
        .apply()

    fun cached(): Pair<Snapshot, Long>? {
        val raw = prefs.getString("snapshot", null) ?: return null
        val at = prefs.getLong("snapshot_at", 0L)
        return runCatching { Api.json.decodeFromString(Snapshot.serializer(), raw) to at }.getOrNull()
    }

    fun api(): Api = Api(baseUrl.orEmpty(), token)
}
