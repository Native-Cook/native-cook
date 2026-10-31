package com.ngocdevv.nativecook.blocks.animations.robotmoodcompanion

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

// Faithful port of the RN Pip screen. Head 300 dp, centre (width / 2, height * .52).
// Six 1.4 s moods switch at 1.2, 2.6, 4.0, 5.4, 6.8 and 8.2 s in an 8.4 s loop.
// Eyes close in .12 s and open in .14 s; room, pose, copy and dots use smoothstep.
// Tap anywhere advances to the next blink, with one light haptic, then waits 2 s.
// RN's frozen guard holds just BEFORE that blink; preserve its executable behavior.
// Reduced motion freezes Happy at t = .6 and disables the tap, just as in RN.

/** Seconds. */
const val LOOP = 8.4
private const val STOP = 1.4
private const val FIRST = 1.2
private const val CLOSE = .12
private const val OPEN = .14
private const val HEAD = 300f
private const val HOLD = 2.0
private const val CYAN = 0xFF3FE0F0.toInt()

private data class RobotMood(val name: String, val line: String, val background: Color, val ink: Color)

private val MOODS = listOf(
    RobotMood("Happy", "Pip had a good day. So did you?", Color(0xFFF8E6A0), Color(0xFF3A2E0A)),
    RobotMood("Surprised", "Three new messages, all at once.", Color(0xFFF0957F), Color(0xFF3E150B)),
    RobotMood("Sleepy", "Past eleven. Pip is winding down.", Color(0xFFB49E9E), Color(0xFF2E2222)),
    RobotMood("Proud", "You finished every task today.", Color(0xFF55CF9C), Color(0xFF0D3B27)),
    RobotMood("Sad", "You skipped your walk again.", Color(0xFF1F4652), Color(0xFFE6F6FA)),
    RobotMood("Grumpy", "Four hours of meetings? Really?", Color(0xFFE8206E), Color(0xFFFFF0F5)),
)

private fun clamp01(x: Double) = x.coerceIn(0.0, 1.0)
private fun smooth(x: Double): Double {
    val u = clamp01(x)
    return u * u * (3 - 2 * u)
}

/** RN interpolates rounded sRGB bytes, rather than linear-light colors. */
private fun mixColor(a: Color, b: Color, u: Double): Color {
    val aa = a.toArgb()
    val bb = b.toArgb()
    fun channel(shift: Int) = (((aa shr shift) and 255) * (1 - u) + ((bb shr shift) and 255) * u).roundToInt()
    return Color(channel(16), channel(8), channel(0))
}

private data class MoodFrame(val cur: Int, val prev: Int, val fade: Double, val open: Double, val s: Double)

private fun moodAt(t: Double): MoodFrame {
    val k = floor((t - FIRST) / STOP).toInt() + 1
    val b = FIRST + (k - 1) * STOP
    val s = if (k == 0) t + (LOOP - (FIRST + 5 * STOP)) else t - b
    val toNext = FIRST + k * STOP - t
    var open = 1.0
    if (s < OPEN) open = smooth(s / OPEN)
    if (toNext < CLOSE) open = smooth(toNext / CLOSE)
    val cur = ((k % 6) + 6) % 6
    return MoodFrame(cur, (cur + 5) % 6, smooth((s + CLOSE) / (CLOSE + OPEN)), open, s)
}

private data class RobotPose(val x: Float = 0f, val y: Float = 0f, val rotation: Float = 0f)

private fun poseOf(m: Int, s: Double): RobotPose = when (m) {
    0 -> RobotPose(y = (-6 * abs(sin(s * PI * 2.2))).toFloat())
    1 -> RobotPose(y = (-16 * sin(PI * clamp01(s / .45))).toFloat())
    2 -> RobotPose(y = 6f, rotation = (7 * sin(s * 1.6 + .4)).toFloat())
    3 -> RobotPose(y = (-10 - 4 * sin(s * PI * 1.4)).toFloat(), rotation = -3f)
    4 -> RobotPose(y = 10f, rotation = -5f)
    else -> RobotPose(x = (3.5 * sin(s * 42) * exp(-2 * s)).toFloat())
}

