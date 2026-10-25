package com.ngocdevv.nativecook.blocks.pickers.crayonboxpicker

import android.graphics.Paint
import android.graphics.RectF
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.cssBoxShadow
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/*
 Crayon Box Picker — "Doodlebug · Pick a colour". An open box of twelve wax crayons stands above a
 sheet of drawing paper. Pull a crayon up and it slides out against the box's friction and wobbles
 free while its neighbours lean into the gap; let go and it swings down onto the paper, scribbles a
 swatch in wax (the old swatch fades under it), becomes the current colour (its name rolls in), and
 goes home into its slot.

 Touch: drag a crayon upward (0.85 × the finger, rubber-banding past fully out); let go past 75 %
 to pick it, or earlier and it springs back (0.4 s, damping 0.7). Sideways speed tips it. A tick
 when it comes free, a light tap as it is picked, a tick when the name changes. With autoPlay, 2 s
 after a pick finishes the scene cross-fades to the loop's first frame (0.8 s). Reduced motion:
 nothing moves by itself; a pick changes the colour and swatch at once.

 One Canvas: desk, paper, box and the twelve crayons are baked once off the main thread; per frame
 the crayon bitmaps are placed, the swatches drawn and the box front laid over them.
*/

private const val AUTO = 0
private const val USER = 1
private const val RETURN = 2
private const val STILL = 3
private const val RETURN_FOR = 0.8f
private const val NAME_H = 28f
private const val NONE = -100f

private val Ink = Color(0xFF173A30)
private val MutedInk = Color(0xFF4E6E66)
private val Desk = Color(0xFFA9C9C2)

private class CrayonState(val art: CrayonArt, val auto: Boolean, val reduced: Boolean, val haptic: (Int) -> Unit) {
    val g = art.geo
    val path = art.path
    var mode = if (auto) AUTO else STILL
    var now = 0f
    var loopT = 0f
    var modeAt = 0f
    var lastTouch = 0f
    var touching = false
    var active = -1
    var pull = 0f
    private var pullVel = 0f
    /** 0 still, 1 easing to 1 over 0.12 s (from pullFrom at pullAt), 2 springing back to 0. */
    private var pullAnim = 0
    private var pullFrom = 0f
    private var pullAt = 0f
    var wobble = 0f
    var releaseAt = NONE
    var freed = false
    /** The chosen colour (from, to, since) and the swatch (old, new, since). */
    val colourState = floatArrayOf(REST_COLOUR.toFloat(), REST_COLOUR.toFloat(), NONE)
    val swatchState = floatArrayOf(REST_COLOUR.toFloat(), REST_COLOUR.toFloat(), NONE)
    private var tickAt = NONE

    // What the canvas draws.
    val poses = FloatArray(72)
    val swatch = FloatArray(4)
    val overPoses = FloatArray(72)
    val overSwatch = FloatArray(4)
    var overAlpha = 0f
    val frame = mutableIntStateOf(0)
    val colFrom = mutableIntStateOf(REST_COLOUR)
    val colTo = mutableIntStateOf(REST_COLOUR)
    val colRoll = mutableFloatStateOf(1f)
    val running = mutableStateOf(true)

    private val tmp = FloatArray(4)
    private val ph = FloatArray(4)
    private val slotTmp = FloatArray(4)
    private val pen0 = floatArrayOf(path.pts[0], path.pts[1])
    private val penEnd = floatArrayOf(path.pts[path.pts.size - 2], path.pts[path.pts.size - 1])
    private val pen = FloatArray(2)
    private val col = FloatArray(3)

    init { compute() }

    private fun loopFrame(t: Float, outPoses: FloatArray, outSwatch: FloatArray) {
        crLoopPick(t, ph, tmp)
        val phase = ph[1].toInt()
        if (phase == 3) path.point(path.total * ph[2], pen) else { pen[0] = pen0[0]; pen[1] = pen0[1] }
        crPoses(g, ph[0].toInt(), phase, ph[2], ph[3], pen0, pen, penEnd, outPoses, slotTmp)
        crLoopSwatch(t, outSwatch)
    }

    /** Which crayon a point is on, front row first; -1 for none. */
    fun crayonAt(x: Float, y: Float): Int {
        for (row in 1 downTo 0) for (k in 0 until 6) {
            val i = if (row == 0) k else 6 + k
            crSlot(g, i, slotTmp)
            val top = g.rimY - slotTmp[3] - 12
            if (abs(x - slotTmp[0]) < g.pitch * 0.5f && y > top && y < g.rimY + (if (row == 1) g.frontH * 0.6f else 0f)) return i
        }
        return -1
    }

