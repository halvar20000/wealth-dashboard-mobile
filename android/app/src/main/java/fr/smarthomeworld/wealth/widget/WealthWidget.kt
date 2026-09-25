package fr.smarthomeworld.wealth.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import fr.smarthomeworld.wealth.MainActivity
import fr.smarthomeworld.wealth.data.Repo
import fr.smarthomeworld.wealth.data.Snapshot
import fr.smarthomeworld.wealth.data.Store
import fr.smarthomeworld.wealth.ui.Fmt
import fr.smarthomeworld.wealth.R

/**
 * The figure on the home screen.
 *
 * It draws the **cached** snapshot and nothing else: a widget that
 * waits for a network is a widget that shows a spinner all morning.
 * The cache is the one the app already keeps, so the number here and
 * the number in the app are the same number, with the day it was read
 * printed under it. Tapping the figure opens the app; tapping the line
 * underneath asks the dashboard for a fresh one.
 */
class WealthWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val cached = Store(context).cached()
        provideContent {
            GlanceTheme {
                Body(cached?.first, cached?.second ?: 0L)
            }
        }
    }
}

@Composable
private fun Body(snap: Snapshot?, at: Long) {
    val context = LocalContext.current
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(16.dp)
            .padding(16.dp)
            .clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            context.getString(R.string.net_worth),
            style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onSurfaceVariant),
        )
        Text(
            Fmt.money(snap?.netWorth?.total, snap?.baseCurrency),
            style = TextStyle(
                fontSize = 26.sp, fontWeight = FontWeight.Bold,
                color = GlanceTheme.colors.onSurface),
        )
        Spacer(GlanceModifier.height(6.dp))
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            val today = snap?.performance?.get("1d")?.twr
            val month = snap?.performance?.get("1m")?.twr
            Text(
                listOfNotNull(
                    today?.let { context.getString(R.string.widget_today, Fmt.percent(it)) },
                    month?.let { context.getString(R.string.widget_30d, Fmt.percent(it)) },
                ).joinToString(" · ").ifBlank { context.getString(R.string.widget_no_figures) },
                style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onSurfaceVariant),
            )
        }
        val queue = (snap?.waiting?.uncategorised ?: 0) + (snap?.waiting?.unassignedSpending ?: 0)
        if (queue > 0) {
            Spacer(GlanceModifier.height(6.dp))
            Text(
                context.resources.getQuantityString(R.plurals.widget_waiting, queue, queue),
                style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.primary),
            )
        }
        Spacer(GlanceModifier.height(8.dp))
        Text(
            if (at > 0) context.getString(R.string.widget_as_of, Fmt.since(context, at))
            else context.getString(R.string.widget_tap_open),
            style = TextStyle(fontSize = 11.sp, color = GlanceTheme.colors.onSurfaceVariant),
            modifier = GlanceModifier.clickable(actionRunCallback<RefreshWidget>()),
        )
    }
}

/** Ask the dashboard, keep what came back, redraw. A refresh that
 *  fails leaves the figures alone — the same rule as in the app. */
class RefreshWidget : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        runCatching { Repo(Store(context)).refresh() }
        WealthWidget().update(context, glanceId)
    }
}

class WealthWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WealthWidget()
}

/** Called after the app itself has refreshed, so the home screen does
 *  not keep yesterday's figure while the app shows today's. */
suspend fun refreshWidgets(context: Context) {
    runCatching { WealthWidget().updateAll(context) }
}
