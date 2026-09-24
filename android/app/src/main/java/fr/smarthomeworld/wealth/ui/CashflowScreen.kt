package fr.smarthomeworld.wealth.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.smarthomeworld.wealth.CashflowState
import fr.smarthomeworld.wealth.data.MonthFlow

/**
 * What came in, what went out, and what is left.
 *
 * The dashboard does the arithmetic — thirteen months, every currency
 * at the rate of its month, transfers between your own accounts left
 * out because moving money is not spending it. The page draws it and
 * says the one figure a household actually argues about: what an
 * average month leaves over.
 */
@Composable
fun CashflowScreen(
    state: CashflowState,
    baseCurrency: String,
    onMonths: (Int) -> Unit,
    onRefresh: () -> Unit,
    onTransactions: () -> Unit = {},
) {
    val flow = state.flow
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text("Cashflow", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold)
                Row {
                    listOf(6, 13, 25).forEach { m ->
                        TextButton(onClick = { onMonths(m) }) {
                            Text(
                                "$m M",
                                fontWeight = if (state.months == m) FontWeight.Bold else FontWeight.Normal,
                                color = if (state.months == m) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        if (flow == null) {
            item {
                Box(Modifier.fillMaxWidth().height(220.dp), Alignment.Center) {
                    when {
                        state.loading -> CircularProgressIndicator()
                        state.error != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(state.error, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(10.dp))
                            OutlinedButton(onClick = onRefresh) { Text("Nochmal laden") }
                        }
                        else -> Text("Noch nichts geladen.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            return@LazyColumn
        }

        val ccy = flow.baseCurrency.ifBlank { baseCurrency }

        item {
            Card(shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Im Schnitt pro Monat", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(Fmt.signedMoney(flow.averageNet, ccy),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (flow.averageNet >= 0) Gain else Loss)
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Figure("Ein", Fmt.money(flow.averageIncome, ccy), Gain)
                        Figure("Aus", Fmt.money(flow.averageSpending, ccy), Loss)
                        Figure("Angelegt",
                            Fmt.money(flow.totalInvestment / (flow.monthsCovered.coerceAtLeast(1)), ccy),
                            MaterialTheme.colorScheme.primary)
                    }
                    if (flow.unconverted.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Ohne Kurs und daher nicht umgerechnet: " +
                                flow.unconverted.joinToString(", ") {
                                    "${Fmt.money(it.amount, it.currency)} ${it.currency}"
                                },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        item { Bars(flow.months, ccy) }

        item {
            Text("Wohin es geht", style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold)
        }
        items(flow.byCategory.take(14)) { c ->
            val share = if (flow.averageSpending > 0) c.perMonth / flow.averageSpending else 0.0
            Column(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(c.label.ifBlank { c.category }, style = MaterialTheme.typography.bodyMedium)
                    Text("${Fmt.money(c.perMonth, ccy)} / Monat",
                        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { share.coerceIn(0.0, 1.0).toFloat() },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = parseColour(c.colour) ?: MaterialTheme.colorScheme.primary,
                )
            }
        }

        if (flow.incomeByCategory.isNotEmpty()) {
            item {
                Text("Woher es kommt", style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold)
            }
            items(flow.incomeByCategory.take(8)) { c ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(c.label.ifBlank { c.category }, style = MaterialTheme.typography.bodyMedium)
                    Text("${Fmt.money(c.perMonth, ccy)} / Monat",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold, color = Gain)
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun Figure(label: String, value: String, colour: Color) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold, color = colour)
    }
}

/** A month at a time: what came in above the line, what went out below
 *  it. Twelve bars say more about a household than any average. */
@Composable
private fun Bars(months: List<MonthFlow>, currency: String) {
    if (months.isEmpty()) return
    val biggest = months.maxOf { maxOf(it.income, it.spending) }.takeIf { it > 0 } ?: 1.0
    val last = months.last()
    Column {
        Canvas(Modifier.fillMaxWidth().height(140.dp)) {
            val slot = size.width / months.size
            val bar = (slot * 0.34f).coerceAtMost(14f * density)
            val mid = size.height / 2
            months.forEachIndexed { i, m ->
                val centre = slot * i + slot / 2
                val up = (m.income / biggest * (mid - 6)).toFloat()
                val down = (m.spending / biggest * (mid - 6)).toFloat()
                drawRect(Gain.copy(alpha = if (m === last) 1f else 0.75f),
                    topLeft = Offset(centre - bar - 1f, mid - up), size = Size(bar, up))
                drawRect(Loss.copy(alpha = if (m === last) 1f else 0.75f),
                    topLeft = Offset(centre + 1f, mid), size = Size(bar, down))
            }
            drawLine(Color.Gray.copy(alpha = 0.35f), Offset(0f, mid), Offset(size.width, mid), 1f)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(months.first().month, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "${last.month}: ${Fmt.signedMoney(last.net, currency)}",
                style = MaterialTheme.typography.labelSmall,
                color = if (last.net >= 0) Gain else Loss)
        }
    }
}

/** "#7c3aed" as the dashboard writes it; anything else is ignored. */
private fun parseColour(hex: String?): Color? {
    val clean = hex?.trim()?.removePrefix("#") ?: return null
    if (clean.length != 6) return null
    return runCatching { Color(("ff$clean").toLong(16)) }.getOrNull()
}
