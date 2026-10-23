package com.ngocdevv.nativecook.blocks.cards.aurawalletwidget

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

// Grid-unit strokes keep the same 1.5-point weight at each display size.
@Composable
internal fun AuraIcon(pathData: String, side: Double, unit: Double, color: Color = Color.White,
    modifier: Modifier = Modifier, rounded: Boolean = true) {
    val path = remember(pathData) { PathParser().parsePathString(pathData).toPath() }
    val stroke = remember(side, rounded) {
        Stroke((AuraPalette.Actions.stroke * 24 / side).toFloat(), cap = StrokeCap.Round,
            join = if (rounded) StrokeJoin.Round else StrokeJoin.Miter)
    }
    Canvas(modifier.size((side * unit).dp)) {
        scale(size.width / 24f, size.height / 24f, Offset.Zero) { drawPath(path, color, style = stroke) }
    }
}
