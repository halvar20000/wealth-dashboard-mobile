package fr.smarthomeworld.wealth.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.smarthomeworld.wealth.PortfolioState
import fr.smarthomeworld.wealth.data.Account
import fr.smarthomeworld.wealth.data.Holding
import fr.smarthomeworld.wealth.data.Period

/** The windows the chart offers, in the order the dashboard names them. */
private val PERIODS = listOf(
    "1m" to "1 M", "3m" to "3 M", "6m" to "6 M",
    "ytd" to "YTD", "1y" to "1 J", "all" to "Alles",
)

/**
 * Everything that is owned, and how it got here.
 *
 * Three views of one portfolio: the securities with what each has made,
 * where the money sits by asset class, and the accounts themselves. The
 * chart at the top belongs to all three — it is what the depots hold,
 * the securities alone: cash and property have the overview's line, and
 * mixed in here they would hide how the shares are doing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortfolioScreen(
    state: PortfolioState,
    accounts: List<Account>,
    onPeriod: (String) -> Unit,
    onAccount: (Account) -> Unit,
    onRefresh: () -> Unit,
) {
    var view by rememberSaveable { mutableStateOf(0) }   // 0 Wertpapiere, 1 Aufteilung, 2 Konten
    val line = state.history.points.mapNotNull { it.securities }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column {
                Text("Depot", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    Fmt.money(line.lastOrNull() ?: state.securities, state.baseCurrency),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold)
                val change = if (line.size > 1) line.last() - line.first() else null
                if (change != null) {
                    val pct = line.first().takeIf { it != 0.0 }?.let { change / it }
                    Text(
                        listOfNotNull(
                            Fmt.signedMoney(change, state.baseCurrency),
                            pct?.let { Fmt.percent(it) },
                            state.history.firstDate?.let { "seit ${Fmt.day(it)}" },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (change >= 0) Gain else Loss)
                }
            }
        }

        item {
            if (state.loading && line.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(170.dp), Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (line.size > 1) {
                LineChart(line)
            } else {
                Box(Modifier.fillMaxWidth().height(170.dp), Alignment.Center) {
                    Text("Für diesen Zeitraum gibt es noch keine Punkte.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                PERIODS.forEachIndexed { i, (key, label) ->
                    SegmentedButton(
                        selected = state.period == key,
                        onClick = { onPeriod(key) },
                        shape = SegmentedButtonDefaults.itemShape(i, PERIODS.size),
                    ) { Text(label, style = MaterialTheme.typography.labelSmall) }
                }
            }
        }

        item {
            TabRow(selectedTabIndex = view) {
                listOf("Wertpapiere", "Aufteilung", "Konten").forEachIndexed { i, title ->
                    Tab(selected = view == i, onClick = { view = i },
                        text = { Text(title, style = MaterialTheme.typography.labelLarge) })
                }
            }
        }

        if (state.error != null && state.holdings.isEmpty()) {
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                       modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                    Text(state.error, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(onClick = onRefresh) { Text("Nochmal laden") }
                }
            }
        }

        when (view) {
            0 -> {
                item { Totals(state) }
                items(state.holdings, key = { it.isin }) { h ->
                    HoldingCard(h, state.returns.holdings[h.isin], state.baseCurrency)
                }
            }
            1 -> item { AllocationCard(state) }
            else -> items(accounts, key = { it.id }) { a ->
                ListItem(
                    headlineContent = { Text(a.name) },
                    supportingContent = {
                        Text(listOfNotNull(a.bank, a.type).joinToString(" · ").ifBlank { "Konto" })
                    },
                    trailingContent = {
                        Text(Fmt.money(a.balanceBase ?: a.balance, state.baseCurrency),
                            fontWeight = FontWeight.SemiBold)
                    },
                    modifier = Modifier.clickable { onAccount(a) },
                )
            }
        }
    }
}

/** What the securities are worth, what went in, and what came of it. */
@Composable
private fun Totals(state: PortfolioState) {
    val value = state.holdings.sumOf { it.valueBase }
    val invested = state.holdings.sumOf { it.invested }
    val gain = value - invested
    Card(shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween) {
            Figure("Wertpapiere", Fmt.money(value, state.baseCurrency))
            Figure("Eingezahlt", Fmt.money(invested, state.baseCurrency))
            Figure(
                "Gewinn", Fmt.signedMoney(gain, state.baseCurrency),
                colour = if (gain >= 0) Gain else Loss,
                note = state.returns.all?.twr?.let { Fmt.percent(it) })
        }
    }
}

