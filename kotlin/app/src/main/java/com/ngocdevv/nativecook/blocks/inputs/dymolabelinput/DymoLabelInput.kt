package com.ngocdevv.nativecook.blocks.inputs.dymolabelinput

import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.cssBoxShadow
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/*
 Dymo Label Input — "Shelfwise · Label the jar". A handheld embossing label maker sits on the counter
 under a pantry shelf. Typing turns its character wheel to each letter (a spin with a detent click),
 the trigger is squeezed and the letter is pressed into glossy red tape as raised, stress-whitened
 plastic; the tape steps along. Done (or Cut) snaps the cutter and the strip flies onto the jar of
 oats, where it sticks with a squash and wraps round the glass.

 Touch: type in the field; or turn the wheel with a finger (detents, a tick each) and tap the handle
 to press the letter under the head; tape colour chips re-colour the tape (0.25 s). After a cut the
 labelled jar slides away and a fresh one comes in. With autoPlay, 2 s after the last touch (field
 closed, no strip in flight) the scene cross-fades to the loop's first frame (0.8 s); while the loop
 plays it types "OATS" into the field without focusing it. Reduced motion: the rest frame, still;
 typed letters appear pressed at once, the strip lands without flying.

 One Canvas: wall, shelf, jar, body and wheel are baked once off the main thread; per frame the
 bitmaps are placed and the tape and its letters drawn.
*/

private const val AUTO = 0
private const val USER = 1
private const val RETURN = 2
private const val STILL = 3
private const val RETURN_FOR = 0.8f
private const val CHIP = 36f
private const val CHIP_GAP = 12f
private const val HAPTIC_TICK = 0
private const val HAPTIC_LIGHT = 1
private const val HAPTIC_MEDIUM = 2

private val EaseSpin = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)
private val Ink = Color(0xFF2B2722)
private val MutedInk = Color(0xFF857867)
private val Card = Color(0xFFFFFCF7)
private val Backdrop = Color(0xFFEDE4D7)

private class DyState(val art: DymoArt, val auto: Boolean, val reduced: Boolean, val haptic: (Int) -> Unit) {
    val g = art.geo
    var mode = if (auto) AUTO else STILL
    var now = 0f
    var loopT = 0f
    var modeAt = 0f
    var lastTouch = 0f
    var focused = false
    var touching = false
    var wheel = 0f
    var lastSlot = 0
    val user = DymoUser()

    // Wheel motion: queued spins (start, target, duration), the tween under way, or a spring.
    private val spins = ArrayDeque<FloatArray>()
    private var tweening = false
    private var tFrom = 0f
    private var tTo = 0f
    private var tStart = 0f
    private var tDur = 0f
    private var springing = false
    private var sTarget = 0f
    private var sVel = 0f
    /** Haptics waiting for their moment: time, kind. */
    private val pending = ArrayList<FloatArray>()
    private var ringX = 0f
    private var ringVel = 0f

    val text = mutableStateOf("")
    val tape = mutableIntStateOf(0)
    val ring = mutableFloatStateOf(0f)
    val frame = mutableIntStateOf(0)
    val running = mutableStateOf(true)
    val pose = DymoPose()
    val overPose = DymoPose()
    var overAlpha = 0f
    private var scriptN = -1

    init { compute() }

    private fun jumpWheel(v: Float) {
        wheel = v
        spins.clear(); tweening = false; springing = false
    }

    private fun tween(to: Float, start: Float, dur: Float) {
        spins.addLast(floatArrayOf(start, to, dur))
    }

    /** Hands the machine to the user as the loop left it. */
    fun takeOver() {
        running.value = true
        lastTouch = now
        if (mode == USER) return
        if (mode == AUTO) {
            val lt = loopT
            dyLoopPose(lt, overPose)
            user.reset()
            user.text = overPose.tape
            for (i in overPose.tape.indices) user.e.add(now - 1)
            val cut = lt >= 4.7f && lt < 8.4f
            user.strip = if (cut) WORD else ""
            user.cutAt = if (cut) now - (lt - 4.75f) else -100f
            jumpWheel(overPose.wheel)
            lastSlot = dySlotAt(wheel)
            text.value = overPose.tape
        } else {
            user.reset()
            if (mode == STILL) jumpWheel(0f)
        }
        mode = USER
    }

