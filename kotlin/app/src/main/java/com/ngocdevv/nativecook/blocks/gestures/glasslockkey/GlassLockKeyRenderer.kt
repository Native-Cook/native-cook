package com.ngocdevv.nativecook.blocks.gestures.glasslockkey

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

/** Native Canvas port of the Skia section in GlassUpgrade.tsx. All inputs are dp. */
internal class GlassLockKeyRenderer(
    private val assets: GlassLockKeyAssets,
) {
    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val source = Rect()
    private val destination = RectF()
    private val matrix = Matrix()
    private val srcAtop = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
    private val background = parseColor(GlassLockKeyTokens.REPLICA_BG)
    private val glow = parseColor(GlassLockKeyTokens.UPG.glow.color)

    fun draw(
        canvas: Canvas,
        simulation: GlassLockKeySimulation,
        width: Float,
        height: Float,
    ) {
        if (width <= 0f || height <= 0f) return
        fillPaint.reset()
        fillPaint.isAntiAlias = true
        fillPaint.color = background
        canvas.drawRect(0f, 0f, width, height, fillPaint)

        val scale = simulation.s.toFloat()
        val fittedScale = width / GlassLockKeyTokens.RENDER.sourceWidth.toFloat()
        val insert = insertProgress(simulation)
        val seatedOpacity = clamp(
            (insert - GlassLockKeyTokens.RENDER.seatedBlendStart) /
                GlassLockKeyTokens.RENDER.seatedBlendSpan,
        )
        val lockOpacity = max(simulation.show, seatedOpacity).toFloat().coerceIn(0f, 1f)

        drawRoomGlow(canvas, simulation, scale, lockOpacity)

        canvas.save()
        canvas.concat(rigMatrix(simulation, scale))
        drawLock(canvas, simulation, width, height, scale, fittedScale, lockOpacity)
        drawTick(canvas, simulation, scale)
        drawSeatedKey(canvas, simulation, width, height, fittedScale, seatedOpacity)
        canvas.restore()

        drawLooseKey(canvas, simulation, width, height, scale, fittedScale, insert)
    }

    private fun drawRoomGlow(
        canvas: Canvas,
        simulation: GlassLockKeySimulation,
        scale: Float,
        lockOpacity: Float,
    ) {
        val opacity = lockOpacity * (
            GlassLockKeyTokens.RENDER.glowBaseOpacity.toFloat() +
                GlassLockKeyTokens.RENDER.glowSheetOpacity.toFloat() * simulation.sheet.toFloat()
            )
        if (opacity <= 0f) return
        val centerX = GlassLockKeyTokens.UPG.lockCentre.x.toFloat() * scale
        val centerY = GlassLockKeyTokens.UPG.lockCentre.y.toFloat() * scale
        val radius = GlassLockKeyTokens.UPG.glow.r.toFloat() * scale
        fillPaint.reset()
        fillPaint.isAntiAlias = true
        fillPaint.shader = RadialGradient(
            centerX,
            centerY,
            radius,
            intArrayOf(withOpacity(glow, opacity), Color.argb(0, 80, 96, 255)),
            null,
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(centerX, centerY, radius, fillPaint)
        fillPaint.shader = null
    }

    private fun drawLock(
        canvas: Canvas,
        simulation: GlassLockKeySimulation,
        width: Float,
        height: Float,
        scale: Float,
        fittedScale: Float,
        opacity: Float,
    ) {
        if (opacity <= 0f) return
        canvas.saveLayerAlpha(0f, 0f, width, height, alpha(opacity))
        canvas.concat(appearMatrix(simulation, scale, opacity))
        canvas.concat(fitMatrix(GlassLockKeyTokens.REPLICA_FIT.lock, GlassLockKeyTokens.REPLICA_FIT.lockCentre, fittedScale))

        drawBitmap(canvas, assets.shadow, width, height)

        val shackle = simulation.shackle.toFloat()
        val closedOpacity = 1f - shackle.coerceIn(0f, 1f)
        if (closedOpacity > 0f) {
            canvas.saveLayerAlpha(0f, 0f, width, height, alpha(closedOpacity))
            canvas.translate(
                (GlassLockKeyTokens.REPLICA_FIT.shackleClosed[0] +
                    GlassLockKeyTokens.RENDER.shackleTravelX * simulation.shackle).toFloat() * fittedScale,
                (GlassLockKeyTokens.REPLICA_FIT.shackleClosed[1] -
                    GlassLockKeyTokens.RENDER.shackleTravelY * simulation.shackle).toFloat() * fittedScale,
            )
            drawBitmap(canvas, assets.shackleClosed, width, height)
            canvas.restore()
        }
        val openOpacity = shackle.coerceIn(0f, 1f)
        if (openOpacity > 0f) {
            canvas.saveLayerAlpha(0f, 0f, width, height, alpha(openOpacity))
            canvas.translate(
                (GlassLockKeyTokens.REPLICA_FIT.shackleOpen[0] -
                    GlassLockKeyTokens.RENDER.shackleTravelX * (1.0 - simulation.shackle)).toFloat() * fittedScale,
                (GlassLockKeyTokens.REPLICA_FIT.shackleOpen[1] +
                    GlassLockKeyTokens.RENDER.shackleTravelY * (1.0 - simulation.shackle)).toFloat() * fittedScale,
            )
            drawBitmap(canvas, assets.shackleOpen, width, height)
            canvas.restore()
        }

        // Body and glint share an isolated layer, matching Skia's `layer` + srcATop clip.
        canvas.saveLayer(0f, 0f, width, height, null)
        drawBitmap(canvas, assets.body, width, height)
        drawGlint(canvas, simulation, scale)
        canvas.restore()
        canvas.restore()
    }

    private fun drawGlint(
        canvas: Canvas,
        simulation: GlassLockKeySimulation,
        scale: Float,
    ) {
        if (simulation.glint < 0.0) return
        val progress = simulation.glint / GlassLockKeyTokens.GM.glint
        val opacity = sin(PI * progress).toFloat() * GlassLockKeyTokens.RENDER.glintOpacity.toFloat()
        if (opacity <= 0f) return
        val x = (
            GlassLockKeyTokens.UPG.lockCentre.x +
                GlassLockKeyTokens.RENDER.glintStartOffset +
                GlassLockKeyTokens.RENDER.glintTravel * progress
            ).toFloat() * scale
        val y = (
            GlassLockKeyTokens.UPG.lockCentre.y + GlassLockKeyTokens.RENDER.glintTopOffset
            ).toFloat() * scale
        val bandWidth = GlassLockKeyTokens.RENDER.glintWidth.toFloat() * scale
        val bandHeight = GlassLockKeyTokens.RENDER.glintHeight.toFloat() * scale
        canvas.save()
        matrix.setValues(
            floatArrayOf(
                1f, tan(GlassLockKeyTokens.RENDER.glintSkew).toFloat(), x,
                0f, 1f, y,
                0f, 0f, 1f,
            ),
        )
        canvas.concat(matrix)
        fillPaint.reset()
        fillPaint.isAntiAlias = true
        fillPaint.shader = LinearGradient(
            0f,
            0f,
            bandWidth,
            0f,
            intArrayOf(
                Color.argb(0, 255, 255, 255),
                Color.argb(
                    (GlassLockKeyTokens.RENDER.glintCenterAlpha.toFloat() * opacity * 255f).roundToInt(),
                    255,
                    255,
                    255,
                ),
                Color.argb(0, 255, 255, 255),
            ),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP,
        )
        fillPaint.xfermode = srcAtop
        canvas.drawRect(0f, 0f, bandWidth, bandHeight, fillPaint)
        fillPaint.xfermode = null
        fillPaint.shader = null
        canvas.restore()
    }

    private fun drawTick(
        canvas: Canvas,
        simulation: GlassLockKeySimulation,
        scale: Float,
    ) {
        if (simulation.tick < 0.0) return
        val progress = simulation.tick / GlassLockKeyTokens.GM.tick.duration
        val opacity = GlassLockKeyTokens.GM.tick.alpha * (1.0 - progress) * (1.0 - progress)
        if (opacity <= 0.0) return
        val centerX = GlassLockKeyTokens.UPG.plug.x.toFloat() * scale
        val centerY = GlassLockKeyTokens.UPG.plug.y.toFloat() * scale
        val radius = GlassLockKeyTokens.RENDER.tickRadius.toFloat() * scale
        fillPaint.reset()
        fillPaint.isAntiAlias = true
        fillPaint.shader = RadialGradient(
            centerX,
            centerY,
            radius,
            intArrayOf(
                Color.argb((opacity * 255.0).roundToInt(), 255, 255, 255),
                Color.argb(0, 255, 255, 255),
            ),
            null,
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(centerX, centerY, radius, fillPaint)
        fillPaint.shader = null
    }

    private fun drawSeatedKey(
        canvas: Canvas,
        simulation: GlassLockKeySimulation,
        width: Float,
        height: Float,
        fittedScale: Float,
        seatedOpacity: Double,
    ) {
        if (seatedOpacity <= 0.0) return
        val turn = clamp(simulation.th)
        val fit = mixFit(
            GlassLockKeyTokens.REPLICA_FIT.keyClosed,
            GlassLockKeyTokens.REPLICA_FIT.keyOpen,
            clamp(simulation.th, GlassLockKeyTokens.RENDER.keyTurnMinimum, 1.0),
        )
        canvas.save()
        canvas.concat(fitMatrix(fit, GlassLockKeyTokens.REPLICA_FIT.keyCentre, fittedScale))
        drawBitmapAlpha(canvas, assets.keyInA, width, height, seatedOpacity * (1.0 - turn))
        drawBitmapAlpha(canvas, assets.keyIn, width, height, seatedOpacity * turn)
        canvas.restore()
    }

    private fun drawLooseKey(
        canvas: Canvas,
        simulation: GlassLockKeySimulation,
        width: Float,
        height: Float,
        scale: Float,
        fittedScale: Float,
        insert: Double,
    ) {
        val opacity = 1.0 - clamp(
            (insert - GlassLockKeyTokens.RENDER.seatedBlendStart) /
                GlassLockKeyTokens.RENDER.seatedBlendSpan,
        )
        if (opacity <= 0.0) return
        val resting = simulation.mode == GlassLockKeySimulation.IDLE &&
            simulation.lift < GlassLockKeyTokens.RENDER.restingLiftThreshold &&
            !simulation.reduced
        val spinProgress = if (resting) {
            clamp((simulation.clock % GlassLockKeyTokens.GM.spin.every) / GlassLockKeyTokens.GM.spin.turn)
        } else {
            0.0
        }
        val spin = max(GlassLockKeyTokens.RENDER.spinMinimumScale, abs(cos(PI * spinProgress)))
        val x = simulation.kx + (GlassLockKeyTokens.UPG.plug.x * simulation.s - simulation.kx) * insert
        val y = simulation.ky + (GlassLockKeyTokens.UPG.plug.y * simulation.s - simulation.ky) * insert
        val heldScale = 1.0 + GlassLockKeyTokens.RENDER.looseLiftScale * simulation.lift
        canvas.saveLayerAlpha(0f, 0f, width, height, alpha(opacity.toFloat()))
        canvas.concat(
            centeredTransform(
                targetX = x.toFloat(),
                targetY = y.toFloat(),
                angle = (GlassLockKeyTokens.UPG.insertAngle * insert).toFloat(),
                scaleX = (heldScale * spin).toFloat(),
                scaleY = heldScale.toFloat(),
                centerX = GlassLockKeyTokens.UPG.keyFront.x.toFloat() * scale,
                centerY = GlassLockKeyTokens.UPG.keyFront.y.toFloat() * scale,
            ),
        )
        canvas.concat(fitMatrix(GlassLockKeyTokens.REPLICA_FIT.front, GlassLockKeyTokens.REPLICA_FIT.frontCentre, fittedScale))
        drawBitmap(canvas, assets.keyFront, width, height)
        canvas.restore()
    }

    private fun rigMatrix(simulation: GlassLockKeySimulation, scale: Float): Matrix {
        val centerX = GlassLockKeyTokens.UPG.lockCentre.x.toFloat() * scale
        val centerY = GlassLockKeyTokens.UPG.lockCentre.y.toFloat() * scale
        val shake = if (simulation.shake < 0.0) {
            0.0
        } else {
            val progress = simulation.shake / GlassLockKeyTokens.GM.shake.duration
            GlassLockKeyTokens.GM.shake.amp * (1.0 - progress) * sin(2.0 * PI * GlassLockKeyTokens.GM.shake.cycles * progress)
        }
        val recoil = if (simulation.glint < 0.0 || simulation.reduced) {
            0.0
        } else {
            GlassLockKeyTokens.GM.recoil.amp * exp(-GlassLockKeyTokens.GM.recoil.decay * simulation.glint) *
                sin(2.0 * PI * GlassLockKeyTokens.GM.recoil.hz * simulation.glint)
        }
        val groupScale = (1.0 - (1.0 - GlassLockKeyTokens.GM.shrink) * simulation.sheet).toFloat()
        return centeredTransform(
            targetX = centerX + shake.toFloat() * scale,
            targetY = centerY - GlassLockKeyTokens.GM.rise.toFloat() * scale * simulation.sheet.toFloat() + recoil.toFloat() * scale,
            angle = 0f,
            scaleX = groupScale,
            scaleY = groupScale,
            centerX = centerX,
            centerY = centerY,
        )
    }

    private fun appearMatrix(
        simulation: GlassLockKeySimulation,
        scale: Float,
        lockOpacity: Float,
    ): Matrix {
        val appear = if (simulation.reduced) 1f else {
            (GlassLockKeyTokens.GM.appear + (1.0 - GlassLockKeyTokens.GM.appear) * lockOpacity).toFloat()
        }
        val centerX = GlassLockKeyTokens.UPG.plug.x.toFloat() * scale
        val centerY = GlassLockKeyTokens.UPG.plug.y.toFloat() * scale
        return centeredTransform(centerX, centerY, 0f, appear, appear, centerX, centerY)
    }

    /** T(target) · R(angle) · S(x,y) · T(-centre), matching the Skia transform array order. */
    private fun centeredTransform(
        targetX: Float,
        targetY: Float,
        angle: Float,
        scaleX: Float,
        scaleY: Float,
        centerX: Float,
        centerY: Float,
    ): Matrix {
        val cosine = cos(angle)
        val sine = sin(angle)
        val a = cosine * scaleX
        val b = sine * scaleX
        val c = -sine * scaleY
        val d = cosine * scaleY
        matrix.setValues(
            floatArrayOf(
                a, c, targetX - a * centerX - c * centerY,
                b, d, targetY - b * centerX - d * centerY,
                0f, 0f, 1f,
            ),
        )
        return Matrix(matrix)
    }

    /** T((centre+offset)k) · R(-degrees) · Kx(-atan(shear/sy)) · S · T(-centre*k). */
    private fun fitMatrix(fit: DoubleArray, center: DoubleArray, fittedScale: Float): Matrix {
        val scaleX = fit[0].toFloat()
        val scaleY = fit[1].toFloat()
        val angle = (-fit[2] * PI / 180.0).toFloat()
        val shear = if (fit.size > 5) fit[5].toFloat() else 0f
        val cosine = cos(angle)
        val sine = sin(angle)
        val skewTimesY = tan(-kotlin.math.atan((shear / scaleY).toDouble())).toFloat() * scaleY
        val a = cosine * scaleX
        val b = sine * scaleX
        val c = cosine * skewTimesY - sine * scaleY
        val d = sine * skewTimesY + cosine * scaleY
        val centerX = center[0].toFloat() * fittedScale
        val centerY = center[1].toFloat() * fittedScale
        val targetX = (center[0] + fit[3]).toFloat() * fittedScale
        val targetY = (center[1] + fit[4]).toFloat() * fittedScale
        matrix.setValues(
            floatArrayOf(
                a, c, targetX - a * centerX - c * centerY,
                b, d, targetY - b * centerX - d * centerY,
                0f, 0f, 1f,
            ),
        )
        return Matrix(matrix)
    }

    private fun mixFit(first: DoubleArray, second: DoubleArray, progress: Double): DoubleArray {
        val firstShear = if (first.size > 5) first[5] else 0.0
        val secondShear = if (second.size > 5) second[5] else 0.0
        return doubleArrayOf(
            mix(first[0], second[0], progress),
            mix(first[1], second[1], progress),
            mix(first[2], second[2], progress),
            mix(first[3], second[3], progress),
            mix(first[4], second[4], progress),
            mix(firstShear, secondShear, progress),
        )
    }

    private fun insertProgress(simulation: GlassLockKeySimulation): Double {
        if (simulation.mode == GlassLockKeySimulation.INSERTING) {
            if (simulation.reduced) return 1.0
            return easeInOut(clamp(simulation.modeT / GlassLockKeyTokens.GM.insert))
        }
        if (simulation.mode == GlassLockKeySimulation.SEATED ||
            simulation.mode == GlassLockKeySimulation.OPEN ||
            simulation.mode == GlassLockKeySimulation.CLOSING
        ) {
            return 1.0
        }
        return 0.0
    }

    private fun drawBitmap(canvas: Canvas, bitmap: Bitmap, width: Float, height: Float) {
        imagePaint.alpha = 255
        source.set(0, 0, bitmap.width, bitmap.height)
        destination.set(0f, 0f, width, height)
        canvas.drawBitmap(bitmap, source, destination, imagePaint)
    }

    private fun drawBitmapAlpha(
        canvas: Canvas,
        bitmap: Bitmap,
        width: Float,
        height: Float,
        opacity: Double,
    ) {
        val value = clamp(opacity)
        if (value <= 0.0) return
        imagePaint.alpha = alpha(value.toFloat())
        source.set(0, 0, bitmap.width, bitmap.height)
        destination.set(0f, 0f, width, height)
        canvas.drawBitmap(bitmap, source, destination, imagePaint)
        imagePaint.alpha = 255
    }

    private fun parseColor(value: String): Int {
        if (value.startsWith("#")) return Color.parseColor(value)
        val channels = value.substringAfter('(').substringBefore(')').split(',').map(String::trim)
        val red = channels[0].toInt()
        val green = channels[1].toInt()
        val blue = channels[2].toInt()
        val alpha = (channels[3].toDouble() * 255.0).roundToInt()
        return Color.argb(alpha, red, green, blue)
    }

    private fun withOpacity(color: Int, opacity: Float): Int {
        val combinedAlpha = (Color.alpha(color) * opacity).roundToInt().coerceIn(0, 255)
        return Color.argb(combinedAlpha, Color.red(color), Color.green(color), Color.blue(color))
    }

    private fun alpha(opacity: Float): Int = (opacity.coerceIn(0f, 1f) * 255f).roundToInt()

    private fun mix(first: Double, second: Double, progress: Double): Double {
        return first + (second - first) * progress
    }

    private fun clamp(value: Double, low: Double = 0.0, high: Double = 1.0): Double {
        return value.coerceIn(low, high)
    }

    private fun easeInOut(value: Double): Double {
        return if (value < 0.5) {
            4.0 * value * value * value
        } else {
            1.0 - (-2.0 * value + 2.0).pow(3.0) / 2.0
        }
    }
}
