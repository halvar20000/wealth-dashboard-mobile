package fr.smarthomeworld.wealth.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.serialization.builtins.ListSerializer

/**
 * Where the token and the last figures live: an encrypted preference
 * file, keyed by the phone's hardware keystore. The cached snapshot is
 * in there too — it is the household's money, not a picture of a cat,
 * and a plain file is readable by a backup tool.
 */
class Store(context: Context) {

    /** For the words the requests say when the dashboard says none. */
    val context: Context = context.applicationContext

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

    /**
     * Whose figures the app shows: null for everyone, else a person's id
     * as `people` gives it — the same lens as the switch at the top of
     * the dashboard. The name is kept beside it so the switch can say
     * whose picture this is before the dashboard has answered.
     */
    var person: Int?
        get() = prefs.getInt("person", -1).takeIf { it >= 0 }
        set(value) = prefs.edit().putInt("person", value ?: -1).apply()

    var personName: String?
        get() = prefs.getString("person_name", null)
        set(value) = prefs.edit().putString("person_name", value).apply()

    /** The last answer, so the app opens on figures rather than a spinner.
     *  It is kept with whose it was: one person's net worth shown under
     *  the household's name would be a wrong figure, not an old one. */
    fun cache(raw: String, whose: Int? = person) = prefs.edit()
        .putString("snapshot", raw)
        .putLong("snapshot_at", System.currentTimeMillis())
        .putInt("snapshot_person", whose ?: -1)
        .apply()

    fun cached(): Pair<Snapshot, Long>? {
        if (prefs.getInt("snapshot_person", -1) != (person ?: -1)) return null
        val raw = prefs.getString("snapshot", null) ?: return null
        val at = prefs.getLong("snapshot_at", 0L)
        return runCatching { Api.json.decodeFromString(Snapshot.serializer(), raw) to at }.getOrNull()
    }

    /**
     * The decisions taken on the phone that the dashboard has not
     * heard yet. A triage on a train is the point of doing it on a
     * phone, so a verdict is kept here first and sent when there is a
     * network — in the order it was taken, because two verdicts on the
     * same row must land the way the thumb meant them.
     */
    fun pending(): List<Verdict> {
        val raw = prefs.getString("pending", null) ?: return emptyList()
        return runCatching {
            Api.json.decodeFromString(ListSerializer(Verdict.serializer()), raw)
        }.getOrDefault(emptyList())
    }

    fun queue(verdict: Verdict) = savePending(pending() + verdict)

    fun savePending(list: List<Verdict>) = prefs.edit()
        .putString("pending", Api.json.encodeToString(ListSerializer(Verdict.serializer()), list))
        .apply()

    /** Whether the background round runs, and the last thing it said —
     *  so that it does not say the same thing every three hours. */
    var watch: Boolean
        get() = prefs.getBoolean("watch", false)
        set(value) = prefs.edit().putBoolean("watch", value).apply()

    var lastNotice: String?
        get() = prefs.getString("last_notice", null)
        set(value) = prefs.edit().putString("last_notice", value).apply()

    /**
     * The asset classes left out of the figure at the top — a house is
     * worth what it is worth, and somebody who wants to know what they
     * could actually spend does not want it counted. The dashboard
     * keeps no opinion about this; it is the phone's own view, and it
     * survives a restart because retyping it every morning would be
     * worse than not having it.
     */
    var excludedClasses: Set<String>
        get() = prefs.getStringSet("excluded_classes", emptySet()).orEmpty()
        set(value) = prefs.edit().putStringSet("excluded_classes", value).apply()

    fun api(): Api = Api(context, baseUrl.orEmpty(), token, person)
}
