package fr.smarthomeworld.wealth.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
    var picked by remember(values) { mutableStateOf<Int?>(null) }
    var width by remember { mutableStateOf(0) }

    fun indexAt(x: Float): Int {
        if (width <= 0) return 0
        val i = (x / width * (values.size - 1)).roundToInt()
        return i.coerceIn(0, values.lastIndex)
    }

    val touch = if (!scrubbable) Modifier else Modifier
        .pointerInput(values) {
            detectTapGestures(onPress = { at ->
                picked = indexAt(at.x)
                tryAwaitRelease()
                picked = null
            })
        }
        .pointerInput(values) {
            detectHorizontalDragGestures(
                onDragStart = { at -> picked = indexAt(at.x) },
                onDragEnd = { picked = null },
                onDragCancel = { picked = null },
                onHorizontalDrag = { change, _ ->
                    change.consume()
                    picked = indexAt(change.position.x)
                },
            )
        }

    Box(modifier.onSizeChanged { width = it.width }.then(touch)) {
        Canvas(Modifier.fillMaxSize()) {
            if (values.size < 2) return@Canvas
            val lo = values.min()
            val hi = values.max()
            val span = (hi - lo).takeIf { it > 0.0 } ?: 1.0
            val stepX = size.width / (values.size - 1)
            // A little air above and below, so the line never touches the edge.
            val top = size.height * 0.08f
            val usable = size.height * 0.84f

            fun pointAt(i: Int): Offset {
                val y = top + usable * (1f - ((values[i] - lo) / span).toFloat())
                return Offset(stepX * i, y)
            }

            drawLine(grid, Offset(0f, size.height), Offset(size.width, size.height), 1f)

            val path = Path().apply {
                moveTo(0f, pointAt(0).y)
                for (i in 1 until values.size) {
                    val p = pointAt(i)
                    lineTo(p.x, p.y)
                }
            }
            // The same path closed downwards, filled with a fading wash.
            val under = Path().apply {
                addPath(path)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
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
            } else {
                drawCircle(line, radius = 4.dp.toPx(), center = pointAt(values.lastIndex))
            }
        }

        val i = picked
        if (i != null) {
            // The label rides above the finger but stays inside the
            // chart, so the first and last days are readable too.
            var labelWidth by remember { mutableStateOf(0) }
            val x = if (values.size > 1) width * i / (values.size - 1) else 0
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
