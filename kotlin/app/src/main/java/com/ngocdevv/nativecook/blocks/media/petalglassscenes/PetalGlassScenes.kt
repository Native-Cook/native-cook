package com.ngocdevv.nativecook.blocks.media.petalglassscenes

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.HapticFeedbackConstants
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * "petalfield", a focus-sound app: its scene switcher. Macro petal scenes drift slowly behind a
 * tall glass card named after the scene. The card is a thick slab: it magnifies the petals, warps
 * them with a slow liquid ripple and bends them hard along its bevelled rim with a thin colour
 * split. Each scene change grows the next scene out of the glass: first inside the card, a beat at
 * its rim, then out across the screen behind a lens-like front. Ported from the Swift reference.
 *
 * Loop (LOOP = 9 s): Clarity rests 1.6 s → Depth grows out of the glass (1.4 s) → rests → Echo →
 * back to Clarity at 9 s. Drag anywhere to move the card and watch the refraction shift (it springs
 * back); swipe left or right to change the scene. The loop resumes 2 s after the last touch at the
 * shown scene's rest. Reduced motion: Clarity, still; swipes cross-fade. Below Android 13 (no
 * RuntimeShader) the petals are drawn flat and the card is a plain translucent pane.
 */
@Composable
fun PetalGlassScenes(autoPlay: Boolean = false) {
    val m = PetalGlassScenesMotion
    val context = LocalContext.current
    val view = LocalView.current
    val owner = LocalLifecycleOwner.current
    val reduced = rememberLiveReducedMotion()
    var active by remember { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, _ -> active = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    BlockSystemBars(darkIcons = false)

    val res = remember { PetalGlassScenesResources.load(context) }
    val clock = remember { mutableDoubleStateOf(0.0) }
    val touch = remember { PetalGlassScenesTouch() }
    val awake = remember { mutableStateOf(false) }
    val playing = autoPlay && !reduced
    val running = active && (playing || awake.value)
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        var startNanos = -1L
        val base = clock.doubleValue
        while (true) {
            withFrameNanos { now ->
                if (startNanos < 0) startNanos = now
                val c = base + (now - startNanos) / 1e9
                clock.doubleValue = c
                if (touch.takeHaptic()) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            }
            if (!playing && !touch.touching && clock.doubleValue > touch.idleUntil) {
                awake.value = false
                break
            }
        }
    }

    val step = { dx: Double ->
        val c = clock.doubleValue
        touch.begin(c, playing, reduced)
        touch.end(c, dx * 120, 0.0, dx * 1000, 0.0, reduced)
        awake.value = true
        clock.doubleValue += 1e-6
    }

    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF6BA8DB))) {
        val k = maxWidth.value / 393f
        val height = (maxHeight.value / k).toDouble()
        val top = (WindowInsets.safeDrawing.getTop(density) / density.density / k).toDouble()
        val bottom = (WindowInsets.safeDrawing.getBottom(density) / density.density / k).toDouble()

        Canvas(
            Modifier
                .fillMaxSize()
                .semantics {
                    contentDescription = "petalfield focus scenes: Clarity, Depth and Echo, macro petals behind a glass card. " +
                        "Drag the glass, swipe for a scene."
                    customActions = listOf(
                        CustomAccessibilityAction("Next scene") { step(-1.0); true },
                        CustomAccessibilityAction("Previous scene") { step(1.0); true },
                    )
                }
                .pointerInput(reduced, playing) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val scale = size.width / 393f
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        var dragging = false
                        var dx = 0.0
                        var dy = 0.0
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            tracker.addPosition(change.uptimeMillis, change.position)
                            dx = ((change.position.x - down.position.x) / scale).toDouble()
                            dy = ((change.position.y - down.position.y) / scale).toDouble()
                            if (!dragging && hypot(dx, dy) > 4) {
                                dragging = true
                                touch.begin(clock.doubleValue, playing, reduced)
                                awake.value = true
                                clock.doubleValue += 1e-6
                            }
                            if (dragging) {
                                // Under reduced motion the card stays put; only the swipe counts.
                                if (!reduced) touch.move(clock.doubleValue, dx, dy)
                                change.consume()
                            }
                        } while (event.changes.any { it.pressed })
                        if (dragging) {
                            val v = tracker.calculateVelocity()
                            touch.end(clock.doubleValue, dx, dy, (v.x / scale).toDouble(), (v.y / scale).toDouble(), reduced)
                        }
                    }
                },
        ) {
            val c = clock.doubleValue
            val f = touch.frame(c, playing, reduced)
            val canvas = drawContext.canvas.nativeCanvas
            val scale = size.width / 393f
            val layout = PetalGlassScenesLayout(height, top, bottom)
            PetalGlassScenesArt.drawGlass(canvas, res, f, layout, scale, reduced)
            canvas.save()
            canvas.scale(scale, scale)
            PetalGlassScenesArt.drawCardText(canvas, res, f, layout)
            PetalGlassScenesArt.drawChrome(canvas, res, f, layout, reduced)
            canvas.restore()
        }
    }
}

