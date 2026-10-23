package com.ngocdevv.nativecook.blocks.gestures.glasslockkey

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import androidx.annotation.RequiresApi
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Native Canvas equivalent of RN's retained Skia scene. Coordinates are density-independent pixels. */
internal class GlassLockKeyRenderer(
    context: Context,
    private val geometry: GlassLockKeyGeometry,
    private val density: Float,
) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val image = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val semibold = Typeface.createFromAsset(context.assets, GlassLockKeyTokens.Assets.interSemibold)
    private val medium = Typeface.createFromAsset(context.assets, GlassLockKeyTokens.Assets.interMedium)
    private val face = facePath(0f)
    private val faceInner = facePath(5f)
    private val shackle = shacklePath()
    private val ring = ringPath()
    private val blade = bladePath()
    private val background = renderBackground()

    fun draw(canvas: Canvas, simulation: GlassLockKeySimulation) {
        canvas.drawBitmap(background, 0f, 0f, image)
        canvas.save()
        canvas.scale(density, density)
        drawRig(canvas, simulation)
        canvas.restore()
    }

    private fun drawRig(canvas: Canvas, simulation: GlassLockKeySimulation) {
        val centerX = geometry.width / 2.0
        val centerY = geometry.lockTop + GlassLockKeyTokens.Lock.height * geometry.scale / 2.0
        val rigScale = 1.0 - (1.0 - GlassLockKeyTokens.Sheet.shrink) * simulation.sheet
        val rise = -GlassLockKeyTokens.Sheet.rise * geometry.height * simulation.sheet
        canvas.save()
        canvas.translate(centerX.toFloat(), (centerY + rise).toFloat())
        canvas.scale(rigScale.toFloat(), rigScale.toFloat())
        canvas.translate(-centerX.toFloat(), -centerY.toFloat())

        drawContactShadows(canvas, simulation)
        if (simulation.inserted) drawKey(canvas, simulation, behindBody = true)
        drawLock(canvas, simulation)
        if (!simulation.inserted) drawKey(canvas, simulation, behindBody = false)
        canvas.restore()
    }

    private fun drawContactShadows(canvas: Canvas, simulation: GlassLockKeySimulation) {
        fill.resetForFill(GlassLockKeyTokens.Colors.shadow)
        fill.maskFilter = BlurMaskFilter(16f, BlurMaskFilter.Blur.NORMAL)
        canvas.drawOval(
            RectF(
                (geometry.faceX + 6.0 * geometry.scale).toFloat(),
                (geometry.lockBottom + 30.0 * geometry.scale).toFloat(),
                (geometry.faceX + (GlassLockKeyTokens.Lock.face + 16.0) * geometry.scale).toFloat(),
                (geometry.lockBottom + 50.0 * geometry.scale).toFloat(),
            ),
            fill,
        )
        fill.maskFilter = BlurMaskFilter(8f, BlurMaskFilter.Blur.NORMAL)
        fill.alpha = (255 * 0.8).roundToInt()
        val shadowY = simulation.ky +
            (GlassLockKeyTokens.Key.blade + GlassLockKeyTokens.Key.ring + 18.0) * geometry.scale
        val shadowScale = geometry.scale * (1.0 - 0.2 * simulation.lift)
        canvas.save()
        canvas.translate(simulation.kx.toFloat(), shadowY.toFloat())
        canvas.scale(shadowScale.toFloat(), shadowScale.toFloat())
        canvas.drawOval(RectF(-24f, 0f, 24f, 10f), fill)
        canvas.restore()
        fill.maskFilter = null
        fill.alpha = 255
    }

    private fun drawLock(canvas: Canvas, simulation: GlassLockKeySimulation) {
        val lock = GlassLockKeyTokens.Lock
        val shake = simulation.shakeOffset() * geometry.scale
        canvas.save()
        canvas.translate(
            (geometry.faceX + shake).toFloat(),
            (geometry.lockTop + simulation.bounce * geometry.scale).toFloat(),
        )
        canvas.scale(geometry.scale.toFloat(), geometry.scale.toFloat())

        canvas.save()
        canvas.translate(0f, (-lock.openLift * simulation.shackle).toFloat())
        val hingeX = lock.face / 2.0 + lock.opening / 2.0 + lock.shackle / 2.0
        canvas.rotate(
            Math.toDegrees(lock.openTilt * simulation.shackle).toFloat(),
            hingeX.toFloat(),
            lock.longLeg.toFloat(),
        )
        drawShackle(canvas)
        canvas.restore()

        drawBody(canvas, simulation)
        canvas.restore()
    }

    private fun drawShackle(canvas: Canvas) {
        val lock = GlassLockKeyTokens.Lock
        stroke.resetForStroke(lock.shackle.toFloat())
        stroke.strokeCap = Paint.Cap.BUTT
        stroke.shader = LinearGradient(
            0f,
            (-lock.shackleRise).toFloat(),
            lock.face.toFloat(),
            lock.longLeg.toFloat(),
            intArrayOf(
                GlassLockKeyTokens.Colors.steelHigh,
                GlassLockKeyTokens.Colors.steel,
                GlassLockKeyTokens.Colors.steelDeep,
            ),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawPath(shackle, stroke)
        stroke.shader = null

        canvas.save()
        canvas.translate(-8f, -4f)
        stroke.resetForStroke(3f, GlassLockKeyTokens.Colors.steelEdge)
        stroke.alpha = (255 * 0.8).roundToInt()
        stroke.maskFilter = BlurMaskFilter(1.2f, BlurMaskFilter.Blur.NORMAL)
        canvas.drawPath(shackle, stroke)
        canvas.restore()

        canvas.save()
        canvas.translate(9f, 2f)
        stroke.resetForStroke(2f, Color.argb(89, 20, 24, 28))
        stroke.maskFilter = BlurMaskFilter(1.5f, BlurMaskFilter.Blur.NORMAL)
        canvas.drawPath(shackle, stroke)
        canvas.restore()
        stroke.maskFilter = null
        stroke.alpha = 255
    }

    private fun drawBody(canvas: Canvas, simulation: GlassLockKeySimulation) {
        val lock = GlassLockKeyTokens.Lock
        for (step in SIDE_STEPS) {
            canvas.save()
            canvas.translate((lock.side * step).toFloat(), (-lock.side * 0.5 * step).toFloat())
            fill.resetForFill()
            fill.shader = LinearGradient(
                0f,
                0f,
                0f,
                lock.height.toFloat(),
                GlassLockKeyTokens.Colors.brassSide,
                GlassLockKeyTokens.Colors.brassSideDeep,
                Shader.TileMode.CLAMP,
            )
            canvas.drawPath(face, fill)
            canvas.restore()
        }

        fill.resetForFill()
        fill.shader = LinearGradient(
            0f,
            0f,
            (lock.face * 0.4).toFloat(),
            lock.height.toFloat(),
            intArrayOf(
                GlassLockKeyTokens.Colors.brassHigh,
                GlassLockKeyTokens.Colors.brass,
                GlassLockKeyTokens.Colors.brassDeep,
            ),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawPath(face, fill)

        fill.shader = RadialGradient(
            (lock.face * 0.3).toFloat(),
            (lock.height * 0.22).toFloat(),
            (lock.face * 0.7).toFloat(),
            Color.argb(140, 255, 240, 200),
            Color.TRANSPARENT,
            Shader.TileMode.CLAMP,
        )
        canvas.drawPath(face, fill)
        fill.shader = null

        canvas.save()
        canvas.clipPath(face)
        stroke.resetForStroke(0.6f)
        var y = 0
        while (y < lock.height.toInt()) {
            stroke.color = if ((y / 3) % 2 == 0) {
                Color.argb(26, 255, 240, 205)
            } else {
                Color.argb(20, 80, 50, 12)
            }
            canvas.drawLine(0f, y.toFloat(), lock.face.toFloat(), y + 0.6f, stroke)
            y += 3
        }
        drawGlint(canvas, simulation)
        canvas.restore()

        drawEngraving(canvas)
        stroke.resetForStroke(1.4f)
        stroke.shader = LinearGradient(
            0f,
            0f,
            0f,
            lock.height.toFloat(),
            intArrayOf(
                GlassLockKeyTokens.Colors.brassEdge,
                Color.argb(38, 255, 236, 190),
                Color.argb(102, 60, 36, 8),
            ),
            null,
            Shader.TileMode.CLAMP,
        )
        canvas.drawPath(face, stroke)
        stroke.shader = null
        stroke.resetForStroke(0.8f, Color.argb(56, 255, 236, 190))
        canvas.drawPath(faceInner, stroke)

        val glow = holeGlow(simulation)
        if (glow > 0.001) {
            fill.resetForFill(GlassLockKeyTokens.Colors.glow)
            fill.alpha = (Color.alpha(fill.color) * glow).roundToInt().coerceIn(0, 255)
            fill.maskFilter = BlurMaskFilter(16f, BlurMaskFilter.Blur.NORMAL)
            canvas.drawCircle(
                (lock.face / 2.0).toFloat(),
                (lock.height + lock.plugHeight / 2.0).toFloat(),
                lock.glowRadius.toFloat(),
                fill,
            )
            fill.maskFilter = null
        }

        fill.resetForFill()
        fill.shader = LinearGradient(
            (lock.face / 2.0 - lock.plugWidth / 2.0).toFloat(),
            0f,
            (lock.face / 2.0 + lock.plugWidth / 2.0).toFloat(),
            0f,
            intArrayOf(
                GlassLockKeyTokens.Colors.brassDeep,
                GlassLockKeyTokens.Colors.plug,
                GlassLockKeyTokens.Colors.brassDeep,
            ),
            null,
            Shader.TileMode.CLAMP,
        )
        canvas.drawRoundRect(
            RectF(
                (lock.face / 2.0 - lock.plugWidth / 2.0).toFloat(),
                (lock.height - 3.0).toFloat(),
                (lock.face / 2.0 + lock.plugWidth / 2.0).toFloat(),
                (lock.height + lock.plugHeight).toFloat(),
            ),
            4f,
            4f,
            fill,
        )
        fill.resetForFill(GlassLockKeyTokens.Colors.slot)
        canvas.drawRect(
            (lock.face / 2.0 - lock.slot / 2.0).toFloat(),
            (lock.height + 1.0).toFloat(),
            (lock.face / 2.0 + lock.slot / 2.0).toFloat(),
            (lock.height + lock.plugHeight - 1.0).toFloat(),
            fill,
        )
    }

    private fun drawGlint(canvas: Canvas, simulation: GlassLockKeySimulation) {
        if (simulation.glintTime <= 0.0 || simulation.glintTime >= GlassLockKeyTokens.glintDuration) return
        val progress = simulation.glintTime / GlassLockKeyTokens.glintDuration
        val opacity = sin(PI * progress)
        val x = -60.0 + (GlassLockKeyTokens.Lock.face + 120.0) * progress
        canvas.save()
        canvas.translate(x.toFloat(), -20f)
        canvas.skew(-0.35f, 0f)
        fill.resetForFill(GlassLockKeyTokens.Colors.glint)
        fill.alpha = (Color.alpha(fill.color) * opacity).roundToInt().coerceIn(0, 255)
        fill.maskFilter = BlurMaskFilter(9f, BlurMaskFilter.Blur.NORMAL)
        canvas.drawRect(0f, 0f, 30f, (GlassLockKeyTokens.Lock.height + 40.0).toFloat(), fill)
        fill.maskFilter = null
        canvas.restore()
    }

    private fun drawEngraving(canvas: Canvas) {
        val faceWidth = GlassLockKeyTokens.Lock.face.toFloat()
        val faceHeight = GlassLockKeyTokens.Lock.height.toFloat()
        fill.resetForFill(GlassLockKeyTokens.Colors.engrave)
        fill.typeface = semibold
        fill.textSize = 13f
        val widths = MARK.map { fill.measureText(it.toString()) }
        val markWidth = widths.sum() + 4f * (MARK.length - 1)
        var x = faceWidth / 2f - markWidth / 2f
        MARK.forEachIndexed { index, char ->
            fill.color = GlassLockKeyTokens.Colors.engraveLight
            canvas.drawText(char.toString(), x, faceHeight * 0.42f + 0.8f, fill)
            fill.color = GlassLockKeyTokens.Colors.engrave
            canvas.drawText(char.toString(), x, faceHeight * 0.42f, fill)
            x += widths[index] + 4f
        }
        fill.typeface = medium
        fill.textSize = 7.5f
        canvas.drawText(
            SIZE,
            faceWidth / 2f - fill.measureText(SIZE) / 2f,
            faceHeight * 0.42f + 16f,
            fill,
        )
        fill.typeface = null
    }

    private fun drawKey(canvas: Canvas, simulation: GlassLockKeySimulation, behindBody: Boolean) {
        val key = GlassLockKeyTokens.Key
        val shake = if (behindBody) simulation.shakeOffset() * geometry.scale else 0.0
        val bob = if (simulation.mode == GlassLockKeySimulation.IDLE && !simulation.reduced) {
            sin(2.0 * PI * simulation.clock / GlassLockKeyTokens.Carry.bobPeriod) *
                GlassLockKeyTokens.Carry.bob * geometry.scale
        } else {
            0.0
        }
        val tilt = if (simulation.mode == GlassLockKeySimulation.CARRYING) {
            (simulation.kvx * 0.0003).coerceIn(-0.12, 0.12)
        } else {
            0.0
        }
        val scale = geometry.scale *
            (1.0 + (GlassLockKeyTokens.Carry.lift - 1.0) * simulation.lift)
        canvas.save()
        canvas.translate((simulation.kx + shake).toFloat(), (simulation.ky + bob).toFloat())
        canvas.rotate(Math.toDegrees(tilt).toFloat())
        canvas.scale(scale.toFloat(), scale.toFloat())

        fill.resetForFill()
        fill.shader = LinearGradient(
            (-key.shaft).toFloat(),
            0f,
            (key.shaft + key.bitWidth).toFloat(),
            0f,
            intArrayOf(
                GlassLockKeyTokens.Colors.steelDeep,
                GlassLockKeyTokens.Colors.steelHigh,
                GlassLockKeyTokens.Colors.steel,
                GlassLockKeyTokens.Colors.steelDeep,
            ),
            floatArrayOf(0f, 0.35f, 0.6f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawPath(blade, fill)
        stroke.resetForStroke(0.8f, Color.argb(128, 30, 34, 38))
        canvas.drawPath(blade, stroke)

        val visualTheta = simulation.theta * GlassLockKeyTokens.Turn.visual
        val turnCos = cos(visualTheta)
        val turnSin = sin(visualTheta)
        val ringY = key.blade + key.ring / 2.0 - 2.0
        val widthScale = max(0.02, abs(turnCos)).toFloat()
        val edgeAlpha = min(1.0, abs(turnSin) * 1.6).toFloat()
        if (edgeAlpha > 0f) {
            drawRing(canvas, ringY, widthScale, (7.0 * turnSin).toFloat(), GlassLockKeyTokens.Colors.steelDeep, edgeAlpha)
            drawRing(canvas, ringY, widthScale, (3.5 * turnSin).toFloat(), GlassLockKeyTokens.Colors.steel, edgeAlpha)
        }
        canvas.save()
        canvas.translate(0f, ringY.toFloat())
        canvas.scale(widthScale, 1f)
        canvas.translate(0f, -ringY.toFloat())
        fill.resetForFill()
        fill.shader = LinearGradient(
            (-key.ring / 2.0).toFloat(),
            (ringY - key.ring / 2.0).toFloat(),
            (key.ring / 2.0).toFloat(),
            (ringY + key.ring / 2.0).toFloat(),
            intArrayOf(
                GlassLockKeyTokens.Colors.steelHigh,
                GlassLockKeyTokens.Colors.steel,
                GlassLockKeyTokens.Colors.steelDeep,
            ),
            null,
            Shader.TileMode.CLAMP,
        )
        canvas.drawPath(ring, fill)
        stroke.resetForStroke(1f, GlassLockKeyTokens.Colors.steelEdge)
        stroke.alpha = (255 * 0.7).roundToInt()
        canvas.drawPath(ring, stroke)
        stroke.alpha = 255
        canvas.restore()
        canvas.restore()
    }

    private fun drawRing(
        canvas: Canvas,
        centerY: Double,
        scaleX: Float,
        translateX: Float,
        color: Int,
        alpha: Float,
    ) {
        canvas.save()
        canvas.translate(translateX, centerY.toFloat())
        canvas.scale(scaleX, 1f)
        canvas.translate(0f, -centerY.toFloat())
        fill.resetForFill(color)
        fill.alpha = (255f * alpha).roundToInt()
        canvas.drawPath(ring, fill)
        canvas.restore()
    }

    private fun holeGlow(simulation: GlassLockKeySimulation): Double {
        if (simulation.mode == GlassLockKeySimulation.SEATED) {
            return 0.45 + 0.35 * (simulation.theta / 1.57).coerceIn(0.0, 1.0)
        }
        if (simulation.mode != GlassLockKeySimulation.CARRYING) return 0.0
        val distance = kotlin.math.hypot(
            simulation.kx - geometry.plugX,
            simulation.ky - geometry.plugY,
        )
        return (1.0 - distance /
            (GlassLockKeyTokens.Carry.magnet * 2.0 * geometry.scale)).coerceIn(0.0, 1.0) * 0.8
    }

    private fun renderBackground(): Bitmap {
        val width = max(1, (geometry.width * density).roundToInt())
        val height = max(1, (geometry.height * density).roundToInt())
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            drawShaderBackground(canvas, width, height)
        } else {
            drawFallbackBackground(canvas, width, height)
        }
        return bitmap
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun drawShaderBackground(canvas: Canvas, width: Int, height: Int) {
        val shader = RuntimeShader(BACKGROUND_SHADER)
        shader.setFloatUniform("resolution", width.toFloat(), height.toFloat())
        shader.setFloatUniform(
            "poolCenter",
            width / 2f,
            ((geometry.lockTop - 40.0 * geometry.scale) * density).toFloat(),
        )
        shader.setFloatUniform("poolRadius", (geometry.width * 0.85 * density).toFloat())
        fill.resetForFill()
        fill.shader = shader
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fill)
        fill.shader = null
    }

    private fun drawFallbackBackground(canvas: Canvas, width: Int, height: Int) {
        fill.resetForFill()
        fill.shader = LinearGradient(
            0f,
            0f,
            0f,
            height.toFloat(),
            GlassLockKeyTokens.Colors.bgTop,
            GlassLockKeyTokens.Colors.bgBottom,
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fill)
        fill.shader = RadialGradient(
            width / 2f,
            ((geometry.lockTop - 40.0 * geometry.scale) * density).toFloat(),
            (geometry.width * 0.85 * density).toFloat(),
            GlassLockKeyTokens.Colors.pool,
            Color.TRANSPARENT,
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fill)
        fill.shader = null
    }

    private fun facePath(inset: Float): Path = Path().apply {
        val width = GlassLockKeyTokens.Lock.face.toFloat() - inset * 2f
        val height = GlassLockKeyTokens.Lock.height.toFloat() - inset * 2f
        val radius = max(4f, GlassLockKeyTokens.Lock.radius.toFloat() - inset)
        addRoundRect(RectF(inset, inset, inset + width, inset + height), radius, radius, Path.Direction.CW)
    }

    private fun shacklePath(): Path {
        val lock = GlassLockKeyTokens.Lock
        val half = lock.opening / 2.0 + lock.shackle / 2.0
        val centerX = lock.face / 2.0
        val top = -lock.shackleRise + lock.shackle / 2.0
        val arcY = top + half
        return Path().apply {
            moveTo((centerX - half).toFloat(), lock.shortLeg.toFloat())
            lineTo((centerX - half).toFloat(), arcY.toFloat())
            arcTo(
                RectF(
                    (centerX - half).toFloat(),
                    top.toFloat(),
                    (centerX + half).toFloat(),
                    (top + half * 2.0).toFloat(),
                ),
                180f,
                180f,
                false,
            )
            lineTo((centerX + half).toFloat(), lock.longLeg.toFloat())
        }
    }

    private fun ringPath(): Path {
        val key = GlassLockKeyTokens.Key
        val centerY = key.blade + key.ring / 2.0 - 2.0
        return Path().apply {
            fillType = Path.FillType.EVEN_ODD
            addCircle(0f, centerY.toFloat(), (key.ring / 2.0).toFloat(), Path.Direction.CW)
            addCircle(0f, centerY.toFloat(), (key.ring / 2.0 - key.rim).toFloat(), Path.Direction.CW)
        }
    }

    private fun bladePath(): Path {
        val key = GlassLockKeyTokens.Key
        val halfWidth = key.shaft / 2.0
        return Path().apply {
            moveTo((-halfWidth).toFloat(), key.blade.toFloat())
            lineTo((-halfWidth).toFloat(), 4f)
            quadTo((-halfWidth).toFloat(), 0f, 0f, 0f)
            quadTo(halfWidth.toFloat(), 0f, halfWidth.toFloat(), 4f)
            lineTo(halfWidth.toFloat(), key.bitFromTip.toFloat())
            lineTo((halfWidth + key.bitWidth).toFloat(), key.bitFromTip.toFloat())
            lineTo((halfWidth + key.bitWidth).toFloat(), (key.bitFromTip + 9.0).toFloat())
            lineTo((halfWidth + key.bitWidth - 6.0).toFloat(), (key.bitFromTip + 9.0).toFloat())
            lineTo((halfWidth + key.bitWidth - 6.0).toFloat(), (key.bitFromTip + 15.0).toFloat())
            lineTo((halfWidth + key.bitWidth).toFloat(), (key.bitFromTip + 15.0).toFloat())
            lineTo((halfWidth + key.bitWidth).toFloat(), (key.bitFromTip + key.bitHeight).toFloat())
            lineTo(halfWidth.toFloat(), (key.bitFromTip + key.bitHeight).toFloat())
            lineTo(halfWidth.toFloat(), key.blade.toFloat())
            close()
            addRoundRect(
                RectF(
                    (-key.collar / 2.0).toFloat(),
                    (key.blade - 8.0).toFloat(),
                    (key.collar / 2.0).toFloat(),
                    key.blade.toFloat(),
                ),
                3f,
                3f,
                Path.Direction.CW,
            )
        }
    }

    private fun Paint.resetForFill(color: Int = Color.BLACK) {
        reset()
        isAntiAlias = true
        style = Paint.Style.FILL
        this.color = color
    }

    private fun Paint.resetForStroke(width: Float, color: Int = Color.BLACK) {
        reset()
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = width
        this.color = color
    }

    private companion object {
        const val MARK = "HALDEN"
        const val SIZE = "40 MM · HARDENED"
        val SIDE_STEPS = doubleArrayOf(1.0, 0.85, 0.7, 0.55, 0.4, 0.25, 0.1)

        const val BACKGROUND_SHADER = """
            uniform float2 resolution;
            uniform float2 poolCenter;
            uniform float poolRadius;
            half4 main(float2 position) {
                float t = clamp(position.y / resolution.y, 0.0, 1.0);
                half3 top = half3(0.062745, 0.086275, 0.078431);
                half3 bottom = half3(0.039216, 0.050980, 0.047059);
                float2 delta = (position - poolCenter) / poolRadius;
                half pool = half(max(0.0, 1.0 - length(delta)) * 0.10);
                half3 base = mix(top, bottom, half(t));
                return half4(base + half3(0.807843, 0.886275, 0.839216) * pool, 1.0);
            }
        """
    }
}
