package com.ngocdevv.nativecook.blocks.gestures.pullleverrefresh

import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

private const val LeverCanvasBleed = 80f
private const val LeverCanvasHeight = 240f

private val LeverTheme = PullLeverRefreshTheme.Lever
private val LightTheme = PullLeverRefreshMotion.Light

/**
 * The lever art is intentionally one small set of canvas layers.
 * Values are read by DrawScope and graphicsLayer callbacks, which invalidates a
 * layer when the state clock advances without recomposing the surrounding screen.
 */
@Composable
internal fun PullLeverRefreshDrawing(
    state: PullLeverRefreshState,
    modifier: Modifier = Modifier,
) {
    val leverLength = (LeverTheme.length + LeverCanvasBleed * 2f).dp
    val canvasHeight = LeverCanvasHeight.dp
    Box(modifier = modifier.requiredWidth(leverLength).requiredHeight(canvasHeight)) {
        if (!PullLeverRefreshMotion.REPLICA_ONLY) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(
                            (LeverCanvasBleed + LeverTheme.thickness / 2f) / (LeverTheme.length + LeverCanvasBleed * 2f),
                            0.5f,
                        )
                        rotationZ = state.turn
                        translationX = LightTheme.shadow.x.dp.toPx()
                        translationY = LightTheme.shadow.y.dp.toPx()
                    },
            ) {
                drawShadowLever()
            }
        }
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (!PullLeverRefreshMotion.REPLICA_ONLY) {
                drawLoadingHalo(state)
                drawTickArc(state)
            }
        }
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(
                        (LeverCanvasBleed + LeverTheme.thickness / 2f) / (LeverTheme.length + LeverCanvasBleed * 2f),
                        0.5f,
                    )
                    rotationZ = state.turn
                },
        ) {
            drawLever(state)
        }
    }
}

private fun DrawScope.drawShadowLever() {
    val y = leverY()
    val thickness = LeverTheme.thickness.dp.toPx()
    val length = LeverTheme.length.dp.toPx()
    val radius = thickness / 2f
    drawShadowRoundRect(
        color = PullLeverRefreshTheme.Color.shadow.copy(alpha = LightTheme.shadow.opacity),
        topLeft = Offset(leverStartX(), y),
        size = androidx.compose.ui.geometry.Size(length, thickness),
        radius = radius,
        shadowColor = PullLeverRefreshTheme.Color.shadow.copy(alpha = LightTheme.shadow.opacity),
        shadowRadius = LightTheme.shadow.blur.dp.toPx(),
    )
    drawShadowSoftDisc(
        color = PullLeverRefreshTheme.Color.shadow.copy(alpha = LightTheme.shadow.opacity),
        radius = LeverTheme.knob.dp.toPx() / 2f,
        center = Offset(knobX(), y + radius),
        shadowColor = PullLeverRefreshTheme.Color.shadow.copy(alpha = LightTheme.shadow.opacity),
        shadowRadius = LightTheme.shadow.blur.dp.toPx(),
    )
}

private fun DrawScope.drawLoadingHalo(state: PullLeverRefreshState) {
    val breath = (state.breath + 1f) / 2f
    val opacity = state.glow * (LightTheme.haloMin + (LightTheme.haloMax - LightTheme.haloMin) * breath)
    if (opacity <= 0f) return
    val scale = (
        PullLeverRefreshTokens.Rendering.haloScaleBase +
            PullLeverRefreshTokens.Rendering.haloScaleGain * state.glow
        ) * (1f + PullLeverRefreshTokens.Rendering.haloBreathScale * breath)
    drawShadowCircle(
        color = PullLeverRefreshTheme.Color.fill.copy(alpha = opacity),
        radius = LightTheme.haloSize.dp.toPx() * scale / 2f,
        center = pivot(),
        shadowColor = PullLeverRefreshTheme.Color.fill.copy(alpha = opacity),
        shadowRadius = LightTheme.haloBlur.dp.toPx() * scale,
    )
}

