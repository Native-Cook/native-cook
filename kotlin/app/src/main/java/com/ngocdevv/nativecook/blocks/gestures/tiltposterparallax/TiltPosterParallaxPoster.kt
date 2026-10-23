package com.ngocdevv.nativecook.blocks.gestures.tiltposterparallax

import android.graphics.BlendMode
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.view.HapticFeedbackConstants
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.R
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

@Composable
internal fun BoxScope.TiltPosterParallaxPoster(
    engine: TiltPosterParallaxEngine,
    cardWidth: Dp,
    cardHeight: Dp,
    left: Dp,
    top: Dp,
    reduceMotion: Boolean,
) {
    val density = LocalDensity.current.density
    val view = LocalView.current
    val cardWidthPoints = cardWidth.value.toDouble()
    val cardHeightPoints = cardHeight.value.toDouble()

    Box(
        modifier = Modifier
            .offset(x = left, y = top)
            .size(width = cardWidth, height = cardHeight),
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    val float = if (reduceMotion) {
                        0.0
                    } else {
                        (1.0 - cos(engine.clockMs / Tokens.FLOAT.periodMs * PI * 2.0)) / 2.0 * Tokens.FLOAT.amplitude
                    }
                    val rise = if (reduceMotion) {
                        1.0
                    } else {
                        1.0 + sin(engine.flip * PI) * Tokens.FLIP.rise
                    }
                    transformOrigin = TransformOrigin(0.5f, 0f)
                    translationY = (-float * density).toFloat()
                    scaleX = (engine.lift * rise).toFloat()
                    scaleY = (engine.lift * rise).toFloat()
                    clip = false
                },
        ) {
            PosterGlow(engine = engine)
            PosterShadow(engine = engine, reduceMotion = reduceMotion)
            PosterEdges(
                engine = engine,
                cardWidth = cardWidth,
                cardHeight = cardHeight,
                reduceMotion = reduceMotion,
            )
            PosterFront(
                engine = engine,
                cardWidth = cardWidth,
                cardHeight = cardHeight,
                reduceMotion = reduceMotion,
            )
            PosterBack(
                engine = engine,
                cardWidth = cardWidth,
                cardHeight = cardHeight,
                reduceMotion = reduceMotion,
            )
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
                .pointerInput(engine, density, cardWidthPoints, cardHeightPoints) {
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
                        var lastTimeMs = down.uptimeMillis.toDouble()
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                tracker.addPointerInputChange(change)
                                lastTimeMs = change.uptimeMillis.toDouble()
                                if (change.changedToUp() || !change.pressed) {
                                    break
                                }
                                if (change.positionChanged()) {
                                    val enteredEdge = engine.moveHold(
                                        x = (change.position.x / density).toDouble(),
                                        y = (change.position.y / density).toDouble(),
                                    )
                                    if (enteredEdge) {
                                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    }
                                }
                                change.consume()
                            }
                        } finally {
                            val velocity = tracker.calculateVelocity()
                            val tapped = engine.endHold(
                                velocityX = (velocity.x / density).toDouble(),
                                velocityY = (velocity.y / density).toDouble(),
                                eventTimeMs = lastTimeMs,
                            )
                            if (tapped) {
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            }
                            engine.realTouchUp()
                        }
                    }
                },
        )
    }
}

@Composable
private fun BoxScope.PosterGlow(engine: TiltPosterParallaxEngine) {
    val liftAlpha = (engine.lift - 1f) / (Tokens.TILT.lift - 1.0)
    val deadZone = Tokens.MATERIAL.glowDeadZoneDeg * PI / 180.0
    val tiltAlpha = max(0.0, abs(engine.tiltX) + abs(engine.tiltY) - deadZone) * Tokens.MATERIAL.glowTiltGain
    val alpha = min(1.0, liftAlpha * Tokens.MATERIAL.glowLiftAlpha + tiltAlpha).toFloat()
    SoftPosterField(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationX = (engine.tiltX * Tokens.MATERIAL.glowShiftX * density).toFloat()
                translationY = ((Tokens.MATERIAL.glowShiftY + engine.tiltY * Tokens.MATERIAL.glowTiltY) * density).toFloat()
                this.alpha = alpha
            },
        color = Tokens.COLOR.glow,
        radius = Tokens.LAYOUT.cardRadius.dp,
        blur = Tokens.MATERIAL.glowBlur.dp,
        insetLeft = Tokens.MATERIAL.glowLeft.toFloat(),
        insetTop = Tokens.MATERIAL.glowTop.toFloat(),
        widthRatio = Tokens.MATERIAL.glowWidth.toFloat(),
        heightRatio = Tokens.MATERIAL.glowHeight.toFloat(),
    )
}

