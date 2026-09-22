package fr.smarthomeworld.wealth.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.smarthomeworld.wealth.data.Account

@Composable
fun AccountsScreen(accounts: List<Account>, base: String, onOpen: (Account) -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(accounts) { a ->
            Card(onClick = { onOpen(a) }, modifier = Modifier.fillMaxWidth()) {
                Row(
                    Modifier.padding(14.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(a.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                        Text(
                            listOfNotNull(a.type, a.bank).joinToString(" · ").ifBlank { "—" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                        Text(Fmt.money(a.balance, a.currency, decimals = true),
                            fontWeight = FontWeight.SemiBold)
                        if (a.currency != null && a.currency != base && a.balanceBase != null) {
                            Text(Fmt.money(a.balanceBase, base),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}
