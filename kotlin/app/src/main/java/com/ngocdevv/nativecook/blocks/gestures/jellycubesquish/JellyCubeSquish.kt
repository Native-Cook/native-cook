package com.ngocdevv.nativecook.blocks.gestures.jellycubesquish

import android.graphics.Bitmap
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.blocks.gestures.jellycubesquish.JellyMath.HALF
import com.ngocdevv.nativecook.blocks.gestures.jellycubesquish.JellyMath.RIGHT
import com.ngocdevv.nativecook.blocks.gestures.jellycubesquish.JellyMath.TO_EYE
import com.ngocdevv.nativecook.blocks.gestures.jellycubesquish.JellyMath.UP
import com.ngocdevv.nativecook.blocks.gestures.jellycubesquish.JellyMath.clamp
import com.ngocdevv.nativecook.blocks.gestures.jellycubesquish.JellyMotion.easeInOut
import com.ngocdevv.nativecook.blocks.gestures.jellycubesquish.JellyMotion.seg
import com.ngocdevv.nativecook.shared.cssBoxShadow
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.math.tanh

/*
 Jelly Cube Squish — a dessert-bar order screen ("Wobble — Build your jelly") with a translucent
 jelly cube on a plate: lit per vertex (diffuse, a Fresnel sheen, see-through far faces, an inner
 glow, suspended bubbles, additive gloss and a reflected window) and deformed as a soft body. Poke
 it and it dents and wobbles; drag it and the part under the finger stretches, then springs back
 wobbling. Pick a flavour and the new colour rises through it from the plate in a gooey front.

 One Canvas: the baked backdrop and plate, then the cube's meshes, the demo finger and the
 flavour's name. Deformation and camera: JellyCubeSquishMath.kt; timeline: JellyCubeSquishMotion.kt;
 drawing: JellyCubeSquishArt.kt. The flavour chips are composables with one sliding highlight,
 moved in its graphicsLayer.

 Touch: springs in the frame loop — squash and lean ω 13 rad/s ζ 0.12, dent ω 20 ζ 0.25, stretch
 ω 12 ζ 0.15. 2 s after the last touch the cube eases to rest (0.9 s), the colour fills back to
 strawberry if needed, and the loop restarts. Reduced motion: no loop, no wobble (the shape follows
 the finger directly); a new flavour shows at once.
 */

private const val IDLE = 2.0
private const val SETTLE = 0.9

internal val JellyBackground = Color(0xFFF1DCD6)
private val Ink = Color(0xFF3A1F22)

/** One spring: value, velocity, target. */
private class JellySpring {
    var x = 0.0
    var v = 0.0
    var t = 0.0

    fun step(omega: Double, zeta: Double, dt: Double, reduced: Boolean) {
        if (reduced) {
            x = t
            v = 0.0
            return
        }
        val n = max(1, ceil(dt * 240).toInt())
        val h = dt / n
        repeat(n) {
            v += (-omega * omega * (x - t) - 2 * zeta * omega * v) * h
            x += v * h
        }
    }

    fun set(x: Double) {
        this.x = x
        v = 0.0
        t = 0.0
    }

    val settled get() = abs(x - t) < 1e-4 && abs(v) < 1e-3
}

private class JellyState {
    /** mode: 0 loop / rest, 1 a finger is in charge, 2 settling back to rest. */
    var mode = 0
    var autoStart = -1.0
    var now = -1.0
    var lastTouch = -1.0
    /** -1 a touch that is not ours, 0 none, 1 touching. */
    var touching = 0
    val sq = JellySpring()
    val lx = JellySpring()
    val ly = JellySpring()
    val dent = JellySpring()
    val sx = JellySpring()
    val sy = JellySpring()
    val sz = JellySpring()
    /** Touch point and the dent's direction. */
    val p = doubleArrayOf(0.0, -HALF, HALF, 0.0, 1.0, 0.0)
    /** Colour morph: from, to, start time. */
    var cFrom = 0
    var cTo = 0
    var cStart = -10.0
    var settleAt = -1.0
    // What the frame draws.
    val d = JellyDeform()
    val colour = JellyColour()
    val finger = DoubleArray(4)
    var time = 0.0
    val frame = mutableIntStateOf(0)

    fun reset() {
        for (s in arrayOf(sq, lx, ly, dent, sx, sy, sz)) s.set(0.0)
        p[0] = 0.0; p[1] = -HALF; p[2] = HALF; p[3] = 0.0; p[4] = 1.0; p[5] = 0.0
        cFrom = 0; cTo = 0; cStart = -10.0
    }

