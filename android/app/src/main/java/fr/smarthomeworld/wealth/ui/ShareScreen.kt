package fr.smarthomeworld.wealth.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.smarthomeworld.wealth.data.Account
import fr.smarthomeworld.wealth.data.ImportReply
import fr.smarthomeworld.wealth.R

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
    /** Account id and the import ids to take back; answers with how
     *  many rows went, or why not. */
    onUndo: (Int, List<Int>, (Int?, String?) -> Unit) -> Unit = { _, _, done -> done(null, null) },
    onClose: () -> Unit,
) {
    var sending by remember { mutableStateOf(false) }
    var reply by remember { mutableStateOf<ImportReply?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var undoing by remember { mutableStateOf(false) }
    var undone by remember { mutableStateOf<Int?>(null) }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(if (reply == null) R.string.import_into else R.string.imported)) },
            actions = { TextButton(onClick = onClose) { Text(stringResource(if (reply == null) R.string.cancel else R.string.done)) } },
        )
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(16.dp),
               verticalArrangement = Arrangement.spacedBy(12.dp)) {

            Text(files.joinToString(", ").ifBlank { stringResource(R.string.nothing_shared) },
                 style = MaterialTheme.typography.bodyMedium,
                 color = MaterialTheme.colorScheme.onSurfaceVariant)

            when {
                !paired -> Text(stringResource(R.string.share_unpaired))
                files.isEmpty() -> Text(stringResource(R.string.share_no_file))
                error != null -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(error!!, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { error = null }) { Text(stringResource(R.string.share_try_another)) }
                }
                reply != null -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Report(reply!!)
                    val ids = reply!!.result?.imports.orEmpty()
                    val account = reply!!.account?.id
                    when {
                        undone != null -> Text(
                            plural(R.plurals.undone_rows, undone ?: 0),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        // An import that brought nothing has nothing to
                        // take back; saying so beats a button that does
                        // nothing.
                        ids.isNotEmpty() && account != null && (reply!!.result?.inserted ?: 0) > 0 ->
                            OutlinedButton(
                                onClick = {
                                    undoing = true
                                    onUndo(account, ids) { n, why ->
                                        undoing = false
                                        undone = n
                                        if (why != null) error = why
                                    }
                                },
                                enabled = !undoing,
                            ) {
                                Text(stringResource(if (undoing) R.string.undoing else R.string.undo_import))
                            }
                    }
                }
                sending -> Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                               horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text(stringResource(R.string.sending))
                }
                accounts.isEmpty() -> Text(stringResource(R.string.share_no_accounts))
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
        Text(r?.label ?: stringResource(R.string.read), fontWeight = FontWeight.SemiBold)
        Text(stringResource(R.string.import_counts, r?.inserted ?: 0, r?.duplicates ?: 0) +
             (reply.account?.name?.let { " · $it" } ?: ""))
        (r?.notes.orEmpty() + r?.problems.orEmpty()).take(4).forEach {
            Text(it, style = MaterialTheme.typography.bodySmall,
                 color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