    /** Presses `added` letters after the ones on the tape, spinning the wheel to each unless `spun`. */
    private fun press(current: String, added: String, spun: Boolean) {
        val t = now
        while (user.e.size > current.length) user.e.removeAt(user.e.size - 1)
        var last = if (user.e.isNotEmpty()) user.e[user.e.size - 1] else t - 1
        var angle = spins.lastOrNull()?.get(1) ?: if (tweening) tTo else if (springing) sTarget else wheel
        for (ch in added) {
            val target = dyAngleFor(dySlotOf(ch), angle)
            val needsSpin = !spun && abs(target - angle) > 0.5f
            val at = max(t + if (needsSpin) 0.36f else 0.04f, last + 0.42f)
            if (needsSpin && !reduced) tween(target, at - 0.36f, 0.32f)
            else if (needsSpin) jumpWheel(target)
            angle = target
            user.e.add(at)
            last = at
            pending.add(floatArrayOf(at, HAPTIC_LIGHT.toFloat()))
        }
        user.text = current + added
        lastTouch = t
        running.value = true
    }

    fun onType(value: String) {
        val clean = value.uppercase().filter { WHEEL.indexOf(it) >= 0 }.take(DYMO_MAX)
        if (mode != USER) takeOver()
        val prev = user.text
        text.value = clean
        if (clean.startsWith(prev)) press(prev, clean.substring(prev.length), false)
        else {
            // Edited in the middle or deleted: the tape shows the new text, already pressed.
            user.text = clean
            user.e.clear()
            for (i in clean.indices) user.e.add(now - 1)
            lastTouch = now
        }
        running.value = true
    }

    fun cut() {
        if (mode != USER) takeOver()
        if (user.text.isEmpty()) return
        val t = now
        user.strip = user.text
        user.stripColour = user.colour
        user.cutAt = t
        user.text = ""
        user.e.clear()
        text.value = ""
        lastTouch = t
        haptic(HAPTIC_MEDIUM)
        pending.add(floatArrayOf(t + if (reduced) 0f else 1.2f, HAPTIC_LIGHT.toFloat()))
        running.value = true
    }

    fun pickTape(i: Int) {
        if (mode != USER) takeOver()
        tape.intValue = i
        haptic(HAPTIC_TICK)
        val t = now
        val shown = if (dySeg(t, user.colourAt, user.colourAt + 0.25f) >= 1) user.colour else user.colourFrom
        user.colourFrom = shown
        user.colour = i
        user.colourAt = t
        lastTouch = t
        running.value = true
    }

    fun pressTrigger() {
        val ch = WHEEL[dySlotAt(wheel)]
        if (ch == ' ') return
        val prev = user.text
        if (prev.length >= DYMO_MAX) return
        text.value = prev + ch
        press(prev, ch.toString(), true)
    }

    fun down() {
        touching = true
        running.value = true
    }

    fun turn(x: Float, y: Float, dx: Float, dy: Float) {
        val rx = x - g.wheelX
        val ry = y - g.wheelY
        val r2 = max(200f, rx * rx + ry * ry)
        // Tangential finger motion turns the wheel about its centre.
        jumpWheel(wheel + (rx * dy - ry * dx) / r2 * (180 / PI.toFloat()))
        val slot = dySlotAt(wheel)
        if (slot != lastSlot) {
            lastSlot = slot
            haptic(HAPTIC_TICK)
        }
        lastTouch = now
    }

    fun release(x: Float, y: Float, vx: Float, vy: Float) {
        val rx = x - g.wheelX
        val ry = y - g.wheelY
        val r2 = max(200f, rx * rx + ry * ry)
        val v = (rx * vy - ry * vx) / r2 * (180 / PI.toFloat())
        sTarget = dyAngleFor(dySlotAt(wheel + v * 0.12f), wheel)
        sVel = v
        springing = true
        spins.clear(); tweening = false
    }