/** Clock is read only by the canvas, so a frame never recomposes the screen. */
private class RobotClock {
    var time by mutableDoubleStateOf(.6)
        private set
    private var elapsed = 0.0
    private var origin = 0.0
    private var touchedAt = -10.0
    private var frozen = false

    fun advance(dt: Double, autoPlay: Boolean) {
        elapsed += dt
        if (!autoPlay && !frozen) {
            time = .6
            return
        }
        if (frozen) {
            val t = time + dt
            if (moodAt(t).s < STOP - CLOSE - .02) time = t else origin += dt
            if (elapsed - touchedAt > HOLD) frozen = false
            return
        }
        time = ((elapsed - origin) % LOOP + LOOP) % LOOP
    }

    fun tap() {
        val mo = moodAt(time)
        val next = FIRST + mo.cur * STOP - CLOSE
        val t = if (mo.cur == 0 && time > FIRST + 5 * STOP) FIRST - CLOSE else ((next % LOOP) + LOOP) % LOOP
        origin = elapsed - t
        time = t
        frozen = true
        touchedAt = elapsed
    }
}

/** Tiny software visor keeps Skia's exact mask-filter glow on every supported API (26+).
 * It is reused while a face is still; only blinks and running tears repaint its bitmap.
 * The artwork is decoded once; the visor and shadow reuse their small pixel buffers.
 */
