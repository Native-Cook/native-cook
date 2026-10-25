package com.ngocdevv.nativecook.blocks.forms.magneticlettersname

import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.cssBoxShadow
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 Magnetic Letters Name — a family app's "Who's joining?" sign-up whose name field is the fridge
 door. A loose heap of glossy plastic magnet letters lies on the brushed steel; typing (or tapping
 a magnet) flies a letter up to the name line, where the door's pull snaps it on with a little
 bounce; backspace pops the last one off and back onto the heap. Magnets can be dragged anywhere,
 onto the line (in between letters) or off it.

 autoPlay (LOOP = 9 s): rest 0–1.3 · "NOAH" typed 1.3–3.4 (a flight of 0.38 s every 0.42 s) ·
 "Continue" lights 3.6 · hold 3.6–5.2 · backspace ×4 5.2–7.0 (0.45 s apart) · rest 7.0–9.0.
 A touch takes over at once from where the loop is; 2 s after the last one every magnet flies home
 (≤ 0.9 s) and the loop starts again from 0. Reduced motion: no loop; letters jump to their place.

 Drawing: the door is one baked bitmap; the magnets are sprites from two baked atlases (glyph-shaped
 shadows, then faces), placed with a canvas transform each frame something moves.
*/

private const val MODE_REST = 0
private const val MODE_USER = 1
private const val MODE_RETURN = 2
/** Seconds after the last touch before the loop takes the door back, and how long that takes. */
private const val IDLE = 2f
private const val RETURN = 0.9f

private val DoorMid = Color(0xFFE8E9E5)
private val Ink = Color(0xFF22262B)
private val InkSoft = Color(0x8F22262B)
private val InkFaint = Color(0x5722262B)
private val Blue = Color(0xFF2350A8)
private val ButtonOff = Color(0xFFC9CCCA)
private val LabelOff = Color(0xFF8A8F8D)
private val LabelOn = Color(0xFFF4F6FB)

private class MagState(val art: MagArt, val reduced: Boolean) {
    val sim = MagSim(art, reduced)
    var mode = MODE_REST
    var clock = 0f
    var now = 0f
    var lastTouch = 0f
    var returnEnd = 0f
    /** Bumped when the sprites move, read only by the canvas. */
    val frame = mutableIntStateOf(0)
    val ready = mutableFloatStateOf(0f)
    val count = mutableIntStateOf(0)
    val text = mutableStateOf("")
    /** Whether the frame loop runs; it stops when nothing moves and no clock has to tick. */
    val running = mutableStateOf(true)
    /** Draw order of this frame. */
    val order = IntArray(sim.n)
    private val key = FloatArray(sim.n)
    var snapped = false

    fun syncText() {
        text.value = sim.nameText()
    }

    /** Takes the door from the loop: every magnet keeps its pose and its flight's progress. */
    fun touched() {
        running.value = true
        lastTouch = now
        if (mode == MODE_USER) return
        if (mode == MODE_REST) {
            sim.takeOver(clock, now)
            sim.ready = ready.floatValue
            syncText()
        }
        mode = MODE_USER
    }

    /** The keyboard's text, applied as backspaces and typed letters; the line is the truth. */
    fun applyText(next: String) {
        touched()
        val cur = sim.nameText()
        var p = 0
        while (p < cur.length && p < next.length && cur[p] == next[p]) p++
        while (sim.name.size > p) sim.removeAt(sim.name.size - 1, true, now)
        for (j in p until next.length) sim.typeLetter(next[j], now)
        syncText()
    }

    fun backspace() {
        touched()
        if (sim.name.isEmpty()) return
        sim.removeAt(sim.name.size - 1, true, now)
        syncText()
    }

    /** A tap: a magnet goes on or off the line; the line itself asks for the keyboard (true), elsewhere dismisses it (false); null otherwise. */
    fun tap(x: Float, y: Float): Boolean? {
        touched()
        val l = art.layout
        val i = sim.hit(x, y)
        if (i >= 0) {
            val k = sim.name.indexOf(i)
            if (k >= 0) sim.removeAt(k, true, now) else sim.typeMagnet(i, now)
            syncText()
            return null
        }
        return y > l.lineY - l.capH - 44 && y < l.lineY + 24
    }

    fun grab(x: Float, y: Float) {
        touched()
        val i = sim.hit(x, y)
        if (i < 0) return
        val wasOnLine = sim.name.contains(i)
        sim.grab(i, x, y, now)
        if (wasOnLine) syncText()
    }