private fun DrawScope.drawTickArc(state: PullLeverRefreshState) {
    val arc = LightTheme.arc
    val pivot = pivot()
    val radius = arc.radius.dp.toPx()
    val dotRadius = arc.size.dp.toPx() / 2f
    repeat(arc.dots) { index ->
        val degree = arc.from + (arc.to - arc.from) * index / (arc.dots - 1).toFloat()
        val radian = degree * PI.toFloat() / 180f
        val on = (state.arcLit - index).coerceIn(0f, 1f)
        val point = Offset(
            pivot.x + cos(radian) * radius,
            pivot.y + sin(radian) * radius,
        )
        scale(1f + PullLeverRefreshTokens.Rendering.arcActiveScale * on, point) {
            drawCircle(color = gammaLerp(PullLeverRefreshTheme.Color.arcOff, PullLeverRefreshTheme.Color.fill, on), radius = dotRadius, center = point)
        }
    }
}

private fun DrawScope.drawLever(state: PullLeverRefreshState) {
    val y = leverY()
    val thickness = LeverTheme.thickness.dp.toPx()
    val length = LeverTheme.length.dp.toPx()
    val radius = thickness / 2f
    val fillWidth = fillWidth(state.fill)
    drawRoundRect(
        color = PullLeverRefreshTheme.Color.track,
        topLeft = Offset(leverStartX(), y),
        size = androidx.compose.ui.geometry.Size(length, thickness),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius),
    )
    drawDots(PullLeverRefreshTheme.Color.trackDot)

    if (state.pulse >= 0f) {
        val shaftWidth = thickness / 2f + run() * state.pulse.coerceAtLeast(0f)
        drawRoundRect(
            brush = Brush.horizontalGradient(
                listOf(PullLeverRefreshTheme.Color.shaftLightStart, PullLeverRefreshTheme.Color.shaftLightEnd),
                startX = leverStartX(),
                endX = leverStartX() + shaftWidth,
            ),
            topLeft = Offset(leverStartX(), y),
            size = androidx.compose.ui.geometry.Size(shaftWidth, thickness),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius),
            alpha = LightTheme.shaftLight,
        )
    }

    val filledColor = gammaLerp(PullLeverRefreshTheme.Color.dimFill, PullLeverRefreshTheme.Color.fill, state.lit)
    val bloom = state.bloom
    scale(bloom, pivot()) {
        drawRoundRect(
            color = filledColor,
            topLeft = Offset(leverStartX(), y),
            size = androidx.compose.ui.geometry.Size(fillWidth, thickness),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius),
        )
        clipRect(left = leverStartX(), top = y, right = leverStartX() + fillWidth, bottom = y + thickness) {
            drawDots(PullLeverRefreshTheme.Color.fillDot)
        }
    }

    drawCircle(
        color = PullLeverRefreshTheme.Color.pivot,
        radius = LeverTheme.pivotDot.dp.toPx() / 2f,
        center = pivot(),
    )
    drawRipple(state)
    drawKnob(state)
    drawComet(state)
}

private fun DrawScope.drawDots(color: Color) {
    val y = leverY() + LeverTheme.thickness.dp.toPx() / 2f
    val first = leverStartX() + LeverTheme.thickness.dp.toPx() / 2f
    val pitch = LeverTheme.pitch.dp.toPx()
    val radius = LeverTheme.dot.dp.toPx() / 2f
    repeat(LeverTheme.dots) { index ->
        drawCircle(color = color, radius = radius, center = Offset(first + pitch * (index + 1), y))
    }
}

private fun DrawScope.drawRipple(state: PullLeverRefreshState) {
    val progress = state.ripple
    if (progress <= 0f || progress >= 1f) return
    val knobRadius = LeverTheme.knob.dp.toPx() / 2f
    val endRadius = LightTheme.ripple.dp.toPx() / 2f
    val scale = 1f + (endRadius / knobRadius - 1f) * progress
    val center = Offset(knobX(), leverY() + LeverTheme.thickness.dp.toPx() / 2f)
    drawCircle(
        color = PullLeverRefreshTheme.Color.fill.copy(
            alpha = PullLeverRefreshTokens.Rendering.rippleOpacity * (1f - progress),
        ),
        // A View border is drawn inside its original 33 dp knob before the view scales.
        radius = (knobRadius - PullLeverRefreshTokens.Rendering.rippleStroke.dp.toPx() / 2f) * scale,
        center = center,
        style = androidx.compose.ui.graphics.drawscope.Stroke(
            width = PullLeverRefreshTokens.Rendering.rippleStroke.dp.toPx() * scale,
        ),
    )
}

