package fr.smarthomeworld.wealth.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.smarthomeworld.wealth.data.Period
import fr.smarthomeworld.wealth.data.Snapshot

private val PERIODS = listOf(
    "1d" to "1 day", "1w" to "1 week", "1m" to "1 month", "3m" to "3 months",
    "ytd" to "YTD", "1y" to "1 year", "3y" to "3 years", "all" to "Since start",
)

@Composable
fun OverviewScreen(
    snapshot: Snapshot,
    at: Long,
    stale: Boolean,
    error: String?,
    onAccounts: () -> Unit,
    onTransactions: () -> Unit,
) {
    val ccy = snapshot.baseCurrency
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
                    Fmt.money(snapshot.netWorth.total, ccy),
                    fontSize = 40.sp, fontWeight = FontWeight.ExtraBold,
                )
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
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(PERIODS.filter { snapshot.performance[it.first] != null }) { (key, label) ->
                    PerfTile(label, snapshot.performance.getValue(key), ccy)
                }
            }
        }

        if (snapshot.netWorth.byClass.isNotEmpty()) {
            item { Section("By class") }
            items(snapshot.netWorth.byClass) { c ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(c.name, style = MaterialTheme.typography.bodyMedium)
                    Text(Fmt.money(c.value, ccy), style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold)
                }
            }
        }

        snapshot.upcoming?.let { up ->
            item { Section("Next ${up.days} days") }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Cash now", style = MaterialTheme.typography.bodyMedium)
                            Text(Fmt.money(up.starting, ccy), fontWeight = FontWeight.SemiBold)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("After what is due", style = MaterialTheme.typography.bodyMedium)
                            Text(Fmt.money(up.ending, ccy), fontWeight = FontWeight.SemiBold)
                        }
                        up.lowest?.let {
                            Text(
                                "Lowest ${Fmt.money(it.running, ccy)} on ${Fmt.day(it.date)} — ${it.name}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if ((it.running ?: 0.0) < 0) Loss else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
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
    Card(Modifier.width(126.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(Fmt.percent(p.twr), color = colour, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
            Text(Fmt.signedMoney(p.pnl, currency), style = MaterialTheme.typography.bodySmall,
                color = colour.copy(alpha = 0.85f))
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