    fun drag(x: Float, y: Float, vx: Float) {
        if (sim.held < 0) return
        running.value = true
        lastTouch = now
        sim.heldX = x; sim.heldY = y; sim.vx = vx
    }

    fun drop() {
        if (sim.held < 0) return
        running.value = true
        lastTouch = now
        if (sim.drop(now)) syncText()
    }

    /** 2 s after the last touch: the line empties and every magnet flies home, staggered. */
    private fun beginReturn() {
        sim.sendHome(now)
        syncText()
        returnEnd = now + RETURN
        mode = MODE_RETURN
    }

    /** One frame; returns whether the loop must keep running. */
    fun step(dt: Float, auto: Boolean): Boolean {
        val s = sim
        val before = now
        val at = before + dt
        now = at
        var moving = false
        val rdy: Float
        var cnt = 0
        snapped = false

        if (mode == MODE_REST) {
            val t = if (auto) (clock + dt) % LOOP else 0f
            clock = t
            for (i in 0 until s.n) s.autoPose(i, t, s.poses[i])
            moving = auto
            rdy = magAutoReady(t)
            cnt = magAutoCount(t)
        } else {
            if (mode == MODE_USER && auto && s.held < 0 && at - lastTouch > IDLE) beginReturn()
            if (mode == MODE_RETURN && at >= returnEnd) {
                mode = MODE_REST
                clock = 0f
            }
            for (i in 0 until s.n) {
                if (s.simPose(i, at, s.poses[i])) moving = true
                if (s.kind[i] == MAG_KIND_SNAP) {
                    val end = s.start[i] + s.dur[i]
                    if (before < end && at >= end) snapped = true
                }
            }
            if (mode != MODE_USER) snapped = false
            val target = if (mode == MODE_USER && s.name.size >= 2) 1f else 0f
            s.ready += (target - s.ready) * min(1f, dt * 14)
            if (kotlin.math.abs(target - s.ready) > 0.002f) moving = true
            rdy = s.ready
            for (i in s.name) {
                if (s.kind[i] != MAG_KIND_SNAP || at >= s.start[i] + s.dur[i]) cnt++
            }
        }
        if (ready.floatValue != rdy) ready.floatValue = rdy
        if (count.intValue != cnt) count.intValue = cnt

        // The clock has to keep ticking while the loop plays (or waits to take the door back) and while a magnet is held.
        val clockNeeded = auto || s.held >= 0
        if (!moving && s.idleDrawn) return clockNeeded
        s.idleDrawn = !moving

        // Draw order: flat magnets by their place in the heap, lifted ones above them by height.
        val n = s.n
        val base = art.layout.order
        for (r in 0 until n) {
            val i = base[r]
            val p = s.poses[i]
            order[r] = i
            key[r] = (if (p.lift > 0.01f) 10 + p.lift else 0f) + r / n.toFloat()
        }
        for (a in 1 until n) {
            val vi = order[a]
            val vk = key[a]
            var b = a - 1
            while (b >= 0 && key[b] > vk) { order[b + 1] = order[b]; key[b + 1] = key[b]; b-- }
            order[b + 1] = vi; key[b + 1] = vk
        }
        frame.intValue += 1
        return true
    }
}

