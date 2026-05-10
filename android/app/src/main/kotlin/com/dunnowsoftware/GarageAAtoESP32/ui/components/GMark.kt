package com.dunnowsoftware.GarageAAtoESP32.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.unit.Dp

/**
 * Simple ring + tongue mark used inside hero buttons / logo blocks.
 * Same shape language as the launcher icon (donut with a horizontal tongue
 * extending right) but rendered cleanly via even-odd fill so it scales.
 */
@Composable
fun GMark(size: Dp, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(size)) {
        val s = this.size.minDimension
        val u = s / 64f
        val cx = s / 2f
        val cy = s / 2f
        val outerR = 24f * u
        val innerR = 14f * u

        val ring = Path().apply {
            fillType = PathFillType.EvenOdd
            addOval(Rect(Offset(cx - outerR, cy - outerR), Offset(cx + outerR, cy + outerR)))
            addOval(Rect(Offset(cx - innerR, cy - innerR), Offset(cx + innerR, cy + innerR)))
        }
        drawPath(ring, color = color)

        // Tongue: connects center to outer-right edge, drawn over the ring/hole.
        val tongueH = 8f * u
        val tongue = Path().apply {
            moveTo(cx, cy - tongueH / 2f)
            lineTo(cx + outerR + 2f * u, cy - tongueH / 2f)
            lineTo(cx + outerR + 2f * u, cy + tongueH / 2f)
            lineTo(cx, cy + tongueH / 2f)
            close()
        }
        drawPath(tongue, color = color)
    }
}
