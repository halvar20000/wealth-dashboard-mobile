package fr.smarthomeworld.wealth.ui

import java.text.NumberFormat
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Currency
import java.util.Locale

/** Numbers as the phone's language writes them, in the money's own unit. */
object Fmt {

    fun money(value: Double?, currency: String?, decimals: Boolean = false): String {
        if (value == null) return "—"
        val f = NumberFormat.getCurrencyInstance(Locale.getDefault())
        runCatching { f.currency = Currency.getInstance((currency ?: "EUR").uppercase()) }
        f.maximumFractionDigits = if (decimals) 2 else 0
        f.minimumFractionDigits = if (decimals) 2 else 0
        return f.format(value)
    }

    fun signedMoney(value: Double?, currency: String?): String {
        if (value == null) return "—"
        val body = money(kotlin.math.abs(value), currency, decimals = kotlin.math.abs(value) < 1000)
        return (if (value >= 0) "+" else "−") + body
    }

    fun percent(fraction: Double?): String {
        if (fraction == null) return "—"
        val v = fraction * 100
        return String.format(Locale.getDefault(), "%+.2f %%", v)
    }

    fun day(iso: String?): String {
        if (iso.isNullOrBlank()) return ""
        return runCatching {
            LocalDate.parse(iso.take(10)).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
        }.getOrDefault(iso)
    }

    /** "just now", "12 min ago", "3 h ago", or the day. */
    fun since(millis: Long): String {
        if (millis <= 0) return ""
        val d = Duration.between(Instant.ofEpochMilli(millis), Instant.now())
        return when {
            d.toMinutes() < 1 -> "just now"
            d.toHours() < 1 -> "${d.toMinutes()} min ago"
            d.toDays() < 1 -> "${d.toHours()} h ago"
            else -> day(LocalDate.now().minusDays(d.toDays()).toString())
        }
    }
}
