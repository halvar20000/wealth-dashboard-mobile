package fr.smarthomeworld.wealth.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.smarthomeworld.wealth.data.TransactionPage

@Composable
fun TransactionsScreen(
    page: TransactionPage?,
    title: String,
    onSearch: (String) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it; onSearch(it) },
            label = { Text("Search $title") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        if (page == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
            }
            return@Column
        }
        Text(
            "${page.returned} of ${page.matched}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(page.transactions) { t ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text(t.description?.take(70) ?: t.kind.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                        Text(
                            listOfNotNull(Fmt.day(t.date), t.account, t.category).joinToString(" · "),
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