private fun DrawScope.drawKnob(state: PullLeverRefreshState) {
    val knobRadius = LeverTheme.knob.dp.toPx() / 2f
    val center = Offset(knobX(), leverY() + LeverTheme.thickness.dp.toPx() / 2f)
    drawSoftDisc(
        color = PullLeverRefreshTheme.Color.finger,
        radius = knobRadius,
        center = center,
    )
    if (state.knobLit > 0f) {
        drawSoftDisc(
            color = PullLeverRefreshTheme.Color.knobLit.copy(alpha = state.knobLit),
            radius = knobRadius,
            center = center,
        )
    }
    if (PullLeverRefreshMotion.REPLICA_ONLY) return
    val highlightOpacity = (
        kotlin.math.abs(state.turn - LightTheme.restAngle) / LightTheme.highlightRamp
        ).coerceIn(0f, 1f)
    if (highlightOpacity <= 0f) return
    val radian = (LightTheme.direction - state.turn) * PI.toFloat() / 180f
    val offset = LeverTheme.knob.dp.toPx() * LightTheme.highlightOffset
    drawShadowCircle(
        color = PullLeverRefreshTheme.Color.specular.copy(
            alpha = PullLeverRefreshTheme.Color.specular.alpha * highlightOpacity,
        ),
        radius = LeverTheme.knob.dp.toPx() * LightTheme.highlightSize / 2f,
        center = Offset(center.x + cos(radian) * offset, center.y + sin(radian) * offset),
        shadowColor = PullLeverRefreshTheme.Color.fill.copy(
            alpha = PullLeverRefreshTokens.Rendering.highlightShadowOpacity * highlightOpacity,
        ),
        shadowRadius = PullLeverRefreshTokens.Rendering.highlightShadowBlur.dp.toPx(),
    )
}

private fun DrawScope.drawComet(state: PullLeverRefreshState) {
    val pulse = state.pulse
    if (pulse < 0f) return
    val y = leverY() + LeverTheme.thickness.dp.toPx() / 2f
    val headRadius = LightTheme.cometHead.dp.toPx() / 2f
    repeat(LightTheme.cometTrail) { trailIndex ->
        val k = trailIndex + 1
        val point = pulse - (k * LightTheme.cometTrailPt.dp.toPx()) / LightTheme.cometTrail / run()
        if (point >= 0f) {
            val trailScale = 1f - k / (LightTheme.cometTrail + 2f)
            val trailOpacity = 1f - k / (LightTheme.cometTrail + 1f)
            drawShadowCircle(
                color = PullLeverRefreshTheme.Color.fill.copy(alpha = trailOpacity),
                radius = headRadius * trailScale,
                center = Offset(pivot().x + run() * point, y),
                shadowColor = PullLeverRefreshTheme.Color.fill.copy(
                    alpha = PullLeverRefreshTokens.Rendering.trailShadowOpacity * trailOpacity,
                ),
                shadowRadius = PullLeverRefreshTokens.Rendering.trailShadowBlur.dp.toPx() * trailScale,
            )
        }
    }
    drawShadowCircle(
        color = PullLeverRefreshTheme.Color.fill,
        radius = headRadius,
        center = Offset(pivot().x + run() * pulse.coerceAtLeast(0f), y),
        shadowColor = PullLeverRefreshTheme.Color.fill,
        shadowRadius = LightTheme.cometBlur.dp.toPx(),
    )
}

private fun DrawScope.fillWidth(fill: Float): Float {
    val thickness = LeverTheme.thickness.dp.toPx()
    if (fill >= 1f) return LeverTheme.length.dp.toPx()
    val dots = kotlin.math.floor(
        max(fill, 0f) * LeverTheme.dots + PullLeverRefreshTokens.Rendering.quantizationEpsilon,
    )
    return thickness + dots * LeverTheme.pitch.dp.toPx()
}

private fun DrawScope.leverY() = (size.height - LeverTheme.thickness.dp.toPx()) / 2f

private fun DrawScope.leverStartX() = (size.width - LeverTheme.length.dp.toPx()) / 2f

private fun DrawScope.pivot() = Offset(leverStartX() + LeverTheme.thickness.dp.toPx() / 2f, size.height / 2f)

private fun DrawScope.knobX() = leverStartX() + LeverTheme.length.dp.toPx() - LeverTheme.thickness.dp.toPx() / 2f

private fun DrawScope.run() = knobX() - pivot().x

