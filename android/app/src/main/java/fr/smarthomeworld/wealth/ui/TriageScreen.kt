package fr.smarthomeworld.wealth.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.smarthomeworld.wealth.data.Category
import fr.smarthomeworld.wealth.data.Person
import fr.smarthomeworld.wealth.data.Waiting1
import fr.smarthomeworld.wealth.R

/**
 * The queue as a stack of cards, one thumb.
 *
 * A browser does this badly: a table of two hundred rows with a
 * dropdown on each is a thing nobody finishes. A card at a time, with
 * the app's own guess on the right and "leave it" on the left, is a
 * thing somebody finishes on a train — which is why the verdicts are
 * written down before they are sent and go out when there is a
 * network. See Repo.decide().
 *
 * Right: take the guess (or, with no guess, open the sheet).
 * Left:  skip — the row stays in the queue, it is simply not this one.
 * Tap:   the sheet, to choose something else.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TriageScreen(
    owning: Boolean,
    rows: List<Waiting1>,
    remaining: Int,
    categories: List<Category>,
    people: List<Person>,
    waiting: Int,
    loading: Boolean,
    error: String?,
    onDecide: (Waiting1, String?, String?, String?, Boolean) -> Unit,  // row, category, owner, pattern, remember
    onUndo: () -> Unit,
    onRefresh: () -> Unit,
) {
    var index by remember(rows) { mutableStateOf(0) }
    var sheetFor by remember { mutableStateOf<Waiting1?>(null) }
    val row = rows.getOrNull(index)

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(if (owning) R.string.triage_ask_whose else R.string.triage_ask_what),
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                listOfNotNull(
                    stringResource(R.string.triage_left, (remaining - index).coerceAtLeast(0)),
                    if (waiting > 0) stringResource(R.string.triage_to_send, waiting) else null,
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(12.dp))

        when {
            loading && rows.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator()
            }
            error != null && rows.isEmpty() -> Empty(error, onRefresh)
            row == null -> Empty(
                stringResource(if (remaining > 0) R.string.triage_fetched_all
                               else R.string.triage_empty), onRefresh)
            else -> {
                SwipeCard(
                    row = row,
                    guess = if (owning) null else row.suggestion,
                    guessLabel = categories.firstOrNull { it.slug == row.suggestion }?.label,
                    onRight = {
                        val guess = row.suggestion
                        if (!owning && guess != null) {
                            onDecide(row, guess, null, row.pattern, true); index++
                        } else sheetFor = row
                    },
                    onLeft = { index++ },
                    onTap = { sheetFor = row },
                )
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { index++ }) { Text(stringResource(R.string.triage_skip)) }
                    TextButton(onClick = onUndo, enabled = waiting > 0) {
                        Icon(Icons.Default.Undo, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.triage_undo))
                    }
                    Button(onClick = { sheetFor = row }) {
                        Icon(Icons.Default.Check, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.triage_choose))
                    }
                }
            }
        }
    }

    sheetFor?.let { pick ->
        ModalBottomSheet(onDismissRequest = { sheetFor = null }) {
            // The rule is decided here, above the choice, because the
            // words a rule remembers are what makes it right or wrong
            // for the next hundred rows — and they were only ever
            // shown, never editable.
            var pattern by remember(pick.id) { mutableStateOf(pick.pattern.orEmpty()) }
            var makeRule by remember(pick.id) { mutableStateOf(true) }
            RuleEditor(pattern, makeRule, { pattern = it }, { makeRule = it })
            if (owning) {
                PeopleSheet(people) { owner ->
                    onDecide(pick, null, owner, pattern.trim().ifBlank { null }, makeRule)
                    sheetFor = null; index++
                }
            } else {
                CategorySheet(categories, pick.suggestion) { slug ->
                    onDecide(pick, slug, null, pattern.trim().ifBlank { null }, makeRule)
                    sheetFor = null; index++
                }
            }
        }
    }
}

/** What the rule will remember, before it is made. Off means this one
 *  row only — the correction that must not become a habit. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RuleEditor(
    pattern: String,
    enabled: Boolean,
    onPattern: (String) -> Unit,
    onRemember: (Boolean) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.rule_remember), style = MaterialTheme.typography.bodyMedium)
            Switch(checked = enabled, onCheckedChange = onRemember)
        }
        AnimatedVisibility(enabled) {
            OutlinedTextField(
                value = pattern,
                onValueChange = onPattern,
                label = { Text(stringResource(R.string.rule_pattern)) },
                supportingText = {
                    Text(stringResource(R.string.rule_pattern_note))
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(8.dp))
        HorizontalDivider()
    }
}

@Composable
private fun Empty(text: String, onRefresh: () -> Unit) {
    Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) {
        Text(text, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onRefresh) { Text(stringResource(R.string.fetch_again)) }
    }
}

/** One row, and a thumb. The card follows the finger and tints towards
 *  what letting go would do. */
