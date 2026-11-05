package fr.smarthomeworld.wealth.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.TextButton
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.res.stringResource
import fr.smarthomeworld.wealth.R
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Two charts, drawn by hand.
 *
 * A charting library would be four megabytes and a theme of its own for
 * a line and a ring; Canvas draws both in fifty lines and inherits the
 * app's colours. No axes, no gridlines, no legend inside the picture:
 * the numbers that matter are written next to it as text, where a
 * screen reader can find them.
 */

/** The eight colours a share is drawn in, in order. */
val Slices = listOf(
    Color(0xFF4F86F7), Color(0xFF34D399), Color(0xFFF59E0B), Color(0xFFF87171),
    Color(0xFFA78BFA), Color(0xFF22D3EE), Color(0xFFFB7185), Color(0xFF94A3B8),
)

/**
 * The line of a value over time. Rising or falling decides the colour,
 * because the first thing anybody asks of this chart is the direction.
 *
 * A finger on the line picks the nearest day: a thin rule marks it and
 * a label above says when and how much. Sliding sideways walks through
 * time; lifting the finger puts the chart back as it was. Sideways
 * only, so a vertical swipe still scrolls the page the chart sits in.
 *
 * Two fingers zoom into time, and only time: spreading them shows fewer
 * days across the width, moving them together walks the window along.
 * The height follows the days in view, so a quiet week is not a flat
 * line. A button in the corner puts the whole window back, and so does
 * new data.
 */