@Composable
private fun Figure(label: String, value: String, colour: androidx.compose.ui.graphics.Color? = null,
                   note: String? = null) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = colour ?: MaterialTheme.colorScheme.onSurface)
        if (note != null) {
            Text(note, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** One security. Tapping it opens what would otherwise be clutter. */
@Composable
private fun HoldingCard(h: Holding, ret: Period?, base: String?) {
    var open by remember(h.isin) { mutableStateOf(false) }
    Card(shape = RoundedCornerShape(18.dp),
         modifier = Modifier.fillMaxWidth().clickable { open = !open }) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(h.name, style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold, maxLines = 2)
                    Text(
                        listOfNotNull(
                            h.symbol ?: h.isin,
                            "${Fmt.quantity(h.quantity)} × ${Fmt.money(h.price, h.currency, decimals = true)}",
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(Fmt.money(h.valueBase, base), fontWeight = FontWeight.Bold)
                    val colour = if (h.gain >= 0) Gain else Loss
                    Text(
                        listOfNotNull(
                            ret?.twr?.let { Fmt.percent(it) },
                            Fmt.signedMoney(h.gain, base),
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = colour)
                }
            }
            AnimatedVisibility(open) {
                Column(Modifier.padding(top = 12.dp),
                       verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Detail("Eingezahlt", Fmt.money(h.invested, base))
                    ret?.twrAnnual?.let { Detail("Rendite p. a.", Fmt.percent(it)) }
                    ret?.mwr?.let { Detail("Geldgewichtet p. a.", Fmt.percent(it)) }
                    ret?.since?.let { Detail("Seit", Fmt.day(it)) }
                    h.priceAsOf?.let { Detail("Kurs vom", Fmt.day(it)) }
                    h.lastTrade?.let { Detail("Letzter Handel", Fmt.day(it)) }
                    if (h.accounts.isNotEmpty()) Detail("Depot", h.accounts.joinToString(", "))
                    if (h.incompleteHistory) {
                        Text("Die Handelshistorie ist lückenhaft — die Rendite ist eine Näherung.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun Detail(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

/** Where the money sits. The ring, then the same figures as words. */
@Composable
private fun AllocationCard(state: PortfolioState) {
    val rows = state.allocation.dimensions["asset_class"]?.rows.orEmpty()
        .sortedByDescending { it.value }
    Card(shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(16.dp)) {
            if (rows.isEmpty()) {
                Text("Noch keine Aufteilung — die Positionen sind nicht klassifiziert.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@Column
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                DonutChart(rows.map { it.value }) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(Fmt.money(state.allocation.total, state.baseCurrency),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold)
                        Text("gesamt", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    rows.forEachIndexed { i, r ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp)
                                .background(Slices[i % Slices.size], CircleShape))
                            Spacer(Modifier.width(8.dp))
                            Text(assetClass(r.key), Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall)
                            Text("${r.share.toInt()} %",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Detail("davon Bargeld", Fmt.money(state.allocation.cash, state.baseCurrency))
        }
    }
}

/** The dashboard's slugs, in German. An unknown one is shown as it is. */
private fun assetClass(key: String): String = when (key) {
    "equity" -> "Aktien"
    "bond" -> "Anleihen"
    "real_estate" -> "Immobilien"
    "commodity" -> "Rohstoffe"
    "cash" -> "Bargeld"
    "crypto" -> "Krypto"
    "other" -> "Sonstiges"
    else -> key.replaceFirstChar { it.uppercase() }
}