    /** A tap on the handle and trigger (right of the wheel, below the tape) presses the letter under the head. */
    fun tap(x: Float, y: Float) {
        if (x > g.wheelX + g.wheelR * 0.4f && y > g.wheelY - g.wheelR * 0.5f && y < g.wheelY + g.wheelR * 1.1f) {
            takeOver()
            pressTrigger()
        }
    }

    fun up() {
        touching = false
        lastTouch = now
        running.value = true
    }

    fun step(dt: Float): Boolean {
        val t = now + dt
        now = t
        if (mode == AUTO) {
            loopT = (loopT + dt) % LOOP
            val n = dyLoopText(loopT)
            if (n != scriptN) { scriptN = n; text.value = WORD.substring(0, n) }
        }
        val busy = user.cutAt > 0 && t < user.cutAt + 3.6f
        val lastE = if (user.e.isNotEmpty()) user.e[user.e.size - 1] else -100f
        if (mode == USER && auto && !focused && !touching && !busy && t - max(lastTouch, lastE) > 2) {
            mode = RETURN
            modeAt = t
            spins.clear(); springing = false
            tween(dyAngleFor(0, wheel), t, 0.7f)
            text.value = ""
            tape.intValue = 0
        }
        if (mode == RETURN && t - modeAt >= RETURN_FOR) {
            mode = AUTO
            loopT = 0f
            scriptN = -1
            user.reset()
            jumpWheel(0f)
        }
        // The wheel.
        val head = spins.firstOrNull()
        if (head != null && t >= head[0]) {
            spins.removeFirst()
            tFrom = wheel; tTo = head[1]; tStart = head[0]; tDur = head[2]; tweening = true; springing = false
        }
        if (tweening) {
            val u = dySeg(t, tStart, tStart + tDur)
            wheel = tFrom + (tTo - tFrom) * EaseSpin.transform(u)
            if (u >= 1) tweening = false
        }
        if (springing) {
            // 0.4 s, damping 0.8, carrying the finger's velocity.
            val w0 = 2 * PI.toFloat() / 0.4f
            var left = dt
            while (left > 0) {
                val h = min(left, 1 / 240f)
                sVel += (-w0 * w0 * (wheel - sTarget) - 2 * 0.8f * w0 * sVel) * h
                wheel += sVel * h
                left -= h
            }
            if (abs(wheel - sTarget) < 0.01f && abs(sVel) < 0.1f) { wheel = sTarget; springing = false }
        }
        // Haptics that were scheduled for now.
        var i = 0
        while (i < pending.size) {
            if (t >= pending[i][0]) { haptic(pending[i][1].toInt()); pending.removeAt(i) } else i++
        }
        // The chip ring springs to the picked tape (0.4 s, damping 0.75).
        val ringTarget = if (mode == AUTO || mode == STILL) 0f else user.colour.toFloat()
        var ringMoving = false
        if (reduced) ringX = ringTarget
        else if (ringX != ringTarget || ringVel != 0f) {
            val w0 = 2 * PI.toFloat() / 0.4f
            var left = dt
            while (left > 0) {
                val h = min(left, 1 / 240f)
                ringVel += (-w0 * w0 * (ringX - ringTarget) - 2 * 0.75f * w0 * ringVel) * h
                ringX += ringVel * h
                left -= h
            }
            if (abs(ringX - ringTarget) < 0.0005f && abs(ringVel) < 0.005f) { ringX = ringTarget; ringVel = 0f }
            ringMoving = true
        }
        ring.floatValue = ringX
        compute()
        frame.intValue += 1
        val anim = tweening || springing || spins.isNotEmpty() || pending.isNotEmpty() || ringMoving
        return when (mode) {
            AUTO, RETURN -> true
            USER -> auto || focused || touching || anim || busy || t < lastE + 0.5f || t < user.colourAt + 0.3f
            else -> false
        }
    }