@Composable
private fun BoxScope.PosterShadow(engine: TiltPosterParallaxEngine, reduceMotion: Boolean) {
    val turn = if (reduceMotion) 0.0 else engine.flip * PI
    val narrow = Tokens.MATERIAL.shadowScale * max(Tokens.MATERIAL.shadowEdgeScale, abs(cos(turn)))
    val alpha = Tokens.MATERIAL.shadowAlpha + (engine.lift - 1f) * Tokens.MATERIAL.shadowLiftAlpha
    SoftPosterField(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationX = (engine.tiltX * Tokens.MATERIAL.shadowShiftX * density).toFloat()
                translationY = ((Tokens.MATERIAL.shadowShiftY + engine.tiltY * Tokens.MATERIAL.shadowTiltY) * density).toFloat()
                scaleX = narrow.toFloat()
                scaleY = Tokens.MATERIAL.shadowScale.toFloat()
                this.alpha = alpha.toFloat()
            },
        color = Tokens.COLOR.shadow,
        radius = Tokens.LAYOUT.cardRadius.dp,
        blur = Tokens.MATERIAL.shadowBlur.dp,
        insetLeft = 0f,
        insetTop = 0f,
        widthRatio = 1f,
        heightRatio = 1f,
    )
}

@Composable
private fun SoftPosterField(
    modifier: Modifier,
    color: Color,
    radius: Dp,
    blur: Dp,
    insetLeft: Float,
    insetTop: Float,
    widthRatio: Float,
    heightRatio: Float,
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        androidx.compose.foundation.Canvas(
            modifier = modifier
                .fillMaxSize()
                .blur(blur, edgeTreatment = BlurredEdgeTreatment.Unbounded),
        ) {
            drawRoundRect(
                color = color,
                topLeft = Offset(size.width * insetLeft, size.height * insetTop),
                size = Size(size.width * widthRatio, size.height * heightRatio),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius.toPx()),
            )
        }
        return
    }
    androidx.compose.foundation.Canvas(modifier = modifier) {
        repeat(7) { index ->
            val progress = index / 6f
            val spread = blur.toPx() * progress * 0.6f
            val left = size.width * insetLeft - spread
            val top = size.height * insetTop - spread
            val width = size.width * widthRatio + spread * 2f
            val height = size.height * heightRatio + spread * 2f
            drawRoundRect(
                color = color.copy(alpha = color.alpha * (1f - progress) * 0.24f),
                topLeft = Offset(left, top),
                size = Size(width, height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius.toPx() + spread),
            )
        }
    }
}

@Composable
private fun BoxScope.PosterFront(
    engine: TiltPosterParallaxEngine,
    cardWidth: Dp,
    cardHeight: Dp,
    reduceMotion: Boolean,
) {
    val density = LocalDensity.current.density
    val angle = engine.tiltX + if (reduceMotion) 0f else engine.flip * PI.toFloat()
    val opacity = if (reduceMotion) 1f - engine.flip else if (cos(angle) >= 0f) 1f else 0f
    Box(
        modifier = Modifier
            .matchParentSize()
            .graphicsLayer {
                cameraDistance = perspectiveCameraDistance(density)
                rotationX = Math.toDegrees(-engine.tiltY.toDouble()).toFloat()
                rotationY = Math.toDegrees(angle.toDouble()).toFloat()
                alpha = opacity
            }
            .clip(RoundedCornerShape(Tokens.LAYOUT.cardRadius.dp)),
    ) {
        val overscanWidth = cardWidth * Tokens.LAYOUT.plateOverscan.toFloat()
        val overscanHeight = cardHeight * Tokens.LAYOUT.plateOverscan.toFloat()
        Image(
            painter = painterResource(R.drawable.tiltposterparallax_poster_plate),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(width = overscanWidth, height = overscanHeight)
                .align(Alignment.Center)
                .graphicsLayer {
                    translationX = (engine.tiltX * Tokens.PARALLAX.plate * density).toFloat()
                    translationY = (engine.tiltY * Tokens.PARALLAX.plate * density).toFloat()
                },
        )
        Image(
            painter = painterResource(R.drawable.tiltposterparallax_poster_figure),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    translationX = (engine.tiltX * Tokens.PARALLAX.figure * density).toFloat()
                    translationY = (engine.tiltY * Tokens.PARALLAX.figure * density).toFloat()
                },
        )
        PosterFrontLight(engine = engine)
        PosterTitle(engine = engine, cardWidth = cardWidth, cardHeight = cardHeight)
        androidx.compose.foundation.Canvas(modifier = Modifier.matchParentSize()) {
            drawRoundRect(
                color = Tokens.COLOR.rim,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(Tokens.LAYOUT.cardRadius.dp.toPx()),
                style = androidx.compose.ui.graphics.drawscope.Stroke(Tokens.MATERIAL.rimWidth.dp.toPx()),
            )
        }
    }
}

