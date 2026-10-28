package com.ngocdevv.nativecook.blocks.cards.trophysavingsmilestone

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale

/** Three 32-point reference discs with one-point white linework. */
@Composable
internal fun TrophySavingsMilestoneMetricIcon(kind: Int, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.drawWithCache {
        val path = Path().apply {
            when (kind) {
                0 -> {
                    addOval(Rect(10f, 10f, 22f, 22f))
                    addOval(Rect(12.5f, 12.5f, 19.5f, 19.5f))
                    moveTo(16f, 16f); lineTo(22.5f, 9.5f)
                    moveTo(19.5f, 9.5f); lineTo(22.5f, 9.5f); lineTo(22.5f, 12.5f)
                }
                1 -> {
                    addOval(Rect(10f, 10f, 22f, 22f))
                    moveTo(16f, 12.5f); lineTo(16f, 16.5f); lineTo(18.5f, 18f)
                }
                else -> {
                    moveTo(11f, 13f); lineTo(21f, 13f)
                    moveTo(18f, 10f); lineTo(21f, 13f); lineTo(18f, 16f)
                    moveTo(21f, 19f); lineTo(11f, 19f)
                    moveTo(14f, 16f); lineTo(11f, 19f); lineTo(14f, 22f)
                }
            }
        }
        val stroke = Stroke(1f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        onDrawBehind {
            scale(size.width / 32f, size.height / 32f, pivot = Offset.Zero) {
                drawCircle(color, 16f, Offset(16f, 16f))
                drawPath(path, Color.White, style = stroke)
            }
        }
    })
}
