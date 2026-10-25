package com.ngocdevv.nativecook.blocks.forms.sandpincode

import android.graphics.Paint
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.cssBoxShadow
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/*
 Sand PIN Code — a wellness app's lock screen on a zen sand tray (a port of SandPinCode.tsx).
 Each digit typed on the river-stone keypad is carved into its slot by a bamboo stylus (path trim,
 0.26 s): the groove is a floor stroke, a shadowed upper wall, a lit lower wall and a faint berm. A
 wrong code shakes the tray, then a rake sweeps across and the digits crumble into grains that
 slide and sink, leaving fresh rake lines; the right code sends rings out through the sand and the
 tray brightens. A horizontal swipe over the tray rakes it by hand.

 One Canvas (the sand shader, grooves, grains, rake and stylus); the keypad and copy are
 composables. One frame float drives every draw and layer.
*/

/** The canvas reaches this far past each side of the screen so a shake never shows its edge. */
private const val PAD = 24f
private const val IDLE = 2f
private const val RETURN_LEN = 0.75f
private const val KEY_W = 84f
private const val KEY_H = 60f
private const val KEY_GAP_X = 22f
private const val KEY_GAP_Y = 14f
private val KEYS = arrayOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "del")

private class SandState(val reduced: Boolean, val auto: Boolean) {
    val frame = mutableFloatStateOf(0f)
    val mode = mutableIntStateOf(0)
    val rev = mutableIntStateOf(0)
    var clock = 0f
    var now = 0f
    var lastTouch = 0f
    var user = SandUser()
    var x0 = 0f
    var rakeFrom = 0f
    var rakeTo = 0f

    private val shown = SandFrame()
    private var cachedAt = Float.NaN
    private var cachedMode = -1
    private var cachedRev = -1

    val tNow get() = if (mode.intValue == 0) clock else now

    /** The tray on screen; reads the frame state, so call it only from draw / layer lambdas. */
    fun sand(): SandFrame {
        val f = frame.floatValue
        val m = mode.intValue
        val r = rev.intValue
        if (f == cachedAt && m == cachedMode && r == cachedRev) return shown
        cachedAt = f; cachedMode = m; cachedRev = r
        if (m == 0) {
            if (auto) sandAuto(clock, x0, shown.also { it.copyFrom(SandFrame()) }) else shown.copyFrom(SandFrame())
            return shown
        }
        val u = user
        val n = now
        shown.copyFrom(u.sand)
        shown.press = if (shown.key >= 0) sandPressAt(n - u.keyAt) else 0f
        shown.shake = if (reduced) 0f else sandShakeAt(n - u.shakeAt)
        val back = if (u.returnAt >= 0f) 1 - sandEaseInOutCubic(sandSeg(n, u.returnAt, u.returnAt + RETURN_LEN - 0.05f)) else 1f
        if (u.unlockAt >= 0f) {
            shown.ripple = if (reduced) -1f else n - u.unlockAt
            shown.bright = sandEaseOutCubic(sandSeg(n, u.unlockAt, u.unlockAt + 0.4f)) * back
            shown.unlock = sandEaseOutCubic(sandSeg(n, u.unlockAt + 0.1f, u.unlockAt + 0.5f)) * back
        }
        shown.fade = back
        return shown
    }

    /** Leaves autoPlay for the user's own tray (it starts empty: the loop's rest). */
    fun takeOver() {
        if (mode.intValue != 1) {
            user = SandUser()
            mode.intValue = 1
        }
        lastTouch = now
    }

    fun markPasses(n: Float) {
        val s = user.sand
        for (i in 0 until SAND_SLOTS) {
            if (s.digits[i] >= 0 && s.rakeOn > 0f && s.rakeX >= x0 + i * SAND_SLOT_GAP) {
                s.grainDigits[i] = s.digits[i]
                s.grainAt[i] = n
                s.digits[i] = -1
                s.carveAt[i] = -1f
                s.linesOn = 1f
            }
        }
        if (s.digits.all { it < 0 }) user.entered.clear()
    }