@Composable
private fun PosterFrontLight(engine: TiltPosterParallaxEngine) {
    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
        val centre = Offset(
            x = size.width * (Tokens.MATERIAL.glareBaseX + engine.tiltX * Tokens.PARALLAX.glare).toFloat(),
            y = size.height * (Tokens.MATERIAL.glareBaseY + engine.tiltY * Tokens.PARALLAX.glare).toFloat(),
        )
        val radius = size.width * Tokens.MATERIAL.glareRadius.toFloat()
        val extra = min(
            Tokens.MATERIAL.glareMaxExtraAlpha,
            (abs(engine.tiltX) + abs(engine.tiltY)) * Tokens.MATERIAL.glareTiltGain,
        )
        val alpha = (Tokens.MATERIAL.glareBaseAlpha + extra).toFloat()
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Tokens.COLOR.glare.copy(alpha = Tokens.COLOR.glare.alpha * alpha), Tokens.COLOR.glareClear),
                center = centre,
                radius = radius,
            ),
            radius = radius,
            center = centre,
        )
    }
}

@Composable
private fun PosterTitle(engine: TiltPosterParallaxEngine, cardWidth: Dp, cardHeight: Dp) {
    val density = LocalDensity.current.density
    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
        val canvas = drawContext.canvas.nativeCanvas
        val textSize = Tokens.TYPE.posterTitle.toFloat() * density
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.typeface = Typeface.create(Tokens.SCRIPT_FONT, Typeface.BOLD)
        paint.textSize = textSize
        val textWidth = paint.measureText(Tokens.COPY.title)
        val baseX = (cardWidth.toPx() - textWidth) / 2f
        val baseY = cardHeight.toPx() * Tokens.LAYOUT.titleY.toFloat()
        val shiftX = (engine.tiltX * Tokens.PARALLAX.title * density).toFloat()
        val shiftY = (engine.tiltY * Tokens.PARALLAX.title * density).toFloat()
        val pivotX = baseX + textWidth / 2f
        canvas.save()
        canvas.translate(shiftX + pivotX, shiftY + baseY)
        canvas.skew(Tokens.LAYOUT.titleSkew.toFloat(), 0f)
        canvas.translate(-pivotX, -baseY)
        paint.color = Tokens.COLOR.titleShadow.toArgb()
        paint.shader = null
        canvas.drawText(
            Tokens.COPY.title,
            baseX + Tokens.MATERIAL.titleShadowX.toFloat() * density,
            baseY + Tokens.MATERIAL.titleShadowY.toFloat() * density,
            paint,
        )
        paint.shader = LinearGradient(
            baseX,
            baseY - 50f * density,
            baseX + textWidth,
            baseY + 10f * density,
            Tokens.COLOR.gold.map { it.toArgb() }.toIntArray(),
            null,
            Shader.TileMode.CLAMP,
        )
        canvas.drawText(Tokens.COPY.title, baseX, baseY, paint)
        val holoAt = (
            engine.tiltX * Tokens.HOLO.travelX +
                engine.tiltY * Tokens.HOLO.travelY +
                engine.flip * Tokens.HOLO.flipTravel
            ).toFloat() * textWidth
        paint.shader = LinearGradient(
            baseX + holoAt,
            baseY - textSize,
            baseX + holoAt + textWidth * Tokens.HOLO.period.toFloat(),
            baseY + textSize * 0.3f,
            Tokens.COLOR.holo.map { it.toArgb() }.toIntArray(),
            null,
            Shader.TileMode.MIRROR,
        )
        val holoExtra = min(
            Tokens.HOLO.max - Tokens.HOLO.rest,
            (abs(engine.tiltX) + abs(engine.tiltY)) * Tokens.HOLO.gain,
        )
        paint.alpha = ((Tokens.HOLO.rest + holoExtra) * 255.0).toInt()
        setOverlayBlend(paint)
        canvas.drawText(Tokens.COPY.title, baseX, baseY, paint)
        paint.alpha = 255
        val foilAt = 0.5 + (engine.tiltX + engine.tiltY) / 2.0 * Tokens.PARALLAX.foil
        paint.shader = LinearGradient(
            baseX + textWidth * foilAt.toFloat() - Tokens.MATERIAL.foilHalfWidth.toFloat() * density,
            baseY - Tokens.MATERIAL.foilTop.toFloat() * density,
            baseX + textWidth * foilAt.toFloat() + Tokens.MATERIAL.foilHalfWidth.toFloat() * density,
            baseY + Tokens.MATERIAL.foilBottom.toFloat() * density,
            intArrayOf(Tokens.COLOR.foilClear.toArgb(), Tokens.COLOR.foil.toArgb(), Tokens.COLOR.foilClear.toArgb()),
            null,
            Shader.TileMode.CLAMP,
        )
        setScreenBlend(paint)
        canvas.drawText(Tokens.COPY.title, baseX, baseY, paint)
        paint.xfermode = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            paint.blendMode = BlendMode.SRC_OVER
        }
        canvas.restore()
    }
}

