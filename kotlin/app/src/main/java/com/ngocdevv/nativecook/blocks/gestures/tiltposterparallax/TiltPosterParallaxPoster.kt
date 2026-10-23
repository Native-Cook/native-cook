package com.ngocdevv.nativecook.blocks.gestures.tiltposterparallax

import android.graphics.BlendMode
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

@Composable
internal fun BoxScope.TiltPosterParallaxPoster(
    engine: TiltPosterParallaxEngine,
    art: TiltPosterParallaxArt,
    typography: TiltPosterParallaxTypography,
    cardWidth: Dp,
    cardHeight: Dp,
    left: Dp,
    top: Dp,
    reduceMotion: Boolean,
) {
    val density = LocalDensity.current.density
    val view = LocalView.current
    Box(
        modifier = Modifier
            .offset(x = left, y = top)
            .size(cardWidth, cardHeight),
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    val float = if (reduceMotion) 0.0 else {
                        (1.0 - cos(engine.clockMs / Tokens.FLOAT.periodMs * PI * 2.0)) / 2.0 * Tokens.FLOAT.amplitude
                    }
                    val flipRise = if (reduceMotion) 1.0 else 1.0 + sin(engine.flip * PI) * Tokens.FLIP.rise
                    transformOrigin = TransformOrigin.Center
                    translationY = (-float * density).toFloat()
                    scaleX = (engine.lift * flipRise).toFloat()
                    scaleY = scaleX
                    clip = false
                },
        ) {
            TiltPosterParallaxPosterGlow(engine)
            TiltPosterParallaxPosterShadow(engine, reduceMotion)
            TiltPosterParallaxPosterEdges(engine, cardWidth, cardHeight, reduceMotion)
            TiltPosterParallaxPosterFront(engine, art, typography, cardWidth, cardHeight, reduceMotion)
            TiltPosterParallaxPosterBack(engine, typography, reduceMotion)
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .semantics {
                    role = Role.Image
                    contentDescription = Tokens.COPY.posterLabel
                    onClick(label = "Turn over") {
                        engine.toggleFlip()
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        true
                    }
                }
                .pointerInput(engine, density) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val tracker = VelocityTracker()
                        tracker.addPointerInputChange(down)
                        engine.realTouchDown()
                        engine.beginHold(
                            x = (down.position.x / density).toDouble(),
                            y = (down.position.y / density).toDouble(),
                            eventTimeMs = down.uptimeMillis.toDouble(),
                            scripted = false,
                        )
                        var lastTime = down.uptimeMillis.toDouble()
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                tracker.addPointerInputChange(change)
                                lastTime = change.uptimeMillis.toDouble()
                                if (change.changedToUp() || !change.pressed) break
                                if (change.positionChanged()) {
                                    val edge = engine.moveHold(
                                        x = (change.position.x / density).toDouble(),
                                        y = (change.position.y / density).toDouble(),
                                    )
                                    if (edge) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                }
                                change.consume()
                            }
                        } finally {
                            val velocity = tracker.calculateVelocity()
                            val tapped = engine.endHold(
                                velocityX = (velocity.x / density).toDouble(),
                                velocityY = (velocity.y / density).toDouble(),
                                eventTimeMs = lastTime,
                            )
                            if (tapped) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            engine.realTouchUp()
                        }
                    }
                },
        )
    }
}

@Composable
private fun BoxScope.TiltPosterParallaxPosterGlow(engine: TiltPosterParallaxEngine) {
    TiltPosterParallaxSoftField(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                val liftAlpha = ((engine.lift - 1f) / (Tokens.TILT.lift - 1.0)).toFloat()
                val deadZone = 2.0 * PI / 180.0
                val tiltAlpha = max(0.0, abs(engine.tiltX) + abs(engine.tiltY) - deadZone) * 1.2
                translationX = (engine.tiltX * 40f * density)
                translationY = ((30f + engine.tiltY * 30f) * density)
                alpha = min(1f, liftAlpha * 0.6f + tiltAlpha.toFloat())
            },
        color = Tokens.COLOR.glow,
        blur = Tokens.MATERIAL.glowBlur.dp,
        insetLeft = 0.08f,
        insetTop = 0.20f,
        widthRatio = 0.84f,
        heightRatio = 0.78f,
    )
}