/**
 * A family app's sign-up whose name field is a fridge door: magnet letters fly up from a heap and
 * snap onto the name line; backspace pops them back. Tap, drag or type.
 *
 * @param autoPlay spells "NOAH" on the door, lights "Continue", backspaces it away, and starts over.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MagneticLettersName(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val reduced = rememberReducedMotion()
    val auto = autoPlay && !reduced
    val density = LocalDensity.current
    val view = LocalView.current
    val top = maxOf(
        WindowInsets.statusBarsIgnoringVisibility.getTop(density),
        WindowInsets.displayCutout.getTop(density),
    ) / density.density
    val bottom = WindowInsets.navigationBarsIgnoringVisibility.getBottom(density) / density.density

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(DoorMid),
    ) {
        val w = maxWidth.value
        val h = maxHeight.value
        val art by produceState<MagArt?>(null, w, h, top, bottom) {
            value = withContext(Dispatchers.Default) { magBakeArt(w, h, top, bottom, density.density) }
        }
        val a = art ?: return@BoxWithConstraints
        val state = remember(a, reduced) { MagState(a, reduced) }
        val l = a.layout
        val focus = remember { FocusRequester() }
        val focusManager = LocalFocusManager.current
        val keyboard = LocalSoftwareKeyboardController.current

        val running by state.running
        LaunchedEffect(state, auto, running) {
            if (!running) return@LaunchedEffect
            var last = -1L
            while (true) {
                val keep = withFrameNanos { t ->
                    val dt = if (last < 0) 1f / 60 else min((t - last) / 1e9f, 0.05f)
                    last = t
                    val keep = state.step(dt, auto)
                    if (state.snapped) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    keep
                }
                if (!keep) {
                    state.running.value = false
                    break
                }
            }
        }

        val faceCell = l.cell * a.px
        val shadowCell = l.shadowCell * MAG_SHADOW_PX
        val bitmapPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG) }
        val src = remember { Rect() }
        val dst = remember { RectF() }
        Canvas(
            Modifier
                .fillMaxSize()
                .semantics { contentDescription = "Fridge door with magnet letters. Tap a magnet to add it to your name, drag magnets, or type." }
                .pointerInput(state) {
                    val d = density.density
                    val slop = 6 * d
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val start = down.position
                        val t0 = down.uptimeMillis
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        var dragging = false
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                tracker.addPosition(change.uptimeMillis, change.position)
                                val p = change.position
                                if (!dragging && (p - start).getDistance() > slop) {
                                    dragging = true
                                    state.grab(start.x / d, start.y / d)
                                    state.drag(p.x / d, p.y / d, tracker.calculateVelocity().x / d)
                                } else if (dragging && change.pressed) {
                                    state.drag(p.x / d, p.y / d, tracker.calculateVelocity().x / d)
                                }
                                if (dragging) change.consume()
                                if (!change.pressed) {
                                    if (!dragging && change.uptimeMillis - t0 <= 400) {
                                        when (state.tap(p.x / d, p.y / d)) {
                                            true -> { focus.requestFocus(); keyboard?.show() }
                                            false -> focusManager.clearFocus()
                                            null -> Unit
                                        }
                                    }
                                    break
                                }
                            }
                        } finally {
                            if (dragging) state.drop()
                        }
                    }
                },
        ) {
            state.frame.intValue
            val d = density.density
            val s = state.sim
            drawIntoCanvas { canvas ->
                val c = canvas.nativeCanvas
                c.save()
                c.scale(d, d)
                dst.set(0f, 0f, l.w, l.h)
                c.drawBitmap(a.door, null, dst, bitmapPaint)
                // Shadows first, all of them, then the faces, so no shadow falls on a magnet below it.
                for (r in 0 until s.n) {
                    val i = state.order[r]
                    val p = s.poses[i]
                    val lv = min(MAG_SHADOW_LEVELS - 1, (p.lift * (MAG_SHADOW_LEVELS - 1)).roundToInt()).coerceAtLeast(0)
                    src.set(
                        (lv * shadowCell).roundToInt(), (i * shadowCell).roundToInt(),
                        ((lv + 1) * shadowCell).roundToInt(), ((i + 1) * shadowCell).roundToInt(),
                    )
                    c.save()
                    c.translate(p.x + 1.2f + 7 * p.lift, p.y + 2 + 13 * p.lift)
                    c.rotate(p.rot * 180f / PI.toFloat())
                    val k = p.scale * (1 + 0.05f * p.lift) / MAG_SHADOW_PX
                    c.scale(k, k)
                    dst.set(-src.width() / 2f, -src.height() / 2f, src.width() / 2f, src.height() / 2f)
                    c.drawBitmap(a.shadows, src, dst, bitmapPaint)
                    c.restore()
                }
                for (r in 0 until s.n) {
                    val i = state.order[r]
                    val p = s.poses[i]
                    val col = i % MAG_FACE_COLS
                    val row = i / MAG_FACE_COLS
                    src.set(
                        (col * faceCell).roundToInt(), (row * faceCell).roundToInt(),
                        ((col + 1) * faceCell).roundToInt(), ((row + 1) * faceCell).roundToInt(),
                    )
                    c.save()
                    c.translate(p.x, p.y)
                    c.rotate(p.rot * 180f / PI.toFloat())
                    val k = p.scale / a.px
                    c.scale(k, k)
                    dst.set(-src.width() / 2f, -src.height() / 2f, src.width() / 2f, src.height() / 2f)
                    c.drawBitmap(a.faces, src, dst, bitmapPaint)
                    c.restore()
                }
                c.restore()
            }
        }

        // Header.
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 28.dp, end = 56.dp, top = (top + 18).dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            BasicText("FRIDGE · MARLOW HOUSEHOLD", style = TextStyle(color = InkFaint, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp))
            BasicText("Who's joining?", style = TextStyle(color = Ink, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.6).sp))
            BasicText(
                "Spell your name on the door. Tap a magnet, drag one, or just type.",
                style = TextStyle(color = InkSoft, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
            )
        }

        // "YOUR NAME" and the counter over the line.
        Row(
            Modifier
                .offset(x = l.lineX0.dp, y = (l.lineY - l.capH - 40).dp)
                .width((l.lineX1 - l.lineX0).dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            BasicText("YOUR NAME", style = TextStyle(color = InkFaint, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp))
            MagCounter(state)
        }

        MagBackspace(Modifier.offset(x = (l.lineX1 + 10).dp, y = (l.letterY - 20).dp)) { state.backspace() }

        // The hidden field the keyboard types into; the line mirrors its text.
        MagHiddenField(state, focus)

        MagContinue(
            state,
            Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 24.dp, end = 24.dp, bottom = (bottom + 20).dp),
        ) {
            if (state.count.intValue >= 2) {
                view.performHapticFeedback(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY,
                )
            }
        }
    }
}

@Composable
private fun MagCounter(state: MagState) {
    BasicText(
        "${state.count.intValue} / ${state.art.maxFit}",
        style = TextStyle(color = InkFaint, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
    )
}

@Composable
private fun MagHiddenField(state: MagState, focus: FocusRequester) {
    BasicTextField(
        value = state.text.value,
        onValueChange = { next ->
            val clean = next.uppercase().filter { it in 'A'..'Z' }.take(state.art.maxFit)
            state.text.value = clean
            state.applyText(clean)
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Characters,
            autoCorrectEnabled = false,
            imeAction = ImeAction.Done,
        ),
        modifier = Modifier
            .offset(x = (-100).dp)
            .size(2.dp)
            .alpha(0f)
            .focusRequester(focus)
            .semantics { contentDescription = "Your name" },
    )
}

@Composable
private fun MagBackspace(modifier: Modifier, onPress: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Box(
        modifier
            .size(40.dp)
            .graphicsLayer {
                val k = if (pressed) 0.94f else 1f
                scaleX = k; scaleY = k
            }
            .cssBoxShadow(Color(0x1F282C30), 6.dp, 14.dp, 20.dp)
            .cssBoxShadow(Color(0x38282C30), 1.dp, 2.dp, 20.dp)
            .background(Color(0xFFEEEFEC), CircleShape)
            .drawBehind {
                // The inset top highlight.
                drawArc(
                    Color(0xE6FFFFFF), 200f, 140f, false,
                    topLeft = Offset(1.dp.toPx(), 1.dp.toPx()),
                    size = Size(size.width - 2.dp.toPx(), size.height - 2.dp.toPx()),
                    style = Stroke(1.dp.toPx()),
                )
            }
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onPress)
            .semantics { contentDescription = "Delete last letter" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.AutoMirrored.Outlined.Backspace, contentDescription = null, tint = InkSoft, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun MagContinue(state: MagState, modifier: Modifier, onPress: () -> Unit) {
    val shadowPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }
    Box(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .graphicsLayer {
                val k = 1 + 0.03f * sin(PI.toFloat() * min(1f, state.ready.floatValue))
                scaleX = k; scaleY = k
            }
            .drawBehind {
                val r = state.ready.floatValue
                val bg = lerp(ButtonOff, Blue, r)
                // box-shadow 0 (2 + 8r) (6 + 18r) rgba(24,52,110,0.08 + 0.3r); a CSS blur is twice the sigma.
                shadowPaint.color = bg.toArgb()
                val radius = sigmaToRadius((6 + 18 * r) / 2 * density)
                shadowPaint.setShadowLayer(radius, 0f, (2 + 8 * r) * density, Color(24 / 255f, 52 / 255f, 110 / 255f, 0.08f + 0.3f * r).toArgb())
                drawIntoCanvas {
                    it.nativeCanvas.drawRoundRect(0f, 0f, size.width, size.height, size.height / 2, size.height / 2, shadowPaint)
                }
            }
            .clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onPress),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            "Continue",
            style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.2.sp, textAlign = TextAlign.Center),
            color = { lerp(LabelOff, LabelOn, state.ready.floatValue) },
        )
    }
}