@Composable
private fun BoxScope.PosterBack(
    engine: TiltPosterParallaxEngine,
    cardWidth: Dp,
    cardHeight: Dp,
    reduceMotion: Boolean,
) {
    val density = LocalDensity.current.density
    val angle = engine.tiltX + if (reduceMotion) 0f else engine.flip * PI.toFloat()
    val opacity = if (reduceMotion) engine.flip else if (cos(angle) < 0f) 1f else 0f
    Box(
        modifier = Modifier
            .matchParentSize()
            .graphicsLayer {
                cameraDistance = perspectiveCameraDistance(density)
                rotationX = Math.toDegrees(-engine.tiltY.toDouble()).toFloat()
                rotationY = Math.toDegrees(angle.toDouble()).toFloat() + if (reduceMotion) 0f else 180f
                alpha = opacity
            }
            .clip(RoundedCornerShape(Tokens.LAYOUT.cardRadius.dp)),
    ) {
        val overscanWidth = cardWidth * Tokens.LAYOUT.plateOverscan.toFloat()
        val overscanHeight = cardHeight * Tokens.LAYOUT.plateOverscan.toFloat()
        Image(
            painter = painterResource(R.drawable.tiltposterparallax_poster_plate),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alpha = Tokens.BACK.plateOpacity.toFloat(),
            modifier = Modifier
                .size(width = overscanWidth, height = overscanHeight)
                .align(Alignment.Center)
                .blur(Tokens.BACK.plateBlur.dp),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Brush.verticalGradient(Tokens.COLOR.back)),
        )
        PosterBackCopy(cardWidth = cardWidth, cardHeight = cardHeight)
        PosterFrontLight(engine = engine)
        androidx.compose.foundation.Canvas(modifier = Modifier.matchParentSize()) {
            drawRoundRect(
                color = Tokens.COLOR.rim,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(Tokens.LAYOUT.cardRadius.dp.toPx()),
                style = androidx.compose.ui.graphics.drawscope.Stroke(Tokens.MATERIAL.rimWidth.dp.toPx()),
            )
        }
    }
}

