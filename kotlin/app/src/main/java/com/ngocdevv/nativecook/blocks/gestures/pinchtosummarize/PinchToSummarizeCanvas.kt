package com.ngocdevv.nativecook.blocks.gestures.pinchtosummarize

import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.graphics.SweepGradient
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.ngocdevv.nativecook.R
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

private val ptsQaReplica: Boolean
    get() = QA_HOLD != null

@Composable
private fun rememberPtsFieldBlurNodes(): PtsFieldBlurNodes? = remember {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PtsFieldBlurNodes() else null
}

@Composable
private fun rememberPtsLightBlurNodes(): PtsLightBlurNodes? = remember {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PtsLightBlurNodes() else null
}

@RequiresApi(Build.VERSION_CODES.S)
private class PtsFieldBlurNodes {
    val glow = PtsBlurNode("pinch-to-summarize-glow")
    val shadow = PtsBlurNode("pinch-to-summarize-shadow")
}

@RequiresApi(Build.VERSION_CODES.S)
private class PtsLightBlurNodes {
    val rim = PtsBlurNode("pinch-to-summarize-rim")
    val bloom = PtsBlurNode("pinch-to-summarize-bloom")
}

@RequiresApi(Build.VERSION_CODES.S)
private class PtsBlurNode(name: String) {
    private val node = RenderNode(name).apply { setClipToBounds(true) }
    private var radius = -1f

    fun record(
        width: Int,
        height: Int,
        radius: Float,
        draw: (android.graphics.Canvas) -> Unit,
    ) {
        node.setPosition(0, 0, width, height)
        if (this.radius != radius) {
            this.radius = radius
            // Android and Skia name this parameter radius differently; the same supplied
            // point value is retained and converted once to physical pixels.
            node.setRenderEffect(
                RenderEffect.createBlurEffect(
                    radius,
                    radius,
                    Shader.TileMode.DECAL,
                ),
            )
        }
        val canvas = node.beginRecording(width, height)
        draw(canvas)
        node.endRecording()
    }

    fun draw(canvas: android.graphics.Canvas) {
        canvas.drawRenderNode(node)
    }
}