    /** The colour state [from, to, fill, roll] of a touch-mode morph, into colour. */
    fun morph(now: Double, reduced: Boolean, out: JellyColour): JellyColour =
        if (reduced) out.set(cTo, cTo, 0.0, 0.0)
        else out.set(cFrom, cTo, easeInOut(seg(now, cStart, cStart + 1)), easeInOut(seg(now, cStart, cStart + 0.3)))

    fun soften(k: Double) {
        d.sq = sq.x * k
        d.lx = lx.x * k
        d.ly = ly.x * k
        d.dent = dent.x * k
        d.px = p[0]; d.py = p[1]; d.pz = p[2]
        d.nx = p[3]; d.ny = p[4]; d.nz = p[5]
        d.sx = sx.x * k
        d.sy = sy.x * k
        d.sz = sz.x * k
    }
}

private class JellyArt(val backdrop: Bitmap, val shadow: Bitmap)

/**
 * A translucent jelly cube on a plate: poke it or drag it to make it wobble, pick a flavour to fill
 * it with a new colour.
 *
 * @param autoPlay pokes the cube, switches to lime, stretches it, and back to strawberry, on an 8 s loop.
 */
@Composable
fun JellyCubeSquish(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val reduced = rememberReducedMotion()
    val auto = autoPlay && !reduced
    val view = LocalView.current
    val screen = LocalDensity.current
    val insets = WindowInsets.safeDrawing
    val safeTop = insets.getTop(screen) / screen.density
    val safeBottom = insets.getBottom(screen) / screen.density
    val state = remember { JellyState() }
    val kit = remember { JellyKit() }
    val fonts = remember { JellyFonts() }
    val scratch = remember { JellyScratch() }
    val rest = remember { JellyMath.restPoints() }
    var wake by remember { mutableIntStateOf(0) }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(JellyBackground)
            .semantics { contentDescription = "A jelly cube on a plate. Poke it or drag it to make it wobble." },
    ) {
        val geo = remember(maxWidth, maxHeight, safeTop, safeBottom) { JellyGeo(maxWidth.value, maxHeight.value, safeTop, safeBottom) }
        var art by remember { mutableStateOf<JellyArt?>(null) }
        val scale = min(screen.density, 2f)

        LaunchedEffect(geo, scale) {
            // The demo hides the status bar and the insets animate for a moment: wait for them to settle
            // (a cancellable delay) so the bake does not restart on every inset frame.
            delay(120)
            art = withContext(Dispatchers.Default) {
                val backdrop = jellyBake(geo.w, geo.h, scale) { jellyDrawBackdrop(it, JellyKit(), geo) }
                val side = (JELLY_SHADOW_HALF + JELLY_SHADOW_PAD) * 2
                val shadow = jellyBake(side, side, scale) { jellyDrawShadow(it, JellyKit()) }
                JellyArt(backdrop, shadow)
            }
        }

        val ready = art != null
        LaunchedEffect(geo, auto, reduced, ready, wake) {
            if (!ready) return@LaunchedEffect
            state.now = -1.0
            while (true) {
                val keepGoing = withFrameNanos { nanos -> jellyStep(state, nanos / 1e9, auto, reduced) }
                state.frame.intValue += 1
                if (!keepGoing) break
            }
        }

        val bitmapPaint = remember { Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG) }
        val backRect = remember(geo) { RectF(0f, 0f, geo.w, geo.h) }

        fun takeOver() {
            val s = state
            if (s.mode != 1) {
                val d = s.d
                val c = s.colour
                val shown = if (c.from == c.to) c.from else if (c.fill >= 0.5) c.to else c.from
                s.sq.set(d.sq); s.lx.set(d.lx); s.ly.set(d.ly); s.dent.set(d.dent)
                s.sx.set(d.sx); s.sy.set(d.sy); s.sz.set(d.sz)
                s.p[0] = d.px; s.p[1] = d.py; s.p[2] = d.pz; s.p[3] = d.nx; s.p[4] = d.ny; s.p[5] = d.nz
                s.cFrom = shown
                s.cTo = shown
                s.cStart = -10.0
            }
            s.mode = 1
            s.autoStart = -1.0
            s.lastTouch = s.now
        }

        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(geo, safeTop) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val dd = screen.density
                        val ex = down.position.x / dd
                        val ey = down.position.y / dd
                        val s = state
                        if (ey > geo.nameBase - 34 || ey < safeTop + 100) {
                            s.touching = -1
                            return@awaitEachGesture
                        }
                        takeOver()
                        s.touching = 1
                        wake++
                        // Where the finger meets the cube: on the plane facing the camera through its centre,
                        // moved toward the eye and clamped onto the box; the dent points into the face it hit.
                        val c = DoubleArray(3)
                        geo.project(0.0, 0.0, HALF, c)
                        val dx = (ex - c[0]) / c[2]
                        val dy = (ey - c[1]) / c[2]
                        val px = RIGHT[0] * dx - UP[0] * dy + TO_EYE[0] * HALF * 1.3
                        val py = RIGHT[1] * dx - UP[1] * dy + TO_EYE[1] * HALF * 1.3
                        val pz = HALF - UP[2] * dy + TO_EYE[2] * HALF * 1.3
                        val q = doubleArrayOf(clamp(px, -HALF, HALF), clamp(py, -HALF, HALF), clamp(pz, 0.0, 2 * HALF))
                        val out = doubleArrayOf(abs(q[0]) / HALF, abs(q[1]) / HALF, abs(q[2] - HALF) / HALF)
                        var axis = 0
                        if (out[1] > out[axis]) axis = 1
                        if (out[2] > out[axis]) axis = 2
                        val nrm = DoubleArray(3)
                        nrm[axis] = if (axis == 2) (if (q[2] > HALF) -1.0 else 1.0) else if (q[axis] > 0) -1.0 else 1.0
                        s.p[0] = q[0]; s.p[1] = q[1]; s.p[2] = q[2]
                        s.p[3] = nrm[0]; s.p[4] = nrm[1]; s.p[5] = nrm[2]
                        s.dent.t = 26.0
                        s.sq.t = 0.06
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                s.lastTouch = s.now
                                val tx = (change.position.x - down.position.x) / dd / c[2]
                                val ty = (change.position.y - down.position.y) / dd / c[2]
                                val len = hypot(tx, ty)
                                change.consume()
                                if (len < 8) continue
                                // Pull: the drag in the camera's plane, rubber-banded to 64 pt.
                                val k = 64 * tanh(len / 64) / len
                                s.dent.t = 0.0
                                s.sq.t = -0.03
                                s.sx.t = (RIGHT[0] * tx - UP[0] * ty) * k
                                s.sy.t = (RIGHT[1] * tx - UP[1] * ty) * k
                                s.sz.t = -UP[2] * ty * k
                            }
                        } finally {
                            s.touching = 0
                            s.lastTouch = s.now
                            val pull = sqrt(s.sx.x * s.sx.x + s.sy.x * s.sy.x + s.sz.x * s.sz.x)
                            // Let go: everything springs home; the pull kicks the wobble modes.
                            s.dent.t = 0.0
                            s.sq.v += 0.03 * pull
                            s.sq.t = 0.0
                            s.lx.v -= s.sx.x * 3
                            s.lx.t = 0.0
                            s.ly.v += -s.sy.x * 3 + if (pull < 4) 40 * s.p[4] else 0.0
                            s.ly.t = 0.0
                            s.sx.t = 0.0
                            s.sy.t = 0.0
                            s.sz.t = 0.0
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        }
                    }
                },
        ) {
            state.frame.intValue
            val a = art ?: return@Canvas
            drawIntoCanvas { cv ->
                val nc = cv.nativeCanvas
                val s = state
                nc.save()
                nc.scale(density, density)
                kit.begin()
                nc.drawBitmap(a.backdrop, null, backRect, bitmapPaint)
                jellyDrawCube(nc, kit, scratch, geo, rest, a.shadow, bitmapPaint, s.d, s.colour, s.time)
                jellyDrawFinger(nc, kit, scratch, geo, s.finger)
                jellyDrawName(nc, kit, geo, fonts, s.colour)
                nc.restore()
            }
        }

        if (ready) {
            Column(Modifier.padding(start = 24.dp, end = 24.dp, top = (safeTop + 16).dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                BasicText("WOBBLE · DESSERT BAR", style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.8.sp, color = Ink.copy(alpha = 0.5f)))
                BasicText("Build your jelly", style = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp, color = Ink))
                BasicText("Step 2 of 3 · Pick a flavour", style = TextStyle(fontSize = 15.sp, color = Ink.copy(alpha = 0.6f)))
            }
            val chipW = (geo.w - 32 - 16) / 3
            Box(Modifier.offset(y = geo.chipsTop.dp).padding(horizontal = 16.dp).fillMaxWidth().height(44.dp)) {
                // The highlight slides with the colour's name; it moves in its layer, not in layout.
                Box(
                    Modifier
                        .width(chipW.dp)
                        .fillMaxHeight()
                        .graphicsLayer {
                            state.frame.intValue
                            val c = state.colour
                            translationX = ((c.from + (c.to - c.from) * c.roll) * (chipW + 8) * density).toFloat()
                        }
                        .cssBoxShadow(Ink.copy(alpha = 0.12f), 6.dp, 16.dp, 22.dp)
                        .cssBoxShadow(Ink.copy(alpha = 0.16f), 1.dp, 2.dp, 22.dp)
                        .background(Color(0xFFFFFDFB), RoundedCornerShape(22.dp)),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    JellyFlavours.forEachIndexed { i, fl ->
                        Row(
                            Modifier
                                .width(chipW.dp)
                                .fillMaxHeight()
                                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button) {
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    takeOver()
                                    val s = state
                                    val c = s.morph(s.now, reduced, JellyColour())
                                    val shown = if (c.from == c.to) c.from else if (c.fill >= 0.5) c.to else c.from
                                    if (shown != i) {
                                        s.cFrom = shown
                                        s.cTo = i
                                        s.cStart = s.now
                                        s.sq.v += 1.2
                                    }
                                    wake++
                                }
                                .semantics { contentDescription = fl.name }
                                .padding(horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Box(Modifier.size(10.dp).background(Color(fl.r.toFloat(), fl.g.toFloat(), fl.b.toFloat()), CircleShape))
                            Spacer(Modifier.width(6.dp))
                            BasicText(fl.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Ink))
                        }
                    }
                }
            }
            JellyCta(Modifier.offset(y = geo.ctaTop.dp).padding(horizontal = 16.dp))
        }
    }
}

