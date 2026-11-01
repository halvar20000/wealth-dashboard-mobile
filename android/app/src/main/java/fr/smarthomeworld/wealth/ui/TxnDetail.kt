package fr.smarthomeworld.wealth.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import fr.smarthomeworld.wealth.R
import fr.smarthomeworld.wealth.TxnEditState
import fr.smarthomeworld.wealth.data.Category
import fr.smarthomeworld.wealth.data.Txn
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale

/** "#7c3aed" as the dashboard writes it; anything else is null. */
fun hexColour(hex: String?): Color? {
    val clean = hex?.trim()?.removePrefix("#") ?: return null
    if (clean.length != 6) return null
    return runCatching { Color(("ff$clean").toLong(16)) }.getOrNull()
}

/** The dashboard's kinds in words; an unknown one as it is. */
@Composable
fun kindLabel(kind: String?): String = when (kind) {
    "deposit" -> stringResource(R.string.kind_deposit)
    "withdrawal" -> stringResource(R.string.kind_withdrawal)
    "buy" -> stringResource(R.string.kind_buy)
    "sell" -> stringResource(R.string.kind_sell)
    "dividend" -> stringResource(R.string.kind_dividend)
    "interest" -> stringResource(R.string.kind_interest)
    "fee" -> stringResource(R.string.kind_fee)
    "tax" -> stringResource(R.string.kind_tax)
    "transfer" -> stringResource(R.string.kind_transfer)
    "other" -> stringResource(R.string.kind_other)
    null, "" -> "—"
    else -> kind.replaceFirstChar { it.uppercase() }
}

private enum class Mode { Show, Categorise, Edit }

/**
 * One row, all of it: every field the dashboard has, and the two things
 * a person wants to do with a row they have just found — file it, or
 * put right what the bank wrote.
 *
 * Filing here is not the triage: it goes now, and "remember" starts off,
 * because a row someone looked up on purpose is usually the exception,
 * not the rule.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TxnDetailSheet(
    txn: Txn,
    categories: List<Category>,
    edit: TxnEditState,
    onDismiss: () -> Unit,
    onCategorise: (category: String, remember: Boolean, pattern: String?) -> Unit,
    onUpdate: (fields: Map<String, Any?>, onDone: () -> Unit) -> Unit,
) {
    var mode by remember(txn.id) { mutableStateOf(Mode.Show) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            if (edit.error != null) {
                Text(edit.error, color = Loss, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 8.dp))
            }
            when (mode) {
                Mode.Show -> Details(txn, categories, edit.saving,
                    onCategorise = { mode = Mode.Categorise }, onEdit = { mode = Mode.Edit })
                Mode.Categorise -> CategoryChooser(txn, categories,
                    onBack = { mode = Mode.Show },
                    onPick = { slug, remember, pattern ->
                        onCategorise(slug, remember, pattern)
                        mode = Mode.Show
                    })
                Mode.Edit -> EditForm(txn, edit.saving,
                    onBack = { mode = Mode.Show },
                    onSave = { fields -> onUpdate(fields) { mode = Mode.Show } })
            }
        }
    }
}

@Composable
private fun Details(
    t: Txn, categories: List<Category>, saving: Boolean,
    onCategorise: () -> Unit, onEdit: () -> Unit,
) {
    val cat = categories.firstOrNull { it.slug == t.category }
    Column(Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(t.description?.ifBlank { null } ?: t.counterparty ?: kindLabel(t.kind),
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(Fmt.signedMoney(t.amount, t.currency),
            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
            color = if (t.amount >= 0) Gain else Loss)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth().clickable(onClick = onCategorise).padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(12.dp).background(hexColour(cat?.colour) ?: MaterialTheme.colorScheme.outline, CircleShape))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.txn_category), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(cat?.label ?: t.category?.ifBlank { null } ?: stringResource(R.string.txn_uncategorised),
                    style = MaterialTheme.typography.bodyLarge)
            }
            if (saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        }
        HorizontalDivider()
        Line(stringResource(R.string.txn_date), Fmt.day(t.date))
        t.account?.let { Line(stringResource(R.string.account), it) }
        t.counterparty?.takeIf { it.isNotBlank() }?.let { Line(stringResource(R.string.txn_counterparty), it) }
        t.description?.takeIf { it.isNotBlank() }?.let { Line(stringResource(R.string.txn_description), it) }
        Line(stringResource(R.string.txn_kind), kindLabel(t.kind))
        Line(stringResource(R.string.txn_amount), Fmt.money(t.amount, t.currency, decimals = true))
        (t.securityName ?: t.isin)?.let { name ->
            Line(stringResource(R.string.txn_security), listOfNotNull(name, t.isin.takeIf { t.securityName != null }).joinToString(" · "))
        }
        t.quantity?.let { Line(stringResource(R.string.txn_quantity), Fmt.quantity(it)) }
        t.price?.let { Line(stringResource(R.string.txn_price), Fmt.money(it, t.currency, decimals = true)) }
        t.fee?.takeIf { it != 0.0 }?.let { Line(stringResource(R.string.txn_fee), Fmt.money(it, t.currency, decimals = true)) }
        t.tax?.takeIf { it != 0.0 }?.let { Line(stringResource(R.string.txn_tax), Fmt.money(it, t.currency, decimals = true)) }
        t.tags?.takeIf { it.isNotBlank() }?.let { Line(stringResource(R.string.txn_tags), it.split(',').joinToString(", ") { s -> s.trim() }) }
        t.source?.takeIf { it.isNotBlank() }?.let { Line(stringResource(R.string.txn_source), it) }
        Line(stringResource(R.string.txn_number), "#${t.id}")
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onCategorise, enabled = !saving) { Text(stringResource(R.string.txn_categorise)) }
            OutlinedButton(onClick = onEdit, enabled = !saving) { Text(stringResource(R.string.txn_edit)) }
        }
    }
}

@Composable
private fun Line(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, Modifier.width(120.dp), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
    }
}

/** The choices, with the rule decided above them — as in the triage,
 *  but off to start with. */