    /** The loop hands over: the colour and swatch it shows become the user's. */
    private fun takeOver() {
        lastTouch = now
        if (mode == USER) return
        var shown = REST_COLOUR.toFloat()
        if (mode == AUTO) {
            crLoopColour(loopT, col)
            shown = if (col[2] >= 0.5f) col[1] else col[0]
            crLoopSwatch(loopT, tmp)
            val s = if (tmp[3] >= 0.5f) tmp[2] else tmp[0]
            swatchState[0] = s; swatchState[1] = s; swatchState[2] = NONE
        } else {
            swatchState[0] = REST_COLOUR.toFloat(); swatchState[1] = REST_COLOUR.toFloat(); swatchState[2] = NONE
        }
        colourState[0] = shown; colourState[1] = shown; colourState[2] = NONE
        active = -1
        releaseAt = NONE
        pull = 0f; pullAnim = 0; pullVel = 0f
        mode = USER
    }

    fun begin(x: Float, y: Float) {
        if (releaseAt > 0) return
        val i = crayonAt(x, y)
        if (i < 0) return
        running.value = true
        takeOver()
        touching = true
        active = i
        freed = false
    }

    fun change(translationY: Float, velocityX: Float) {
        if (!touching || active < 0) return
        crSlot(g, active, slotTmp)
        val out = g.cl - slotTmp[3] + 6
        // Friction inside the box, rubber-band once it is fully out.
        val raw = max(0f, -translationY * 0.85f)
        val over = max(0f, raw - out)
        val shown = if (raw <= out) raw else out + over / (1 + over / 60)
        pull = shown / out
        pullAnim = 0
        wobble = max(-1f, min(1f, wobble * 0.7f + velocityX * 0.0012f))
        if (!freed && raw >= out) {
            freed = true
            haptic(0)
        }
        lastTouch = now
    }

    fun end(activated: Boolean) {
        if (activated && active >= 0 && touching) {
            val t = now
            val shown = if (crSeg(t, colourState[2], colourState[2] + 0.35f) >= 0.5f) colourState[1] else colourState[0]
            if (pull >= 0.75f) {
                haptic(1)
                val i = active.toFloat()
                if (reduced) {
                    colourState[0] = shown; colourState[1] = i; colourState[2] = t - 1
                    swatchState[0] = shown; swatchState[1] = i; swatchState[2] = t - 2
                    pull = 0f
                } else {
                    // Finish coming out (0.12 s), then the pick runs from its release.
                    val r = t + 0.12f
                    pullAnim = 1; pullFrom = pull; pullAt = t
                    releaseAt = r
                    swatchState[0] = swatchState[1]; swatchState[1] = i; swatchState[2] = r + 0.35f
                    colourState[0] = shown; colourState[1] = i; colourState[2] = r + 1.35f
                    tickAt = t + 1.47f
                }
            } else {
                pullAnim = 2
                pullVel = 0f
            }
        }
        touching = false
        lastTouch = now
        running.value = true
    }

    fun step(dt: Float): Boolean {
        val t = now + dt
        now = t
        if (mode == AUTO) loopT = (loopT + dt) % LOOP
        val r = releaseAt
        if (r > 0 && t >= r + 2.25f) {
            active = -1; releaseAt = NONE; pull = 0f; pullAnim = 0; lastTouch = t
        }
        when (pullAnim) {
            1 -> {
                val k = crSeg(t, pullAt, pullAt + 0.12f)
                pull = pullFrom + (1 - pullFrom) * k
                if (k >= 1) pullAnim = 0
            }
            2 -> {
                // A spring of 0.4 s with damping 0.7 back into the slot.
                val w0 = 2 * Math.PI.toFloat() / 0.4f
                var left = dt
                while (left > 0) {
                    val h = min(left, 1 / 240f)
                    pullVel += (-w0 * w0 * pull - 2 * 0.7f * w0 * pullVel) * h
                    pull += pullVel * h
                    left -= h
                }
                if (abs(pull) < 0.001f && abs(pullVel) < 0.01f) { pull = 0f; pullVel = 0f; pullAnim = 0 }
            }
        }
        if (releaseAt < 0 && active >= 0 && !touching && abs(pull) < 0.004f) active = -1
        wobble *= exp(-dt * 5)
        if (mode == USER && auto && !touching && active < 0 && t - lastTouch > 2) {
            mode = RETURN
            modeAt = t
        }
        if (mode == RETURN && t - modeAt >= RETURN_FOR) {
            mode = AUTO
            loopT = 0f
        }
        if (tickAt > 0 && t >= tickAt) { tickAt = NONE; haptic(0) }
        compute()
        frame.intValue += 1
        return when (mode) {
            AUTO, RETURN -> true
            USER -> auto || touching || active >= 0 || pullAnim != 0 || abs(wobble) > 0.001f ||
                t < colourState[2] + 0.4f || t < swatchState[2] + 1.05f || tickAt > 0
            else -> false
        }
    }