/** Baked gradient field, deterministic motes, responsive glow, page shadow and stacked card. */
@Composable
internal fun PtsField(
    stage: PtsStage,
    state: PtsState,
    modifier: Modifier = Modifier,
) {
    val field = ImageBitmap.imageResource(R.drawable.pinchtosummarize_field)
    val fieldB = ImageBitmap.imageResource(R.drawable.pinchtosummarize_field_b)
    val blurNodes = rememberPtsFieldBlurNodes()

    Canvas(modifier = modifier.fillMaxSize()) {
        val density = density
        val progress = state.p.value
        val lift = state.lift.value
        val driftX = state.driftX.value
        val driftY = state.driftY.value
        val think = state.think.value
        val clock = state.clock.value
        val frame = ptsPageFrame(stage, progress, lift, driftX, driftY)

        withTransform({ scale(density, density, pivot = Offset.Zero) }) {
            val fieldScale = 1f + PtsTiming.fieldBreath *
                sin((clock / PtsTiming.fieldPeriod.toFloat()) * (PI * 2.0)).toFloat()
            scale(
                scaleX = fieldScale,
                scaleY = fieldScale,
                pivot = Offset(stage.width / 2f, stage.height / 2f),
            ) {
                drawImage(
                    image = field,
                    dstOffset = IntOffset.Zero,
                    dstSize = IntSize(stage.width.toInt(), stage.height.toInt()),
                )
                drawImage(
                    image = fieldB,
                    dstOffset = IntOffset.Zero,
                    dstSize = IntSize(stage.width.toInt(), stage.height.toInt()),
                    alpha = ptsClamp01(lift),
                )
            }

            if (!ptsQaReplica) {
                drawPtsMotes(stage, clock)
            }

        }

        val glowOpacity = if (ptsQaReplica) {
            0f
        } else {
            ptsClamp01((progress - 0.5f) / 0.5f) *
                (PtsLayout.glowRest + PtsLayout.glowThink * think)
        }
        if (glowOpacity > 0f) {
            val spread = PtsLayout.glowSpread
            drawPtsBlurredRoundRect(
                color = PtsColor.cardGlow,
                left = frame.x - spread,
                top = frame.y - spread * 0.5f,
                right = frame.x + frame.width + spread,
                bottom = frame.y + frame.height + spread * 1.1f,
                radius = frame.radius + spread,
                blur = PtsLayout.glowBlur,
                alpha = glowOpacity,
                density = density,
                blurNode = blurNodes?.glow,
            )
        }

        val shadowOpacity = ptsClamp01(progress) * PtsLayout.shadowAlpha
        if (shadowOpacity > 0f) {
            val shift = ptsClamp01(progress) * PtsLayout.shadowDrop
            drawPtsBlurredRoundRect(
                color = PtsColor.pageShadow,
                left = frame.x,
                top = frame.y + shift,
                right = frame.x + frame.width,
                bottom = frame.y + frame.height + shift,
                radius = frame.radius,
                blur = PtsLayout.shadowBlur,
                alpha = shadowOpacity,
                density = density,
                blurNode = blurNodes?.shadow,
            )
        }

        withTransform({ scale(density, density, pivot = Offset.Zero) }) {
            val stackOpacity = ptsClamp01((progress - PtsLayout.stackFrom) / PtsLayout.stackFade)
            if (stackOpacity > 0f) {
                val stackWidth = frame.width * PtsLayout.stackScale
                drawRoundRect(
                    color = PtsColor.stack,
                    topLeft = Offset(
                        frame.x + (frame.width - stackWidth) / 2f,
                        frame.y - PtsLayout.stackPeek,
                    ),
                    size = Size(
                        stackWidth,
                        frame.height,
                    ),
                    cornerRadius = CornerRadius(frame.radius),
                    alpha = stackOpacity,
                )
            }
        }
    }
}

/** Rim, arrival bloom and scripted finger marks above the page. */
@Composable
internal fun PtsLight(
    stage: PtsStage,
    state: PtsState,
    modifier: Modifier = Modifier,
) {
    val blurNodes = rememberPtsLightBlurNodes()
    Canvas(modifier = modifier.fillMaxSize()) {
        val density = density
        val progress = state.p.value
        val lift = state.lift.value
        val think = state.think.value
        val clock = state.clock.value
        val breath = ptsBreathing(
            think = think,
            clock = clock,
            amplitude = PtsTiming.breathe,
            period = PtsTiming.breathePeriod,
        )
        val frame = ptsPageFrame(
            stage = stage,
            p = progress,
            lift = lift,
            driftX = state.driftX.value,
            driftY = state.driftY.value,
            breath = breath,
        )
        val inset = PtsLayout.rimWidth / 2f
        val rimRect = RoundRect(
            left = frame.x + inset,
            top = frame.y + inset,
            right = frame.x + frame.width - inset,
            bottom = frame.y + frame.height - inset,
            cornerRadius = CornerRadius((frame.radius - inset).coerceAtLeast(0f)),
        )
        val rimOpacity = if (ptsQaReplica) 0f else ptsClamp01(state.rim.value)
        if (rimOpacity > 0f) {
            drawPtsRim(
                rect = rimRect,
                turnDegrees = (clock / PtsTiming.rimTurnMs.toFloat()) * 360f,
                alpha = rimOpacity,
                density = density,
                blurNode = blurNodes?.rim,
            )
        }

        val bloomAt = state.bloomAt.value
        val bloomU = if (bloomAt < 0f) 1f else {
            ptsClamp01((clock - bloomAt) / PtsTiming.bloomMs.toFloat())
        }
        if (bloomU < 1f) {
            val eased = 1f - (1f - bloomU).pow(3)
            val grow = eased * PtsTiming.bloomReach
            drawPtsBloom(
                left = frame.x - grow,
                top = frame.y - grow,
                right = frame.x + frame.width + grow,
                bottom = frame.y + frame.height + grow,
                radius = frame.radius + grow,
                alpha = 1f - bloomU,
                density = density,
                blurNode = blurNodes?.bloom,
            )
        }

        withTransform({ scale(density, density, pivot = Offset.Zero) }) {
            val fingerOpacity = ptsClamp01(state.fingerAlpha.value)
            if (fingerOpacity > 0f) {
                drawPtsFinger(state.fingerAX.value, state.fingerAY.value, fingerOpacity)
                drawPtsFinger(state.fingerBX.value, state.fingerBY.value, fingerOpacity)
            }
        }

    }
}

