package fr.smarthomeworld.wealth.work

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import fr.smarthomeworld.wealth.MainActivity
import fr.smarthomeworld.wealth.R
import fr.smarthomeworld.wealth.data.Repo
import fr.smarthomeworld.wealth.data.Snapshot
import fr.smarthomeworld.wealth.data.Store
import fr.smarthomeworld.wealth.ui.Fmt
import fr.smarthomeworld.wealth.widget.refreshWidgets
import java.util.concurrent.TimeUnit

/**
 * The background round: every few hours, when there is a network, send
 * what the thumb decided on the train, fetch the figures, redraw the
 * home screen — and say something only when there is something to say.
 *
 * It does not push. Your dashboard is on your own network and this app
 * has no server in the middle; a poll every three hours is what that
 * buys, and it costs one request. What it will not do is tell you the
 * same thing twice: the last notice is remembered, and a notice only
 * goes out when it has changed.
 */
class Watcher(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val store = Store(applicationContext)
        if (!store.paired) return Result.success()
        val repo = Repo(store)
        runCatching { repo.flush() }
        val loaded = runCatching { repo.refresh() }.getOrElse { return Result.retry() }
        refreshWidgets(applicationContext)
        if (store.watch) notice(applicationContext, store, loaded.snapshot)
        return Result.success()
    }

    companion object {
        private const val NAME = "wealth-watch"

        /** On: a round every three hours, only with a network. Off: no
         *  work at all — a switch that leaves a worker behind is a lie. */
        fun schedule(context: Context, on: Boolean) {
            val work = WorkManager.getInstance(context)
            if (!on) { work.cancelUniqueWork(NAME); return }
            val request = PeriodicWorkRequestBuilder<Watcher>(3, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            work.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}

private const val CHANNEL = "wealth-watch"

/**
 * What is worth a buzz, in the order it matters: money about to run
 * out, then a bank link that has stopped working, then a queue that
 * has grown. Anything smaller belongs on the screen, not on the
 * lock screen.
 */
fun notice(context: Context, store: Store, snap: Snapshot) {
    val below = snap.upcoming?.belowZero
    val red = snap.sync.red
    val queue = snap.waiting.uncategorised + snap.waiting.unassignedSpending

    val (key, title, text) = when {
        below != null -> Triple(
            "below:${below.date}",
            context.getString(R.string.notice_low_title,
                below.account ?: context.getString(R.string.notice_an_account), Fmt.day(below.date)),
            context.getString(R.string.notice_low_text,
                below.name, Fmt.money(below.running, snap.baseCurrency)))
        red > 0 -> Triple(
            "red:$red",
            context.resources.getQuantityString(R.plurals.notice_red_title, red, red),
            context.getString(R.string.notice_red_text))
        queue >= 10 -> Triple(
            "queue:${queue / 10}",
            context.resources.getQuantityString(R.plurals.notice_queue_title, queue, queue),
            context.getString(R.string.notice_queue_text))
        else -> return
    }
    if (store.lastNotice == key) return

    val manager = NotificationManagerCompat.from(context)
    manager.createNotificationChannel(
        NotificationChannelCompat.Builder(CHANNEL, NotificationManager.IMPORTANCE_DEFAULT)
            .setName(context.getString(R.string.channel_name))
            .setDescription(context.getString(R.string.channel_description))
            .build())
    val open = PendingIntent.getActivity(
        context, 0, Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE)
    val built = NotificationCompat.Builder(context, CHANNEL)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(title)
        .setContentText(text)
        .setStyle(NotificationCompat.BigTextStyle().bigText(text))
        .setContentIntent(open)
        .setAutoCancel(true)
        .build()
    // Android 13 asks for the permission; without it, nothing is posted
    // and nothing is remembered, so the notice survives to the next round.
    if (!manager.areNotificationsEnabled()) return
    runCatching { manager.notify(1, built) }.onSuccess { store.lastNotice = key }
}
