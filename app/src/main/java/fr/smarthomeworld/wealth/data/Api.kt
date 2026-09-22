package fr.smarthomeworld.wealth.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * The dashboard's own HTTP surface, nothing else.
 *
 *   POST /api/v1/pair                     a six-digit code for the token
 *   GET  /api/v1/tools/<name>?args        every tool the assistant has
 *   POST /api/v1/accounts/<id>/import     a statement, as the page takes it
 *
 * One client, one place that knows about the bearer token, and errors
 * that carry the server's own sentence — the dashboard says why in
 * words meant for a person, and repeating them beats inventing worse
 * ones here.
 */
class Api(private val baseUrl: String, private val token: String?) {

    class Failure(val status: Int, message: String) : IOException(message)

    companion object {
        val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

        private val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        /** What a person types: "tower:8000", "https://…/", with or without a scheme. */
        fun normalise(raw: String): String {
            var s = raw.trim().trimEnd('/')
            if (s.isEmpty()) return s
            if (!s.startsWith("http://") && !s.startsWith("https://")) {
                s = (if (s.startsWith("localhost") || s.matches(Regex("""^\d+\.\d+\.\d+\.\d+.*"""))) "http://" else "https://") + s
            }
            return s.trimEnd('/')
        }

        /** Trade a pairing code for the token. No token yet, by definition. */
        fun pair(baseUrl: String, code: String): Paired {
            val url = normalise(baseUrl)
            val body = """{"code":"${code.trim()}"}""".toRequestBody("application/json".toMediaType())
            val req = Request.Builder().url("$url/api/v1/pair").post(body).build()
            client.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                val reply = runCatching { json.decodeFromString<Paired>(text) }.getOrNull()
                if (!resp.isSuccessful || reply?.token == null) {
                    throw Failure(resp.code, reply?.error ?: "The dashboard refused that code.")
                }
                return reply
            }
        }
    }

    private fun get(path: String, args: Map<String, String> = emptyMap()): String {
        val url = ("$baseUrl$path").toHttpUrlOrNull()?.newBuilder()
            ?: throw Failure(0, "That address is not a URL.")
        args.forEach { (k, v) -> url.addQueryParameter(k, v) }
        val req = Request.Builder().url(url.build())
            .header("Authorization", "Bearer ${token.orEmpty()}")
            .header("Accept", "application/json")
            .build()
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                val why = runCatching {
                    json.parseToJsonElement(text).let { (it as? JsonObject)?.get("error")?.toString()?.trim('"') }
                }.getOrNull()
                throw Failure(resp.code, why ?: when (resp.code) {
                    401 -> "This device is no longer paired — pair it again."
                    404 -> "This dashboard does not know that address."
                    else -> "The dashboard answered ${resp.code}."
                })
            }
            return text
        }
    }

    /** Everything a home screen shows, in one round trip. */
    fun snapshot(days: Int = 30): Snapshot {
        val text = get("/api/v1/tools/snapshot", mapOf("days" to days.toString()))
        val reply = json.decodeFromString(ToolReply.serializer(Snapshot.serializer()), text)
        return reply.result ?: throw Failure(200, reply.error ?: "The dashboard sent nothing.")
    }

    fun transactions(accountId: Int? = null, query: String? = null, limit: Int = 100): TransactionPage {
        val args = buildMap {
            put("limit", limit.toString())
            accountId?.let { put("account_id", it.toString()) }
            query?.takeIf { it.isNotBlank() }?.let { put("q", it.trim()) }
        }
        val text = get("/api/v1/tools/transactions", args)
        val reply = json.decodeFromString(ToolReply.serializer(TransactionPage.serializer()), text)
        return reply.result ?: throw Failure(200, reply.error ?: "The dashboard sent nothing.")
    }

}