    fun compute() {
        overAlpha = 0f
        if (mode == AUTO || mode == STILL) {
            val t = if (mode == AUTO) loopT else 0f
            loopFrame(t, poses, swatch)
            if (mode == AUTO) crLoopColour(t, col) else { col[0] = REST_COLOUR.toFloat(); col[1] = REST_COLOUR.toFloat(); col[2] = 1f }
        } else {
            val t = now
            val r = releaseAt
            var phase = 1; var amount = pull; var wob = wobble
            if (r > 0 && t >= r) {
                tmp[0] = r - 1; tmp[1] = r
                val times = floatArrayOf(r - 1, r, r + 0.35f, r + 1.35f, r + 1.85f, r + 2.25f)
                crPickPhase(times, t, ph)
                phase = ph[0].toInt(); amount = ph[1]; wob = ph[2]
            }
            if (phase == 3) path.point(path.total * amount, pen) else { pen[0] = pen0[0]; pen[1] = pen0[1] }
            crPoses(g, active, phase, amount, wob, pen0, pen, penEnd, poses, slotTmp)
            val prog = crSeg(t, swatchState[2], swatchState[2] + 1.0f)
            swatch[0] = swatchState[0]; swatch[1] = 1 - prog; swatch[2] = swatchState[1]; swatch[3] = prog
            if (mode == RETURN) {
                val k = crEaseInOut(crSeg(t, modeAt, modeAt + RETURN_FOR))
                overAlpha = k
                loopFrame(0f, overPoses, overSwatch)
                if (colourState[1] == REST_COLOUR.toFloat()) { col[0] = colourState[1]; col[1] = colourState[1]; col[2] = 1f }
                else { col[0] = colourState[1]; col[1] = REST_COLOUR.toFloat(); col[2] = k }
            } else {
                col[0] = colourState[0]; col[1] = colourState[1]; col[2] = crEaseInOut(crSeg(t, colourState[2], colourState[2] + 0.35f))
            }
        }
        colFrom.intValue = col[0].toInt()
        colTo.intValue = col[1].toInt()
        colRoll.floatValue = col[2]
    }
}

