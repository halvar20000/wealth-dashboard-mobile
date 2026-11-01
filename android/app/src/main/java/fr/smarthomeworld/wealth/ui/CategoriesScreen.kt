package fr.smarthomeworld.wealth.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.smarthomeworld.wealth.CategoriesState
import fr.smarthomeworld.wealth.R
import fr.smarthomeworld.wealth.data.Category

/** The colours offered for a category — the dashboard's own range, so a
 *  category made on the phone sits well beside the built-in ones. */
val CategoryPalette = listOf(
    "#f87171", "#fb923c", "#fbbf24", "#a3e635", "#34d399", "#2dd4bf",
    "#38bdf8", "#5b9dff", "#818cf8", "#a78bfa", "#e879f9", "#f472b6",
    "#94a3b8", "#64748b",
)

private val GROUPS = listOf("spending", "income", "non_spending")

/**
 * The categories the dashboard files rows under — make one, rename it,
 * give it another colour, say what it counts as, or remove it. Each
 * change goes straight to the dashboard: this is its list, not a copy.
 */
@Composable
fun CategoriesScreen(
    state: CategoriesState,
    onSave: (slug: String?, label: String, colour: String, group: String?, onDone: () -> Unit) -> Unit,
    onDelete: (slug: String, onDone: (Int) -> Unit) -> Unit,
    onDismissError: () -> Unit,
    onRefresh: () -> Unit,
) {
    // null: nothing open; a blank slug: a new one.
    var editing by remember { mutableStateOf<Category?>(null) }
    editing?.let { c ->
        CategoryDialog(
            category = c, saving = state.saving, error = state.error,
            onDismiss = { editing = null; onDismissError() },
            onSave = { label, colour, group ->
                onSave(c.slug.ifBlank { null }, label, colour, group) { editing = null }
            },
            onDelete = { onDelete(c.slug) { editing = null } },
        )
    }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
            item {
                Text(stringResource(R.string.categories_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp))
            }
            if (state.error != null && editing == null) {
                item {
                    Text(state.error, color = Loss, style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
            if (state.loading && state.list.isEmpty()) {
                item { Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) { CircularProgressIndicator() } }
            }
            GROUPS.forEach { g ->
                val rows = state.list.filter { (it.group ?: "spending") == g }
                if (rows.isNotEmpty()) {
                    item(key = "h-$g") {
                        Text(groupLabel(g), style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp))
                    }
                    items(rows, key = { it.slug }) { c ->
                        ListItem(
                            leadingContent = {
                                Box(Modifier.size(14.dp).background(hexColour(c.colour) ?: MaterialTheme.colorScheme.outline, CircleShape))
                            },
                            headlineContent = { Text(c.label.ifBlank { c.slug }) },
                            supportingContent = { Text(stringResource(R.string.category_rows, c.transactions)) },
                            modifier = Modifier.clickable { editing = c },
                        )
                    }
                }
            }
            if (!state.loading && state.list.isEmpty()) {
                item {
                    OutlinedButton(onClick = onRefresh, modifier = Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.fetch_again))
                    }
                }
            }
        }
        if (!state.tooOld) {
            ExtendedFloatingActionButton(
                onClick = { editing = Category(slug = "", label = "", group = "spending", colour = CategoryPalette[9]) },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text(stringResource(R.string.category_new)) },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            )
        }
    }
}

@Composable
private fun CategoryDialog(
    category: Category,
    saving: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (String, String, String?) -> Unit,
    onDelete: () -> Unit,
) {
    val isNew = category.slug.isBlank()
    var label by remember(category.slug) { mutableStateOf(category.label) }
    var colour by remember(category.slug) { mutableStateOf(category.colour?.lowercase() ?: CategoryPalette[9]) }
    var group by remember(category.slug) { mutableStateOf(category.group ?: "spending") }
    var confirming by remember(category.slug) { mutableStateOf(false) }

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringResource(R.string.category_delete_confirm, category.label)) },
            text = { Text(stringResource(R.string.category_delete_note, category.transactions, category.rules)) },
            confirmButton = {
                TextButton(onClick = { confirming = false; onDelete() }) {
                    Text(stringResource(R.string.category_delete), color = Loss)
                }
            },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (isNew) R.string.category_new else R.string.category_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(label, { label = it.take(40) }, singleLine = true,
                    label = { Text(stringResource(R.string.category_name)) },
                    modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.category_colour), style = MaterialTheme.typography.labelMedium)
                // Seven to a row: fourteen swatches, two rows, no scrolling.
                CategoryPalette.chunked(7).forEach { line ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        line.forEach { hex ->
                            val chosen = hex == colour
                            Box(Modifier.size(28.dp)
                                .background(hexColour(hex)!!, CircleShape)
                                .then(if (chosen) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                                .clickable { colour = hex })
                        }
                    }
                }
                Text(stringResource(R.string.category_group), style = MaterialTheme.typography.labelMedium)
                if (category.groupLocked) {
                    Text(groupLabel(group) + " — " + stringResource(R.string.group_fixed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    GROUPS.forEach { g ->
                        Row(Modifier.fillMaxWidth().clickable { group = g },
                            verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = group == g, onClick = { group = g })
                            Text(groupLabel(g), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                if (!isNew) {
                    if (category.deletable) {
                        TextButton(onClick = { confirming = true }, enabled = !saving) {
                            Text(stringResource(R.string.category_delete), color = Loss)
                        }
                    } else {
                        Text(stringResource(R.string.category_protected),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (error != null) Text(error, color = Loss, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(label, colour, if (category.groupLocked) null else group) },
                enabled = !saving && label.isNotBlank(),
            ) {
                if (saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text(stringResource(R.string.txn_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