class PetalGlassScenesLayout(val height: Double, val top: Double, val bottom: Double) {
    val centerX = 196.5
    val centerY = (height * 0.465).roundToInt().toDouble()
}

class PetalGlassScenesResources(
    val images: List<Bitmap?>,
    val shaders: List<Shader?>,
    val glass: Any?,
    val regular: Typeface,
    val medium: Typeface,
    val semibold: Typeface,
    val light: Typeface,
) {
    val fill = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    val text = TextPaint(Paint.ANTI_ALIAS_FLAG)
    val shape = Paint(Paint.ANTI_ALIAS_FLAG)
    val path = Path()
    val rect = RectF()
    val lines = HashMap<Int, StaticLayout>()

    companion object {
        private fun loadBitmap(context: Context, name: String): Bitmap? = runCatching {
            context.assets.open("petalglassscenes/petalglassscenes_petal-$name.webp").use { stream ->
                BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply {
                    inScaled = false
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                })?.also { it.prepareToDraw() }
            }
        }.getOrNull()

        private fun weight(w: Int): Typeface =
            if (Build.VERSION.SDK_INT >= 28) Typeface.create(Typeface.DEFAULT, w, false)
            else if (w >= 600) Typeface.DEFAULT_BOLD else Typeface.DEFAULT

        fun load(context: Context): PetalGlassScenesResources {
            val images = listOf("clarity", "depth", "echo").map { loadBitmap(context, it) }
            val shaders = images.map { image ->
                image?.let {
                    BitmapShader(it, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                        if (Build.VERSION.SDK_INT >= 33) filterMode = BitmapShader.FILTER_MODE_LINEAR
                    }
                }
            }
            val glass: Any? = if (Build.VERSION.SDK_INT >= 33) RuntimeShader(PETAL_GLASS_SCENES_AGSL) else null
            return PetalGlassScenesResources(images, shaders, glass, weight(400), weight(500), weight(600), weight(300))
        }
    }
}

object PetalGlassScenesArt {
    private val m = PetalGlassScenesMotion

    class Scene(val title: String, val line: String, val sound: String)

    val scenes = listOf(
        Scene("Clarity", "Bright air for clear,\nearly focus.", "Morning air · 25 min"),
        Scene("Depth", "Low tones that hold\nlong, deep work.", "Deep hum · 50 min"),
        Scene("Echo", "Soft layers that fade as\nyou rest.", "Warm echo · 15 min"),
    )

    const val CARD_W = 240.0
    const val CARD_H = 344.0
    const val CARD_R = 28.0
    private const val INK = 0xFF1B2233.toInt()

    /** Where scene [scene]'s image lies, in units (see the Swift `drift`). */
    fun drift(image: Bitmap?, scene: Int, f: PetalGlassScenesMotion.Frame, w: Double, h: Double, still: Boolean): RectF {
        val iw = max(image?.width ?: 720, 1).toDouble()
        val ih = max(image?.height ?: 1080, 1).toDouble()
        val phase = scene * 2.1
        val breathe = if (still) 0.0 else 0.025 * sin(f.theta + phase)
        val cover = max(w / iw, h / ih) * (1.10 + breathe)
        val rw = iw * cover
        val rh = ih * cover
        val px = if (still) 0.0 else 14 * sin(f.theta + phase) - f.offsetX * 0.12
        val py = if (still) 0.0 else 10 * sin(f.theta + phase + 1.9) - f.offsetY * 0.12
        val x = w / 2 - rw / 2 + px
        val y = h / 2 - rh / 2 + py
        return RectF(x.toFloat(), y.toFloat(), (x + rw).toFloat(), (y + rh).toFloat())
    }