    fun pressKey(index: Int, view: View) {
        takeOver()
        val u = user
        val n = now
        val s = u.sand
        if (u.unlockAt >= 0f || u.rakeStart >= 0f || u.checkAt >= 0f || u.fingerRake) return
        u.keyAt = n
        s.key = index
        if (index == 11) {
            val k = u.entered.size - 1
            if (k >= 0) {
                u.entered.removeAt(k)
                s.digits[k] = -1
                s.carveAt[k] = -1f
            }
        } else if (index != 9 && u.entered.size < SAND_SLOTS) {
            val d = if (index == 10) 0 else index + 1
            val k = u.entered.size
            u.entered.add(d)
            s.digits[k] = d
            s.carveAt[k] = n
            s.passAt[k] = -1f
            if (u.entered.size == SAND_SLOTS) u.checkAt = n + SAND_CARVE + 0.12f
        }
        rev.intValue++
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    /** Whether anything on the user's tray still moves at `n`. */
    fun moving(n: Float): Boolean {
        val u = user
        val s = u.sand
        if (mode.intValue == 2 || u.fingerRake || u.rakeStart >= 0f || u.checkAt >= 0f || s.rakeOn > 0f) return true
        if (n - u.keyAt < 0.5f || n - u.shakeAt < 0.5f) return true
        if (u.unlockAt >= 0f && n - u.unlockAt < 3f) return true
        for (i in 0 until SAND_SLOTS) {
            if (s.carveAt[i] >= 0f && n - s.carveAt[i] < SAND_CARVE + 0.25f) return true
            if (s.grainAt[i] >= 0f && n - s.grainAt[i] in 0f..SAND_GRAIN_LIFE) return true
        }
        return false
    }
}

private fun feel(view: View, kind: Int) {
    val c = when {
        kind == 1 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> HapticFeedbackConstants.REJECT
        kind == 2 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> HapticFeedbackConstants.CONFIRM
        else -> HapticFeedbackConstants.LONG_PRESS
    }
    view.performHapticFeedback(c)
}

/**
 * A wellness app's lock screen on a zen sand tray: digits typed on river-stone keys are carved
 * into the sand; a wrong code is raked away, the right one sends rings through the tray.
 *
 * @param autoPlay types a wrong code, rakes it away, types the right one and unlocks, every LOOP seconds.
 */
@Composable
fun SandPinCode(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val reduced = rememberReducedMotion()
    val auto = autoPlay && !reduced
    val state = remember(reduced, auto) { SandState(reduced, auto) }
    val view = LocalView.current
    val density = LocalDensity.current
    val insets = WindowInsets.safeDrawing
    val insetTop = insets.getTop(density) / density.density
    val insetBottom = insets.getBottom(density) / density.density
    val shader = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) runCatching { RuntimeShader(SAND_AGSL) }.getOrNull() else null
    }
    val digitArt = remember { sandMakeDigitArt() }
    val paints = remember { SandGroovePaints() }

    BoxWithConstraints(modifier.fillMaxSize().background(Color(0xFFCDB38C))) {
        val W = maxWidth.value
        val H = maxHeight.value
        val d = density.density
        val CW = W + PAD * 2
        val cx = CW / 2
        val keypadH = KEY_H * 4 + KEY_GAP_Y * 3
        val keypadTop = H - insetBottom - 20f - keypadH
        val headerBottom = insetTop + 128f
        val slotY = ((headerBottom + keypadTop) / 2 + 4).roundToInt().toFloat()
        val x0 = cx - ((SAND_SLOTS - 1) * SAND_SLOT_GAP) / 2
        val rakeFrom = x0 - SAND_RAKE_OVER
        val rakeTo = x0 + (SAND_SLOTS - 1) * SAND_SLOT_GAP + SAND_RAKE_OVER
        val stone = floatArrayOf(CW - PAD - 54f, insetTop + 64f, 24f)
        state.x0 = x0
        state.rakeFrom = rakeFrom
        state.rakeTo = rakeTo

        val rake by produceState<SandSprite?>(null, slotY, d) { value = withContext(Dispatchers.Default) { sandMakeRake(slotY, d) } }
        val stylus by produceState<SandSprite?>(null, d) { value = withContext(Dispatchers.Default) { sandMakeStylus(d) } }
        // Below Android 13 the resting tray is computed once on the CPU.
        val restTray by produceState<android.graphics.Bitmap?>(null, CW, H, slotY, insetTop) {
            if (shader == null) value = withContext(Dispatchers.Default) {
                sandRenderRest(CW.roundToInt(), H.roundToInt(), floatArrayOf(x0, slotY, SAND_SLOT_GAP, SAND_SLOT_HALF_H), stone)
            }
        }

        LaunchedEffect(state) {
            var last = -1L
            while (true) {
                if (state.mode.intValue == 0 && !state.auto) {
                    snapshotFlow { state.mode.intValue }.first { it != 0 }
                    last = -1L
                }
                withFrameNanos { t ->
                    val dt = if (last < 0) 1f / 60f else min((t - last) / 1e9f, 0.064f)
                    last = t
                    val n = state.now + dt
                    state.now = n
                    val m = state.mode.intValue
                    if (m == 0) {
                        state.clock = (state.clock + dt) % LOOP
                        state.frame.floatValue = state.clock
                        return@withFrameNanos
                    }
                    val u = state.user
                    val s = u.sand
                    if (u.checkAt >= 0f && n >= u.checkAt) {
                        u.checkAt = -1f
                        val ok = u.entered.size == SAND_SLOTS && u.entered.indices.all { u.entered[it] == SandCode[it] }
                        if (ok) {
                            u.unlockAt = n
                            feel(view, 2)
                        } else {
                            u.shakeAt = n
                            u.rakeStart = n + 0.42f
                            feel(view, 1)
                        }
                    }
                    if (u.rakeStart >= 0f && n >= u.rakeStart) {
                        val r = sandSeg(n, u.rakeStart, u.rakeStart + SAND_RAKE_LEN)
                        s.rakeX = state.rakeFrom + (state.rakeTo - state.rakeFrom) * sandEaseInOutCubic(r)
                        s.rakeFrom = state.rakeFrom
                        s.rakeOn = if (r < 1f) sandClamp01((n - u.rakeStart) / 0.12f) else 1 - sandSeg(n, u.rakeStart + SAND_RAKE_LEN, u.rakeStart + SAND_RAKE_LEN + 0.25f)
                        if (n > u.rakeStart + SAND_RAKE_LEN + 0.25f) {
                            u.rakeStart = -1f
                            s.rakeOn = 0f
                        }
                    } else if (!u.fingerRake && s.rakeOn > 0f) {
                        s.rakeOn = max(0f, s.rakeOn - dt * 4f)
                    }
                    state.markPasses(n)
                    val busy = u.rakeStart >= 0f || u.fingerRake || u.checkAt >= 0f || (u.unlockAt >= 0f && n - u.unlockAt < 1.4f)
                    if (busy) state.lastTouch = n
                    if (m == 1 && n - state.lastTouch > IDLE) {
                        state.mode.intValue = 2
                        u.returnAt = n
                    }
                    if (m == 2 && n - u.returnAt > RETURN_LEN) {
                        state.user = SandUser()
                        state.clock = 0f
                        state.mode.intValue = 0
                        state.frame.floatValue = 0f
                        return@withFrameNanos
                    }
                    if (state.moving(n)) state.frame.floatValue = n
                }
            }
        }

        val bmp = remember { Paint(Paint.FILTER_BITMAP_FLAG) }
        val shaderPaint = remember { Paint() }
        val grainLight = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE9D9BB.toInt() } }
        val grainDark = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFA88C66.toInt() } }
        val linePaints = remember {
            arrayOf(
                Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeWidth = 4f; color = 0x52FFF6E2 },
                Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeWidth = 3.4f; color = 0x8C846844.toInt() },
                Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeWidth = 1.6f; color = 0x8C5C462A.toInt() },
            )
        }
        val fx = remember { Paint(Paint.ANTI_ALIAS_FLAG) }
        val ring = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE } }
        val lightPath = remember { android.graphics.Path() }
        val darkPath = remember { android.graphics.Path() }
        val linePath = remember { android.graphics.Path() }
        val groove = remember { android.graphics.Path() }

        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { translationX = state.sand().shake * d },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val s = state.sand()
                val t = state.tNow
                drawIntoCanvas { cv ->
                    val nc = cv.nativeCanvas
                    nc.save()
                    nc.scale(d, d)
                    nc.translate(-PAD, 0f)
                    // The tray.
                    if (shader != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        shader.setFloatUniform("res", CW, H)
                        shader.setFloatUniform("slots", x0, slotY, SAND_SLOT_GAP, SAND_SLOT_HALF_H)
                        shader.setFloatUniform("stone", stone[0], stone[1], stone[2])
                        shader.setFloatUniform("ripple", cx, slotY, s.ripple)
                        shader.setFloatUniform("bright", s.bright)
                        shaderPaint.shader = shader
                        nc.drawRect(0f, 0f, CW, H, shaderPaint)
                    } else {
                        restTray?.let { nc.drawBitmap(it, null, RectF(0f, 0f, CW, H), bmp) }
                        if (s.bright > 0.002f) {
                            fx.shader = null
                            fx.color = 0xFFFFE6C2.toInt()
                            fx.alpha = (s.bright * 0.22f * 255).roundToInt()
                            nc.drawRect(0f, 0f, CW, H, fx)
                        }
                        if (s.ripple >= 0f) {
                            val front = s.ripple * 300f
                            val env = exp(-s.ripple * 1.4f)
                            ring.strokeWidth = 10f
                            for (k in 0 until 3) {
                                val rr = front - k * 26f
                                if (rr <= 0f) continue
                                ring.color = if (k % 2 == 0) 0xFFFFF4DC.toInt() else 0xFF8C6E48.toInt()
                                ring.alpha = (env * (0.35f - k * 0.08f) * 255).coerceIn(0f, 255f).roundToInt()
                                nc.drawCircle(cx, slotY, rr, ring)
                            }
                        }
                    }

                    // Fresh rake lines behind the rake.
                    val linesOn = s.linesOn * s.fade
                    if (linesOn > 0.002f) {
                        val end = min(s.rakeX, rakeTo)
                        if (end > rakeFrom) {
                            linePath.rewind()
                            for (k in 0 until 7) {
                                val y = slotY - 42f + k * 14f
                                linePath.moveTo(rakeFrom, y)
                                linePath.lineTo(end, y)
                            }
                            val a = (linesOn * 255).roundToInt()
                            val base = intArrayOf(0x52, 0x8C, 0x8C)
                            val offs = floatArrayOf(0.9f, 1.1f, 0f, 0f, -0.6f, -0.8f)
                            for (j in 0..2) {
                                linePaints[j].alpha = base[j] * a / 255
                                nc.save()
                                nc.translate(offs[j * 2], offs[j * 2 + 1])
                                nc.drawPath(linePath, linePaints[j])
                                nc.restore()
                            }
                        }
                    }

                    // The carved digits.
                    for (i in 0 until SAND_SLOTS) {
                        val dg = s.digits[i]
                        if (dg < 0 || s.carveAt[i] < 0f || t < s.carveAt[i]) continue
                        if (s.passAt[i] >= 0f && t >= s.passAt[i]) continue
                        val op = s.fade
                        if (op <= 0.002f) continue
                        val end = sandEaseInOutCubic(sandClamp01((t - s.carveAt[i]) / SAND_CARVE))
                        groove.rewind()
                        digitArt.measures[dg].getSegment(0f, digitArt.lengths[dg] * end, groove, true)
                        val gx = x0 + i * SAND_SLOT_GAP
                        val layered = op < 0.999f
                        if (layered) nc.saveLayerAlpha(gx - 30f, slotY - 40f, gx + 30f, slotY + 40f, (op * 255).roundToInt())
                        nc.save()
                        nc.translate(gx, slotY)
                        nc.drawPath(groove, paints.berm)
                        nc.translate(1.1f, 1.3f); nc.drawPath(groove, paints.lit); nc.translate(-1.1f, -1.3f)
                        nc.drawPath(groove, paints.floor)
                        nc.translate(-0.9f, -1f); nc.drawPath(groove, paints.shade); nc.translate(0.9f, 1f)
                        val glow = s.bright * 0.85f
                        if (glow > 0.002f) {
                            paints.glow.alpha = (glow * 255).roundToInt()
                            nc.drawPath(groove, paints.glow)
                        }
                        nc.restore()
                        if (layered) nc.restore()
                    }

                    // Grains: 50 per crumbling digit, pushed ahead by the rake, sliding down, sinking.
                    lightPath.rewind()
                    darkPath.rewind()
                    var any = false
                    for (i in 0 until SAND_SLOTS) {
                        val dg = s.grainDigits[i]
                        val at = s.grainAt[i]
                        if (dg < 0 || at < 0f) continue
                        val tau = t - at
                        if (tau < 0f || tau > SAND_GRAIN_LIFE) continue
                        val pts = digitArt.samples[dg]
                        val sx = x0 + i * SAND_SLOT_GAP
                        for (k in 0 until SAND_SAMPLES) {
                            val h1 = sandHash(i * 131 + k * 7.3f)
                            val h2 = sandHash(i * 71 + k * 3.1f + 9f)
                            val life = SAND_GRAIN_LIFE * (0.55f + 0.45f * h2)
                            if (tau > life) continue
                            val push = (14f + 46f * h1) * (1 - exp(-tau / 0.16f))
                            val fall = (6f + 26f * h2) * sandEaseOutCubic(min(1f, tau / 0.55f))
                            val gx = sx + pts[k * 2] + push + (h2 - 0.5f) * 6f * tau
                            val gy = slotY + pts[k * 2 + 1] + fall
                            val r = (0.9f + 1.1f * h1) * (1 - (tau / life).pow(3))
                            (if (k % 3 == 0) darkPath else lightPath).addCircle(gx, gy, r, android.graphics.Path.Direction.CW)
                            any = true
                        }
                    }
                    if (any) {
                        nc.drawPath(lightPath, grainLight)
                        nc.drawPath(darkPath, grainDark)
                    }

                    // The stylus rides the stroke of whichever digit is being carved.
                    stylus?.let { sp ->
                        for (i in 0 until SAND_SLOTS) {
                            val dg = s.digits[i]
                            if (dg < 0 || s.carveAt[i] < 0f) continue
                            val tau = t - s.carveAt[i]
                            if (tau < -0.12f || tau > SAND_CARVE + 0.22f) continue
                            val pr = sandEaseInOutCubic(sandClamp01(tau / SAND_CARVE))
                            val k = min(SAND_SAMPLES - 1, (pr * (SAND_SAMPLES - 1)).roundToInt())
                            val pts = digitArt.samples[dg]
                            val o = if (tau < 0f) sandClamp01((tau + 0.12f) / 0.12f) else 1 - sandClamp01((tau - SAND_CARVE) / 0.22f)
                            if (!state.reduced && o > 0.002f) {
                                nc.save()
                                nc.translate(x0 + i * SAND_SLOT_GAP + pts[k * 2], slotY + pts[k * 2 + 1])
                                bmp.alpha = (o * 255).roundToInt()
                                nc.drawBitmap(sp.bitmap, null, sp.rect, bmp)
                                bmp.alpha = 255
                                nc.restore()
                            }
                            break
                        }
                    }

                    // The rake.
                    rake?.let { rk ->
                        if (s.rakeOn > 0.002f) {
                            nc.save()
                            nc.translate(s.rakeX, 0f)
                            bmp.alpha = (s.rakeOn * 255).roundToInt()
                            nc.drawBitmap(rk.bitmap, null, rk.rect, bmp)
                            bmp.alpha = 255
                            nc.restore()
                        }
                    }
                    nc.restore()
                }
            }

            // The tray takes horizontal swipes: a rake by hand.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(max(0f, keypadTop - 8f).dp)
                    .semantics { contentDescription = "Sand tray with six passcode slots. Swipe across it to rake it smooth." }
                    .pointerInput(state) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            var active = false
                            try {
                                while (true) {
                                    val ev = awaitPointerEvent()
                                    val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!ch.pressed) break
                                    val x = ch.position.x / d + PAD
                                    if (!active) {
                                        val dx = (ch.position.x - down.position.x) / d
                                        val dy = (ch.position.y - down.position.y) / d
                                        if (abs(dy) > 24f) break
                                        if (abs(dx) > 14f) {
                                            active = true
                                            state.takeOver()
                                            val u = state.user
                                            u.fingerRake = true
                                            u.sand.rakeFrom = x
                                            u.sand.rakeX = x
                                            u.sand.rakeOn = 1f
                                            state.rev.intValue++
                                        }
                                    } else {
                                        ch.consume()
                                        val u = state.user
                                        u.sand.rakeX = x
                                        u.sand.rakeFrom = min(u.sand.rakeFrom, x)
                                        state.lastTouch = state.now
                                    }
                                }
                            } finally {
                                val u = state.user
                                if (active && u.fingerRake) {
                                    // Letting go rakes the rest of the tray too: every digit left crumbles.
                                    u.fingerRake = false
                                    val n = state.now
                                    for (i in 0 until SAND_SLOTS) {
                                        if (u.sand.digits[i] >= 0) {
                                            u.sand.grainDigits[i] = u.sand.digits[i]
                                            u.sand.grainAt[i] = n
                                            u.sand.digits[i] = -1
                                            u.sand.carveAt[i] = -1f
                                        }
                                    }
                                    u.entered.clear()
                                    state.rev.intValue++
                                }
                                if (active) state.lastTouch = state.now
                            }
                        }
                    },
            )

            // The keypad.
            Column(
                Modifier
                    .offset(x = ((W - (KEY_W * 3 + KEY_GAP_X * 2)) / 2).dp, y = keypadTop.dp)
                    .width((KEY_W * 3 + KEY_GAP_X * 2).dp),
                verticalArrangement = Arrangement.spacedBy(KEY_GAP_Y.dp),
            ) {
                for (row in 0 until 4) {
                    Row(horizontalArrangement = Arrangement.spacedBy(KEY_GAP_X.dp)) {
                        for (col in 0 until 3) {
                            val i = row * 3 + col
                            SandPebble(i, KEYS[i], state) { state.pressKey(i, view) }
                        }
                    }
                }
            }
        }

        // Header and hint, still while the tray shakes.
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 84.dp, top = (insetTop + 20).dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier
                        .size(26.dp)
                        .cssBoxShadow(Color(0x40503714), 1.dp, 2.dp, 13.dp)
                        .clip(CircleShape)
                        .background(Color(0x73FFF8E8))
                        .drawWithContent {
                            drawContent()
                            clipPath(Path().apply { addOval(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height)) }) {
                                drawCircle(Color(0x99FFFFFF), radius = size.width / 2, style = Stroke(width = 1.dp.toPx()), center = Offset(size.width / 2, size.height / 2 + 0.5f.dp.toPx()))
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    SandLockIcon(open = false, Modifier.graphicsLayer { alpha = 1f - state.sand().unlock })
                    SandLockIcon(open = true, Modifier.graphicsLayer {
                        val u = state.sand().unlock
                        alpha = u
                        scaleX = 0.9f + 0.1f * u
                        scaleY = 0.9f + 0.1f * u
                    })
                }
                BasicText("DUNE", style = TextStyle(color = Color(0xFF5B4528), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp))
            }
            Box {
                Column(Modifier.graphicsLayer {
                    val u = state.sand().unlock
                    alpha = 1f - u
                    translationY = -8f * u * d
                }) {
                    SandTitle("Enter your passcode")
                    SandSub("Draw six digits in the sand.")
                }
                Column(Modifier.graphicsLayer {
                    val u = state.sand().unlock
                    alpha = u
                    translationY = 8f * (1f - u) * d
                }) {
                    SandTitle("Welcome back, Ilsa")
                    SandSub("Your evening session is ready.")
                }
            }
        }
        BasicText(
            "Swipe across the tray to rake it smooth",
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = (slotY + SAND_SLOT_HALF_H + 22f).dp)
                .padding(horizontal = 24.dp),
            style = TextStyle(color = Color(0x8C3D2D18), fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp, textAlign = TextAlign.Center),
        )
    }
}

