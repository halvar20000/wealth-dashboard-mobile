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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.smarthomeworld.wealth.data.ClassValue
import fr.smarthomeworld.wealth.data.Period
import fr.smarthomeworld.wealth.data.Snapshot
import fr.smarthomeworld.wealth.R

// Short on purpose: eight of these sit four to a row on a phone, and
// the long form pushed the last two off the screen.
private val PERIODS = listOf(
    "1d" to R.string.period_1d, "1w" to R.string.period_1w,
    "1m" to R.string.period_1m, "3m" to R.string.period_3m,
    "ytd" to R.string.period_ytd, "1y" to R.string.period_1y,
    "3y" to R.string.period_3y, "all" to R.string.period_start,
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
    val context = LocalContext.current
    // `by_class` carries what is owned and nothing that is owed: the
    // dashboard draws it as a ring, and a ring has no negative slice.
    // The net worth it reports does subtract the debt, so a sum of the
    // classes alone is too high by every mortgage in the house — which
    // is why the debt is put back here, as a line of its own that can
    // be unticked like the others.
    val debt = snapshot.netWorth.debt ?: 0.0
    val classes = snapshot.netWorth.byClass +
        (if (debt > 0.0) listOf(ClassValue(stringResource(R.string.debt), -debt)) else emptyList())
    val left = classes.filter { it.name !in excluded }
    val dropped = classes.filter { it.name in excluded }
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
                Text(stringResource(R.string.net_worth).uppercase(), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 2.sp)
                Text(
                    Fmt.money(shown, ccy),
                    fontSize = 40.sp, fontWeight = FontWeight.ExtraBold,
                )
                if (dropped.isNotEmpty()) {
                    Text(
                        stringResource(R.string.net_worth_without,
                            dropped.joinToString(", ") { it.name },
                            Fmt.money(snapshot.netWorth.total, ccy)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val parts = listOfNotNull(
                    snapshot.netWorth.cash?.let { context.getString(R.string.part_cash, Fmt.money(it, ccy)) },
                    snapshot.netWorth.securities?.let { context.getString(R.string.part_securities, Fmt.money(it, ccy)) },
                ).joinToString(" · ")
                if (parts.isNotEmpty()) {
                    Text(parts, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val when_ = buildString {
                    val since = Fmt.since(context, at)
                    append(context.getString(if (stale) R.string.as_of else R.string.updated, since))
                    snapshot.netWorth.pricesAsOf?.let {
                        append(" · "); append(context.getString(R.string.prices_of, Fmt.day(it)))
                    }
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

        item { Section(stringResource(R.string.performance)) }
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
                                PerfTile(stringResource(label), snapshot.performance.getValue(key), ccy)
                            }
                        }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }

        if (classes.isNotEmpty()) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Section(stringResource(R.string.what_counts))
                    if (dropped.isNotEmpty()) {
                        TextButton(onClick = { dropped.forEach { onToggleClass(it.name) } }) {
                            Text(stringResource(R.string.all_classes))
                        }
                    }
                }
            }
            items(classes) { c ->
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
                        color = when {
                            c.name in excluded -> MaterialTheme.colorScheme.onSurfaceVariant
                            c.value < 0 -> Loss
                            else -> MaterialTheme.colorScheme.onSurface
                        })
                }
            }
        }

        snapshot.upcoming?.let { up ->
            item {
                Column {
                    Section(plural(R.plurals.next_days, up.days))
                    // One line of context, not a card: what is due is the
                    // list below, and the only figure worth carrying over
                    // it is what is left once it has all gone out.
                    Text(
                        stringResource(R.string.cash_now_then,
                            Fmt.money(up.starting, ccy), Fmt.money(up.ending, ccy))
                            + (up.belowZero?.let {
                                " · " + stringResource(R.string.below_zero_on, Fmt.day(it.date))
                            } ?: ""),
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
                                Fmt.day(e.date) + (if (e.estimate) " · " + stringResource(R.string.expected) else ""),
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

        item { Section(stringResource(R.string.waiting_for_you)) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Badge2("${snapshot.waiting.uncategorised}",
                    stringResource(R.string.badge_uncategorised), onTransactions)
                Badge2("${snapshot.waiting.unassignedSpending}",
                    stringResource(R.string.badge_unassigned), onTransactions)
                Badge2("${snapshot.sync.links}", stringResource(R.string.badge_connections) +
                    (if (snapshot.sync.red > 0) " · " + stringResource(R.string.badge_red, snapshot.sync.red)
                     else ""), onAccounts)
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
