package fr.smarthomeworld.wealth

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import fr.smarthomeworld.wealth.data.Api
import fr.smarthomeworld.wealth.data.ImportReply
import fr.smarthomeworld.wealth.data.Repo
import fr.smarthomeworld.wealth.data.Store
import fr.smarthomeworld.wealth.ui.ShareScreen
import fr.smarthomeworld.wealth.ui.WealthTheme
import kotlinx.coroutines.launch

/**
 * The share sheet: a statement from the bank's own app, or from a file
 * manager, straight into an account.
 *
 * The phone hands over a content URI that is readable for exactly as
 * long as this activity lives, so the bytes are read here and now
 * rather than remembered as a link — and a statement is a few hundred
 * kilobytes, which is nothing to hold.
 *
 * The dashboard does the reading. This screen only asks which account
 * the file belongs to and repeats what came back, including the
 * sentences the server says when a file is a scan or when rows were
 * kept out: the app inventing its own wording would be a second place
 * to keep those explanations right.
 */
class ShareActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = Store(this)
        val repo = Repo(store)
        val uris = incoming(intent)

        setContent {
            WealthTheme {
                ShareScreen(
                    paired = store.paired,
                    accounts = repo.accounts(),
                    files = uris.map { nameOf(it) },
                    onSend = { account, onDone ->
                        lifecycleScope.launch {
                            val result = runCatching {
                                repo.importFiles(account.id, uris.mapNotNull { read(it) })
                            }
                            onDone(result.getOrNull(), result.exceptionOrNull()?.message)
                        }
                    },
                    onUndo = { accountId, importIds, onDone ->
                        lifecycleScope.launch {
                            val result = runCatching { repo.undoImport(accountId, importIds) }
                            onDone(result.getOrNull(), result.exceptionOrNull()?.message)
                        }
                    },
                    onClose = { finish() },
                )
            }
        }
    }

    /** One file or several, whichever the sending app offered — and
     *  the one a browser or the Files app "opens with" us, which is a
     *  VIEW carrying the document in `data` rather than a share. */
    private fun incoming(intent: Intent?): List<Uri> = when (intent?.action) {
        Intent.ACTION_VIEW -> listOfNotNull(intent.data)
        Intent.ACTION_SEND -> listOfNotNull(
            if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            else @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_STREAM))
        Intent.ACTION_SEND_MULTIPLE ->
            (if (Build.VERSION.SDK_INT >= 33) intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
             else @Suppress("DEPRECATION") intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)).orEmpty()
        else -> emptyList()
    }

    /** The name the sending app gave the file, or a made-up one — the
     *  dashboard reads the content, not the name, but a report that
     *  says which file it means is worth the two lines. */
    private fun nameOf(uri: Uri): String {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (i >= 0 && c.moveToFirst()) c.getString(i)?.let { return it }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "statement"
    }

    private fun read(uri: Uri): Api.Upload? = runCatching {
        contentResolver.openInputStream(uri)?.use { stream ->
            Api.Upload(nameOf(uri), contentResolver.getType(uri), stream.readBytes())
        }
    }.getOrNull()
}