/**
 * A kids' drawing app's colour picker: an open box of twelve wax crayons above a sheet of paper. Pull
 * a crayon up and let go to scribble a swatch with it and make it the colour.
 *
 * @param autoPlay picks Tangerine, then Sky again, every 9 s.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CrayonBoxPicker(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val reduced = rememberReducedMotion()
    val auto = autoPlay && !reduced
    val density = LocalDensity.current
    val view = LocalView.current
    val top = maxOf(
        WindowInsets.statusBarsIgnoringVisibility.getTop(density),
        WindowInsets.displayCutout.getTop(density),
    ) / density.density
    val bottom = WindowInsets.navigationBarsIgnoringVisibility.getBottom(density) / density.density

    BoxWithConstraints(modifier.fillMaxSize().background(Desk)) {
        val w = maxWidth.value
        val h = maxHeight.value
        var geo by remember { mutableStateOf<CrayonGeo?>(null) }
        val coords = remember { arrayOfNulls<LayoutCoordinates>(2) }
        val measure = {
            val root = coords[0]
            val hero = coords[1]
            if (root != null && hero != null && root.isAttached && hero.isAttached) {
                val r = root.localBoundingBoxOf(hero, clipBounds = false)
                val d = density.density
                val next = CrayonGeo(w, h, r.left / d, r.top / d, r.width / d, r.height / d)
                if (!next.same(geo)) geo = next
            }
        }
        val art by produceState<CrayonArt?>(null, geo) {
            val g = geo ?: return@produceState
            // Wait for the layout to settle before baking; the last art stays on screen meanwhile.
            delay(120)
            val scale = min(2f, density.density)
            value = withContext(Dispatchers.Default) {
                val path = crScribble(g)
                val desk = crBakeDesk(g, scale)
                val front = crBakeFront(g, scale)
                val crayons = List(Crayons.size) { crBakeCrayon(g, it, scale) }
                val (tooth, tr) = crBakeTooth(g)
                CrayonArt(g, path, desk, front, crayons, tooth, tr.left, tr.top, tr.width(), tr.height())
            }
        }
        val a = art
        val haptic = remember(view) {
            { kind: Int -> crayonHaptic(view, kind) }
        }
        val state = remember(a, auto) { a?.let { CrayonState(it, auto, reduced, haptic) } }
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

        val painter = remember(a) { a?.let { CrayonPainter(it) } }
        val placeholder = remember { CrayonPlaceholder() }
        Canvas(Modifier.fillMaxSize().onGloballyPositioned { coords[0] = it; measure() }) {
            state?.frame?.intValue
            val d = density.density
            drawIntoCanvas { canvas ->
                val c = canvas.nativeCanvas
                c.save()
                c.scale(d, d)
                val g = geo
                val f = fade.floatValue
                // Until the art is baked (and while it fades in), the box and paper as flat shapes.
                if (g != null && (state == null || f < 1f)) placeholder.draw(c, g)
                if (state != null && painter != null) {
                    if (f < 1f) c.saveLayerAlpha(0f, 0f, w, h, (f * 255).toInt())
                    painter.drawBox(c, state.poses, state.swatch)
                    if (state.overAlpha > 0f) {
                        c.saveLayerAlpha(0f, 0f, w, h, (state.overAlpha * 255).toInt())
                        painter.drawBox(c, state.overPoses, state.overSwatch)
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
                        .cssBoxShadow(Color(0x1A14322A), 6.dp, 16.dp, 20.dp)
                        .cssBoxShadow(Color(0x2E14322A), 1.dp, 2.dp, 20.dp)
                        .background(Color(0xC7FAFDFB), CircleShape)
                        .semantics { contentDescription = "Back" },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = null, tint = Ink, modifier = Modifier.size(26.dp))
                }
                BasicText("Doodlebug", style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ink))
                Box(
                    Modifier.height(28.dp).background(Color(0x99FAFDFB), RoundedCornerShape(14.dp)).padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText("Page 3", style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MutedInk, fontFeatureSettings = "tnum"))
                }
            }
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                BasicText("Pick a colour", style = TextStyle(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.ExtraBold, color = Ink, letterSpacing = (-0.4).sp))
                BasicText("Pull a crayon out of the box", style = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, color = MutedInk))
            }

            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 300.dp)
                    .fillMaxWidth()
                    .onGloballyPositioned { coords[1] = it; measure() }
                    .semantics { contentDescription = "Box of twelve crayons. Drag one up to pick its colour." }
                    .pointerInput(state) {
                        val s = state ?: return@pointerInput
                        val d = density.density
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val gg = s.g
                            s.begin(gg.heroX + down.position.x / d, gg.heroY + down.position.y / d)
                            val tracker = VelocityTracker()
                            tracker.addPosition(down.uptimeMillis, down.position)
                            var activated = false
                            try {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val ch = event.changes.firstOrNull { it.id == down.id } ?: break
                                    tracker.addPosition(ch.uptimeMillis, ch.position)
                                    if (!ch.pressed) break
                                    val delta = ch.position - down.position
                                    if (!activated && delta.getDistance() > 2 * d) activated = true
                                    if (activated) {
                                        s.change(delta.y / d, tracker.calculateVelocity().x / d)
                                        ch.consume()
                                    }
                                }
                            } finally {
                                s.end(activated)
                            }
                        }
                    },
            )

            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    BasicText("COLOUR", style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MutedInk, letterSpacing = 0.4.sp))
                    Box(Modifier.height(NAME_H.dp).width(180.dp).clipToBounds()) {
                        if (state != null) for (i in Crayons.indices) CrayonNameRow(i, state)
                        else CrayonNameText(REST_COLOUR, Modifier)
                    }
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    BasicText("BOX", style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MutedInk, letterSpacing = 0.4.sp))
                    BasicText("12 wax crayons", style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink, lineHeight = NAME_H.sp))
                }
            }
            CrayonCta()
        }
    }
}

private fun crayonHaptic(view: View, kind: Int) {
    view.performHapticFeedback(if (kind == 1) HapticFeedbackConstants.VIRTUAL_KEY else HapticFeedbackConstants.CLOCK_TICK)
}

@Composable
private fun CrayonNameRow(index: Int, state: CrayonState) {
    CrayonNameText(
        index,
        Modifier.graphicsLayer {
            val from = state.colFrom.intValue
            val to = state.colTo.intValue
            val roll = state.colRoll.floatValue
            val hpx = NAME_H * density
            when {
                index == to -> { alpha = roll; translationY = (1 - roll) * hpx }
                index == from && roll < 1 -> { alpha = 1 - roll; translationY = -roll * hpx }
                else -> { alpha = 0f; translationY = hpx }
            }
        },
    )
}

@Composable
private fun CrayonNameText(index: Int, modifier: Modifier) {
    val c = Crayons[index]
    val colour = Color(0xFF000000 or c.hex)
    Row(modifier.height(NAME_H.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier
                .size(14.dp)
                .cssBoxShadow(Color(0x4D14281E), 1.dp, 2.dp, 7.dp)
                .background(colour, CircleShape)
                .drawBehind {
                    // inset 0 -2 3 rgba(0,0,0,.2), inset 0 1 2 rgba(255,255,255,.35): a rounded bead.
                    drawCircle(
                        Brush.verticalGradient(0f to Color(0x59FFFFFF), 0.3f to Color(0x00FFFFFF), 0.7f to Color(0x00000000), 1f to Color(0x33000000)),
                    )
                },
        )
        BasicText(c.name, style = TextStyle(fontSize = 22.sp, lineHeight = NAME_H.sp, fontWeight = FontWeight.ExtraBold, color = Ink))
    }
}

@Composable
private fun CrayonCta() {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Box(
        Modifier
            .padding(top = 16.dp)
            .fillMaxWidth()
            .height(56.dp)
            .graphicsLayer { val k = if (pressed) 0.97f else 1f; scaleX = k; scaleY = k }
            .cssBoxShadow(Color(0x33173A30), 12.dp, 24.dp, 28.dp)
            .cssBoxShadow(Color(0x40173A30), 2.dp, 4.dp, 28.dp)
            .background(Ink, RoundedCornerShape(28.dp))
            .drawBehind {
                val r = size.height / 2
                drawLine(Color(0x1FFFFFFF), Offset(r, 0.5f.dp.toPx()), Offset(size.width - r, 0.5f.dp.toPx()), 1.dp.toPx())
            }
            .clickable(interactionSource = source, indication = null, role = Role.Button) {},
        contentAlignment = Alignment.Center,
    ) {
        BasicText("Start drawing", style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFF3FAF7)))
    }
}

/** What shows for the moment before the art is baked: the paper, the box and the crayons as flat shapes. */
private class CrayonPlaceholder {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()
    private val s = FloatArray(4)

    fun draw(c: android.graphics.Canvas, g: CrayonGeo) {
        p.color = 0xFFA9C9C2.toInt()
        c.drawRect(0f, 0f, g.w, g.h, p)
        p.color = 0xFFFBF6EC.toInt()
        val px = g.cx - (g.w - 24) / 2
        r.set(px, g.paperY, px + g.w - 24, g.paperY + g.paperH)
        c.drawRoundRect(r, 6f, 6f, p)
        for (i in 0 until 12) {
            crSlot(g, i, s)
            p.color = (0xFF000000 or Crayons[i].hex).toInt()
            r.set(s[0] - g.cw / 2, s[1] - g.cl / 2, s[0] + g.cw / 2, g.rimY + 4)
            c.drawRoundRect(r, g.cw * 0.3f, g.cw * 0.3f, p)
        }
        p.color = 0xFFF2C04A.toInt()
        r.set(g.boxX, g.rimY, g.boxX + g.boxW, g.rimY + g.frontH)
        c.drawRoundRect(r, 6f, 6f, p)
        p.color = 0xFF24604A.toInt()
        c.drawRect(g.boxX, g.rimY + g.frontH * 0.42f, g.boxX + g.boxW, g.rimY + g.frontH * 0.82f, p)
    }
}