    fun compute() {
        overAlpha = 0f
        if (mode == AUTO || mode == STILL) dyLoopPose(if (mode == AUTO) loopT else 0f, pose)
        else {
            dyUserPose(user, now, wheel, reduced, pose)
            if (mode == RETURN) {
                overAlpha = dyEaseInOut(dySeg(now, modeAt, modeAt + RETURN_FOR))
                dyLoopPose(0f, overPose)
            }
        }
    }
}

/**
 * A pantry organiser's label field as a handheld embossing label maker: typing turns its wheel and
 * presses each letter into glossy tape; Cut sends the strip flying onto the jar.
 *
 * @param autoPlay types "OATS", cuts the label onto the jar and swaps the jar, every 9 s.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DymoLabelInput(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val reduced = rememberReducedMotion()
    val auto = autoPlay && !reduced
    val density = LocalDensity.current
    val view = LocalView.current
    val top = maxOf(
        WindowInsets.statusBarsIgnoringVisibility.getTop(density),
        WindowInsets.displayCutout.getTop(density),
    ) / density.density
    val bottom = WindowInsets.navigationBarsIgnoringVisibility.getBottom(density) / density.density
    val ime = WindowInsets.ime
    val focus = remember { FocusRequester() }

    BoxWithConstraints(modifier.fillMaxSize().background(Backdrop)) {
        val w = maxWidth.value
        val h = maxHeight.value
        var geo by remember { mutableStateOf<DymoGeo?>(null) }
        val coords = remember { arrayOfNulls<LayoutCoordinates>(3) }
        val fieldBottom = remember { mutableFloatStateOf(0f) }
        val measure = {
            val root = coords[0]
            val hero = coords[1]
            if (root != null && hero != null && root.isAttached && hero.isAttached) {
                val r = root.localBoundingBoxOf(hero, clipBounds = false)
                val d = density.density
                val next = DymoGeo(w, h, r.left / d, r.top / d, r.width / d, r.height / d)
                if (!next.same(geo)) geo = next
                val field = coords[2]
                if (field != null && field.isAttached) fieldBottom.floatValue = root.localBoundingBoxOf(field, clipBounds = false).bottom / d
            }
        }
        val art by produceState<DymoArt?>(null, geo) {
            val g = geo ?: return@produceState
            // Wait for the layout to settle before baking; the last art stays on screen meanwhile.
            delay(120)
            val scale = min(2f, density.density)
            value = withContext(Dispatchers.Default) {
                DymoArt(g, dyBakeWall(g, scale), dyBakeJar(g, scale), dyBakeBody(g, scale), g.wheelR * 3.6f * 2, dyBakeWheel(g, scale))
            }
        }
        val a = art
        val haptic = remember(view) { { kind: Int -> dymoHaptic(view, kind) } }
        val state = remember(a, auto) { a?.let { DyState(it, auto, reduced, haptic) } }
        val fade = remember(a) { mutableFloatStateOf(0f) }

        if (state != null) {
            val running by state.running
            LaunchedEffect(state, running) {
                if (!running) return@LaunchedEffect
                var last = -1L
                while (true) {
                    val keep = withFrameNanos { t ->
                        val dt = if (last < 0) 0f else min((t - last) / 1e9f, 0.05f)
                        last = t
                        if (fade.floatValue < 1f) fade.floatValue = min(1f, fade.floatValue + dt / 0.3f)
                        state.step(dt) || fade.floatValue < 1f
                    }
                    if (!keep) { state.running.value = false; break }
                }
            }
        }

        val painter = remember(a) { a?.let { DymoPainter(it) } }
        val placeholder = remember { DyPlaceholder() }
        // The keyboard pushes the whole screen up just enough to keep the field visible.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val room = (h - fieldBottom.floatValue - 12) * this.density
                    translationY = -max(0f, ime.getBottom(this).toFloat() - room)
                },
        ) {
            Canvas(Modifier.fillMaxSize().onGloballyPositioned { coords[0] = it; measure() }) {
                state?.frame?.intValue
                val d = density.density
                drawIntoCanvas { canvas ->
                    val c = canvas.nativeCanvas
                    c.save()
                    c.scale(d, d)
                    val g = geo
                    val f = fade.floatValue
                    if (g != null && (state == null || f < 1f)) placeholder.draw(c, g)
                    if (state != null && painter != null) {
                        if (f < 1f) c.saveLayerAlpha(0f, 0f, w, h, (f * 255).toInt())
                        painter.draw(c, state.pose)
                        if (state.overAlpha > 0f) {
                            c.saveLayerAlpha(0f, 0f, w, h, (state.overAlpha * 255).toInt())
                            painter.draw(c, state.overPose)
                            c.restore()
                        }
                        if (f < 1f) c.restore()
                    }
                    c.restore()
                }
            }

            Column(
                Modifier
                    .fillMaxSize()
                    .padding(start = 20.dp, end = 20.dp, top = (top + 6).dp, bottom = (bottom + 12).dp),
            ) {
                Row(Modifier.fillMaxWidth().height(44.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .cssBoxShadow(Color(0x143C2814), 6.dp, 16.dp, 20.dp)
                            .cssBoxShadow(Color(0x293C2814), 1.dp, 2.dp, 20.dp)
                            .background(Color(0xD9FFFCF7), CircleShape)
                            .semantics { contentDescription = "Back" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = null, tint = Ink, modifier = Modifier.size(26.dp))
                    }
                    BasicText("Shelfwise", style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink))
                    BasicText("Pantry · Shelf 2", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MutedInk))
                }
                Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    BasicText("Label the jar", style = TextStyle(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold, color = Ink, letterSpacing = (-0.4).sp))
                    BasicText("Pressed into 9 mm glossy tape", style = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, color = MutedInk))
                }

                Box(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 280.dp)
                        .fillMaxWidth()
                        .onGloballyPositioned { coords[1] = it; measure() }
                        .semantics {
                            contentDescription = "Label maker. Tape reads ${state?.text?.value?.ifEmpty { "nothing yet" } ?: "nothing yet"}. Turn the wheel and tap the handle to press a letter."
                        }
                        .pointerInput(state) {
                            val s = state ?: return@pointerInput
                            val d = density.density
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val gx = s.g.heroX
                                val gy = s.g.heroY
                                s.down()
                                val tracker = VelocityTracker()
                                tracker.addPosition(down.uptimeMillis, down.position)
                                var panning = false
                                var far = false
                                var last = down.position
                                var lastPos = down.position
                                try {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val ch = event.changes.firstOrNull { it.id == down.id } ?: break
                                        tracker.addPosition(ch.uptimeMillis, ch.position)
                                        lastPos = ch.position
                                        if (!ch.pressed) break
                                        val dist = (ch.position - down.position).getDistance()
                                        if (dist > 8 * d) far = true
                                        if (!panning && dist > 3 * d) {
                                            panning = true
                                            s.takeOver()
                                            last = ch.position
                                        }
                                        if (panning) {
                                            val delta = ch.position - last
                                            last = ch.position
                                            s.turn(gx + ch.position.x / d, gy + ch.position.y / d, delta.x / d, delta.y / d)
                                            ch.consume()
                                        }
                                    }
                                    if (panning) {
                                        val v = tracker.calculateVelocity()
                                        s.release(gx + lastPos.x / d, gy + lastPos.y / d, v.x / d, v.y / d)
                                    } else if (!far) s.tap(gx + lastPos.x / d, gy + lastPos.y / d)
                                } finally {
                                    s.up()
                                }
                            }
                        },
                )

                Row(
                    Modifier.fillMaxWidth().onGloballyPositioned { coords[2] = it; measure() },
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val text = state?.text?.value ?: ""
                    val tapeIndex = state?.tape?.intValue ?: 0
                    Box(
                        Modifier
                            .weight(1f)
                            .height(64.dp)
                            .cssBoxShadow(Color(0x123C2814), 10.dp, 24.dp, 16.dp)
                            .cssBoxShadow(Color(0x243C2814), 1.dp, 2.dp, 16.dp)
                            .background(Card, RoundedCornerShape(16.dp))
                            .drawBehind {
                                drawLine(Color(0xE6FFFFFF), Offset(16.dp.toPx(), 0.5f.dp.toPx()), Offset(size.width - 16.dp.toPx(), 0.5f.dp.toPx()), 1.dp.toPx())
                            }
                            .padding(start = 16.dp, end = 16.dp, top = 10.dp),
                    ) {
                        Column {
                            BasicText("Label text", style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MutedInk))
                            val style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink, letterSpacing = 2.sp)
                            BasicTextField(
                                value = text,
                                onValueChange = { state?.onType(it) },
                                singleLine = true,
                                textStyle = style,
                                cursorBrush = SolidColor(Color(0xFF000000 or Tapes[tapeIndex].hex)),
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Characters,
                                    autoCorrectEnabled = false,
                                    imeAction = ImeAction.Done,
                                ),
                                // Done cuts the label and keeps the keyboard up, like a submit.
                                keyboardActions = KeyboardActions(onDone = { state?.cut() }),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                                    .focusRequester(focus)
                                    .onFocusChanged { fs ->
                                        val st = state ?: return@onFocusChanged
                                        st.focused = fs.isFocused
                                        if (fs.isFocused && st.mode != USER) st.takeOver()
                                        st.lastTouch = st.now
                                        st.running.value = true
                                    }
                                    .semantics { contentDescription = "Label text" },
                                decorationBox = { inner ->
                                    Box {
                                        if (text.isEmpty()) BasicText("TYPE A LABEL", style = style.copy(color = Color(0xFFBDAF9E)))
                                        inner()
                                    }
                                },
                            )
                        }
                    }
                    DyCut { state?.cut() }
                }

                Row(
                    Modifier.padding(top = 16.dp).fillMaxWidth().height(48.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicText("TAPE", style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MutedInk, letterSpacing = 0.4.sp))
                    Box {
                        Row(horizontalArrangement = Arrangement.spacedBy(CHIP_GAP.dp)) {
                            Tapes.forEachIndexed { i, tp ->
                                DyChip(tp, selected = (state?.tape?.intValue ?: 0) == i) { state?.pickTape(i) }
                            }
                        }
                        Box(
                            Modifier
                                .offset((-5).dp, (-5).dp)
                                .graphicsLayer { translationX = (state?.ring?.floatValue ?: 0f) * (CHIP + CHIP_GAP) * this.density }
                                .size((CHIP + 10).dp)
                                .border(2.dp, Ink, RoundedCornerShape(14.dp)),
                        )
                    }
                }

                DyCta()
            }
        }
    }
}

private fun dymoHaptic(view: View, kind: Int) {
    view.performHapticFeedback(
        when (kind) {
            HAPTIC_TICK -> HapticFeedbackConstants.CLOCK_TICK
            HAPTIC_MEDIUM -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS
            else -> HapticFeedbackConstants.VIRTUAL_KEY
        },
    )
}

@Composable
private fun DyCut(onPress: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Box(
        Modifier
            .size(64.dp)
            .graphicsLayer { val k = if (pressed) 0.95f else 1f; scaleX = k; scaleY = k }
            .cssBoxShadow(Color(0x29141E1C), 10.dp, 20.dp, 16.dp)
            .cssBoxShadow(Color(0x4D141E1C), 1.dp, 2.dp, 16.dp)
            .background(Color(0xFF3B5752), RoundedCornerShape(16.dp))
            .drawBehind {
                drawLine(Color(0x2EFFFFFF), Offset(16.dp.toPx(), 0.5f.dp.toPx()), Offset(size.width - 16.dp.toPx(), 0.5f.dp.toPx()), 1.dp.toPx())
            }
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onPress)
            .semantics { contentDescription = "Cut the label" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Outlined.ContentCut, contentDescription = null, tint = Color(0xFFF7F1E8), modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun DyChip(tp: DymoTape, selected: Boolean, onPress: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Box(
        Modifier
            .size(CHIP.dp)
            .graphicsLayer { val k = if (pressed) 0.94f else 1f; scaleX = k; scaleY = k }
            .cssBoxShadow(Color(0x241E140A), 4.dp, 10.dp, 10.dp)
            .cssBoxShadow(Color(0x4D1E140A), 1.dp, 2.dp, 10.dp)
            .background(Color(0xFF000000 or tp.hex), RoundedCornerShape(10.dp))
            .drawBehind {
                // inset 0 -3 5 rgba(0,0,0,.22), inset 0 2 3 rgba(255,255,255,.3), and the shine bar.
                drawRoundRect(
                    Brush.verticalGradient(0f to Color(0x4DFFFFFF), 0.16f to Color(0x00FFFFFF), 0.75f to Color(0x00000000), 1f to Color(0x38000000)),
                    cornerRadius = CornerRadius(10.dp.toPx()),
                )
                drawRoundRect(
                    Color(0x52FFFFFF),
                    topLeft = Offset(5.dp.toPx(), 5.dp.toPx()),
                    size = androidx.compose.ui.geometry.Size(size.width - 10.dp.toPx(), 4.dp.toPx()),
                    cornerRadius = CornerRadius(2.dp.toPx()),
                )
            }
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onPress)
            .semantics {
                contentDescription = "${tp.name} tape"
                this.selected = selected
            },
    )
}

@Composable
private fun DyCta() {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Box(
        Modifier
            .padding(top = 16.dp)
            .fillMaxWidth()
            .height(56.dp)
            .graphicsLayer { val k = if (pressed) 0.97f else 1f; scaleX = k; scaleY = k }
            .cssBoxShadow(Color(0x2E2B2722), 12.dp, 24.dp, 28.dp)
            .cssBoxShadow(Color(0x402B2722), 2.dp, 4.dp, 28.dp)
            .background(Ink, RoundedCornerShape(28.dp))
            .drawBehind {
                val r = size.height / 2
                drawLine(Color(0x1FFFFFFF), Offset(r, 0.5f.dp.toPx()), Offset(size.width - r, 0.5f.dp.toPx()), 1.dp.toPx())
            }
            .clickable(interactionSource = source, indication = null, role = Role.Button) {},
        contentAlignment = Alignment.Center,
    ) {
        BasicText("Save to pantry", style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFF7F1E8)))
    }
}

/** What shows for the moment before the art is baked: shelf, jar, machine and tape as flat shapes. */
private class DyPlaceholder {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()

