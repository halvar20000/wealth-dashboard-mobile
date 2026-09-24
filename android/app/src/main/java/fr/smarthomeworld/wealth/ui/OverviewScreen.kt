package fr.smarthomeworld.wealth.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.smarthomeworld.wealth.data.Period
import fr.smarthomeworld.wealth.data.Snapshot

// Short on purpose: eight of these sit four to a row on a phone, and
// the long form pushed the last two off the screen.
private val PERIODS = listOf(
    "1d" to "1 T", "1w" to "1 W", "1m" to "1 M", "3m" to "3 M",
    "ytd" to "YTD", "1y" to "1 J", "3y" to "3 J", "all" to "Start",
)

@Composable
fun OverviewScreen(
    snapshot: Snapshot,
    at: Long,
    stale: Boolean,
    error: String?,
    /** The net worth over the last year, for the line under the figure.
     *  Empty until it has been fetched — the page works without it. */
    history: List<Double> = emptyList(),
    /** Asset classes the user has unticked; the figure leaves them out. */
    excluded: Set<String> = emptySet(),
    onToggleClass: (String) -> Unit = {},
    onAccounts: () -> Unit,
    onTransactions: () -> Unit,
) {
    val ccy = snapshot.baseCurrency
    val left = snapshot.netWorth.byClass.filter { it.name !in excluded }
    val dropped = snapshot.netWorth.byClass.filter { it.name in excluded }
    // With nothing unticked the dashboard's own figure is shown, not a
    // sum of the classes: the two can differ by a rounding, and the
    // number on the phone must match the number in the browser.
    val shown = if (dropped.isEmpty()) snapshot.netWorth.total
                else left.sumOf { it.value }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column {
                Text("NET WORTH", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 2.sp)
                Text(
                    Fmt.money(shown, ccy),
                    fontSize = 40.sp, fontWeight = FontWeight.ExtraBold,
                )
                if (dropped.isNotEmpty()) {
                    Text(
                        "ohne " + dropped.joinToString(", ") { it.name } +
                            " · mit allem " + Fmt.money(snapshot.netWorth.total, ccy),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val parts = listOfNotNull(
                    snapshot.netWorth.cash?.let { "Cash ${Fmt.money(it, ccy)}" },
                    snapshot.netWorth.securities?.let { "Securities ${Fmt.money(it, ccy)}" },
                ).joinToString(" · ")
                if (parts.isNotEmpty()) {
                    Text(parts, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val when_ = buildString {
                    append(if (stale) "As of ${Fmt.since(at)}" else "Updated ${Fmt.since(at)}")
                    snapshot.netWorth.pricesAsOf?.let { append(" · prices ${Fmt.day(it)}") }
                }
                Text(when_, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (history.size > 1) {
            item { LineChart(history, Modifier.fillMaxWidth().height(150.dp)) }
        }

        if (error != null) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(error, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }

        item { Section("Performance") }
        item {
            // Two rows of four rather than a queue that scrolls off the
            // screen: the whole point of these eight is comparing them.
            val windows = PERIODS.filter { snapshot.performance[it.first] != null }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                windows.chunked(4).forEach { row ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        row.forEach { (key, label) ->
                            Box(Modifier.weight(1f)) {
                                PerfTile(label, snapshot.performance.getValue(key), ccy)
                            }
                        }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }

        if (snapshot.netWorth.byClass.isNotEmpty()) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Section("Was zählt mit")
                    if (dropped.isNotEmpty()) {
                        TextButton(onClick = { dropped.forEach { onToggleClass(it.name) } }) {
                            Text("alles")
                        }
                    }
                }
            }
            items(snapshot.netWorth.byClass) { c ->
                Row(
                    Modifier.fillMaxWidth().clickable { onToggleClass(c.name) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = c.name !in excluded, onCheckedChange = { onToggleClass(c.name) })
                    Text(c.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                        color = if (c.name in excluded) MaterialTheme.colorScheme.onSurfaceVariant
                                else MaterialTheme.colorScheme.onSurface)
                    Text(Fmt.money(c.value, ccy), style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (c.name in excluded) MaterialTheme.colorScheme.onSurfaceVariant
                                else MaterialTheme.colorScheme.onSurface)
                }
            }
        }

        snapshot.upcoming?.let { up ->
            item {
                Column {
                    Section("Nächste ${up.days} Tage")
                    // One line of context, not a card: what is due is the
                    // list below, and the only figure worth carrying over
                    // it is what is left once it has all gone out.
                    Text(
                        "Bargeld jetzt ${Fmt.money(up.starting, ccy)} → danach ${Fmt.money(up.ending, ccy)}"
                            + (up.belowZero?.let { " · unter null am ${Fmt.day(it.date)}" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (up.belowZero != null) Loss
                                else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (snapshot.events.isNotEmpty()) {
                items(snapshot.events) { e ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(e.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                            Text(
                                Fmt.day(e.date) + (if (e.estimate) " · expected" else ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            Fmt.signedMoney(e.amount, ccy),
                            color = if (e.amount >= 0) Gain else Loss,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }

        item { Section("Waiting for you") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Badge2("${snapshot.waiting.uncategorised}", "uncategorised", onTransactions)
                Badge2("${snapshot.waiting.unassignedSpending}", "unassigned", onTransactions)
                Badge2("${snapshot.sync.links}", "connections" +
                    (if (snapshot.sync.red > 0) " · ${snapshot.sync.red} red" else ""), onAccounts)
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun Section(title: String) {
    Text(title.uppercase(), style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 2.sp,
        modifier = Modifier.padding(top = 6.dp))
}

@Composable
private fun PerfTile(label: String, p: Period, currency: String) {
    val colour: Color = when {
        p.twr == null -> MaterialTheme.colorScheme.onSurfaceVariant
        p.twr > 0 -> Gain
        p.twr < 0 -> Loss
        else -> MaterialTheme.colorScheme.onSurface
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 10.dp)) {
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Text(Fmt.percent(p.twr), color = colour, fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp, maxLines = 1)
            Text(Fmt.signedMoney(p.pnl, currency), style = MaterialTheme.typography.labelSmall,
                color = colour.copy(alpha = 0.85f), maxLines = 1)
        }
    }
}

@Composable
private fun Badge2(value: String, label: String, onClick: () -> Unit) {
    Card(onClick = onClick) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.Start) {
            Text(value, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(label, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