    /** How far the front must travel from the card's rim to clear the farthest corner. */
    fun reach(cx: Double, cy: Double, hw: Double, hh: Double, w: Double, h: Double): Double {
        var far = 0.0
        for ((x, y) in listOf(0.0 to 0.0, w to 0.0, 0.0 to h, w to h)) {
            val qx = abs(x - cx) - (hw - CARD_R)
            val qy = abs(y - cy) - (hh - CARD_R)
            val d = hypot(max(qx, 0.0), max(qy, 0.0)) + min(max(qx, qy), 0.0) - CARD_R
            far = max(far, d)
        }
        return far + 70
    }

    fun drawGlass(
        canvas: android.graphics.Canvas, res: PetalGlassScenesResources, f: PetalGlassScenesMotion.Frame,
        l: PetalGlassScenesLayout, k: Float, still: Boolean,
    ) {
        val w = 393.0
        val h = l.height
        val scale = if (f.fade >= 0) 1.0 else m.cardScale(f.p)
        val hw = CARD_W / 2 * scale
        val hh = CARD_H / 2 * scale
        val cx = l.centerX + f.offsetX
        val cy = l.centerY + f.offsetY
        val rectA = drift(res.images[f.from], f.from, f, w, h, still)
        val rectB = drift(res.images[f.to], f.to, f, w, h, still)
        val reach = reach(cx, cy, hw, hh, w, h)
        val front = m.front(f.p, reach)
        if (Build.VERSION.SDK_INT >= 33 && res.glass != null) {
            drawShader(canvas, res, f, k, w, h, rectA, rectB, cx, cy, hw, hh, scale, front, still)
        } else {
            drawFlat(canvas, res, f, k, rectA, rectB, cx, cy, hw, hh, scale, front, h.toFloat())
        }
    }

    @RequiresApi(33)
    private fun drawShader(
        canvas: android.graphics.Canvas, res: PetalGlassScenesResources, f: PetalGlassScenesMotion.Frame, k: Float,
        w: Double, h: Double, rectA: RectF, rectB: RectF, cx: Double, cy: Double, hw: Double, hh: Double,
        scale: Double, front: Double, still: Boolean,
    ) {
        val shader = res.glass as RuntimeShader
        val a = res.shaders[f.from] ?: return
        val b = res.shaders[f.to] ?: return
        val imageA = res.images[f.from] ?: return
        val imageB = res.images[f.to] ?: return
        shader.setInputShader("imageA", a)
        shader.setInputShader("imageB", b)
        shader.setFloatUniform("k", k)
        shader.setFloatUniform("size", w.toFloat(), h.toFloat())
        shader.setFloatUniform("rectA", rectA.left, rectA.top, rectA.width(), rectA.height())
        shader.setFloatUniform("pixelsA", imageA.width.toFloat(), imageA.height.toFloat())
        shader.setFloatUniform("rectB", rectB.left, rectB.top, rectB.width(), rectB.height())
        shader.setFloatUniform("pixelsB", imageB.width.toFloat(), imageB.height.toFloat())
        shader.setFloatUniform("card", cx.toFloat(), cy.toFloat(), hw.toFloat(), hh.toFloat())
        shader.setFloatUniform(
            "change", front.toFloat(), m.bulge(f.p).toFloat(),
            (if (still) 9.0 else m.warp(f.p)).toFloat(), f.fade.toFloat(),
        )
        shader.setFloatUniform(
            "lightU", (if (still) 0.0 else f.theta).toFloat(), (f.offsetX * 0.012 + f.offsetY * 0.008).toFloat(),
            (-0.55 + f.offsetX / 160).toFloat(), (-0.83 + f.offsetY / 220).toFloat(),
        )
        shader.setFloatUniform("extra", (CARD_R * scale).toFloat(), (-40 + f.offsetX * 0.6 - f.offsetY * 0.3).toFloat(), 1f, 0f)
        val paint = res.fill
        paint.alpha = 255
        paint.shader = shader
        canvas.drawRect(0f, 0f, (w * k).toFloat(), (h * k).toFloat(), paint)
        paint.shader = null
    }