@Composable
private fun PosterBackCopy(cardWidth: Dp, cardHeight: Dp) {
    val density = LocalDensity.current.density
    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
        val canvas = drawContext.canvas.nativeCanvas
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        titlePaint.typeface = Typeface.create(Tokens.SCRIPT_FONT, Typeface.BOLD)
        titlePaint.textSize = Tokens.TYPE.backTitle.toFloat() * density
        val titleWidth = titlePaint.measureText(Tokens.COPY.title)
        val titleY = cardHeight.toPx() * Tokens.BACK.titleY.toFloat()
        titlePaint.shader = LinearGradient(
            0f,
            titleY - 30f * density,
            cardWidth.toPx(),
            titleY,
            Tokens.COLOR.gold.map { it.toArgb() }.toIntArray(),
            null,
            Shader.TileMode.CLAMP,
        )
        canvas.drawText(Tokens.COPY.title, (cardWidth.toPx() - titleWidth) / 2f, titleY, titlePaint)
        drawRoundRect(
            color = Tokens.COLOR.backRule,
            topLeft = Offset(
                x = (cardWidth.value / 2.0 - Tokens.BACK.rule / 2.0).dp.toPx(),
                y = (cardHeight.value * Tokens.BACK.ruleY).dp.toPx(),
            ),
            size = Size(Tokens.BACK.rule.dp.toPx(), Tokens.BACK.ruleHeight.dp.toPx()),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(0.5.dp.toPx()),
        )
        Tokens.COPY.credits.forEachIndexed { index, credit ->
            val creditPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            creditPaint.typeface = Typeface.create(
                Typeface.SANS_SERIF,
                if (credit.strong) Typeface.BOLD else Typeface.NORMAL,
            )
            creditPaint.textSize = Tokens.TYPE.credit.toFloat() * density
            creditPaint.color = if (credit.strong) Tokens.COLOR.backInk.toArgb() else Tokens.COLOR.backMute.toArgb()
            val width = creditPaint.measureText(credit.text)
            val y = cardHeight.toPx() * Tokens.BACK.creditsY.toFloat() +
                index * Tokens.BACK.lineStep.toFloat() * density +
                credit.gap.toFloat() * density
            canvas.drawText(credit.text, (cardWidth.toPx() - width) / 2f, y, creditPaint)
        }
    }
}

@Composable
private fun BoxScope.PosterEdges(
    engine: TiltPosterParallaxEngine,
    cardWidth: Dp,
    cardHeight: Dp,
    reduceMotion: Boolean,
) {
    if (reduceMotion) {
        return
    }
    val density = LocalDensity.current.density
    val angle = engine.tiltX + engine.flip * PI.toFloat()
    val pitch = -engine.tiltY
    val rightOpacity = edgeVisibility(-cos(pitch) * sin(angle))
    val leftOpacity = edgeVisibility(cos(pitch) * sin(angle))
    val topOpacity = edgeVisibility(-sin(pitch))
    val bottomOpacity = edgeVisibility(sin(pitch))
    val sideOffset = cos(angle) * cardWidth.value / 2f
    val endOffset = cos(pitch) * cardHeight.value / 2f

    Image(
        painter = painterResource(R.drawable.tiltposterparallax_edge_side),
        contentDescription = null,
        contentScale = ContentScale.FillBounds,
        modifier = Modifier
            .align(Alignment.Center)
            .width(Tokens.EDGE.depth.dp)
            .height(cardHeight)
            .graphicsLayer {
                translationX = sideOffset * density
                alpha = rightOpacity
            },
    )
    Image(
        painter = painterResource(R.drawable.tiltposterparallax_edge_side),
        contentDescription = null,
        contentScale = ContentScale.FillBounds,
        modifier = Modifier
            .align(Alignment.Center)
            .width(Tokens.EDGE.depth.dp)
            .height(cardHeight)
            .graphicsLayer {
                translationX = -sideOffset * density
                rotationY = 180f
                alpha = leftOpacity
            },
    )
    Image(
        painter = painterResource(R.drawable.tiltposterparallax_edge_top),
        contentDescription = null,
        contentScale = ContentScale.FillBounds,
        modifier = Modifier
            .align(Alignment.Center)
            .width(cardWidth)
            .height(Tokens.EDGE.depth.dp)
            .graphicsLayer {
                translationY = -endOffset * density
                alpha = topOpacity
            },
    )
    Image(
        painter = painterResource(R.drawable.tiltposterparallax_edge_top),
        contentDescription = null,
        contentScale = ContentScale.FillBounds,
        modifier = Modifier
            .align(Alignment.Center)
            .width(cardWidth)
            .height(Tokens.EDGE.depth.dp)
            .graphicsLayer {
                translationY = endOffset * density
                rotationX = 180f
                alpha = bottomOpacity
            },
    )
}

private fun perspectiveCameraDistance(density: Float): Float {
    return Tokens.TILT.perspective.toFloat() * density / 72f
}

private fun edgeVisibility(value: Float): Float {
    val from = sin(Tokens.EDGE.fromDeg).toFloat()
    val to = sin(Tokens.EDGE.toDeg).toFloat()
    return ((value - from) / (to - from)).coerceIn(0f, 1f)
}

private fun setOverlayBlend(paint: Paint) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        paint.blendMode = BlendMode.OVERLAY
    } else {
        @Suppress("DEPRECATION")
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.OVERLAY)
    }
}

private fun setScreenBlend(paint: Paint) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        paint.blendMode = BlendMode.SCREEN
    } else {
        @Suppress("DEPRECATION")
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
    }
}