@Composable
private fun BoxScope.TiltPosterParallaxPosterShadow(
    engine: TiltPosterParallaxEngine,
    reduceMotion: Boolean,
) {
    TiltPosterParallaxSoftField(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                val turn = if (reduceMotion) 0f else engine.flip * PI.toFloat()
                translationX = -engine.tiltX * 60f * density
                translationY = (22f - engine.tiltY * 40f) * density
                scaleX = 0.9f * max(0.3f, abs(cos(turn)))
                scaleY = 0.92f
                alpha = 0.6f + (engine.lift - 1f) * 2f
            },
        color = Tokens.COLOR.shadow,
        blur = Tokens.MATERIAL.shadowBlur.dp,
        insetLeft = 0f,
        insetTop = 0f,
        widthRatio = 1f,
        heightRatio = 1f,
    )
}

@Composable
private fun TiltPosterParallaxSoftField(
    modifier: Modifier,
    color: Color,
    blur: Dp,
    insetLeft: Float,
    insetTop: Float,
    widthRatio: Float,
    heightRatio: Float,
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Canvas(modifier = modifier.blur(blur, BlurredEdgeTreatment.Unbounded)) {
            drawRoundRect(
                color = color,
                topLeft = Offset(size.width * insetLeft, size.height * insetTop),
                size = Size(size.width * widthRatio, size.height * heightRatio),
                cornerRadius = CornerRadius(Tokens.LAYOUT.cardRadius.dp.toPx()),
            )
        }
    } else {
        Canvas(modifier = modifier) {
            repeat(7) { index ->
                val progress = index / 6f
                val spread = blur.toPx() * progress * 0.6f
                drawRoundRect(
                    color = color.copy(alpha = color.alpha * (1f - progress) * 0.24f),
                    topLeft = Offset(size.width * insetLeft - spread, size.height * insetTop - spread),
                    size = Size(size.width * widthRatio + spread * 2f, size.height * heightRatio + spread * 2f),
                    cornerRadius = CornerRadius(Tokens.LAYOUT.cardRadius.dp.toPx() + spread),
                )
            }
        }
    }
}

@Composable
private fun BoxScope.TiltPosterParallaxPosterFront(
    engine: TiltPosterParallaxEngine,
    art: TiltPosterParallaxArt,
    typography: TiltPosterParallaxTypography,
    cardWidth: Dp,
    cardHeight: Dp,
    reduceMotion: Boolean,
) {
    val density = LocalDensity.current.density
    Box(
        modifier = Modifier
            .matchParentSize()
            .graphicsLayer {
                val angle = engine.tiltX + if (reduceMotion) 0f else engine.flip * PI.toFloat()
                cameraDistance = tiltPosterParallaxCameraDistance(density)
                rotationX = Math.toDegrees(-engine.tiltY.toDouble()).toFloat()
                rotationY = Math.toDegrees(angle.toDouble()).toFloat()
                alpha = if (reduceMotion) 1f - engine.flip else if (cos(angle) >= 0f) 1f else 0f
            }
            .clip(RoundedCornerShape(Tokens.LAYOUT.cardRadius.dp)),
    ) {
        art.layers.forEachIndexed { index, layer ->
            Image(
                bitmap = layer,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(
                        width = cardWidth * TiltPosterParallaxPosterOverscan[index],
                        height = cardHeight * TiltPosterParallaxPosterOverscan[index],
                    )
                    .graphicsLayer {
                        translationX = (engine.tiltX * Tokens.PARALLAX.layers[index] * density).toFloat()
                        translationY = (engine.tiltY * Tokens.PARALLAX.layers[index] * density).toFloat()
                    },
            )
        }
        TiltPosterParallaxPosterTitle(engine, typography)
        TiltPosterParallaxPosterCredit(typography)
        TiltPosterParallaxPosterGlare(engine)
        Canvas(modifier = Modifier.matchParentSize()) {
            drawRoundRect(
                color = Tokens.COLOR.rim,
                cornerRadius = CornerRadius(Tokens.LAYOUT.cardRadius.dp.toPx()),
                style = Stroke(Tokens.MATERIAL.rimWidth.dp.toPx()),
            )
        }
    }
}

private val TiltPosterParallaxPosterOverscan = listOf(1.24f, 1.20f, 1.18f, 1.14f, 1.12f)

