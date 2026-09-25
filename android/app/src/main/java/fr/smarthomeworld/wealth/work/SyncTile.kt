package fr.smarthomeworld.wealth.work

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import fr.smarthomeworld.wealth.data.Repo
import fr.smarthomeworld.wealth.data.Store
import fr.smarthomeworld.wealth.ui.Fmt
import fr.smarthomeworld.wealth.widget.refreshWidgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import fr.smarthomeworld.wealth.R

/**
 * *Sync now* from the pull-down shade — for the moment after you have
 * moved money and want the figure to agree with the bank.
 *
 * It does what the app's refresh does and nothing else: fetch, keep,
 * redraw the widget. The tile says when it last managed it, because a
 * tile that only ever says "Sync" tells you nothing about whether it
 * worked.
 */
class SyncTile : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onStartListening() {
        super.onStartListening()
        paint(null)
    }

    override fun onClick() {
        super.onClick()
        val store = Store(this)
        if (!store.paired) { paint(getString(R.string.tile_unpaired)); return }
        paint(getString(R.string.tile_syncing))
        scope.launch {
            val ok = runCatching { Repo(store).refresh() }.isSuccess
            refreshWidgets(this@SyncTile)
            paint(if (ok) null else getString(R.string.tile_no_answer))
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun paint(saying: String?) {
        val tile = qsTile ?: return
        val at = Store(this).cached()?.second ?: 0L
        tile.state = Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_label)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = saying ?: if (at > 0) Fmt.since(this, at) else getString(R.string.never)
        }
        tile.updateTile()
    }
}