@Composable
fun LineChart(
    values: List<Double>,
    modifier: Modifier = Modifier.fillMaxWidth().height(170.dp),
    /** One ISO date per value, for the label; without them the chart
     *  still draws but a touch shows nothing. */
    dates: List<String> = emptyList(),
    currency: String = "EUR",
) {
    val up = values.size < 2 || values.last() >= values.first()
    val line = if (up) Gain else Loss
    val grid = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)
    val scrubbable = values.size > 1 && dates.size == values.size
    val last = (values.size - 1).coerceAtLeast(1).toFloat()
    var picked by remember(values) { mutableStateOf<Int?>(null) }
    var width by remember { mutableStateOf(0) }
    // The window in view, in fractional indices: the whole line at first.
    var from by remember(values) { mutableStateOf(0f) }
    var to by remember(values) { mutableStateOf(last) }
    val zoomed = from > 0.001f || to < last - 0.001f
    // The narrowest window: a few days, or all of them when there are few.
    val narrowest = minOf(last, 3f)

    fun xOf(i: Float): Float = if (width <= 0) 0f else (i - from) / (to - from) * width

    fun indexAt(x: Float): Int {
        if (width <= 0) return 0
        val i = (from + x / width * (to - from)).roundToInt()
        return i.coerceIn(from.toInt().coerceAtLeast(0),
            kotlin.math.ceil(to).toInt().coerceAtMost(values.lastIndex))
    }

    val touch = if (values.size < 2) Modifier else Modifier.pointerInput(values) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (scrubbable) picked = indexAt(down.position.x)
            var pinching = false
            var sideways = false
            var dx = 0f
            var dy = 0f
            while (true) {
                val event = awaitPointerEvent()
                val pressed = event.changes.filter { it.pressed }
                if (pressed.isEmpty()) break
                if (pressed.size >= 2) {
                    pinching = true
                    picked = null
                    val a = pressed[0]
                    val b = pressed[1]
                    val span = kotlin.math.abs(a.position.x - b.position.x).coerceAtLeast(1f)
                    val before = kotlin.math.abs(a.previousPosition.x - b.previousPosition.x).coerceAtLeast(1f)
                    val centre = (a.position.x + b.position.x) / 2
                    val centreBefore = (a.previousPosition.x + b.previousPosition.x) / 2
                    if (width > 0) {
                        val w = to - from
                        val newW = (w * before / span).coerceIn(narrowest, last)
                        // The day under the fingers stays under the fingers.
                        val held = from + centreBefore / width * w
                        val newFrom = (held - centre / width * newW).coerceIn(0f, last - newW)
                        from = newFrom
                        to = newFrom + newW
                    }
                    event.changes.forEach { it.consume() }
                } else if (!pinching) {
                    val c = pressed[0]
                    if (c.isConsumed) { picked = null; break }   // the page took it: a scroll
                    val d = c.positionChange()
                    dx += d.x; dy += d.y
                    if (!sideways && kotlin.math.abs(dy) > viewConfiguration.touchSlop &&
                        kotlin.math.abs(dy) > kotlin.math.abs(dx)) {
                        picked = null; break                     // vertical: let the page scroll
                    }
                    if (kotlin.math.abs(dx) > viewConfiguration.touchSlop) sideways = true
                    if (sideways && scrubbable) {
                        c.consume()
                        picked = indexAt(c.position.x)
                    }
                }
            }
            picked = null
        }
    }

    Box(modifier.onSizeChanged { width = it.width }.then(touch)) {
        Canvas(Modifier.fillMaxSize().clipToBounds()) {
            if (values.size < 2) return@Canvas
            val first = from.toInt().coerceAtLeast(0)
            val end = kotlin.math.ceil(to).toInt().coerceAtMost(values.lastIndex)
            val shown = values.subList(first, end + 1)
            val lo = shown.min()
            val hi = shown.max()
            val span = (hi - lo).takeIf { it > 0.0 } ?: 1.0
            // A little air above and below, so the line never touches the edge.
            val top = size.height * 0.08f
            val usable = size.height * 0.84f

            fun pointAt(i: Int): Offset {
                val y = top + usable * (1f - ((values[i] - lo) / span).toFloat())
                return Offset((i - from) / (to - from) * size.width, y)
            }

            drawLine(grid, Offset(0f, size.height), Offset(size.width, size.height), 1f)

            val path = Path().apply {
                moveTo(pointAt(first).x, pointAt(first).y)
                for (i in first + 1..end) {
                    val p = pointAt(i)
                    lineTo(p.x, p.y)
                }
            }
            // The same path closed downwards, filled with a fading wash.
            val under = Path().apply {
                addPath(path)
                lineTo(pointAt(end).x, size.height)
                lineTo(pointAt(first).x, size.height)
                close()
            }
            drawPath(under, Brush.verticalGradient(
                listOf(line.copy(alpha = 0.28f), Color.Transparent)))
            drawPath(path, line, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))

            val i = picked
            if (i != null) {
                val p = pointAt(i)
                drawLine(line.copy(alpha = 0.5f), Offset(p.x, 0f), Offset(p.x, size.height),
                    1.dp.toPx())
                drawCircle(line, radius = 5.dp.toPx(), center = p)
            } else if (end == values.lastIndex) {
                drawCircle(line, radius = 4.dp.toPx(), center = pointAt(values.lastIndex))
            }
        }

        val i = picked
        if (i != null) {
            // The label rides above the finger but stays inside the
            // chart, so the first and last days are readable too.
            var labelWidth by remember { mutableStateOf(0) }
            val x = xOf(i.toFloat()).roundToInt()
            val left = (x - labelWidth / 2).coerceIn(0, (width - labelWidth).coerceAtLeast(0))
            Surface(
                Modifier
                    .offset { IntOffset(left, 0) }
                    .onSizeChanged { labelWidth = it.width },
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shadowElevation = 2.dp,
            ) {
                Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Text(Fmt.day(dates[i]), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(Fmt.money(values[i], currency), style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold)
                }
            }
        } else if (zoomed) {
            // The days in view, and the way back to all of them.
            Row(Modifier.align(Alignment.TopEnd), verticalAlignment = Alignment.CenterVertically) {
                if (scrubbable) {
                    val a = dates[from.roundToInt().coerceIn(0, values.lastIndex)]
                    val b = dates[to.roundToInt().coerceIn(0, values.lastIndex)]
                    Text("${Fmt.day(a)} – ${Fmt.day(b)}", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { from = 0f; to = last }) {
                    Text(stringResource(R.string.zoom_reset), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

/**
 * The ring: one arc per share, biggest first, in the order the colours
 * are listed so that the legend beside it can use the same index.
 */
@Composable
fun DonutChart(
    shares: List<Double>,
    modifier: Modifier = Modifier.size(150.dp),
    centre: @Composable () -> Unit = {},
) {
    val track = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val total = shares.sum().takeIf { it > 0 } ?: 1.0
            val width = 18.dp.toPx()
            val inset = width / 2
            val box = Size(size.width - width, size.height - width)
            drawArc(track, 0f, 360f, useCenter = false,
                topLeft = Offset(inset, inset), size = box,
                style = Stroke(width = width))
            var start = -90f
            shares.forEachIndexed { i, share ->
                val sweep = (share / total * 360.0).toFloat()
                drawArc(
                    Slices[i % Slices.size], start, sweep - 1.5f, useCenter = false,
                    topLeft = Offset(inset, inset), size = box,
                    style = Stroke(width = width, cap = StrokeCap.Butt))
                start += sweep
            }
        }
        centre()
    }
}
