package com.ngocdevv.nativecook.blocks.gestures.gumballcrank

import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.os.Build
import androidx.compose.ui.graphics.toArgb
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Native Canvas translation of GumballUpgrade.tsx. Coordinates are dp, because the Compose
 * host scales its native canvas once by display density before calling [draw].
 */
internal class GumballCrankRenderer(
    private val assets: GumballCrankAssets,
) {
    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val source = Rect()
    private val destination = RectF()
    private val globePath = Path()
    private val knobMatrix = ColorMatrix(Tokens.REPLICA_ART.knobMatrix.map(Double::toFloat).toFloatArray())
    private var stageRenderNode: RenderNode? = null
    private var knobRenderNode: RenderNode? = null
    private var knobRenderScale = Float.NaN

    fun draw(
        canvas: Canvas,
        simulation: GumballCrankSimulation,
        width: Float,
        height: Float,
        safeTop: Float,
    ) {
        if (width <= 0f || height <= 0f) return
        val scale = width / Tokens.STAGE_WIDTH.toFloat()
        drawCameraScene(canvas, simulation, width, height, scale)
        drawPotShadow(canvas, simulation, scale)
        drawPot(canvas, simulation, scale, includeOpacity = true)
        drawBlurredStage(canvas, simulation, width, height, scale)
        drawScrim(canvas, simulation, width, height)
        drawConfetti(canvas, simulation, scale)
        drawCoupon(canvas, simulation, scale)
    }

    // RN GumballUpgrade.tsx:181-188, the camera transforms the replica scene only.
    private fun drawCameraScene(
        canvas: Canvas,
        simulation: GumballCrankSimulation,
        width: Float,
        height: Float,
        scale: Float,
    ) {
        val camera = simulation.cameraProgress.toFloat()
        val cameraScale = 1f + (Tokens.Motion.REPLICA_CAMERA.b[0].toFloat() - 1f) * camera
        canvas.save()
        canvas.translate(
            Tokens.Motion.REPLICA_CAMERA.b[1].toFloat() * camera * width / Tokens.RENDER.cameraBasisWidth,
            Tokens.Motion.REPLICA_CAMERA.b[2].toFloat() * camera * width / Tokens.RENDER.cameraBasisWidth,
        )
        canvas.scale(cameraScale, cameraScale)
        drawScene(canvas, simulation, width, height, scale)
        canvas.restore()
    }

    // RN Scene:345-382. The room, fill and glass are all `fit="fill"` layers.
    private fun drawScene(
        canvas: Canvas,
        simulation: GumballCrankSimulation,
        width: Float,
        height: Float,
        scale: Float,
    ) {
        val squash = simulation.machineSquash.toFloat()
        val machineBase = Tokens.RENDER.machineBase * scale
        canvas.save()
        canvas.translate(0f, machineBase)
        canvas.scale(1f + squash * Tokens.RENDER.squashWidthFactor, 1f - squash)
        canvas.translate(0f, -machineBase)
        drawBitmap(canvas, assets.room, 0f, 0f, width, height)
        drawDoor(canvas, scale)
        drawGlobeContents(canvas, simulation, width, height, scale)
        drawBitmap(canvas, assets.globeGlass, 0f, 0f, width, height)
        drawKnobRing(canvas, simulation, scale)
        drawKnob(canvas, simulation, scale)
        canvas.restore()
    }

    private fun drawDoor(canvas: Canvas, scale: Float) {
        val door = Tokens.REPLICA_ART.door
        val left = door.x.toFloat() * scale
        val top = door.y.toFloat() * scale
        val width = door.w.toFloat() * scale
        val height = door.h.toFloat() * scale
        destination.set(left, top, left + width, top + height)
        fillPaint.shader = LinearGradient(
            0f,
            top,
            0f,
            top + height,
            door.top.toArgb(),
            door.bottom.toArgb(),
            Shader.TileMode.CLAMP,
        )
        fillPaint.alpha = 255
        canvas.drawRoundRect(destination, door.r.toFloat() * scale, door.r.toFloat() * scale, fillPaint)
        fillPaint.shader = null
        strokePaint.color = door.shade.toArgb()
        strokePaint.strokeWidth = Tokens.RENDER.doorStroke * scale
        strokePaint.maskFilter = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) BlurMaskFilter(Tokens.RENDER.doorInnerBlur * scale, BlurMaskFilter.Blur.INNER) else null
        canvas.drawRoundRect(destination, door.r.toFloat() * scale, door.r.toFloat() * scale, strokePaint)
        strokePaint.maskFilter = null
    }

    private fun drawGlobeContents(
        canvas: Canvas,
        simulation: GumballCrankSimulation,
        width: Float,
        height: Float,
        scale: Float,
    ) {
        val globe = Tokens.REPLICA_ART.globe
        globePath.reset()
        globePath.addCircle(globe.x.toFloat() * scale, globe.y.toFloat() * scale, (globe.r - Tokens.RENDER.globeClipInset).toFloat() * scale, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(globePath)
        canvas.clipRect(0f, 0f, width, globe.floor.toFloat() * scale)
        canvas.save()
        canvas.translate(simulation.jostleX.toFloat() * Tokens.RENDER.fillJostle * scale, simulation.jostleY.toFloat() * Tokens.RENDER.fillJostle * scale)
        drawBitmap(canvas, assets.globeFill, 0f, 0f, width, height)
        canvas.restore()
        fillPaint.color = Tokens.REPLICA_ART.haze.toArgb()
        canvas.drawRect(0f, 0f, width, height, fillPaint)
        if (simulation.sweepX > Tokens.RENDER.sweepVisibleThreshold) {
            canvas.save()
            canvas.translate(simulation.sweepX.toFloat() * scale, (globe.y - globe.r).toFloat() * scale)
            canvas.skew(Tokens.RENDER.sweepSkew, 0f)
            fillPaint.color = Tokens.UP.sweep.toArgb()
            fillPaint.maskFilter = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) BlurMaskFilter(Tokens.RENDER.sweepBlur * scale, BlurMaskFilter.Blur.NORMAL) else null
            canvas.drawRect(0f, 0f, Tokens.UP.sweepW.toFloat() * scale, (globe.r * 2.0).toFloat() * scale, fillPaint)
            fillPaint.maskFilter = null
            canvas.restore()
        }
        canvas.restore()
    }

    private fun drawKnobRing(canvas: Canvas, simulation: GumballCrankSimulation, scale: Float) {
        val opacity = simulation.ringOpacity.toFloat().coerceIn(0f, 1f)
        if (opacity <= 0f) return
        strokePaint.color = Tokens.UP.ringColor.toArgb()
        strokePaint.alpha = (Tokens.UP.ringColor.alpha * opacity * 255f).roundToInt()
        strokePaint.strokeWidth = Tokens.RENDER.ringStroke * scale
        strokePaint.maskFilter = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) BlurMaskFilter(Tokens.RENDER.ringBlur, BlurMaskFilter.Blur.NORMAL) else null
        canvas.drawCircle(
            knobCenterX().toFloat() * scale,
            knobCenterY().toFloat() * scale,
            Tokens.UP.ring.toFloat() * scale,
            strokePaint,
        )
        strokePaint.alpha = 255
        strokePaint.maskFilter = null
    }

    private fun drawKnob(canvas: Canvas, simulation: GumballCrankSimulation, scale: Float) {
        val knob = Tokens.REPLICA_ART.knob
        val centerX = knobCenterX().toFloat() * scale
        val centerY = knobCenterY().toFloat() * scale
        val pressScale = if (simulation.reduced) 1f else 1f + (Tokens.Motion.KNOB.press.toFloat() - 1f) * simulation.press.toFloat()
        canvas.save()
        canvas.translate(centerX, centerY)
        canvas.rotate(Math.toDegrees(knob.tilt + simulation.visualAngle).toFloat())
        canvas.scale(pressScale, pressScale)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && canvas.isHardwareAccelerated) {
            drawBlurredKnob(canvas, scale)
        } else {
            // API 26–30 keeps the source bitmap's baked softness; MaskFilter only blurs alpha.
            imagePaint.colorFilter = ColorMatrixColorFilter(knobMatrix)
            drawBitmap(canvas, assets.knob, -knob.size.toFloat() * scale / 2f, -knob.size.toFloat() * scale / 2f, knob.size.toFloat() * scale, knob.size.toFloat() * scale, imagePaint)
            imagePaint.colorFilter = null
        }
        canvas.restore()
    }

    private fun drawBlurredKnob(canvas: Canvas, scale: Float) {
        val knob = Tokens.REPLICA_ART.knob
        val blur = Tokens.REPLICA_ART.knobBlur.toFloat() * scale
        val padding = max(1f, blur * 3f)
        val bitmapSize = knob.size.toFloat() * scale
        val nodeSize = max(1, (bitmapSize + padding * 2f).roundToInt())
        var node = knobRenderNode
        if (node == null || knobRenderScale != scale) {
            node = RenderNode("gumball-crank-knob")
            node.setPosition(0, 0, nodeSize, nodeSize)
            val recording = node.beginRecording(nodeSize, nodeSize)
            imagePaint.colorFilter = ColorMatrixColorFilter(knobMatrix)
            drawBitmap(recording, assets.knob, padding, padding, bitmapSize, bitmapSize, imagePaint)
            imagePaint.colorFilter = null
            node.endRecording()
            node.setRenderEffect(RenderEffect.createBlurEffect(blur, blur, Shader.TileMode.CLAMP))
            knobRenderNode = node
            knobRenderScale = scale
        }
        canvas.save()
        canvas.translate(-nodeSize / 2f, -nodeSize / 2f)
        canvas.drawRenderNode(node)
        canvas.restore()
    }

    private fun drawPotShadow(canvas: Canvas, simulation: GumballCrankSimulation, scale: Float) {
        val opacity = Tokens.RENDER.shadowOpacity * simulation.outProgress.toFloat().coerceIn(0f, 1f)
        if (opacity <= 0f) return
        canvas.save()
        canvas.translate(Tokens.REPLICA_ART.pot.cx.toFloat() * scale, Tokens.UP.shadowY.toFloat() * scale)
        val shadowScale = if (simulation.reduced) 1f else Tokens.RENDER.shadowStartScale + Tokens.RENDER.shadowEndScale * simulation.outProgress.toFloat().coerceIn(0f, 1f)
        canvas.scale(shadowScale, shadowScale)
        fillPaint.color = Tokens.UP.shadow.toArgb()
        fillPaint.alpha = (Tokens.UP.shadow.alpha * opacity * 255f).roundToInt()
        fillPaint.maskFilter = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) BlurMaskFilter(Tokens.RENDER.shadowBlur * scale, BlurMaskFilter.Blur.NORMAL) else null
        destination.set(-Tokens.RENDER.shadowHalfWidth * scale, -Tokens.RENDER.shadowHalfHeight * scale, Tokens.RENDER.shadowHalfWidth * scale, Tokens.RENDER.shadowHalfHeight * scale)
        canvas.drawOval(destination, fillPaint)
        fillPaint.alpha = 255
        fillPaint.maskFilter = null
        canvas.restore()
    }

    private fun drawPot(canvas: Canvas, simulation: GumballCrankSimulation, scale: Float, includeOpacity: Boolean) {
        val opacity = if (!includeOpacity) {
            1f
        } else if (simulation.reduced) {
            simulation.outProgress.toFloat().coerceIn(0f, 1f)
        } else {
            (simulation.outProgress / Tokens.RENDER.potFadeThreshold).coerceIn(0.0, 1.0).toFloat()
        }
        if (opacity <= 0f) return
        val pot = Tokens.REPLICA_ART.pot
        canvas.save()
        canvas.translate(simulation.potX.toFloat() * scale, simulation.potY.toFloat() * scale)
        canvas.scale(simulation.potScale.toFloat(), simulation.potScale.toFloat())
        imagePaint.alpha = (opacity * 255f).roundToInt()
        drawBitmap(canvas, assets.pot, -pot.w.toFloat() * scale / 2f, -pot.h.toFloat() * scale / 2f, pot.w.toFloat() * scale, pot.h.toFloat() * scale)
        imagePaint.alpha = 255
        canvas.restore()
    }

    // RN :264-272 crossfades a fixed 14 pt RGB-blurred copy of the complete scene and pot.
    private fun drawBlurredStage(
        canvas: Canvas,
        simulation: GumballCrankSimulation,
        width: Float,
        height: Float,
        scale: Float,
    ) {
        val opacity = simulation.morphProgress.toFloat().coerceIn(0f, 1f)
        if (opacity <= 0f) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && canvas.isHardwareAccelerated) {
            drawHardwareBlurredStage(canvas, simulation, width, height, scale, opacity)
            return
        }
        // API 26–30: preserve the crossfade and geometry without pretending alpha blur is RGB blur.
        val stagePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        stagePaint.alpha = (opacity * 255f).roundToInt()
        canvas.saveLayer(0f, 0f, width, height, stagePaint)
        drawCameraScene(canvas, simulation, width, height, scale)
        drawPot(canvas, simulation, scale, includeOpacity = false)
        canvas.restore()
    }

    private fun drawHardwareBlurredStage(
        canvas: Canvas,
        simulation: GumballCrankSimulation,
        width: Float,
        height: Float,
        scale: Float,
        opacity: Float,
    ) {
        val nodeWidth = max(1, width.roundToInt())
        val nodeHeight = max(1, height.roundToInt())
        val node = stageRenderNode ?: RenderNode("gumball-crank-stage").also { stageRenderNode = it }
        node.setPosition(0, 0, nodeWidth, nodeHeight)
        val recording = node.beginRecording(nodeWidth, nodeHeight)
        drawCameraScene(recording, simulation, width, height, scale)
        drawPot(recording, simulation, scale, includeOpacity = false)
        node.endRecording()
        val blur = Tokens.UP.blur.toFloat() * scale
        node.setRenderEffect(RenderEffect.createBlurEffect(blur, blur, Shader.TileMode.CLAMP))
        node.setAlpha(opacity)
        canvas.drawRenderNode(node)
    }

    private fun drawScrim(canvas: Canvas, simulation: GumballCrankSimulation, width: Float, height: Float) {
        val opacity = Tokens.UP.scrim.toFloat() * simulation.morphProgress.toFloat().coerceIn(0f, 1f)
        if (opacity <= 0f) return
        fillPaint.color = Tokens.UP.scrimColor.toArgb()
        fillPaint.alpha = (opacity * 255f).roundToInt()
        canvas.drawRect(0f, 0f, width, height, fillPaint)
        fillPaint.alpha = 255
    }

    private fun drawConfetti(canvas: Canvas, simulation: GumballCrankSimulation, scale: Float) {
        val opacity = simulation.confettiOpacity.toFloat().coerceIn(0f, 1f)
        if (opacity <= 0f) return
        simulation.confetti.forEachIndexed { index, piece ->
            val size = (
                Tokens.Motion.CONFETTI.size[0] +
                    ((index * Tokens.RENDER.confettiSizeMultiplier) % Tokens.RENDER.confettiSizeModulo) *
                    ((Tokens.Motion.CONFETTI.size[1] - Tokens.Motion.CONFETTI.size[0]) / Tokens.RENDER.confettiSizeDivisions)
            ).toFloat() * scale * Tokens.RENDER.confettiScale
            val sprite = when (index % Tokens.UP.confettiKinds) {
                0 -> assets.slicePeach
                1 -> assets.slicePassion
                else -> assets.peel
            }
            canvas.save()
            canvas.translate(piece.x.toFloat(), piece.y.toFloat())
            canvas.rotate(Math.toDegrees(piece.rotation).toFloat())
            imagePaint.alpha = (opacity * 255f).roundToInt()
            drawBitmapContain(canvas, sprite, -size / 2f, -size / 2f, size, size)
            imagePaint.alpha = 255
            canvas.restore()
        }
    }

    private fun drawCoupon(canvas: Canvas, simulation: GumballCrankSimulation, scale: Float) {
        val width = simulation.couponWidth.toFloat() * scale
        val height = simulation.couponHeight.toFloat() * scale
        if (width <= 0f || height <= 0f) return
        canvas.save()
        canvas.translate(simulation.couponX.toFloat() * scale, simulation.couponY.toFloat() * scale)
        canvas.rotate(Math.toDegrees(simulation.couponRotation).toFloat())
        val panelOpacity = simulation.panelOpacity.toFloat().coerceIn(0f, 1f)
        if (panelOpacity > 0f) {
            fillPaint.color = Tokens.UP.navy.toArgb()
            fillPaint.alpha = (panelOpacity * 255f).roundToInt()
            destination.set(-width / 2f, -height / 2f, width / 2f, height / 2f)
            canvas.drawRoundRect(destination, Tokens.UP.card.r.toFloat() * scale, Tokens.UP.card.r.toFloat() * scale, fillPaint)
            fillPaint.alpha = 255
        }
        val couponOpacity = simulation.couponOpacity.toFloat().coerceIn(0f, 1f)
        if (couponOpacity > 0f) {
            imagePaint.alpha = (couponOpacity * 255f).roundToInt()
            drawBitmap(canvas, assets.coupon, -width / 2f, -height / 2f, width, height)
            imagePaint.alpha = 255
        }
        val stubOpacity = simulation.stubCoinOpacity.toFloat().coerceIn(0f, 1f)
        if (stubOpacity > 0f) {
            imagePaint.alpha = (couponOpacity * stubOpacity * 255f).roundToInt()
            val coinX = (Tokens.UP.card.w * (Tokens.UP.stub.x - 0.5)).toFloat() * scale - 20f * scale
            val coinSize = Tokens.RENDER.stubCoinSize * scale
            drawBitmap(canvas, assets.coin, coinX, -coinSize / 2f, coinSize, coinSize)
            imagePaint.alpha = 255
        }
        canvas.restore()
    }

    // RN FlyingCoin:404-423 uses eight staggered quadratic paths and 0.45 s ease-in-out travel.
    fun drawFlyingCoins(canvas: Canvas, simulation: GumballCrankSimulation, scale: Float, safeTop: Float) {
        if (simulation.reduced || simulation.phase != GumballCrankPhase.COLLECTING) return
        val fromX = Tokens.UP.card.x + Tokens.UP.card.w * Tokens.UP.stub.x
        val fromY = Tokens.UP.card.y + Tokens.UP.card.h * Tokens.UP.stub.y
        val toX = Tokens.UP.counter.left + Tokens.RENDER.flyingCoinCounterX
        val toY = safeTop / scale + Tokens.UP.counter.top + Tokens.RENDER.flyingCoinCounterY
        repeat(Tokens.Motion.UP_T.coins) { index ->
            val progress = clamp((simulation.phaseTime - index * Tokens.Motion.UP_T.coinStagger) / Tokens.Motion.UP_T.coinFly)
            if (progress <= 0.0 || progress >= 1.0) return@repeat
            val eased = easeInOut(progress)
            val controlX = (fromX + toX) / 2.0 + Tokens.RENDER.flyingCoinArcX - index * Tokens.RENDER.flyingCoinArcXStep
            val controlY = min(fromY, toY) - Tokens.RENDER.flyingCoinArcY + index * Tokens.RENDER.flyingCoinArcYStep
            val x = quadratic(fromX, controlX, toX, eased)
            val y = quadratic(fromY, controlY, toY, eased)
            val diameter = Tokens.RENDER.flyingCoinDiameter * scale
            canvas.save()
            canvas.translate(x.toFloat() * scale, y.toFloat() * scale)
            val coinScale = 1f - Tokens.RENDER.flyingCoinShrink * eased.toFloat()
            canvas.scale(coinScale, coinScale)
            drawBitmap(canvas, assets.coin, -diameter / 2f, -diameter / 2f, diameter, diameter)
            canvas.restore()
        }
    }

    private fun drawBitmap(canvas: Canvas, bitmap: android.graphics.Bitmap, left: Float, top: Float, width: Float, height: Float, paint: Paint = imagePaint) {
        source.set(0, 0, bitmap.width, bitmap.height)
        destination.set(left, top, left + width, top + height)
        canvas.drawBitmap(bitmap, source, destination, paint)
    }

    private fun drawBitmapContain(canvas: Canvas, bitmap: android.graphics.Bitmap, left: Float, top: Float, width: Float, height: Float) {
        val imageRatio = bitmap.width.toFloat() / max(1, bitmap.height)
        val boxRatio = width / max(1f, height)
        val drawWidth: Float
        val drawHeight: Float
        if (imageRatio > boxRatio) {
            drawWidth = width
            drawHeight = width / imageRatio
        } else {
            drawHeight = height
            drawWidth = height * imageRatio
        }
        drawBitmap(canvas, bitmap, left + (width - drawWidth) / 2f, top + (height - drawHeight) / 2f, drawWidth, drawHeight)
    }

    private fun clamp(value: Double) = value.coerceIn(0.0, 1.0)

    private fun easeInOut(value: Double): Double = if (value < 0.5) 8.0 * value * value * value * value else 1.0 - Math.pow(-2.0 * value + 2.0, 4.0) / 2.0

    private fun quadratic(start: Double, control: Double, end: Double, progress: Double): Double {
        val inverse = 1.0 - progress
        return inverse * inverse * start + 2.0 * inverse * progress * control + progress * progress * end
    }

    private fun knobCenterX() = Tokens.REPLICA_ART.knob.x + Tokens.REPLICA_ART.knob.size / 2.0

    private fun knobCenterY() = Tokens.REPLICA_ART.knob.y + Tokens.REPLICA_ART.knob.size / 2.0
}