    /** Below Android 13: the petals flat, the front as a clip, the card as a translucent pane. */
    private fun drawFlat(
        canvas: android.graphics.Canvas, res: PetalGlassScenesResources, f: PetalGlassScenesMotion.Frame, k: Float,
        rectA: RectF, rectB: RectF, cx: Double, cy: Double, hw: Double, hh: Double, scale: Double, front: Double, h: Float,
    ) {
        canvas.save()
        canvas.scale(k, k)
        val paint = res.fill
        paint.shader = null
        paint.color = android.graphics.Color.BLACK
        res.images[f.from]?.let { canvas.drawBitmap(it, null, rectA, paint) }
        if (f.from != f.to) {
            if (f.fade >= 0) {
                paint.alpha = (f.fade * 255).roundToInt()
                res.images[f.to]?.let { canvas.drawBitmap(it, null, rectB, paint) }
                paint.alpha = 255
            } else if (front > -120) {
                val r = (CARD_R + min(front, 0.0)).coerceAtLeast(0.0)
                val grow = front.coerceAtLeast(-min(hw, hh))
                res.rect.set((cx - hw - grow).toFloat(), (cy - hh - grow).toFloat(), (cx + hw + grow).toFloat(), (cy + hh + grow).toFloat())
                res.path.reset()
                res.path.addRoundRect(res.rect, (r + max(front, 0.0)).toFloat(), (r + max(front, 0.0)).toFloat(), Path.Direction.CW)
                canvas.save()
                canvas.clipPath(res.path)
                res.images[f.to]?.let { canvas.drawBitmap(it, null, rectB, paint) }
                canvas.restore()
            }
        }
        val card = RectF((cx - hw).toFloat(), (cy - hh).toFloat(), (cx + hw).toFloat(), (cy + hh).toFloat())
        val r = (CARD_R * scale).toFloat()
        val s = res.shape
        s.style = Paint.Style.FILL
        s.color = 0x24000000
        canvas.drawRoundRect(RectF(card.left, card.top + 10, card.right, card.bottom + 14), r, r, s)
        s.color = 0x1FFFFFFF
        canvas.drawRoundRect(card, r, r, s)
        s.style = Paint.Style.STROKE
        s.strokeWidth = 1.2f
        s.color = 0x8CFFFFFF.toInt()
        canvas.drawRoundRect(card, r, r, s)
        s.style = Paint.Style.FILL
        // The scrims.
        s.shader = android.graphics.LinearGradient(0f, 0f, 0f, 150f, 0x1F000000, 0, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, 393f, 150f, s)
        s.shader = android.graphics.LinearGradient(0f, h - 260f, 0f, h, 0, 0x42000000, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, h - 260f, 393f, h, s)
        s.shader = null
        canvas.restore()
    }

    private class Fx(val scene: Int, val title: PetalGlassScenesMotion.Fx, val line: PetalGlassScenesMotion.Fx)

    private fun texts(f: PetalGlassScenesMotion.Frame): List<Fx> {
        val none = PetalGlassScenesMotion.Fx(1.0, 0.0, 0.0)
        if (f.from == f.to || f.p <= 0) return listOf(Fx(f.from, none, none))
        if (f.fade >= 0) {
            val out = PetalGlassScenesMotion.Fx(1 - f.fade, 0.0, 0.0)
            val inn = PetalGlassScenesMotion.Fx(f.fade, 0.0, 0.0)
            return listOf(Fx(f.from, out, out), Fx(f.to, inn, inn))
        }
        return listOf(
            Fx(f.from, m.textOut(f.p), m.textOut(f.p, 0.04)),
            Fx(f.to, m.textIn(f.p), m.textIn(f.p, 0.04)),
        )
    }

    private fun alpha(base: Int, a: Double): Int = (base.toDouble() * a.coerceIn(0.0, 1.0)).roundToInt()

    private fun blur(paint: Paint, radius: Double) {
        paint.maskFilter = if (radius > 0.2) BlurMaskFilter(radius.toFloat(), BlurMaskFilter.Blur.NORMAL) else null
    }

