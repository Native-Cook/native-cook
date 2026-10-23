package com.ngocdevv.nativecook.blocks.forms.keycapmoodinput

import androidx.annotation.RequiresApi
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import com.ngocdevv.nativecook.R
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round

private fun Double.f() = toFloat()
private fun keycapSrgb(value: Double): Double = if (value <= 0.0031308) value * 12.92 else 1.055 * max(0.0, value).pow(1 / 2.4) - 0.055
private fun keycapArgb(rgb: DoubleArray, alpha: Double = 1.0): Int = Color.argb(
    (alpha.coerceIn(0.0, 1.0) * 255).toInt(),
    (keycapSrgb(rgb[0]).coerceIn(0.0, 1.0) * 255).toInt(),
    (keycapSrgb(rgb[1]).coerceIn(0.0, 1.0) * 255).toInt(),
    (keycapSrgb(rgb[2]).coerceIn(0.0, 1.0) * 255).toInt(),
)
private fun DoubleArray.floats() = FloatArray(size) { this[it].f() }

/** A single hardware canvas keeps shaders, lit wells, and dot legends on the same frame clock. */
internal class KeycapView(
    context: Context,
    private val reduced: Boolean,
    private val autoPlay: Boolean,
    private val onTouch: () -> Unit,
    private val onEvents: (KeycapMotion) -> Unit,
) : View(context) {
    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val dotPaint = Paint().apply { isAntiAlias = false }
    private val legendBlurPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fallbackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val opaque = bitmap(R.drawable.keycapmoodinput_opaque)
    private val glassLight = bitmap(R.drawable.keycapmoodinput_glass_light)
    private val glass = bitmap(R.drawable.keycapmoodinput_glass)
    private val mini = bitmap(R.drawable.keycapmoodinput_mini)
    private val well = bitmap(R.drawable.keycapmoodinput_well_glow)
    private val sceneShader = if (Build.VERSION.SDK_INT >= 33) RuntimeShader(KEYCAP_SCENE_AGSL) else null
    private val capShaders = if (Build.VERSION.SDK_INT >= 33) Array(10) { RuntimeShader(KEYCAP_CAP_AGSL) } else emptyArray()
    private val opaqueBitmapShader = BitmapShader(opaque, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    private val glassLightBitmapShader = BitmapShader(glassLight, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    private val glassBitmapShader = BitmapShader(glass, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    private val miniBitmapShader = BitmapShader(mini, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    private val wellBitmapShader = BitmapShader(well, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    var layout = KeycapLayout(402.0, 874.0)
        private set
    var motion = KeycapMotion(layout, reduced, autoPlay)
        private set
    private var reveal = 0.0
    private var revealAge = 0.0

    init {
        isClickable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private fun bitmap(id: Int): Bitmap = resources.openRawResource(id).use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply {
            inScaled = false
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }) ?: error("Missing keycap asset $id")
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            layout = KeycapLayout(w / density.toDouble(), h / density.toDouble())
            motion = KeycapMotion(layout, reduced, autoPlay)
            revealAge = 0.0
            reveal = 0.0
            onEvents(motion)
        }
    }

    fun advance(ms: Double) {
        if (width == 0 || height == 0) return
        var remaining = ms.coerceAtLeast(0.0)
        val frameEvents = ArrayList<KeycapEvent>()
        while (remaining > 0.0) {
            val chunk = min(100.0, remaining)
            motion.step(chunk)
            frameEvents.addAll(motion.events)
            remaining -= chunk
        }
        motion.events.clear()
        motion.events.addAll(frameEvents)
        revealAge += ms
        reveal = (revealAge / 240.0).coerceIn(0.0, 1.0)
        if (motion.events.isNotEmpty()) {
            for (event in motion.events) {
                when {
                    event.mask and 15 != 0 -> performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    event.mask and 32 != 0 -> performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    event.mask and 64 != 0 -> performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    event.mask and 16 != 0 -> performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                }
            }
            onEvents(motion)
        }
        invalidate()
    }

    fun accessibilityAction(type: String, key: Int = 0, target: Int = 0) {
        onTouch()
        motion.action(type, key, target)
        onEvents(motion)
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (width == 0 || height == 0) return false
        val pointer = KeycapPointer(event.actionMasked != MotionEvent.ACTION_UP && event.actionMasked != MotionEvent.ACTION_CANCEL,
            event.x / density.toDouble(), event.y / density.toDouble())
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent.requestDisallowInterceptTouchEvent(true)
                onTouch()
                motion.touch(pointer)
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                motion.touch(pointer)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                motion.touch(pointer, event.actionMasked == MotionEvent.ACTION_CANCEL)
                onEvents(motion)
                invalidate()
                return true
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.BLACK)
        canvas.save()
        canvas.scale(density, density)
        if (Build.VERSION.SDK_INT >= 33 && sceneShader != null) {
            drawScene(canvas)
            for (i in 0..5) if (motion.out[i] > 0.001) drawCap(canvas, 4 + i, 3, i)
            for (i in 0..3) drawCap(canvas, i, i, -1)
        } else drawFallback(canvas)
        for (i in 0..3) drawLegend(canvas, i)
        canvas.restore()
    }

    private fun miniGeometry(i: Int): Triple<KeycapPoint, Double, Double> {
        val out = motion.out[i]
        val center = layout.center(3)
        val amount = if (reduced) 1.0 else out
        val target = layout.minis[i]
        val point = KeycapPoint(center.x + (target.x - center.x) * amount, center.y + (target.y - center.y) * amount)
        val scale = layout.u * (if (reduced) 1.0 else 0.6 + 0.4 * out.coerceIn(0.0, 1.0))
        val opacity = if (reduced) out.coerceIn(0.0, 1.0) else (out / 0.4).coerceIn(0.0, 1.0)
        return Triple(point, scale, opacity)
    }

    private fun mixedMood(): DoubleArray = DoubleArray(3) { c ->
        keycapMoodRgb[motion.mood.from][c] + (keycapMoodRgb[motion.mood.to][c] - keycapMoodRgb[motion.mood.from][c]) * motion.mood.p
    }

    @RequiresApi(33)
    private fun drawScene(canvas: Canvas) {
        val shader = sceneShader ?: return
        val mood = mixedMood()
        val centers = FloatArray(8)
        val colors = FloatArray(12)
        val gains = FloatArray(8)
        for (i in 0..3) {
            val center = layout.center(i)
            centers[2 * i] = center.x.f()
            centers[2 * i + 1] = center.y.f()
            for (c in 0..2) colors[3 * i + c] = when (i) {
                3 -> mood[c].f()
                1 -> (doubleArrayOf(0.0109601, 0.0251869, 1.0)[c] * 0.45).f()
                else -> (0.05 + motion.scene[c] * motion.rim[i]).f()
            }
            gains[2 * i] = when (i) {
                3 -> (0.14 + 0.3 * motion.charge + 0.25 * (motion.burst - 1)).f()
                1 -> 0.08f
                else -> 0.01f
            }
            gains[2 * i + 1] = when (i) { 3 -> (0.09 * motion.burst).f(); 1 -> 0.04f; else -> 0.00025f }
        }
        val minis = FloatArray(24)
        val miniColors = FloatArray(18)
        for (i in 0..5) {
            val (point, scale, opacity) = miniGeometry(i)
            minis[4 * i] = point.x.f()
            minis[4 * i + 1] = point.y.f()
            minis[4 * i + 2] = opacity.f()
            minis[4 * i + 3] = scale.f()
            for (c in 0..2) miniColors[3 * i + c] = keycapMoodRgb[keycapFanOrder[i]][c].f()
        }
        shader.setInputShader("wellData", wellBitmapShader)
        shader.setFloatUniform("u", layout.u.f())
        shader.setFloatUniform("centres", centers)
        shader.setFloatUniform("colors", colors)
        shader.setFloatUniform("gains", gains)
        shader.setFloatUniform("minis", minis)
        shader.setFloatUniform("miniColors", miniColors)
        shader.setFloatUniform("fanOrder", keycapFanOrder.map { it.toFloat() }.toFloatArray())
        shader.setFloatUniform("scene", motion.scene.floats())
        shader.setFloatUniform("burst", motion.burst.f())
        shader.setFloatUniform("ring", if (reduced) 0f else motion.ring.f())
        shader.setFloatUniform("selected", motion.choices[3].toFloat())
        shader.setFloatUniform("wellTile", 720f)
        shader.setFloatUniform("pxPerPt", 3f)
        shader.setFloatUniform("pageExposure", 0.12f)
        shader.setFloatUniform("wellFeather", 14f)
        shader.setFloatUniform("page", 300f, 0.16f, 0.05f, 0.45f)
        shader.setFloatUniform("ringLook", 520f, 60f, 0.22f, 1.5f)
        shader.setFloatUniform("top", (110 * layout.u).f(), (18 * layout.u).f())
        shader.setFloatUniform("miniGlow", 42f, 0.025f, 0.065f)
        paint.shader = shader
        paint.alpha = (255 * reveal).toInt().coerceIn(0, 255)
        canvas.drawRect(0f, 0f, layout.width.f(), layout.height.f(), paint)
        paint.shader = null
        paint.alpha = 255
    }

    @RequiresApi(33)
    private fun drawCap(canvas: Canvas, shaderIndex: Int, key: Int, miniIndex: Int) {
        val shader = capShaders[shaderIndex]
        val small = miniIndex >= 0
        val hero = key == 3
        val sizeRatio = if (small) 52.0 / 134 else 1.0
        val (center, scale, opacity) = if (small) miniGeometry(miniIndex) else Triple(layout.center(key), layout.u, 1.0)
        val mix = if (small) KeycapMoodMix(keycapFanOrder[miniIndex], keycapFanOrder[miniIndex]) else motion.mood
        val from = if (hero) keycapMoodRgb[mix.from] else doubleArrayOf(0.82, 0.82, 0.82)
        val to = if (hero) keycapMoodRgb[mix.to] else from
        val cobalt = doubleArrayOf(0.0109601, 0.0251869, 1.0)
        val upper = DoubleArray(3) { c -> when (key) {
            0 -> cobalt[c] * 0.9 + motion.scene[c] * motion.rim[0] * 1.6
            1 -> 0.25 + motion.scene[c] * motion.rim[1] * 1.4
            2 -> motion.scene[c] * (1 + motion.rim[2] * 1.6)
            else -> 0.0
        } }
        val lower = DoubleArray(3) { c -> if (key == 1) cobalt[c] * 0.25 + motion.scene[c] * motion.rim[1] * 1.4 else 0.0 }
        val charge = if (small) 0.0 else motion.charge
        val burst = if (small) 1.0 else motion.burst
        val led = if (hero) DoubleArray(3) { c -> keycapMoodRgb[mix.from][c] + (keycapMoodRgb[mix.to][c] - keycapMoodRgb[mix.from][c]) * mix.p }
            else if (key == 1) cobalt else doubleArrayOf(0.05, 0.05, 0.05)
        val faceRows = FloatArray(10)
        for (row in 0..9) for (col in 0..11) if (keycapFaceBitmaps[mix.to][row][col] == 'X') faceRows[row] += (1 shl col).toFloat()
        val data = if (small) miniBitmapShader else if (key == 1) glassLightBitmapShader else opaqueBitmapShader
        shader.setInputShader("data", data)
        shader.setInputShader("picture", glassBitmapShader)
        shader.setFloatUniform("centre", center.x.f(), center.y.f())
        shader.setFloatUniform("scale", scale.f())
        shader.setFloatUniform("tileSize", if (small) 192f else 480f)
        shader.setFloatUniform("pxPerPt", 3f)
        shader.setFloatUniform("travel", (if (small) motion.miniX[miniIndex] else motion.x[key]).f())
        shader.setFloatUniform("press", (3.454838 * sizeRatio).f(), (4.397497 * sizeRatio).f())
        shader.setFloatUniform("albedoFrom", from.floats())
        shader.setFloatUniform("albedoTo", to.floats())
        shader.setFloatUniform("progress", if (hero) mix.p.f() else 1f)
        shader.setFloatUniform("origin", (mix.ox / layout.u).f(), (mix.oy / layout.u).f())
        shader.setFloatUniform("radial", if (hero && !small && mix.radial) 1f else 0f)
        shader.setFloatUniform("upper", upper.floats())
        shader.setFloatUniform("lower", lower.floats())
        shader.setFloatUniform("rimGain", if (hero) { if (small) 0.34f else (1 + charge).f() } else 0f)
        shader.setFloatUniform("innerGain", if (hero) { if (small) 0.45f else (0.45 * burst - 0.12 * charge).f() } else 0f)
        shader.setFloatUniform("led", led.floats())
        shader.setFloatUniform("opacity", opacity.f())
        shader.setFloatUniform("dim", if (hero) 1f else motion.dim.f())
        shader.setFloatUniform("gloss", 0.9f)
        shader.setFloatUniform("well", 0.0015f, 0.075f, 0.42f, (3 * sizeRatio).f())
        shader.setFloatUniform("softGradient", 8f)
        shader.setFloatUniform("flood", 6f, 0.5f)
        shader.setFloatUniform("mini", if (small) 1f else 0f)
        shader.setFloatUniform("glassMode", if (key == 1) 1f else 0f)
        val dotPitch = round(2 * scale * density) / density / scale
        shader.setFloatUniform("face", (-7.386787 * sizeRatio).f(), (-9.403995 * sizeRatio).f(), dotPitch.f())
        shader.setFloatUniform("faceRows", faceRows)
        shader.setFloatUniform("ink", 0.0033465f, 0.0033465f, 0.004777f)
        shader.setFloatUniform("deboss", (-0.5 * sizeRatio).f(), (0.67 * sizeRatio).f(), 0.32f)
        val radius = (if (small) 34.0 else 84.0) * scale
        paint.shader = shader
        paint.alpha = (255 * reveal).toInt().coerceIn(0, 255)
        canvas.drawRect((center.x - radius).f(), (center.y - radius).f(), (center.x + radius).f(), (center.y + radius).f(), paint)
        paint.shader = null
        paint.alpha = 255
    }

    private fun drawLegend(canvas: Canvas, key: Int) {
        val flip = motion.legends[key]
        val pitch = keycapDotPitch(key, layout.u, density.toDouble())
        val base = layout.center(key)
        val x = round((base.x - 7.386787 * layout.u) * density) / density + 3.454838 * layout.u * motion.x[key]
        val y = round((base.y - 9.403995 * layout.u) * density) / density + 4.397497 * layout.u * motion.x[key]
        val rim = motion.rim[key].coerceIn(0.0, 1.0)
        val baseInk = doubleArrayOf(0.0033465, 0.0033465, 0.004777)
        val color = DoubleArray(3) { c -> (if (key == 1) 1.0 else baseInk[c]) * (1 - rim) + motion.scene[c] * 1.15 * rim }
        val alpha = reveal * if (key == 3) 1.0 else motion.dim
        val originX = motion.mood.ox - (-7.386787 * layout.u)
        val originY = motion.mood.oy - (-9.403995 * layout.u)
        val layers = if (flip.t >= 0 && flip.mode == 2) listOf(flip.from to (1 - flip.t / 140.0).coerceIn(0.0, 1.0), flip.to to (flip.t / 140.0).coerceIn(0.0, 1.0)) else emptyList()
        if (layers.isNotEmpty()) {
            for ((choice, amount) in layers) drawDots(canvas, key, keycapLegends[key][choice].map { it to 1.0 }, x, y, pitch, color, alpha * amount)
        } else {
            val dots = keycapFlipDots(key, flip, pitch, originX, originY)
            drawDots(canvas, key, dots, x, y, pitch, color, alpha)
        }
    }

    private fun drawDots(canvas: Canvas, key: Int, dots: List<Pair<KeycapDot, Double>>, x: Double, y: Double,
                         pitch: Double, color: DoubleArray, alpha: Double) {
        if (alpha <= 0.001) return
        val path = Path()
        for ((dot, height) in dots) {
            if (height < 0.12) continue
            val left = round((x + (dot.x - 0.5) * pitch) * density) / density
            val top = round((y + (dot.y - 0.5) * pitch) * density) / density + pitch * (1 - height) / 2
            path.addRect(left.f(), top.f(), (left + pitch).f(), (top + pitch * height).f(), Path.Direction.CW)
        }
        if (key == 1) {
            legendBlurPaint.color = Color.argb((alpha * 0.45 * 255).toInt(), 6, 16, 58)
            legendBlurPaint.maskFilter = BlurMaskFilter((2 * layout.u).f(), BlurMaskFilter.Blur.NORMAL)
            canvas.save()
            canvas.translate((1.5 * layout.u).f(), (2.5 * layout.u).f())
            canvas.drawPath(path, legendBlurPaint)
            canvas.restore()
        }
        val rim = motion.rim[key].coerceIn(0.0, 1.0)
        if (rim > 0.001) {
            legendBlurPaint.color = keycapArgb(motion.scene, alpha * 0.6 * rim)
            legendBlurPaint.maskFilter = BlurMaskFilter((3 * layout.u).f(), BlurMaskFilter.Blur.NORMAL)
            canvas.drawPath(path, legendBlurPaint)
        }
        legendBlurPaint.maskFilter = null
        for ((dot, height) in dots) {
            if (height < 0.12) continue
            val left = round((x + (dot.x - 0.5) * pitch) * density) / density
            val top = round((y + (dot.y - 0.5) * pitch) * density) / density + pitch * (1 - height) / 2
            val right = left + pitch
            val bottom = top + pitch * height
            if (key != 1) {
                dotPaint.color = Color.argb((alpha * 0.32 * 255).toInt(), 255, 255, 255)
                canvas.drawRect((left - 0.5 * layout.u).f(), (top + 0.67 * layout.u).f(),
                    (right - 0.5 * layout.u).f(), (bottom + 0.67 * layout.u).f(), dotPaint)
            }
        }
        dotPaint.color = keycapArgb(color, alpha)
        canvas.drawPath(path, dotPaint)
    }

    /** API 26–32: designed still material with the same geometry, copy and selectable moods. */
    private fun drawFallback(canvas: Canvas) {
        val center = layout.center(3)
        val mood = keycapMoodHex[motion.choices[3]]
        for (i in 0..3) {
            val c = layout.center(i)
            val side = 134 * layout.u
            val left = c.x - side / 2
            val top = c.y - side / 2 + motion.x[i] * 4 * layout.u
            val radius = 22 * layout.u
            fallbackPaint.shader = null
            fallbackPaint.color = when (i) { 1 -> Color.rgb(25, 43, 232); 3 -> mood; else -> Color.rgb(226, 226, 230) }
            fallbackPaint.setShadowLayer(18 * layout.u.f(), 0f, 9 * layout.u.f(),
                when (i) { 1 -> 0xAA1234FF.toInt(); 3 -> mood; else -> 0x33000000 })
            canvas.drawRoundRect(RectF(left.f(), top.f(), (left + side).f(), (top + side).f()), radius.f(), radius.f(), fallbackPaint)
            fallbackPaint.clearShadowLayer()
            val faceInset = 14 * layout.u
            fallbackPaint.shader = LinearGradient(0f, top.f(), 0f, (top + side).f(),
                if (i == 1) 0xFF304CF9.toInt() else if (i == 3) mood else 0xFFF6F6F6.toInt(),
                if (i == 1) 0xFF1728AD.toInt() else if (i == 3) mood else 0xFFBABABC.toInt(), Shader.TileMode.CLAMP)
            canvas.drawRoundRect(RectF((left + faceInset).f(), (top + faceInset).f(), (left + side - faceInset).f(),
                (top + side - faceInset).f()), (radius * 0.8).f(), (radius * 0.8).f(), fallbackPaint)
            fallbackPaint.shader = null
        }
        for (i in 0..5) if (motion.out[i] > 0.01) {
            val (point, scale, opacity) = miniGeometry(i)
            val side = 52 * scale
            fallbackPaint.color = keycapMoodHex[keycapFanOrder[i]]
            fallbackPaint.alpha = (255 * opacity).toInt()
            canvas.drawRoundRect(RectF((point.x - side / 2).f(), (point.y - side / 2).f(),
                (point.x + side / 2).f(), (point.y + side / 2).f()), (10 * scale).f(), (10 * scale).f(), fallbackPaint)
        }
    }
}
