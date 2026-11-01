package fr.smarthomeworld.wealth.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.smarthomeworld.wealth.TxnEditState
import fr.smarthomeworld.wealth.data.Category
import fr.smarthomeworld.wealth.data.TransactionPage
import fr.smarthomeworld.wealth.data.Txn
import fr.smarthomeworld.wealth.R

@Composable
fun TransactionsScreen(
    page: TransactionPage?,
    title: String,
    onSearch: (String) -> Unit,
    /** The catalogue, for labels and the choice on a row's page. */
    categories: List<Category> = emptyList(),
    edit: TxnEditState = TxnEditState(),
    onOpen: () -> Unit = {},
    onCategorise: (Txn, String, Boolean, String?) -> Unit = { _, _, _, _ -> },
    onUpdate: (Txn, Map<String, Any?>, () -> Unit) -> Unit = { _, _, _ -> },
) {
    var query by rememberSaveable { mutableStateOf("") }
    // The row whose page is open, by id: the list may be reloaded under
    // it, and the page should then show the row as it is now.
    var openId by rememberSaveable { mutableStateOf<Int?>(null) }
    val open = page?.transactions?.firstOrNull { it.id == openId }
    if (open != null) {
        TxnDetailSheet(
            txn = open, categories = categories, edit = edit,
            onDismiss = { openId = null },
            onCategorise = { slug, remember, pattern -> onCategorise(open, slug, remember, pattern) },
            onUpdate = { fields, done -> onUpdate(open, fields, done) },
        )
    }
    val labels = remember(categories) { categories.associate { it.slug to it.label } }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it; onSearch(it) },
            label = { Text(stringResource(R.string.search_in, title)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        if (page == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
            }
        } else {
        Text(
            stringResource(R.string.n_of_m, page.returned, page.matched),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(page.transactions, key = { it.id }) { t ->
                Row(Modifier.fillMaxWidth().clickable { openId = t.id; onOpen() },
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text(t.description?.take(70) ?: t.kind.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                        Text(
                            listOfNotNull(Fmt.day(t.date), t.account,
                                t.category?.let { labels[it] ?: it }).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        Fmt.signedMoney(t.amount, t.currency),
                        color = if (t.amount >= 0) Gain else Loss,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
        }
    }
}
