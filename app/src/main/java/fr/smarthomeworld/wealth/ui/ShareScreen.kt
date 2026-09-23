package fr.smarthomeworld.wealth.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.smarthomeworld.wealth.data.Account
import fr.smarthomeworld.wealth.data.ImportReply

/**
 * Which account does this statement belong to? — and then what the
 * dashboard made of it. Three states, no more: pick, sending, done.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareScreen(
    paired: Boolean,
    accounts: List<Account>,
    files: List<String>,
    onSend: (Account, (ImportReply?, String?) -> Unit) -> Unit,
    onClose: () -> Unit,
) {
    var sending by remember { mutableStateOf(false) }
    var reply by remember { mutableStateOf<ImportReply?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(if (reply == null) "Import into…" else "Imported") },
            actions = { TextButton(onClick = onClose) { Text(if (reply == null) "Cancel" else "Done") } },
        )
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(16.dp),
               verticalArrangement = Arrangement.spacedBy(12.dp)) {

            Text(files.joinToString(", ").ifBlank { "Nothing was shared." },
                 style = MaterialTheme.typography.bodyMedium,
                 color = MaterialTheme.colorScheme.onSurfaceVariant)

            when {
                !paired -> Text("Pair this phone with your dashboard first — open the app and " +
                                "enter the six-digit code from Settings → Assistants.")
                files.isEmpty() -> Text("The app that shared this sent no file.")
                error != null -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(error!!, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { error = null }) { Text("Try another account") }
                }
                reply != null -> Report(reply!!)
                sending -> Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                               horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("Sending to the dashboard…")
                }
                accounts.isEmpty() -> Text("No accounts yet — open the app once so it knows them.")
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(accounts) { a ->
                        Card(onClick = {
                            sending = true
                            onSend(a) { ok, why ->
                                sending = false
                                reply = ok
                                error = why
                            }
                        }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp)) {
                                Text(a.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                                Text(listOfNotNull(a.type, a.bank).joinToString(" · ").ifBlank { "—" },
                                     style = MaterialTheme.typography.bodySmall,
                                     color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

/**
 * What came back, in the dashboard's own words. A file that brought
 * nothing new is not a failure — a statement overlapping the last one
 * is the normal case — so the count is stated plainly and the server's
 * notes (a scan, rows kept out) are repeated underneath.
 */
@Composable
private fun Report(reply: ImportReply) {
    val r = reply.result
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(r?.label ?: "Read", fontWeight = FontWeight.SemiBold)
        Text("${r?.inserted ?: 0} new, ${r?.duplicates ?: 0} already there" +
             (reply.account?.name?.let { " · $it" } ?: ""))
        (r?.notes.orEmpty() + r?.problems.orEmpty()).take(4).forEach {
            Text(it, style = MaterialTheme.typography.bodySmall,
                 color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
