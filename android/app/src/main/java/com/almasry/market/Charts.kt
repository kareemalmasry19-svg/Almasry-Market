package com.almasry.market

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke

/** Small trend line for list rows. Time runs left → right. */
@Composable
fun Sparkline(values: List<Double>, modifier: Modifier = Modifier) {
    if (values.size < 2) return
    val color = changeColor(values.last() - values.first())
    Canvas(modifier) {
        val min = values.min()
        val max = values.max()
        val span = (max - min).takeIf { it > 0 } ?: 1.0
        val step = size.width / (values.size - 1)
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = i * step
            val y = size.height - ((v - min) / span * size.height).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(width = 2f * density, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/**
 * Larger chart for the detail screen: filled price line plus optional
 * horizontal support/resistance guides.
 */
@Composable
fun PriceChart(
    values: List<Double>,
    modifier: Modifier = Modifier,
    support: Double? = null,
    resistance: Double? = null,
) {
    if (values.size < 2) return
    val color = changeColor(values.last() - values.first())
    Canvas(modifier) {
        val guides = listOfNotNull(support, resistance)
        val min = (values + guides).min()
        val max = (values + guides).max()
        val span = (max - min).takeIf { it > 0 } ?: 1.0
        val pad = size.height * 0.06f
        val h = size.height - pad * 2
        fun y(v: Double) = pad + h - ((v - min) / span * h).toFloat()
        val step = size.width / (values.size - 1)

        val line = Path()
        values.forEachIndexed { i, v -> if (i == 0) line.moveTo(0f, y(v)) else line.lineTo(i * step, y(v)) }
        val fill = Path().apply {
            addPath(line)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.35f), Color.Transparent)))
        drawPath(line, color, style = Stroke(width = 2.5f * density, cap = StrokeCap.Round, join = StrokeJoin.Round))

        val dash = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
        support?.let {
            drawLine(Up.copy(alpha = 0.6f), Offset(0f, y(it)), Offset(size.width, y(it)), 1.5f * density, pathEffect = dash)
        }
        resistance?.let {
            drawLine(Down.copy(alpha = 0.6f), Offset(0f, y(it)), Offset(size.width, y(it)), 1.5f * density, pathEffect = dash)
        }
    }
}