    private fun bodyLayout(res: PetalGlassScenesResources, scene: Int): StaticLayout =
        res.lines.getOrPut(scene) {
            val p = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = res.regular
                textSize = 13f
                color = android.graphics.Color.WHITE
            }
            StaticLayout.Builder.obtain(scenes[scene].line, 0, scenes[scene].line.length, p, 196)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(3f, 1f)
                .setIncludePad(false)
                .build()
        }

    /** Title (rotated, reading top to bottom) and the two lines, riding on the card. */
    fun drawCardText(canvas: android.graphics.Canvas, res: PetalGlassScenesResources, f: PetalGlassScenesMotion.Frame, l: PetalGlassScenesLayout) {
        val scale = if (f.fade >= 0) 1.0 else m.cardScale(f.p)
        val cx = l.centerX + f.offsetX
        val cy = l.centerY + f.offsetY
        val left = (cx - CARD_W / 2).toFloat()
        val top = (cy - CARD_H / 2).toFloat()
        canvas.save()
        canvas.scale(scale.toFloat(), scale.toFloat(), cx.toFloat(), cy.toFloat())
        val t = res.text
        for (item in texts(f)) {
            val s = scenes[item.scene]
            if (item.title.alpha > 0.003) {
                t.reset()
                t.isAntiAlias = true
                t.typeface = res.light
                t.textAlign = Paint.Align.LEFT
                t.fontFeatureSettings = null
                t.textSize = 50f
                t.letterSpacing = -0.5f / 50f
                t.color = android.graphics.Color.argb(alpha(255, item.title.alpha), 255, 255, 255)
                t.setShadowLayer(6f, 0f, 1f, android.graphics.Color.argb(alpha(31, item.title.alpha), 0, 0, 0))
                blur(t, item.title.blur)
                val fm = t.fontMetrics
                val lineH = fm.descent - fm.ascent
                // The rotated line box is 60 wide from the card's left + 18; its baseline sits
                // `descent` in from the box's left side.
                val baseX = 18f + (60f - lineH) / 2f + fm.descent
                canvas.save()
                canvas.translate(left + baseX, top + 24f + item.title.shift.toFloat())
                canvas.rotate(90f)
                canvas.drawText(s.title, 0f, 0f, t)
                canvas.restore()
            }
            if (item.line.alpha > 0.003) {
                val layout = bodyLayout(res, item.scene)
                layout.paint.color = android.graphics.Color.argb(alpha(235, item.line.alpha), 255, 255, 255)
                layout.paint.setShadowLayer(6f, 0f, 1f, android.graphics.Color.argb(alpha(31, item.line.alpha), 0, 0, 0))
                blur(layout.paint, item.line.blur)
                canvas.save()
                canvas.translate(left + 22f, (top + CARD_H - 22 - layout.height + item.line.shift * 0.5).toFloat())
                layout.draw(canvas)
                canvas.restore()
            }
        }
        t.maskFilter = null
        canvas.restore()
    }

    /** Draws [text] with the top of its line box at [top]. */
    private fun textTop(canvas: android.graphics.Canvas, text: String, x: Float, top: Float, paint: Paint) {
        val fm = paint.fontMetrics
        canvas.drawText(text, x, top - fm.ascent, paint)
    }

    private fun chromePaint(res: PetalGlassScenesResources, face: Typeface, size: Float, a: Double): TextPaint {
        val t = res.text
        t.reset()
        t.isAntiAlias = true
        t.typeface = face
        t.textSize = size
        t.textAlign = Paint.Align.LEFT
        t.letterSpacing = 0f
        t.fontFeatureSettings = null
        t.maskFilter = null
        t.color = android.graphics.Color.argb(alpha(255, a), 255, 255, 255)
        t.setShadowLayer(8f, 0f, 1f, android.graphics.Color.argb(alpha(36, a), 0, 0, 0))
        return t
    }

    fun drawChrome(
        canvas: android.graphics.Canvas, res: PetalGlassScenesResources, f: PetalGlassScenesMotion.Frame,
        l: PetalGlassScenesLayout, reduced: Boolean,
    ) {
        val items = texts(f)
        val top = l.top.toFloat()

        // Wordmark.
        var t = chromePaint(res, res.semibold, 17f, 1.0)
        t.letterSpacing = -0.2f / 17f
        textTop(canvas, "petalfield", 24f, top + 10f, t)
        val wordH = t.fontMetrics.let { it.descent - it.ascent }
        t = chromePaint(res, res.regular, 12f, 0.78)
        textTop(canvas, "Focus scenes", 24f, top + 10f + wordH + 2f, t)

        // Counter.
        for (item in items) {
            t = chromePaint(res, res.medium, 13f, item.title.alpha)
            t.fontFeatureSettings = "tnum"
            t.textAlign = Paint.Align.RIGHT
            textTop(canvas, "%02d / %02d".format(item.scene + 1, m.SCENES), 393f - 24f, top + 13f + (item.title.shift * 0.4).toFloat(), t)
        }

        // Page dots.
        val dots = (l.centerY + CARD_H / 2 + 38).toFloat()
        val p = if (f.fade >= 0) f.fade else m.easeInOutCubic(m.seg(f.p, 0.3, 0.9))
        val at = f.from + (f.to - f.from) * (if (f.from == f.to) 0.0 else p)
        val widths = (0 until m.SCENES).map { 6.0 + 12.0 * max(0.0, 1 - abs(it - at)) }
        var x = (393.0 - (widths.sum() + 8.0 * (m.SCENES - 1))) / 2
        val s = res.shape
        s.style = Paint.Style.FILL
        s.shader = null
        for (i in 0 until m.SCENES) {
            val near = max(0.0, 1 - abs(i - at))
            s.color = android.graphics.Color.argb(((0.5 + 0.45 * near) * 255).roundToInt(), 255, 255, 255)
            s.setShadowLayer(8f, 0f, 1f, 0x24000000)
            res.rect.set(x.toFloat(), dots - 3f, (x + widths[i]).toFloat(), dots + 3f)
            canvas.drawRoundRect(res.rect, 3f, 3f, s)
            x += widths[i] + 8
        }
        s.clearShadowLayer()

        // Hint.
        t = chromePaint(res, res.regular, 12f, 0.72)
        t.textAlign = Paint.Align.CENTER
        textTop(canvas, "Drag the glass · swipe for a scene", 196.5f, dots + 16f, t)

        // Now playing.
        val barTop = (l.height - l.bottom - 20 - 56).toFloat()
        t = chromePaint(res, res.semibold, 11f, 0.8)
        t.letterSpacing = 1.2f / 11f
        val labelH = t.fontMetrics.let { it.descent - it.ascent }
        val soundPaintH = chromePaint(res, res.medium, 17f, 1.0).fontMetrics.let { it.descent - it.ascent }
        val blockTop = barTop + (56f - (labelH + 5f + soundPaintH)) / 2f
        t = chromePaint(res, res.semibold, 11f, 0.8)
        t.letterSpacing = 1.2f / 11f
        textTop(canvas, "NOW PLAYING", 24f + 13f + 7f, blockTop, t)
        // Equalizer: three bars bouncing whole cycles per loop.
        s.color = android.graphics.Color.WHITE
        s.setShadowLayer(8f, 0f, 1f, 0x24000000)
        val eqBottom = blockTop + labelH / 2f + 6f
        for (i in 0 until 3) {
            val kk = doubleArrayOf(7.0, 11.0, 13.0)[i]
            val v = 0.5 + 0.3 * sin(kk * f.theta + i * 1.7) + 0.2 * sin((kk + 4) * f.theta + i)
            val bh = (3 + 9 * (if (reduced) 0.6 else v)).toFloat()
            val bx = 24f + i * 5f
            res.rect.set(bx, eqBottom - bh, bx + 3f, eqBottom)
            canvas.drawRoundRect(res.rect, 1f, 1f, s)
        }
        s.clearShadowLayer()
        for (item in items) {
            t = chromePaint(res, res.medium, 17f, item.line.alpha)
            blur(t, item.line.blur * 0.5)
            textTop(canvas, scenes[item.scene].sound, 24f, blockTop + labelH + 5f + (item.line.shift * 0.4).toFloat(), t)
            t.maskFilter = null
        }

        // Pause button.
        val bx = 393f - 24f - 28f
        val by = barTop + 28f
        s.color = android.graphics.Color.argb(235, 255, 255, 255)
        s.setShadowLayer(12f, 0f, 6f, 0x29000000)
        canvas.drawCircle(bx, by, 28f, s)
        s.clearShadowLayer()
        s.color = INK
        res.rect.set(bx - 7f, by - 8.5f, bx - 2.5f, by + 8.5f)
        canvas.drawRoundRect(res.rect, 1.5f, 1.5f, s)
        res.rect.set(bx + 2.5f, by - 8.5f, bx + 7f, by + 8.5f)
        canvas.drawRoundRect(res.rect, 1.5f, 1.5f, s)
    }
}