@Composable
private fun SandTitle(text: String) {
    BasicText(
        text,
        maxLines = 1,
        autoSize = TextAutoSize.StepBased(minFontSize = 18.sp, maxFontSize = 27.sp),
        style = TextStyle(color = Color(0xFF3D2D18), fontSize = 27.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
    )
}

@Composable
private fun SandSub(text: String) {
    BasicText(
        text,
        modifier = Modifier.padding(top = 4.dp),
        style = TextStyle(color = Color(0xAD3D2D18), fontSize = 15.sp, fontWeight = FontWeight.Medium),
    )
}

/** A small padlock (lock.fill / lock.open.fill), 13 dp. */
@Composable
private fun SandLockIcon(open: Boolean, modifier: Modifier) {
    Canvas(modifier.size(13.dp)) {
        val u = size.width / 13f
        val c = Color(0xFF5B4528)
        drawRoundRect(c, Offset(1.5f * u, 6f * u), androidx.compose.ui.geometry.Size(10f * u, 7f * u), androidx.compose.ui.geometry.CornerRadius(1.6f * u))
        val shackle = Path().apply {
            if (open) {
                moveTo(9f * u, 6f * u); lineTo(9f * u, 3.6f * u)
                cubicTo(9f * u, 0.4f * u, 13f * u, 0.4f * u, 13f * u, 3.6f * u)
            } else {
                moveTo(3.8f * u, 6f * u); lineTo(3.8f * u, 4f * u)
                cubicTo(3.8f * u, 0.6f * u, 9.2f * u, 0.6f * u, 9.2f * u, 4f * u); lineTo(9.2f * u, 6f * u)
            }
        }
        drawPath(shackle, c, style = Stroke(width = 1.6f * u, cap = StrokeCap.Round))
    }
}

/** A river stone key: irregular corners, a warm grey gradient, two shadows on the sand, an engraved label. */
@Composable
private fun SandPebble(index: Int, label: String, state: SandState, onPress: () -> Unit) {
    if (label.isEmpty()) {
        Box(Modifier.size(KEY_W.dp, KEY_H.dp), contentAlignment = Alignment.Center) {
            BasicText("Forgot?", style = TextStyle(color = Color(0x993D2D18), fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
        }
        return
    }
    val shape = remember(index) {
        fun r(k: Int) = (22f + sandHash((index * 9 + k).toFloat()) * 14f).dp
        RoundedCornerShape(topStart = r(1), topEnd = r(2), bottomStart = r(3), bottomEnd = r(4))
    }
    val rot = remember(index) { (sandHash((index * 5 + 2).toFloat()) - 0.5f) * 6f }
    val colors = remember(index) {
        val tone = sandHash((index * 3 + 1).toFloat())
        when {
            tone > 0.66f -> listOf(Color(0xFFB7AEA1), Color(0xFF8F877C), Color(0xFF655F57))
            tone > 0.33f -> listOf(Color(0xFFA9A59C), Color(0xFF807C74), Color(0xFF57544F))
            else -> listOf(Color(0xFFBCA995), Color(0xFF94826E), Color(0xFF6A5B4B))
        }
    }
    val avgR = remember(index) { (0 until 4).map { 22f + sandHash((index * 9 + it + 1).toFloat()) * 14f }.average().toFloat() }
    Box(
        Modifier
            .size(KEY_W.dp, KEY_H.dp)
            .semantics {
                role = Role.Button
                contentDescription = if (label == "del") "Delete" else label
            }
            .pointerInput(index) {
                awaitEachGesture {
                    awaitFirstDown()
                    onPress()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size((KEY_W - 6).dp, (KEY_H - 4).dp)
                .graphicsLayer {
                    val s = state.sand()
                    val p = if (s.key == index) s.press else 0f
                    translationY = 2f * p * density
                    scaleX = 1f + 0.05f * p
                    scaleY = 1f - 0.09f * p
                    rotationZ = rot
                }
                .cssBoxShadow(Color(0x66463014), 2.dp, 3.dp, avgR.dp)
                .cssBoxShadow(Color(0x47463014), 12.dp, 20.dp, avgR.dp)
                .clip(shape)
                .drawWithContent {
                    val w = size.width
                    val h = size.height
                    drawRect(Brush.linearGradient(0f to colors[0], 0.55f to colors[1], 1f to colors[2], start = Offset(0.2f * w, 0f), end = Offset(0.8f * w, h)))
                    // inset 0 -2px 3px rgba(30,24,18,0.35) and inset 0 1.5px 0 rgba(255,255,255,0.35).
                    val outline = shape.createOutline(size, layoutDirection, this)
                    drawOutline(outline, Color(0x591E1812), style = Stroke(width = 3.dp.toPx()))
                    drawRect(Brush.verticalGradient(0.7f to Color(0x001E1812), 1f to Color(0x4D1E1812)))
                    drawOutline(outline, Color(0x59FFFFFF), style = Stroke(width = 1.5f.dp.toPx()))
                    drawRect(Brush.verticalGradient(0.5f to Color(0x00000000), 1f to colors[2].copy(alpha = 0.35f)))
                    drawContent()
                },
            contentAlignment = Alignment.Center,
        ) {
            if (label == "del") {
                Canvas(Modifier.size(20.dp)) {
                    val u = size.width / 20f
                    val c = Color(0xC728231E)
                    val body = Path().apply {
                        moveTo(7f * u, 4.5f * u); lineTo(17.5f * u, 4.5f * u); lineTo(17.5f * u, 15.5f * u); lineTo(7f * u, 15.5f * u)
                        lineTo(2f * u, 10f * u); close()
                    }
                    drawPath(body, c, style = Stroke(width = 1.6f * u, join = StrokeJoin.Round))
                    drawLine(c, Offset(9.5f * u, 7.5f * u), Offset(14.5f * u, 12.5f * u), 1.6f * u, StrokeCap.Round)
                    drawLine(c, Offset(14.5f * u, 7.5f * u), Offset(9.5f * u, 12.5f * u), 1.6f * u, StrokeCap.Round)
                }
            } else {
                BasicText(
                    label,
                    style = TextStyle(
                        color = Color(0xCC26211C),
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Medium,
                        fontFeatureSettings = "tnum",
                        shadow = Shadow(Color(0x59FFFFFF), Offset(0f, 1f * LocalDensity.current.density), 0f),
                    ),
                )
            }
        }
    }
}
