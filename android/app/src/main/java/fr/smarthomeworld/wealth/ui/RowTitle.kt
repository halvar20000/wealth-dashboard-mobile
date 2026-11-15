package fr.smarthomeworld.wealth.ui

/** How a row is named in a list: the merchant first, when the bank gave
 *  one. A card payment's description is only "VISA Debitkartenumsatz
 *  vom …"; the shop is in the counterparty. So the counterparty is the
 *  title and the description, if it says something else, the small line
 *  under it. Without a counterparty the row reads as it always did. The
 *  same rule as iOS's `RowTitle`. */
data class RowTitle(val title: String, val subtitle: String?)

fun rowTitle(counterparty: String?, description: String?, kind: String?, limit: Int): RowTitle {
    val who = tidy(counterparty)
    val what = tidy(description)
    return if (who != null) {
        RowTitle(who.take(limit), what?.takeIf { it != who }?.take(limit))
    } else {
        RowTitle((what ?: tidy(kind)).orEmpty().take(limit), null)
    }
}

private val spaces = Regex("\\s+")

/** Trimmed, runs of spaces folded into one (banks pad names to a fixed
 *  width); null when nothing is left. */
private fun tidy(text: String?): String? =
    text?.trim()?.replace(spaces, " ")?.ifEmpty { null }