@Composable
private fun TiltPosterParallaxPosterTitle(
    engine: TiltPosterParallaxEngine,
    typography: TiltPosterParallaxTypography,
) {
    val density = LocalDensity.current.density
    Canvas(modifier = Modifier.fillMaxSize()) {
        val canvas = drawContext.canvas.nativeCanvas
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = typography.serifTypeface
            textSize = Tokens.TYPE.posterTitle.toFloat() * density
        }
        val lineWidths = Tokens.COPY.posterTitle.map(paint::measureText)
        val titleWidth = lineWidths.maxOrNull() ?: 0f
        val titleY = size.height * Tokens.LAYOUT.titleY.toFloat()
        val shiftX = engine.tiltX * Tokens.PARALLAX.title.toFloat() * density
        val shiftY = engine.tiltY * Tokens.PARALLAX.title.toFloat() * density
        val holoAt = (
            engine.tiltX * Tokens.HOLO.travelX +
                engine.tiltY * Tokens.HOLO.travelY +
                engine.flip * Tokens.HOLO.flipTravel
            ).toFloat() * titleWidth
        val holoAlpha = (
            Tokens.HOLO.rest + min(
                Tokens.HOLO.max - Tokens.HOLO.rest,
                (abs(engine.tiltX) + abs(engine.tiltY)) * Tokens.HOLO.gain,
            )
            ).toFloat()
        val foilAt = (0.5 + (engine.tiltX + engine.tiltY) / 2.0 * Tokens.PARALLAX.foil).toFloat()
        canvas.save()
        canvas.translate(shiftX, shiftY)
        Tokens.COPY.posterTitle.forEachIndexed { index, line ->
            val x = (size.width - lineWidths[index]) / 2f
            val y = titleY + index * Tokens.TYPE.posterLine.toFloat() * density
            paint.shader = null
            paint.color = Tokens.COLOR.embossDark.toArgb()
            paint.alpha = 255
            canvas.drawText(line, x + (1.2f - engine.tiltX * 4f) * density, y + (1.8f - engine.tiltY * 4f) * density, paint)
            paint.color = Tokens.COLOR.embossLight.toArgb()
            canvas.drawText(line, x + (-0.6f + engine.tiltX * 2f) * density, y + (-0.8f + engine.tiltY * 2f) * density, paint)
            paint.shader = LinearGradient(
                x, y - 46f * density, x + lineWidths[index], y + 8f * density,
                Tokens.COLOR.gold.map { it.toArgb() }.toIntArray(), null, Shader.TileMode.CLAMP,
            )
            paint.color = Color.White.toArgb()
            canvas.drawText(line, x, y, paint)
            paint.shader = LinearGradient(
                (size.width - titleWidth) / 2f + holoAt,
                titleY - Tokens.TYPE.posterTitle.toFloat() * density,
                (size.width - titleWidth) / 2f + holoAt + titleWidth * Tokens.HOLO.period.toFloat(),
                titleY + Tokens.TYPE.posterTitle.toFloat() * density,
                Tokens.COLOR.holo.map { it.toArgb() }.toIntArray(), null, Shader.TileMode.MIRROR,
            )
            paint.alpha = (holoAlpha * 255f).toInt().coerceIn(0, 255)
            tiltPosterParallaxSetOverlay(paint)
            canvas.drawText(line, x, y, paint)
            paint.alpha = 255
            paint.shader = LinearGradient(
                (size.width - titleWidth) / 2f + titleWidth * foilAt - 46f * density,
                titleY - 50f * density,
                (size.width - titleWidth) / 2f + titleWidth * foilAt + 46f * density,
                titleY + 50f * density,
                intArrayOf(Tokens.COLOR.foilClear.toArgb(), Tokens.COLOR.foil.toArgb(), Tokens.COLOR.foilClear.toArgb()),
                null,
                Shader.TileMode.CLAMP,
            )
            tiltPosterParallaxSetScreen(paint)
            canvas.drawText(line, x, y, paint)
            tiltPosterParallaxResetBlend(paint)
        }
        canvas.restore()
    }
}