private class RobotArtwork(private val head: Bitmap, density: Float) {
    private val faceScale = density
    private val face = Bitmap.createBitmap(ceil(192 * faceScale).toInt(), ceil(123 * faceScale).toInt(), Bitmap.Config.ARGB_8888)
    private val faceCanvas = android.graphics.Canvas(face)
    private var faceMood = -1
    private var faceOpen = -1.0
    private var faceSeconds = -1.0
    private val faceBounds = RectF(.6f - 96, .6f - 61.5f, .6f + 96, .6f + 61.5f)
    private val imageBounds = RectF(-HEAD / 2, -HEAD / 2, HEAD / 2, HEAD / 2)
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val path = Path()
    private val oval = RectF()
    private val shadow = Bitmap.createBitmap(ceil(280 * density).toInt(), ceil(100 * density).toInt(), Bitmap.Config.ARGB_8888)
    private val shadowCanvas = android.graphics.Canvas(shadow)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x29000000
        maskFilter = BlurMaskFilter(sigmaToRadius(10f), BlurMaskFilter.Blur.NORMAL)
    }
    private var lastShadowScale = Float.NaN
    private val shadowBounds = RectF()
    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 9f; color = 0xFFFFF1C2.toInt() }
    private val zPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f; strokeCap = Paint.Cap.ROUND; color = android.graphics.Color.WHITE }

    private fun cyan(p: Paint, glow: Boolean, alpha: Float = 1f) {
        p.color = CYAN
        p.alpha = (alpha * (if (glow) .65f else 1f) * 255).roundToInt().coerceIn(0, 255)
        p.maskFilter = if (glow) BlurMaskFilter(sigmaToRadius(6f), BlurMaskFilter.Blur.NORMAL) else null
    }

    private fun eye(c: android.graphics.Canvas, m: Int, side: Int, ex: Float, ey: Float, open: Double, glow: Boolean) {
        val rx = 16.5f
        val ry = max(1.5, 15 * open).toFloat()
        cyan(fill, glow)
        if (m == 2 || m == 3) {
            cyan(stroke, glow)
            stroke.strokeWidth = 4f
            path.rewind()
            oval.set(ex - rx, ey - 9, ex + rx, ey + 9)
            path.addArc(oval, if (m == 2) 20f else 200f, 140f)
            c.drawPath(path, stroke)
            return
        }
        val erx = if (m == 1) 14f else rx
        val ery = if (m == 1) max(1.5, 20 * open).toFloat() else ry
        c.save()
        path.rewind()
        if (m == 0) {
            path.addRect(ex - 30, ey - 40, ex + 30, ey + 3, Path.Direction.CW)
        } else if (m == 4 || m == 5) {
            val inner = if (m == 5) ey - 2 else ey - ery - 4
            val outer = if (m == 5) ey - ery - 6 else ey - 3
            val yl = if (side < 0) outer else inner
            val yr = if (side < 0) inner else outer
            path.moveTo(ex - 30, yl - (yr - yl) * (14f / 60))
            path.lineTo(ex + 30, yr + (yr - yl) * (14f / 60))
            path.lineTo(ex + 30, ey + 40)
            path.lineTo(ex - 30, ey + 40)
            path.close()
        } else {
            path.addRect(ex - 30, ey - 40, ex + 30, ey + 40, Path.Direction.CW)
        }
        c.clipPath(path)
        c.drawOval(ex - erx, ey - ery, ex + erx, ey + ery, fill)
        c.restore()
    }

    private fun face(c: android.graphics.Canvas, m: Int, open: Double, s: Double, glow: Boolean) {
        eye(c, m, -1, -42f, -6f, open, glow)
        eye(c, m, 1, 42f, -6f, open, glow)
        cyan(stroke, glow)
        stroke.strokeWidth = 3.5f
        if (m == 1) {
            cyan(fill, glow)
            c.drawOval(-5f, 24f, 5f, 37f, fill)
        } else if (m == 3) {
            path.rewind()
            oval.set(-9f, 16f, 9f, 28f)
            path.addArc(oval, 20f, 140f)
            c.drawPath(path, stroke)
        } else if (m == 4) {
            val run = (s % .9) / .9
            val len = 10 + 34 * run
            cyan(stroke, glow, (1 - run * .3).toFloat())
            stroke.strokeWidth = 7f
            for (side in intArrayOf(-1, 1)) {
                val x0 = side * 46f
                path.rewind()
                path.moveTo(x0, 6f)
                for (i in 1..8) path.lineTo(x0 + (sin(i * 1.4 + s * 6) * 2.5).toFloat(), (6 + len * i / 8).toFloat())
                c.drawPath(path, stroke)
            }
        } else if (m == 5) {
            path.rewind()
            path.moveTo(-10f, 30f)
            path.quadTo(0f, 24f, 10f, 30f)
            c.drawPath(path, stroke)
        }
    }

    private fun visor(mo: MoodFrame): Bitmap {
        if (faceMood == mo.cur && faceOpen == mo.open && (mo.cur != 4 || faceSeconds == mo.s)) return face
        faceMood = mo.cur
        faceOpen = mo.open
        faceSeconds = mo.s
        face.eraseColor(android.graphics.Color.TRANSPARENT)
        faceCanvas.save()
        faceCanvas.scale(faceScale, faceScale)
        path.rewind()
        oval.set(0f, 0f, 192f, 123f)
        path.addRoundRect(oval, 50f, 50f, Path.Direction.CW)
        faceCanvas.clipPath(path)
        faceCanvas.translate(96f, 61.5f)
        face(faceCanvas, mo.cur, mo.open, mo.s, true)
        face(faceCanvas, mo.cur, mo.open, mo.s, false)
        faceCanvas.restore()
        return face
    }

    fun draw(c: android.graphics.Canvas, mo: MoodFrame, cx: Float, cy: Float) {
        val a = poseOf(mo.prev, mo.s + STOP)
        val b = poseOf(mo.cur, mo.s)
        val w = mo.fade.toFloat()
        val x = a.x * (1 - w) + b.x * w
        val y = a.y * (1 - w) + b.y * w
        val rot = a.rotation * (1 - w) + b.rotation * w
        val shadowScale = 1 - clamp01(-y / 20.0).toFloat() * .2f
        if (shadowScale != lastShadowScale) {
            lastShadowScale = shadowScale
            shadow.eraseColor(android.graphics.Color.TRANSPARENT)
            shadowCanvas.save()
            shadowCanvas.scale(faceScale, faceScale)
            // Only the ellipse narrows under lift; RN keeps its Gaussian sigma at 10 dp.
            shadowCanvas.drawOval(140 - 100 * shadowScale, 39f, 140 + 100 * shadowScale, 61f, shadowPaint)
            shadowCanvas.restore()
        }
        shadowBounds.set(cx - 140, cy + 89, cx + 140, cy + 189)
        c.drawBitmap(shadow, null, shadowBounds, bitmapPaint)

        c.save()
        c.translate(cx + x, cy + y)
        c.rotate(rot)
        val halo = if (mo.cur == 3) smooth(mo.s / .3) else if (mo.prev == 3) 1 - mo.fade else 0.0
        if (halo > 0) {
            haloPaint.alpha = (halo * 255).roundToInt()
            val hw = (150 * (.4 + .6 * halo)).toFloat()
            val hy = (-128 - 12 * halo).toFloat()
            c.drawOval(-hw / 2, hy, hw / 2, hy + 26, haloPaint)
        }
        c.drawBitmap(head, null, imageBounds, bitmapPaint)
        c.drawBitmap(visor(mo), null, faceBounds, bitmapPaint)
        c.restore()

        if (mo.cur == 2) for (i in 0..2) {
            val u = ((mo.s + i * .45) % 1.35) / 1.35
            zPaint.alpha = (sin(PI * u) * mo.open * 255).roundToInt().coerceIn(0, 255)
            val s = (8 + 8 * u).toFloat()
            val zx = cx + 110 + (20 * u).toFloat()
            val zy = cy - 110 - (70 * u).toFloat()
            path.rewind()
            path.moveTo(zx - s / 2, zy - s / 2)
            path.lineTo(zx + s / 2, zy - s / 2)
            path.lineTo(zx - s / 2, zy + s / 2)
            path.lineTo(zx + s / 2, zy + s / 2)
            c.drawPath(path, zPaint)
        }
    }
}

