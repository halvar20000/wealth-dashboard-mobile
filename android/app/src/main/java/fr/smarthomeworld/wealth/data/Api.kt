package fr.smarthomeworld.wealth.data

import android.content.Context
import fr.smarthomeworld.wealth.R
import java.io.IOException
import java.util.Locale
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
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
class Api(
    private val context: Context,
    private val baseUrl: String,
    private val token: String?,
    /** Whose picture: null for the household, else a person's id from
     *  `people`. The dashboard's own switch at the top of every page. */
    private val person: Int? = null,
) {

    class Failure(val status: Int, message: String) : IOException(message)

    /** A file on its way to the dashboard: its bytes, its name, its type. */
    data class Upload(val name: String, val mime: String?, val bytes: ByteArray)

    companion object {
        /** The tools that take `person`. Only these get it: a tool
         *  without the argument answers 400 to one it does not know. */
        private val SCOPED = setOf(
            "snapshot", "holdings", "net_worth_history", "allocation", "performance",
            "cashflow", "uncategorised", "unowned_spending", "transactions",
        )

        val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

        // The dashboard answers in its own language setting, and without
        // one in the language the request asks for. OkHttp asks for none.
        private val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder()
                    .header("Accept-Language", Locale.getDefault().toLanguageTag())
                    .build())
            }
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
        fun pair(context: Context, baseUrl: String, code: String): Paired {
            val url = normalise(baseUrl)
            val body = """{"code":"${code.trim()}"}""".toRequestBody("application/json".toMediaType())
            val req = Request.Builder().url("$url/api/v1/pair").post(body).build()
            client.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                val reply = runCatching { json.decodeFromString<Paired>(text) }.getOrNull()
                if (!resp.isSuccessful || reply?.token == null) {
                    throw Failure(resp.code, reply?.error ?: context.getString(R.string.error_code_refused))
                }
                return reply
            }
        }
    }

    /** A tool called with a JSON body — what a write wants, so that a
     *  merchant with an ampersand in its name is not a query-string
     *  problem. */
    private fun post(path: String, args: Map<String, String>): String {
        val body = buildString {
            append('{')
            args.entries.forEachIndexed { i, (k, v) ->
                if (i > 0) append(',')
                append(json.encodeToString(String.serializer(), k)); append(':')
                // Numbers and booleans go bare; the server takes both.
                if (v == "true" || v == "false" || v.toIntOrNull() != null) append(v)
                else append(json.encodeToString(String.serializer(), v))
            }
            append('}')
        }.toRequestBody("application/json".toMediaType())
        val req = Request.Builder().url("$baseUrl$path")
            .header("Authorization", "Bearer ${token.orEmpty()}")
            .header("Accept", "application/json")
            .post(body).build()
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                val why = runCatching {
                    json.parseToJsonElement(text).let { (it as? JsonObject)?.get("error")?.toString()?.trim('"') }
                }.getOrNull()
                throw Failure(resp.code, why ?: context.getString(R.string.error_answered, resp.code))
            }
            return text
        }
    }

    private fun get(path: String, args: Map<String, String> = emptyMap()): String {
        val url = ("$baseUrl$path").toHttpUrlOrNull()?.newBuilder()
            ?: throw Failure(0, context.getString(R.string.error_not_url))
        args.forEach { (k, v) -> url.addQueryParameter(k, v) }
        if (person != null && path.removePrefix("/api/v1/tools/") in SCOPED) {
            url.addQueryParameter("person", person.toString())
        }
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
                    401 -> context.getString(R.string.error_unpaired)
                    404 -> context.getString(R.string.error_not_found)
                    else -> context.getString(R.string.error_answered, resp.code)
                })
            }
            return text
        }
    }

    /** Everything a home screen shows, in one round trip. */
    fun snapshot(days: Int = 30): Snapshot {
        val text = get("/api/v1/tools/snapshot", mapOf("days" to days.toString()))
        val reply = json.decodeFromString(ToolReply.serializer(Snapshot.serializer()), text)
        return reply.result ?: throw Failure(200, reply.error ?: context.getString(R.string.error_sent_nothing))
    }

    /**
     * A statement into an account, as the import page takes it: the
     * field name is `file`, several at once are allowed, and the
     * dashboard answers with the report it would have shown on screen.
     * The bytes are read into memory — a statement is a few hundred
     * kilobytes, and a phone's share sheet hands over a stream that
     * does not survive the request being retried.
     */
    fun importFiles(accountId: Int, files: List<Upload>): ImportReply {
        if (files.isEmpty()) throw Failure(0, context.getString(R.string.error_nothing_to_send))
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
        files.forEach { f ->
            body.addFormDataPart("file", f.name,
                f.bytes.toRequestBody((f.mime ?: "application/octet-stream").toMediaType()))
        }
        val req = Request.Builder().url("$baseUrl/api/v1/accounts/$accountId/import")
            .header("Authorization", "Bearer ${token.orEmpty()}")
            .header("Accept", "application/json")
            .post(body.build()).build()
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            val reply = runCatching { json.decodeFromString<ImportReply>(text) }.getOrNull()
            if (reply == null) throw Failure(resp.code, context.getString(R.string.error_answered, resp.code))
            if (!resp.isSuccessful || !reply.ok) {
                throw Failure(resp.code, reply.error ?: context.getString(R.string.error_answered, resp.code))
            }
            return reply
        }
    }

    // ── The portfolio ────────────────────────────────────────────

    /** Every security held, worked out from the trades. */
    fun holdings(): Holdings {
        val text = get("/api/v1/tools/holdings")
        val reply = json.decodeFromString(ToolReply.serializer(Holdings.serializer()), text)
        return reply.result ?: throw Failure(200, reply.error ?: context.getString(R.string.error_sent_nothing))
    }

    /** The net worth on a set of days — the line on the chart. */
    fun history(period: String = "1y"): History {
        val text = get("/api/v1/tools/net_worth_history", mapOf("period" to period))
        val reply = json.decodeFromString(ToolReply.serializer(History.serializer()), text)
        return reply.result ?: throw Failure(200, reply.error ?: context.getString(R.string.error_sent_nothing))
    }

    /** Where the money sits: by asset class, by region, by bucket. */
    fun allocation(): Allocation {
        val text = get("/api/v1/tools/allocation")
        val reply = json.decodeFromString(ToolReply.serializer(Allocation.serializer()), text)
        return reply.result ?: Allocation()
    }

    /** The return of the whole portfolio and of each holding. */
    fun returns(): Returns {
        val text = get("/api/v1/tools/performance")
        val reply = json.decodeFromString(ToolReply.serializer(Returns.serializer()), text)
        return reply.result ?: Returns()
    }

    /** Put a file import back: every row it brought, nothing else.
     *  Needs a dashboard on 0.74.0 or newer. */
    fun undoImport(accountId: Int, importId: Int): Int {
        val text = post("/api/v1/tools/undo_import", mapOf(
            "account_id" to accountId.toString(), "import_id" to importId.toString()))
        val reply = json.decodeFromString(ToolReply.serializer(UndoneImport.serializer()), text)
        return reply.result?.removed ?: throw Failure(200, reply.error ?: context.getString(R.string.error_sent_nothing))
    }

    /** Income and spending per month, the dashboard's own arithmetic. */
    fun cashflow(months: Int = 13): Cashflow {
        val text = get("/api/v1/tools/cashflow", mapOf("months" to months.toString()))
        val reply = json.decodeFromString(ToolReply.serializer(Cashflow.serializer()), text)
        return reply.result ?: throw Failure(200, reply.error ?: context.getString(R.string.error_sent_nothing))
    }

    /** Quote every holding again and refetch the rates — no bank is
     *  touched, which is why this is the one a phone may press often. */
    fun refreshMarket(): Market {
        val text = post("/api/v1/tools/refresh_market", emptyMap())
        val reply = json.decodeFromString(ToolReply.serializer(Market.serializer()), text)
        return reply.result ?: throw Failure(200, reply.error ?: context.getString(R.string.error_sent_nothing))
    }

    /** The queue of rows with no category, biggest first. */
    fun uncategorised(limit: Int = 60): Queue {
        val text = get("/api/v1/tools/uncategorised", mapOf("limit" to limit.toString()))
        val reply = json.decodeFromString(ToolReply.serializer(Queue.serializer()), text)
        return reply.result ?: throw Failure(200, reply.error ?: context.getString(R.string.error_sent_nothing))
    }

    /** Every category, for the sheet of choices. The tool answers with
     *  a bare list. */
    fun categories(): List<Category> {
        val text = get("/api/v1/tools/categories")
        val reply = json.decodeFromString(
            ToolReply.serializer(ListSerializer(Category.serializer())), text)
        return reply.result ?: emptyList()
    }

    /** The spending nobody has claimed yet. */
    fun unowned(limit: Int = 60): Unowned {
        val text = get("/api/v1/tools/unowned_spending", mapOf("limit" to limit.toString()))
        val reply = json.decodeFromString(ToolReply.serializer(Unowned.serializer()), text)
        return reply.result ?: throw Failure(200, reply.error ?: context.getString(R.string.error_sent_nothing))
    }

    /** The household, for "whose spending is this". */
    fun people(): List<Person> {
        val text = get("/api/v1/tools/people")
        val reply = json.decodeFromString(ToolReply.serializer(PeopleList.serializer()), text)
        return reply.result?.people ?: emptyList()
    }

    /** One decision. The dashboard files the row and, where the verdict
     *  says so, remembers it as a rule for the next one like it. */
    fun send(verdict: Verdict) {
        verdict.category?.let { category ->
            val args = buildMap {
                put("txn_id", verdict.txnId.toString())
                put("category", category)
                put("remember", verdict.remember.toString())
                verdict.pattern?.takeIf { it.isNotBlank() }?.let { put("pattern", it) }
            }
            post("/api/v1/tools/set_category", args)
        }
        verdict.owner?.let { owner ->
            // The same switch as the category: "this row only" must mean
            // this row only, whichever queue the thumb was in.
            post("/api/v1/tools/set_owner", mapOf(
                "txn_id" to verdict.txnId.toString(), "owner" to owner,
                "remember" to verdict.remember.toString()))
        }
    }

    fun transactions(accountId: Int? = null, query: String? = null, limit: Int = 100): TransactionPage {
        val args = buildMap {
            put("limit", limit.toString())
            accountId?.let { put("account_id", it.toString()) }
            query?.takeIf { it.isNotBlank() }?.let { put("q", it.trim()) }
        }
        val text = get("/api/v1/tools/transactions", args)
        val reply = json.decodeFromString(ToolReply.serializer(TransactionPage.serializer()), text)
        return reply.result ?: throw Failure(200, reply.error ?: context.getString(R.string.error_sent_nothing))
    }

}