@Composable
private fun TiltPosterParallaxPosterCredit(typography: TiltPosterParallaxTypography) {
    val density = LocalDensity.current.density
    Canvas(modifier = Modifier.fillMaxSize()) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = typography.mediumTypeface
            textSize = Tokens.TYPE.credit.toFloat() * density
            color = Tokens.COLOR.posterInk.toArgb()
        }
        val width = paint.measureText(Tokens.COPY.posterCredit)
        drawContext.canvas.nativeCanvas.drawText(
            Tokens.COPY.posterCredit,
            (size.width - width) / 2f,
            size.height - 18f * density,
            paint,
        )
    }
}

@Composable
private fun TiltPosterParallaxPosterGlare(engine: TiltPosterParallaxEngine) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val center = Offset(
            size.width * (0.5f + engine.tiltX * Tokens.PARALLAX.glare.toFloat()),
            size.height * (0.3f + engine.tiltY * Tokens.PARALLAX.glare.toFloat()),
        )
        val radius = size.width * 0.9f
        val opacity = 0.1f + min(0.3f, (abs(engine.tiltX) + abs(engine.tiltY)) * 0.9f)
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Tokens.COLOR.glare.copy(alpha = Tokens.COLOR.glare.alpha * opacity), Tokens.COLOR.glareClear),
                center,
                radius,
            ),
            radius = radius,
            center = center,
        )
    }
}

@Composable
private fun BoxScope.TiltPosterParallaxPosterBack(
    engine: TiltPosterParallaxEngine,
    typography: TiltPosterParallaxTypography,
    reduceMotion: Boolean,
) {
    val density = LocalDensity.current.density
    Box(
        modifier = Modifier
            .matchParentSize()
            .graphicsLayer {
                val angle = engine.tiltX + if (reduceMotion) 0f else engine.flip * PI.toFloat()
                cameraDistance = tiltPosterParallaxCameraDistance(density)
                rotationX = Math.toDegrees(-engine.tiltY.toDouble()).toFloat()
                rotationY = Math.toDegrees(angle.toDouble()).toFloat() + if (reduceMotion) 0f else 180f
                alpha = if (reduceMotion) engine.flip else if (cos(angle) < 0f) 1f else 0f
            }
            .clip(RoundedCornerShape(Tokens.LAYOUT.cardRadius.dp))
            .background(Brush.verticalGradient(Tokens.COLOR.back)),
    ) {
        TiltPosterParallaxPosterBackCopy(typography)
        TiltPosterParallaxPosterGlare(engine)
        Canvas(modifier = Modifier.matchParentSize()) {
            drawRoundRect(
                color = Tokens.COLOR.rim,
                cornerRadius = CornerRadius(Tokens.LAYOUT.cardRadius.dp.toPx()),
                style = Stroke(Tokens.MATERIAL.rimWidth.dp.toPx()),
            )
        }
    }
}

@Composable
private fun TiltPosterParallaxPosterBackCopy(typography: TiltPosterParallaxTypography) {
    val density = LocalDensity.current.density
    Canvas(modifier = Modifier.fillMaxSize()) {
        val canvas = drawContext.canvas.nativeCanvas
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = typography.serifTypeface
            textSize = Tokens.TYPE.backTitle.toFloat() * density
        }
        val titleWidth = title.measureText(Tokens.COPY.title)
        val titleY = size.height * Tokens.BACK.titleY.toFloat()
        title.shader = LinearGradient(
            0f, titleY - 30f * density, size.width, titleY,
            Tokens.COLOR.gold.map { it.toArgb() }.toIntArray(), null, Shader.TileMode.CLAMP,
        )
        canvas.drawText(Tokens.COPY.title, (size.width - titleWidth) / 2f, titleY, title)
        drawRoundRect(
            color = Tokens.COLOR.backRule,
            topLeft = Offset((size.width - Tokens.BACK.rule.toFloat() * density) / 2f, size.height * Tokens.BACK.ruleY.toFloat()),
            size = Size(Tokens.BACK.rule.toFloat() * density, density),
            cornerRadius = CornerRadius(0.5f * density),
        )
        Tokens.COPY.credits.forEachIndexed { index, credit ->
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = if (credit.strong) typography.semiboldTypeface else typography.mediumTypeface
                textSize = Tokens.TYPE.credit.toFloat() * density
                color = if (credit.strong) Tokens.COLOR.backInk.toArgb() else Tokens.COLOR.backMute.toArgb()
            }
            val width = paint.measureText(credit.text)
            val y = size.height * Tokens.BACK.creditsY.toFloat() +
                index * Tokens.BACK.lineStep.toFloat() * density + credit.gap.toFloat() * density
            canvas.drawText(credit.text, (size.width - width) / 2f, y, paint)
        }
    }
}

