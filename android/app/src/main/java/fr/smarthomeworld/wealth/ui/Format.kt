package fr.smarthomeworld.wealth.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Currency
import java.util.Locale
import fr.smarthomeworld.wealth.R

/** Numbers as the phone's language writes them, in the money's own unit.
 *  Money always with its cents, so 3 300 € and 200,30 € read alike.
 *  Settings can pick other separators than the phone's: [shape]. */
object Fmt {

    /** The separators a user can pick instead of the phone's: group,
     *  then decimal. The same four as the dashboard's setting. */
    val SHAPES: Map<String, Pair<Char, Char>> = linkedMapOf(
        "de" to ('.' to ','),
        "en" to (',' to '.'),
        "fr" to ('\u202F' to ','),
        "ch" to ('\u2019' to '.'),
    )

    /** "" for the phone's own way, else a key of [SHAPES]. State, so a
     *  screen showing amounts redraws when it changes. Store sets it. */
    var shape: String by mutableStateOf("")

    /** "1.234,56": how a shape is offered in Settings. */
    fun example(code: String): String {
        val (group, decimal) = SHAPES[code] ?: return ""
        return "1${group}234${decimal}56"
    }

    /** The phone's text with the picked separators. Only a mark between
     *  two digits is a separator, so "kr." or "Fr." keep their dot. */
    private fun reshape(text: String): String {
        val (group, decimal) = SHAPES[shape] ?: return text
        val sym = DecimalFormatSymbols.getInstance(Locale.getDefault())
        val decimals = setOf(sym.decimalSeparator, sym.monetaryDecimalSeparator)
        val out = StringBuilder(text.length)
        for (i in text.indices) {
            val c = text[i]
            val between = i > 0 && i < text.length - 1 && text[i - 1].isDigit() && text[i + 1].isDigit()
            out.append(if (!between || c.isDigit()) c else if (c in decimals) decimal else group)
        }
        return out.toString()
    }

    fun money(value: Double?, currency: String?): String {
        if (value == null) return "—"
        val f = NumberFormat.getCurrencyInstance(Locale.getDefault())
        runCatching { f.currency = Currency.getInstance((currency ?: "EUR").uppercase()) }
        f.maximumFractionDigits = 2
        f.minimumFractionDigits = 2
        return reshape(f.format(value))
    }

    fun signedMoney(value: Double?, currency: String?): String {
        if (value == null) return "—"
        val body = money(kotlin.math.abs(value), currency)
        return (if (value >= 0) "+" else "−") + body
    }

    /** Units of a security: whole where they are whole, because
     *  "1'061" reads better than "1'061.000". */
    fun quantity(value: Double?): String {
        if (value == null) return "—"
        val f = NumberFormat.getNumberInstance(Locale.getDefault())
        f.maximumFractionDigits = if (value == Math.floor(value)) 0 else 3
        return reshape(f.format(value))
    }

    fun percent(fraction: Double?): String {
        if (fraction == null) return "—"
        val v = fraction * 100
        return reshape(String.format(Locale.getDefault(), "%+.2f %%", v))
    }

    fun day(iso: String?): String {
        if (iso.isNullOrBlank()) return ""
        return runCatching {
            LocalDate.parse(iso.take(10)).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
        }.getOrDefault(iso)
    }

    /** "just now", "12 min ago", "3 h ago", or the day. */
    fun since(context: Context, millis: Long): String {
        if (millis <= 0) return ""
        val d = Duration.between(Instant.ofEpochMilli(millis), Instant.now())
        return when {
            d.toMinutes() < 1 -> context.getString(R.string.just_now)
            d.toHours() < 1 -> context.getString(R.string.minutes_ago, d.toMinutes())
            d.toDays() < 1 -> context.getString(R.string.hours_ago, d.toHours())
            else -> day(LocalDate.now().minusDays(d.toDays()).toString())
        }
    }
}
