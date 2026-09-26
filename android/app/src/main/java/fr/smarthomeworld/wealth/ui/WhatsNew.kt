package fr.smarthomeworld.wealth.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.smarthomeworld.wealth.R

/** One `## x.y.z` of CHANGELOG.md and the lines under it. */
data class Release(val version: String, val notes: List<String>)

/** CHANGELOG.md, as the build packed it: the repository's own file,
 *  shared with iOS and TestFlight. Whatever stands before the first
 *  heading is for whoever edits the file, not for the app. */
fun readChangelog(context: Context): List<Release> {
    val text = runCatching { context.assets.open("CHANGELOG.md").bufferedReader().use { it.readText() } }
        .getOrNull() ?: return emptyList()
    val releases = mutableListOf<Release>()
    var version: String? = null
    var notes = mutableListOf<String>()
    for (line in text.lines()) {
        val trimmed = line.trim()
        if (trimmed.startsWith("## ")) {
            version?.let { releases += Release(it, notes) }
            version = trimmed.removePrefix("## ").trim()
            notes = mutableListOf()
        } else if (version != null && trimmed.isNotEmpty()) {
            notes += trimmed.removePrefix("- ").trim()
        }
    }
    version?.let { releases += Release(it, notes) }
    return releases
}

/** Every release, newest first, in a dialog over the settings. */
@Composable
fun WhatsNewDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val releases = remember { readChangelog(context) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
        title = { Text(stringResource(R.string.whats_new)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                releases.forEach { release ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(release.version, style = MaterialTheme.typography.titleSmall)
                        release.notes.forEach { note ->
                            Row {
                                Text("•  ", style = MaterialTheme.typography.bodyMedium)
                                Text(note, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        },
    )
}