private fun DrawScope.drawSoftDisc(
    color: Color,
    radius: Float,
    center: Offset,
) {
    val softEdge = LightTheme.knobSoft.dp.toPx()
    val outerRadius = radius + softEdge
    val solidStop = ((radius - softEdge) / outerRadius).coerceIn(0f, 1f)
    drawCircle(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                solidStop to color,
                1f to color.copy(alpha = 0f),
            ),
            center = center,
            radius = outerRadius,
        ),
        radius = outerRadius,
        center = center,
    )
}

private fun DrawScope.drawShadowSoftDisc(
    color: Color,
    radius: Float,
    center: Offset,
    shadowColor: Color,
    shadowRadius: Float,
) {
    val softEdge = LightTheme.knobSoft.dp.toPx()
    val outerRadius = radius + softEdge
    val solidStop = ((radius - softEdge) / outerRadius).coerceIn(0f, 1f)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawCircle(
                center.x,
                center.y,
                outerRadius,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = RadialGradient(
                        center.x,
                        center.y,
                        outerRadius,
                        intArrayOf(color.toArgb(), color.copy(alpha = 0f).toArgb()),
                        floatArrayOf(solidStop, 1f),
                        Shader.TileMode.CLAMP,
                    )
                    setShadowLayer(shadowRadius, 0f, 0f, shadowColor.toArgb())
                },
            )
        }
    } else {
        repeat(6) { layer ->
            val spread = shadowRadius * (layer + 1) / 6f
            val alpha = shadowColor.alpha * (1f - layer / 6f) * 0.12f
            drawCircle(
                color = shadowColor.copy(alpha = alpha),
                radius = outerRadius + spread,
                center = center,
            )
        }
        drawSoftDisc(
            color = color,
            radius = radius,
            center = center,
        )
    }
}

private fun DrawScope.drawShadowCircle(
    color: Color,
    radius: Float,
    center: Offset,
    shadowColor: Color,
    shadowRadius: Float,
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawCircle(
                center.x,
                center.y,
                radius,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.color = color.toArgb()
                    setShadowLayer(shadowRadius, 0f, 0f, shadowColor.toArgb())
                },
            )
        }
    } else {
        // API 26–27 cannot render a hardware shadow layer reliably, so layered discs
        // preserve the same soft falloff without promoting the whole screen to software.
        repeat(6) { layer ->
            val spread = shadowRadius * (layer + 1) / 6f
            val alpha = shadowColor.alpha * (1f - layer / 6f) * 0.12f
            drawCircle(shadowColor.copy(alpha = alpha), radius + spread, center)
        }
        drawCircle(color, radius, center)
    }
}

private fun DrawScope.drawShadowRoundRect(
    color: Color,
    topLeft: Offset,
    size: androidx.compose.ui.geometry.Size,
    radius: Float,
    shadowColor: Color,
    shadowRadius: Float,
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawRoundRect(
                RectF(topLeft.x, topLeft.y, topLeft.x + size.width, topLeft.y + size.height),
                radius,
                radius,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.color = color.toArgb()
                    setShadowLayer(shadowRadius, 0f, 0f, shadowColor.toArgb())
                },
            )
        }
    } else {
        repeat(6) { layer ->
            val spread = shadowRadius * (layer + 1) / 6f
            val alpha = shadowColor.alpha * (1f - layer / 6f) * 0.12f
            drawRoundRect(
                color = shadowColor.copy(alpha = alpha),
                topLeft = Offset(topLeft.x - spread, topLeft.y - spread),
                size = androidx.compose.ui.geometry.Size(size.width + spread * 2f, size.height + spread * 2f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius + spread, radius + spread),
            )
        }
        drawRoundRect(color, topLeft, size, androidx.compose.ui.geometry.CornerRadius(radius, radius))
    }
}

/** Reanimated interpolateColor uses gamma 2.2 by default for these colour transitions. */
private fun gammaLerp(start: Color, end: Color, progress: Float): Color {
    val t = progress.coerceIn(0f, 1f)
    fun channel(a: Float, b: Float): Float = ((a.toDouble().pow(2.2) + (b.toDouble().pow(2.2) - a.toDouble().pow(2.2)) * t).pow(1.0 / 2.2)).toFloat()
    return Color(
        red = channel(start.red, end.red),
        green = channel(start.green, end.green),
        blue = channel(start.blue, end.blue),
        alpha = start.alpha + (end.alpha - start.alpha) * t,
    )
}