/** One frame of the clock, the springs and the colour. Returns whether to keep running. */
private fun jellyStep(s: JellyState, t1: Double, auto: Boolean, reduced: Boolean): Boolean {
    val first = s.now < 0
    val dt = if (first) 0.0 else min(t1 - s.now, 0.05)
    s.now = t1
    if (s.lastTouch < 0) s.lastTouch = t1
    if (first && auto && s.mode == 0) s.autoStart = t1
    if (s.mode == 0 && s.autoStart >= 0) {
        val t = (t1 - s.autoStart) % LOOP
        JellyMotion.autoDeform(t, s.d)
        JellyMotion.autoColour(t, s.colour)
        JellyMotion.autoFinger(t, s.d, s.finger)
        s.time = t
        return true
    }
    s.sq.step(13.0, 0.12, dt, reduced)
    s.lx.step(13.0, 0.12, dt, reduced)
    s.ly.step(13.0, 0.12, dt, reduced)
    s.dent.step(20.0, 0.25, dt, reduced)
    s.sx.step(12.0, 0.15, dt, reduced)
    s.sy.step(12.0, 0.15, dt, reduced)
    s.sz.step(12.0, 0.15, dt, reduced)
    var k = 1.0
    if (s.mode == 2) {
        k = 1 - easeInOut(seg(t1, s.settleAt, s.settleAt + SETTLE))
        val c = s.morph(t1, reduced, s.colour)
        if (k <= 0 && ((c.to == 0 && c.fill >= 1) || (c.from == c.to && c.to == 0))) {
            s.reset()
            s.mode = 0
            s.autoStart = t1
        }
    } else if (s.mode == 1 && auto && s.touching == 0 && t1 - s.lastTouch > IDLE) {
        s.mode = 2
        s.settleAt = t1
        val c = s.morph(t1, reduced, s.colour)
        val shown = if (c.fill >= 0.5) c.to else c.from
        if (shown != 0) {
            s.cFrom = shown
            s.cTo = 0
            s.cStart = t1
        }
    }
    s.soften(k)
    s.morph(t1, reduced, s.colour)
    s.finger.fill(0.0)
    s.time = ((t1 % LOOP) + LOOP) % LOOP
    if (auto) return true
    // Without the loop the frames stop once the springs and the colour have come to rest.
    val springs = arrayOf(s.sq, s.lx, s.ly, s.dent, s.sx, s.sy, s.sz)
    val still = s.touching != 1 && springs.all { it.settled } && t1 > s.cStart + 1
    return !still
}

@Composable
private fun JellyCta(modifier: Modifier) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Row(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .graphicsLayer {
                val k = if (pressed) 0.97f else 1f
                scaleX = k
                scaleY = k
            }
            .cssBoxShadow(Ink.copy(alpha = 0.22f), 10.dp, 24.dp, 28.dp)
            .cssBoxShadow(Ink.copy(alpha = 0.3f), 1.dp, 2.dp, 28.dp)
            .background(Ink, RoundedCornerShape(28.dp))
            .clickable(interactionSource = source, indication = null, role = Role.Button) {}
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        BasicText("Add to order", style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFFFF4F1)))
        BasicText("\$6.50", style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color(0xB3FFF4F1), fontFeatureSettings = "tnum"))
    }
}