private fun robotText(font: FontFamily, size: Float, line: Float = size * 1.2f, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = font,
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = weight,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
)

@Composable
fun RobotMoodCompanion(
    modifier: Modifier = Modifier,
    brand: String = "pip",
    action: String = "Talk to Pip",
    autoPlay: Boolean = false,
) {
    val context = LocalContext.current
    val d = LocalDensity.current.density
    val view = LocalView.current
    val reduced = rememberReducedMotion()
    val auto by rememberUpdatedState(autoPlay)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val clock = remember { RobotClock() }
    val face = remember(context) { Typeface.createFromAsset(context.assets, "robotmoodcompanion/robotmoodcompanion_BricolageHeadline.ttf") }
    val family = remember(face) { FontFamily(androidx.compose.ui.text.font.Typeface(face)) }
    val head = remember(context) {
        context.assets.open("robotmoodcompanion/robotmoodcompanion_robot-head.png").use { BitmapFactory.decodeStream(it) }
    }
    val artwork = remember(head, d) { RobotArtwork(head, d) }
    val measurer = rememberTextMeasurer()
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    // RN's auto status bar uses the host's light icons throughout these room colors.
    BlockSystemBars(darkIcons = false)

    LaunchedEffect(lifecycle, reduced) {
        if (reduced) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var previous = 0L
            while (true) withFrameNanos { now ->
                val dt = if (previous == 0L) 0.0 else (now - previous) / 1e9
                previous = now
                clock.advance(dt, auto)
            }
        }
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val width = maxWidth.value
        val height = maxHeight.value
        val safeTop = insets.calculateTopPadding().value
        val safeBottom = insets.calculateBottomPadding().value
        val textWidth = ((width - 48) * d).roundToInt()
        val names = remember(measurer, family, textWidth) {
            MOODS.map { measurer.measure(AnnotatedString(it.name), robotText(family, 64f, 70f).copy(letterSpacing = (-2).sp), constraints = Constraints(maxWidth = textWidth)) }
        }
        val lines = remember(measurer, textWidth) {
            MOODS.map { measurer.measure(AnnotatedString(it.line), robotText(FontFamily.Default, 18f, 24f), constraints = Constraints(maxWidth = textWidth)) }
        }
        val brandText = remember(measurer, family, brand) { measurer.measure(AnnotatedString(brand), robotText(family, 24f)) }
        val battery = remember(measurer) { measurer.measure(AnnotatedString("Battery 82 %"), robotText(FontFamily.Default, 14f, weight = FontWeight.SemiBold)) }
        val actionText = remember(measurer, family, action) { measurer.measure(AnnotatedString(action), robotText(family, 18f)) }
        fun tap() {
            clock.tap()
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        }

        Canvas(
            Modifier.fillMaxSize()
                .pointerInput(reduced) {
                    if (!reduced) awaitEachGesture {
                        val down = awaitFirstDown()
                        val up = waitForUpOrCancellation()
                        // Gesture.Tap() uses 500 ms and no distance limit in the RN reference.
                        if (up != null && up.uptimeMillis - down.uptimeMillis <= 500) {
                            up.consume()
                            tap()
                        }
                    }
                }
                .semantics {
                    role = Role.Button
                    contentDescription = "Pip. Tap to change its mood."
                    if (!reduced) onClick("Change Pip's mood") { tap(); true }
                },
        ) {
            val mo = moodAt(clock.time)
            val ink = mixColor(MOODS[mo.prev].ink, MOODS[mo.cur].ink, mo.fade)
            val background = mixColor(MOODS[mo.prev].background, MOODS[mo.cur].background, mo.fade)
            drawRect(background)
            val top = (safeTop + 8) * d
            drawText(brandText, ink, Offset(24 * d, top + (34 * d - brandText.size.height) / 2))
            drawText(battery, ink.copy(alpha = .7f), Offset((width - 24) * d - battery.size.width, top + (34 * d - battery.size.height) / 2))

            for (k in MOODS.indices) {
                val opacity = (if (mo.cur == k) mo.fade else if (mo.prev == k) 1 - mo.fade else 0.0).toFloat()
                if (opacity <= 0f) continue
                val titleTop = (safeTop + 64 + 12 * (1 - opacity)) * d
                drawText(names[k], ink.copy(alpha = opacity), Offset(24 * d, titleTop))
                drawText(lines[k], ink.copy(alpha = opacity * .8f), Offset(24 * d, titleTop + 74 * d))
            }

            drawIntoCanvas { canvas ->
                val c = canvas.nativeCanvas
                c.save()
                c.scale(d, d)
                artwork.draw(c, mo, width / 2, height * .52f)
                c.restore()
            }

            val buttonY = (height - safeBottom - 14 - 58) * d
            drawRoundRect(ink, Offset(24 * d, buttonY), Size((width - 48) * d, 58 * d), CornerRadius(29 * d))
            drawText(actionText, background, Offset((size.width - actionText.size.width) / 2, buttonY + (58 * d - actionText.size.height) / 2))
            // The six widths always add up to 66 dp, plus five 8 dp gaps.
            var dotX = (width - 106) / 2 * d
            val dotY = buttonY - 28 * d
            for (k in MOODS.indices) {
                val on = (if (mo.cur == k) mo.fade else if (mo.prev == k) 1 - mo.fade else 0.0).toFloat()
                val dotWidth = (8 + 18 * on) * d
                drawRoundRect(ink.copy(alpha = .3f + .7f * on), Offset(dotX, dotY), Size(dotWidth, 8 * d), CornerRadius(4 * d))
                dotX += dotWidth + 8 * d
            }
        }
    }
}
