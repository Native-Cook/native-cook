package com.ngocdevv.nativecook.blocks.modals.pitstopreview

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke

private const val LIGHT_TRAIL = 3
private const val LIGHT_TRAIL_STEP = 0.07

@Composable
internal fun PitStopReviewLights(
    snapshot: State<PitStopReviewRuntime.Snapshot>,
    slots: State<List<Offset>>,
    scale: Float,
    safeTop: Float,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val state = snapshot.value
        if (state.quiet || slots.value.size < 5) return@Canvas
        val scene = state.scene
        val unit = density * scale
        val gantry = PitStopReviewTheme.Gantry
        val heroTop = (safeTop + PitStopReviewTheme.Hero.top * scale) * density
        val lift = (1.0 - PitStopReviewMotion.panel(scene)) * PitStopReviewMotion.M.panelRise * unit
        repeat(5) { index ->
            val incoming = PitStopReviewMotion.flyIn(scene, index)
            val returning = PitStopReviewMotion.flyBack(scene, index)
            val flyingIn = incoming > 0.0 && incoming < 1.0
            val flyingBack = returning > 0.0 && returning < 1.0
            if (!flyingIn && !flyingBack) return@repeat
            val progress = if (flyingIn) incoming else 1.0 - returning
            val start = Offset(
                (gantry.centerX + (index - 2) * gantry.pitch) * unit,
                heroTop + gantry.centerY * unit,
            )
            val slot = slots.value[index]
            val end = Offset(slot.x, slot.y + lift.toFloat())
            val morph = PitStopReviewMotion.smooth((progress - 0.2) / 0.6)
            val radius = ((gantry.radius + (PitStopReviewTheme.Stars.radius - gantry.radius) * morph) * unit).toFloat()
            repeat(LIGHT_TRAIL + 1) { trailIndex ->
                val trailProgress = PitStopReviewMotion.clamp(
                    if (flyingIn) {
                        progress - trailIndex * LIGHT_TRAIL_STEP
                    } else {
                        progress + trailIndex * LIGHT_TRAIL_STEP
                    },
                )
                val point = pitStopReviewLightArc(start, end, index, trailProgress, unit)
                val trailRadius = radius * (1f - trailIndex * 0.18f)
                val alpha = if (trailIndex == 0) {
                    1f
                } else {
                    ((0.32f - trailIndex * 0.08f) * (1f - kotlin.math.abs(morph.toFloat() - 0.5f)))
                }
                val path = Path()
                repeat(20) { pointIndex ->
                    val shape = PitStopReviewMotion.morphPoint(
                        pointIndex,
                        morph,
                        point.x.toDouble(),
                        point.y.toDouble(),
                        trailRadius.toDouble(),
                    )
                    if (pointIndex == 0) {
                        path.moveTo(shape.first.toFloat(), shape.second.toFloat())
                    } else {
                        path.lineTo(shape.first.toFloat(), shape.second.toFloat())
                    }
                }
                path.close()
                val lampAlpha = alpha * (1f - morph.toFloat())
                if (lampAlpha > 0.001f) {
                    drawCircle(
                        Color(0xFFFF3B30).copy(alpha = lampAlpha * 0.42f),
                        trailRadius * 2.4f,
                        point,
                    )
                    drawPath(path, Color(0xFFFF3B30).copy(alpha = lampAlpha))
                    drawCircle(
                        Color(0xFFFFD2C2).copy(alpha = lampAlpha),
                        trailRadius * 0.34f,
                        point - Offset(trailRadius * 0.12f, trailRadius * 0.16f),
                    )
                }
                val starAlpha = alpha * morph.toFloat()
                if (trailIndex == 0 && starAlpha > 0.001f) {
                    drawPath(
                        path,
                        Color.White.copy(alpha = starAlpha),
                        style = Stroke(2f * unit, join = StrokeJoin.Round),
                    )
                }
            }
        }
    }
}

private fun pitStopReviewLightArc(
    start: Offset,
    end: Offset,
    index: Int,
    progress: Double,
    unit: Float,
): Offset {
    val control = Offset(
        (start.x + end.x) / 2f + (index - 2) * 16f * unit,
        start.y + (end.y - start.y) * 0.12f - 40f * unit,
    )
    val amount = progress.toFloat()
    val inverse = 1f - amount
    return start * (inverse * inverse) +
        control * (2f * inverse * amount) +
        end * (amount * amount)
}