@Composable
private fun SwipeCard(
    row: Waiting1,
    guess: String?,
    guessLabel: String?,
    onRight: () -> Unit,
    onLeft: () -> Unit,
    onTap: () -> Unit,
) {
    var offset by remember(row.id) { mutableStateOf(0f) }
    val x by animateFloatAsState(offset, label = "swipe")
    val decided = 260f

    val tint = when {
        x > 40 -> Color(0x3334D399)
        x < -40 -> Color(0x33F87171)
        else -> Color.Transparent
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                translationX = x
                rotationZ = (x / 60f).coerceIn(-6f, 6f)
            }
            .pointerInput(row.id) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        when {
                            offset > decided -> { offset = 0f; onRight() }
                            offset < -decided -> { offset = 0f; onLeft() }
                            else -> offset = 0f
                        }
                    },
                    onHorizontalDrag = { _, delta -> offset += delta },
                )
            },
    ) {
        Column(Modifier.background(tint).padding(20.dp).fillMaxWidth()) {
            Text(Fmt.signedMoney(row.amount, row.currency),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = if (row.amount < 0) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Text(row.description?.take(90) ?: row.counterparty ?: "—",
                style = MaterialTheme.typography.bodyLarge)
            row.counterparty?.takeIf { it.isNotBlank() && it != row.description }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(10.dp))
            Text(listOfNotNull(Fmt.day(row.date), row.accountName).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (guess != null) {
                Spacer(Modifier.height(14.dp))
                AssistChip(onClick = onRight, label = {
                    Text(stringResource(R.string.swipe_right, guessLabel ?: guess))
                })
                row.pattern?.takeIf { it.isNotBlank() }?.let {
                    Text(stringResource(R.string.and_remember, it), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Spacer(Modifier.height(14.dp))
                AssistChip(onClick = onTap, label = { Text(stringResource(R.string.tap_to_choose)) })
            }
        }
    }
}

/** The categories, the ones actually in use first. */
@Composable
private fun CategorySheet(categories: List<Category>, suggestion: String?, onPick: (String) -> Unit) {
    val sorted = remember(categories, suggestion) {
        categories.sortedWith(
            compareByDescending<Category> { it.slug == suggestion }
                .thenByDescending { it.transactions })
    }
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp),
        contentPadding = PaddingValues(bottom = 24.dp)) {
        items(sorted) { c ->
            ListItem(
                headlineContent = { Text(c.label) },
                supportingContent = {
                    Text(listOfNotNull(
                        c.group, if (c.transactions > 0) plural(R.plurals.rows, c.transactions) else null,
                    ).joinToString(" · "))
                },
                trailingContent = if (c.slug == suggestion) ({ Text(stringResource(R.string.guess)) }) else null,
                modifier = Modifier.clickable { onPick(c.slug) },
            )
        }
    }
}

/** The household, plus "shared" — halved between them by the dashboard. */
@Composable
private fun PeopleSheet(people: List<Person>, onPick: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        people.forEach { p ->
            ListItem(headlineContent = { Text(p.name) },
                modifier = Modifier.clickable { onPick(p.id.toString()) })
        }
        ListItem(
            headlineContent = { Text(stringResource(R.string.shared)) },
            supportingContent = { Text(stringResource(R.string.shared_note)) },
            modifier = Modifier.clickable { onPick("shared") },
        )
    }
}