@Composable
private fun BoxScope.TiltPosterParallaxPosterEdges(
    engine: TiltPosterParallaxEngine,
    cardWidth: Dp,
    cardHeight: Dp,
    reduceMotion: Boolean,
) {
    if (reduceMotion) return
    val density = LocalDensity.current.density
    val edgeBrush = Brush.horizontalGradient(Tokens.COLOR.edge)
    val topBrush = Brush.verticalGradient(Tokens.COLOR.edge)
    Box(
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .width(Tokens.EDGE.depth.dp)
            .height(cardHeight)
            .graphicsLayer {
                val angle = engine.tiltX + engine.flip * PI.toFloat()
                val pitch = -engine.tiltY
                cameraDistance = tiltPosterParallaxCameraDistance(density)
                rotationX = Math.toDegrees(pitch.toDouble()).toFloat()
                rotationY = Math.toDegrees(angle.toDouble()).toFloat() + 90f
                alpha = tiltPosterParallaxEdgeVisibility(-cos(pitch) * sin(angle))
            }
            .background(edgeBrush),
    )
    Box(
        modifier = Modifier
            .align(Alignment.CenterStart)
            .width(Tokens.EDGE.depth.dp)
            .height(cardHeight)
            .graphicsLayer {
                val angle = engine.tiltX + engine.flip * PI.toFloat()
                val pitch = -engine.tiltY
                cameraDistance = tiltPosterParallaxCameraDistance(density)
                rotationX = Math.toDegrees(pitch.toDouble()).toFloat()
                rotationY = Math.toDegrees(angle.toDouble()).toFloat() - 90f
                alpha = tiltPosterParallaxEdgeVisibility(cos(pitch) * sin(angle))
            }
            .background(edgeBrush),
    )
    Box(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .width(cardWidth)
            .height(Tokens.EDGE.depth.dp)
            .graphicsLayer {
                val angle = engine.tiltX + engine.flip * PI.toFloat()
                val pitch = -engine.tiltY
                cameraDistance = tiltPosterParallaxCameraDistance(density)
                rotationY = Math.toDegrees(angle.toDouble()).toFloat()
                rotationX = Math.toDegrees(pitch.toDouble()).toFloat() - 90f
                alpha = tiltPosterParallaxEdgeVisibility(-sin(pitch))
            }
            .background(topBrush),
    )
    Box(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .width(cardWidth)
            .height(Tokens.EDGE.depth.dp)
            .graphicsLayer {
                val angle = engine.tiltX + engine.flip * PI.toFloat()
                val pitch = -engine.tiltY
                cameraDistance = tiltPosterParallaxCameraDistance(density)
                rotationY = Math.toDegrees(angle.toDouble()).toFloat()
                rotationX = Math.toDegrees(pitch.toDouble()).toFloat() + 90f
                alpha = tiltPosterParallaxEdgeVisibility(sin(pitch))
            }
            .background(topBrush),
    )
}

private fun tiltPosterParallaxCameraDistance(density: Float): Float {
    return Tokens.TILT.perspective.toFloat() * density / 72f
}

private fun tiltPosterParallaxEdgeVisibility(value: Float): Float {
    val from = sin(Tokens.EDGE.fromDeg).toFloat()
    val to = sin(Tokens.EDGE.toDeg).toFloat()
    return ((value - from) / (to - from)).coerceIn(0f, 1f)
}

private fun tiltPosterParallaxSetOverlay(paint: Paint) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) paint.blendMode = BlendMode.OVERLAY else {
        @Suppress("DEPRECATION")
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.OVERLAY)
    }
}

private fun tiltPosterParallaxSetScreen(paint: Paint) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) paint.blendMode = BlendMode.SCREEN else {
        @Suppress("DEPRECATION")
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
    }
}

private fun tiltPosterParallaxResetBlend(paint: Paint) {
    @Suppress("DEPRECATION")
    paint.xfermode = null
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) paint.blendMode = BlendMode.SRC_OVER
}