private fun DrawScope.drawPtsMotes(stage: PtsStage, clock: Float) {
    val seconds = clock / 1_000f
    repeat(PtsTiming.motes) { index ->
        val firstRaw = sin(index * 12.9898) * 43_758.5453
        val secondRaw = sin(index * 78.233) * 12_543.123
        val first = (firstRaw - floor(firstRaw)).toFloat()
        val second = (secondRaw - floor(secondRaw)).toFloat()
        val speed = PtsTiming.moteSpeedMin +
            (PtsTiming.moteSpeedMax - PtsTiming.moteSpeedMin) * second
        val y = stage.height -
            ((second * stage.height + seconds * speed) % (stage.height + 20f))
        val x = first * stage.width + sin(seconds * 0.6f + index) * 6f
        drawCircle(
            color = PtsColor.mote,
            radius = 1f + 1.6f * second,
            center = Offset(x, y),
            alpha = 0.08f + 0.14f * first,
        )
    }
}

private fun DrawScope.drawPtsBlurredRoundRect(
    color: Color,
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    radius: Float,
    blur: Float,
    alpha: Float,
    density: Float,
    blurNode: PtsBlurNode?,
) {
    if (
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        blurNode != null &&
        drawContext.canvas.nativeCanvas.isHardwareAccelerated
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            this.color = color.copy(alpha = color.alpha * alpha).toArgb()
        }
        blurNode.record(
            width = size.width.roundToInt(),
            height = size.height.roundToInt(),
            radius = blur * density,
        ) { canvas ->
            canvas.drawRoundRect(
                left * density,
                top * density,
                right * density,
                bottom * density,
                radius * density,
                radius * density,
                paint,
            )
        }
        drawIntoCanvas { canvas -> blurNode.draw(canvas.nativeCanvas) }
        return
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            this.color = color.copy(alpha = color.alpha * alpha).toArgb()
            setShadowLayer(
                blur * density,
                0f,
                0f,
                color.copy(alpha = color.alpha * alpha).toArgb(),
            )
        }
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawRoundRect(
                left * density,
                top * density,
                right * density,
                bottom * density,
                radius * density,
                radius * density,
                paint,
            )
        }
        return
    }

    // API 26–27 do not reliably accelerate mask filters; three fixed layers are the fallback.
    val fallback = floatArrayOf(1f, 0.66f, 0.33f)
    for ((index, fraction) in fallback.withIndex()) {
        val grow = blur * fraction * density
        drawRoundRect(
            color = color,
            topLeft = Offset(left * density - grow, top * density - grow),
            size = Size(
                (right - left) * density + grow * 2f,
                (bottom - top) * density + grow * 2f,
            ),
            cornerRadius = CornerRadius(radius * density + grow),
            alpha = alpha * (index + 1) / 9f,
        )
    }
}