    fun draw(c: android.graphics.Canvas, g: DymoGeo) {
        p.color = 0xFFEADFD0.toInt()
        c.drawRect(0f, 0f, g.w, g.h, p)
        p.color = 0xFFD9CCBA.toInt()
        c.drawRect(0f, g.shelfY + 20, g.w, g.h, p)
        p.color = 0xFFB88653.toInt()
        c.drawRect(0f, g.shelfY - 8, g.w, g.shelfY + 20, p)
        p.color = 0xFFCDAE7A.toInt()
        r.set(g.cx - g.jarW / 2, g.shelfY - g.jarH + 22, g.cx + g.jarW / 2, g.shelfY)
        c.drawRoundRect(r, 18f, 18f, p)
        p.color = 0xFF3B5752.toInt()
        c.drawCircle(g.wheelX, g.wheelY, g.wheelR + 18, p)
        r.set(g.wheelX, g.wheelY - g.wheelR * 0.35f, g.wheelX + g.wheelR * 2.9f, g.wheelY + g.wheelR * 0.7f)
        c.drawRoundRect(r, g.wheelR * 0.5f, g.wheelR * 0.5f, p)
        p.color = 0xFF2A2D33.toInt()
        c.drawCircle(g.wheelX, g.wheelY, g.wheelR, p)
        p.color = 0xFFC3313B.toInt()
        r.set(g.headX - 7.5f - 18, g.tapeY - 12, g.headX + 70, g.tapeY + 12)
        c.drawRoundRect(r, 2.5f, 2.5f, p)
    }
}