@Composable
private fun CategoryChooser(
    t: Txn, categories: List<Category>,
    onBack: () -> Unit,
    onPick: (String, Boolean, String?) -> Unit,
) {
    var keep by remember { mutableStateOf(false) }
    var pattern by remember { mutableStateOf(t.counterparty?.takeIf { it.isNotBlank() } ?: t.description?.take(30).orEmpty()) }
    var search by remember { mutableStateOf("") }
    val shown = categories
        .filter { search.isBlank() || it.label.contains(search.trim(), ignoreCase = true) || it.slug.contains(search.trim(), ignoreCase = true) }
        .sortedByDescending { it.transactions }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.txn_back)) }
            Text(stringResource(R.string.txn_categorise), style = MaterialTheme.typography.titleMedium)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.rule_remember), Modifier.weight(1f))
            Switch(checked = keep, onCheckedChange = { keep = it })
        }
        if (keep) {
            OutlinedTextField(pattern, { pattern = it }, singleLine = true,
                label = { Text(stringResource(R.string.rule_pattern)) },
                modifier = Modifier.fillMaxWidth())
            Text(stringResource(R.string.rule_pattern_note), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text(stringResource(R.string.txn_this_row_only), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedTextField(search, { search = it }, singleLine = true,
            label = { Text(stringResource(R.string.search_categories)) },
            modifier = Modifier.fillMaxWidth())
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
            items(shown, key = { it.slug }) { c ->
                ListItem(
                    leadingContent = {
                        Box(Modifier.size(12.dp).background(hexColour(c.colour) ?: MaterialTheme.colorScheme.outline, CircleShape))
                    },
                    headlineContent = { Text(c.label.ifBlank { c.slug }) },
                    supportingContent = { Text(groupLabel(c.group)) },
                    trailingContent = { if (c.slug == t.category) Text("✓") },
                    modifier = Modifier.clickable { onPick(c.slug, keep, if (keep) pattern else null) },
                )
            }
        }
    }
}

/** Description, counterparty, date and amount: what a bank gets wrong. */
@Composable
private fun EditForm(t: Txn, saving: Boolean, onBack: () -> Unit, onSave: (Map<String, Any?>) -> Unit) {
    val numbers = remember { NumberFormat.getNumberInstance(Locale.getDefault()).apply { isGroupingUsed = false; maximumFractionDigits = 2 } }
    var description by remember { mutableStateOf(t.description.orEmpty()) }
    var counterparty by remember { mutableStateOf(t.counterparty.orEmpty()) }
    var date by remember { mutableStateOf(t.date.take(10)) }
    var amount by remember { mutableStateOf(numbers.format(t.amount)) }
    var problem by remember { mutableStateOf<Int?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.txn_back)) }
            Text(stringResource(R.string.txn_edit), style = MaterialTheme.typography.titleMedium)
        }
        OutlinedTextField(description, { description = it },
            label = { Text(stringResource(R.string.txn_description)) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(counterparty, { counterparty = it }, singleLine = true,
            label = { Text(stringResource(R.string.txn_counterparty)) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(date, { date = it }, singleLine = true,
            label = { Text(stringResource(R.string.txn_date)) },
            placeholder = { Text(stringResource(R.string.txn_date_hint)) },
            modifier = Modifier.fillMaxWidth())
        OutlinedTextField(amount, { amount = it }, singleLine = true,
            label = { Text(stringResource(R.string.txn_amount)) },
            suffix = { t.currency?.let { Text(it) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth())
        problem?.let { Text(stringResource(it), color = Loss, style = MaterialTheme.typography.bodySmall) }
        Button(enabled = !saving, onClick = {
            val parsed = parseAmount(amount)
            val day = runCatching { LocalDate.parse(date.trim()) }.getOrNull()
            problem = when {
                parsed == null -> R.string.txn_amount_bad
                day == null -> R.string.txn_date_bad
                else -> null
            }
            if (parsed != null && day != null) {
                // Only what changed goes: a field sent unchanged is still
                // a correction on the dashboard's side.
                val fields = buildMap<String, Any?> {
                    if (description.trim() != t.description.orEmpty().trim()) put("description", description.trim())
                    if (counterparty.trim() != t.counterparty.orEmpty().trim()) put("counterparty", counterparty.trim())
                    if (day.toString() != t.date.take(10)) put("txn_date", day.toString())
                    if (kotlin.math.abs(parsed - t.amount) > 0.0001) put("amount", parsed)
                }
                onSave(fields)
            }
        }) {
            if (saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            else Text(stringResource(R.string.txn_save))
        }
    }
}

/** "-12,50", "−12.50", "1234" — the phone's own way or the other one. */
internal fun parseAmount(raw: String): Double? {
    val s = raw.trim().replace('−', '-').replace(" ", "").replace(" ", "")
    if (s.isEmpty()) return null
    val lastComma = s.lastIndexOf(','); val lastDot = s.lastIndexOf('.')
    val normal = when {
        lastComma > lastDot -> s.replace(".", "").replace(',', '.')
        else -> s.replace(",", "")
    }
    return normal.toDoubleOrNull()
}

@Composable
fun groupLabel(group: String?): String = when (group) {
    "spending" -> stringResource(R.string.group_spending)
    "income" -> stringResource(R.string.group_income)
    "non_spending" -> stringResource(R.string.group_non_spending)
    else -> group.orEmpty()
}