private fun DrawScope.drawPtsRim(
    rect: RoundRect,
    turnDegrees: Float,
    alpha: Float,
    density: Float,
    blurNode: PtsBlurNode?,
) {
    val centerX = rect.center.x * density
    val centerY = rect.center.y * density
    val shader = SweepGradient(
        centerX,
        centerY,
        PtsColor.rim.map { it.toArgb() }.toIntArray(),
        null,
    ).apply {
        setLocalMatrix(Matrix().apply { setRotate(turnDegrees, centerX, centerY) })
    }
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        this.shader = shader
        this.alpha = (alpha * 0.75f * 255f).toInt().coerceIn(0, 255)
        strokeWidth = PtsLayout.rimGlow * density
        if (Build.VERSION.SDK_INT in Build.VERSION_CODES.P until Build.VERSION_CODES.S) {
            setShadowLayer(
                PtsLayout.rimGlow * density,
                0f,
                0f,
                PtsColor.rim.first().copy(alpha = alpha * 0.75f).toArgb(),
            )
        }
    }
    drawIntoCanvas { canvas ->
        val native = canvas.nativeCanvas
        val left = rect.left * density
        val top = rect.top * density
        val right = rect.right * density
        val bottom = rect.bottom * density
        val radius = rect.topLeftCornerRadius.x * density
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            blurNode != null &&
            native.isHardwareAccelerated
        ) {
            blurNode.record(
                width = size.width.roundToInt(),
                height = size.height.roundToInt(),
                radius = PtsLayout.rimGlow * density,
            ) { recordingCanvas ->
                recordingCanvas.drawRoundRect(left, top, right, bottom, radius, radius, paint)
            }
            blurNode.draw(native)
        } else {
            native.drawRoundRect(left, top, right, bottom, radius, radius, paint)
        }
        if (Build.VERSION.SDK_INT in Build.VERSION_CODES.P until Build.VERSION_CODES.S) {
            paint.clearShadowLayer()
        }
        paint.strokeWidth = PtsLayout.rimWidth * density
        paint.alpha = (alpha * 255f).toInt().coerceIn(0, 255)
        native.drawRoundRect(left, top, right, bottom, radius, radius, paint)
    }
}

private fun DrawScope.drawPtsBloom(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    radius: Float,
    alpha: Float,
    density: Float,
    blurNode: PtsBlurNode?,
) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = PtsLayout.rimWidth * 2f * density
        color = PtsColor.bloom.copy(alpha = alpha).toArgb()
        if (Build.VERSION.SDK_INT in Build.VERSION_CODES.P until Build.VERSION_CODES.S) {
            setShadowLayer(
                PtsLayout.rimGlow * density,
                0f,
                0f,
                PtsColor.bloom.copy(alpha = alpha).toArgb(),
            )
        }
    }
    drawIntoCanvas { canvas ->
        val native = canvas.nativeCanvas
        val leftPx = left * density
        val topPx = top * density
        val rightPx = right * density
        val bottomPx = bottom * density
        val radiusPx = radius * density
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            blurNode != null &&
            native.isHardwareAccelerated
        ) {
            blurNode.record(
                width = size.width.roundToInt(),
                height = size.height.roundToInt(),
                radius = PtsLayout.rimGlow * density,
            ) { recordingCanvas ->
                recordingCanvas.drawRoundRect(
                    leftPx,
                    topPx,
                    rightPx,
                    bottomPx,
                    radiusPx,
                    radiusPx,
                    paint,
                )
            }
            blurNode.draw(native)
        } else {
            native.drawRoundRect(
                leftPx,
                topPx,
                rightPx,
                bottomPx,
                radiusPx,
                radiusPx,
                paint,
            )
        }
    }
}

private fun DrawScope.drawPtsFinger(x: Float, y: Float, alpha: Float) {
    drawCircle(
        color = PtsColor.finger,
        radius = PtsLayout.fingerRadius,
        center = Offset(x, y),
        alpha = alpha,
    )
    drawCircle(
        color = PtsColor.fingerRing,
        radius = PtsLayout.fingerRadius,
        center = Offset(x, y),
        alpha = alpha,
        style = Stroke(width = 1.5f),
    )
}
