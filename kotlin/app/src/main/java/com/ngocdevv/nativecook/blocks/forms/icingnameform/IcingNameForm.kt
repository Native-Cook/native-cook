package com.ngocdevv.nativecook.blocks.forms.icingnameform

import android.graphics.Paint
import android.view.HapticFeedbackConstants
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.cssBoxShadow
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 Icing Name Form — "Crumb & Co · Write on the cake". An 8-inch cake seen from above on a gold drum,
 a ring of piped rosettes round its edge. Whatever is typed in the message field is piped onto it in
 buttercream: the nozzle follows each letter's strokes and lays a glossy tube with a pressure bulge
 where it starts, lifting between strokes. The colour chips re-pipe the message in another icing,
 which flows through the letters. The price rolls with every letter.

 Touch: typing pipes each new letter (0.22 s + 5 ms a pt of icing); the message re-centres with a
 0.35 s glide; a chip flows the new colour through the letters in 0.7 s. Drag on the cake to turn the
 turntable (it follows the finger round its centre and coasts on release). With autoPlay, 2 s after
 the last touch (and with the field closed) the cake turns home and cross-fades back to the loop's
 first frame in 0.8 s; while the loop plays it types its script into the field without focusing it.
 Reduced motion: "Happy birthday" stays piped; typed letters appear whole.

 One Canvas: counter and cake baked once off the main thread; per frame the cake bitmap, the
 lettering paths, the knife and the piping bag are drawn from one state object.
*/

private const val AUTO = 0
private const val USER = 1
private const val RETURN = 2
private const val STILL = 3
private const val RETURN_FOR = 0.8f
private const val GLIDE = 0.35f
private const val FLOW_FOR = 0.7f
private const val NONE = -100f
private const val CHIP = 38f
private const val CHIP_GAP = 14f

private val Ink = Color(0xFF3B2418)
private val MutedInk = Color(0xFF8C7462)
private val Card = Color(0xFFFFFBF6)
private val Backdrop = Color(0xFFF1E9DF)

private fun pipeDuration(len: Float) = 0.22f + len / 200

private class IcUser(
    val l: IcingLettering,
    val typedAt: FloatArray,
    val fromX: FloatArray,
    val fromY: FloatArray,
    val movedAt: Float,
    var colorFrom: Int,
    var colorTo: Int,
    var colorAt: Float,
)

private class IcState(val art: IcingArt, val auto: Boolean, val reduced: Boolean) {
    val g = art.geo
    var mode = if (auto) AUTO else STILL
    var now = 0f
    var loopT = 0f
    var modeAt = 0f
    var lastTouch = 0f
    var focused = false
    var touching = false
    var turn = 0f
    var spin = 0f
    private var turnFrom = 0f
    var grab = 0f
    val pen = floatArrayOf(g.parkX, g.parkY, 1f)
    private var chipUser = 0f
    private var chipVel = 0f
    private var chipTarget = 0f
    private val priceUser = floatArrayOf(4450f, 4450f, NONE)
    var user: IcUser? = null
    private var activeUntil = 0f

    val text = mutableStateOf(REST_TEXT)
    val icing = mutableIntStateOf(0)
    val chipPos = mutableFloatStateOf(0f)
    val priceFrom = mutableFloatStateOf(4450f)
    val priceTo = mutableFloatStateOf(4450f)
    val priceRoll = mutableFloatStateOf(1f)
    val frame = mutableIntStateOf(0)
    val running = mutableStateOf(true)
    private var scriptAt = -1

    // Scratch.
    var fracs = FloatArray(0)
    var shiftX = FloatArray(ICING_MAX_LETTERS)
    var shiftY = FloatArray(ICING_MAX_LETTERS)
    private val tmp = FloatArray(2)
    private val target = FloatArray(3)
    private val price = FloatArray(3)

    init { compute() }

    private fun userFracs(u: IcUser, t: Float, instant: Boolean) {
        val l = u.l
        if (fracs.size < l.strokes.size) fracs = FloatArray(l.strokes.size)
        var o = 0
        for (gi in l.firstStroke.indices) {
            val len = l.glyphLength[gi]
            val p = if (instant) 1f else min(1f, max(0f, (t - u.typedAt[gi]) / pipeDuration(len)))
            var ink = p * len
            for (k in 0 until l.strokeCount[gi]) {
                val s = l.strokes[l.firstStroke[gi] + k]
                val full = max(0.5f, s.full)
                fracs[o++] = min(1f, max(0f, ink / full))
                ink -= full
            }
        }
    }

    private fun userShift(u: IcUser, t: Float) {
        val k = 1 - icEaseInOut(icSeg(t, u.movedAt, u.movedAt + GLIDE))
        for (i in u.fromX.indices) { shiftX[i] = u.fromX[i] * k; shiftY[i] = u.fromY[i] * k }
    }

    /** The nozzle's target while the user types: x, y (cake-local), busy. */
    private fun userPen(u: IcUser, out: FloatArray) {
        val l = u.l
        for (i in l.strokes.indices.reversed()) {
            if (fracs[i] > 0 && fracs[i] < 1) {
                val s = l.strokes[i]
                icStrokePoint(s.pts, s.len, fracs[i] * s.full, tmp)
                out[0] = tmp[0] + shiftX[s.glyph]; out[1] = tmp[1] + shiftY[s.glyph]; out[2] = 1f
                return
            }
        }
        out[0] = g.parkX; out[1] = g.parkY; out[2] = 0f
    }

    /** Hands the screen to the user with whatever message the cake shows. */
    fun takeOver() {
        running.value = true
        lastTouch = now
        if (mode == USER && user != null) return
        val shown = text.value
        val colour = icing.intValue
        val l = icingLayout(shown, g.boxW, g.boxH)
        val n = l.firstStroke.size
        user = IcUser(l, FloatArray(n) { NONE }, FloatArray(n), FloatArray(n), NONE, colour, colour, NONE)
        chipUser = colour.toFloat(); chipTarget = chipUser; chipVel = 0f
        val cents = icPriceFor(shown, colour).toFloat()
        priceUser[0] = cents; priceUser[1] = cents; priceUser[2] = NONE
        pen[0] = g.parkX; pen[1] = g.parkY; pen[2] = 1f
        if (mode != RETURN) turn = 0f
        mode = USER
    }

    fun onType(value: String) {
        if (mode != USER) takeOver()
        val chars = icingChars(value).take(ICING_MAX_LETTERS)
        val next = chars.joinToString("")
        val prevText = text.value
        text.value = next
        val t = now
        val prev = user
        val l = icingLayout(next, g.boxW, g.boxH)
        val prevChars = icingChars(prevText)
        val k = if (prev != null) 1 - icEaseInOut(icSeg(t, prev.movedAt, prev.movedAt + GLIDE)) else 0f
        val n = chars.size
        val typedAt = FloatArray(n); val fromX = FloatArray(n); val fromY = FloatArray(n)
        for (i in 0 until n) {
            val same = prev != null && i < prev.l.originX.size && i < prevChars.size && prevChars[i] == chars[i] && abs(prev.l.size - l.size) < 0.5f
            typedAt[i] = if (same) prev!!.typedAt[i] else t
            fromX[i] = if (same) prev!!.l.originX[i] + prev.fromX[i] * k - l.originX[i] else 0f
            fromY[i] = if (same) prev!!.l.originY[i] + prev.fromY[i] * k - l.originY[i] else 0f
        }
        val colour = prev?.colorTo ?: icing.intValue
        user = IcUser(l, typedAt, fromX, fromY, t, prev?.colorFrom ?: colour, colour, prev?.colorAt ?: NONE)
        priceUser[0] = icPriceFor(prevText, colour).toFloat()
        priceUser[1] = icPriceFor(next, colour).toFloat()
        priceUser[2] = t
        var longest = 0f
        for (gi in l.glyphLength.indices) if (typedAt[gi] == t) longest = max(longest, pipeDuration(l.glyphLength[gi]))
        activeUntil = max(activeUntil, t + max(longest, GLIDE + 0.1f))
        lastTouch = t
        running.value = true
    }

    fun pickIcing(i: Int) {
        if (mode != USER) takeOver()
        val message = text.value
        val from = icPriceFor(message, icing.intValue).toFloat()
        val to = icPriceFor(message, i).toFloat()
        icing.intValue = i
        val t = now
        val u = user ?: return
        val flowDone = t - u.colorAt >= FLOW_FOR
        if (flowDone) u.colorFrom = u.colorTo
        u.colorTo = i
        u.colorAt = t
        chipTarget = i.toFloat()
        priceUser[0] = from; priceUser[1] = to; priceUser[2] = t
        activeUntil = max(activeUntil, t + FLOW_FOR + 0.1f)
        lastTouch = t
        running.value = true
    }

    fun down(x: Float, y: Float) {
        touching = true
        grab = atan2(y - g.cy, x - g.cx)
        spin = 0f
        running.value = true
    }

    fun drag(x: Float, y: Float) {
        val ang = atan2(y - g.cy, x - g.cx)
        var d = ang - grab
        if (d > PI) d -= 2 * PI.toFloat()
        if (d < -PI) d += 2 * PI.toFloat()
        grab = ang
        if (mode == USER) turn += d
        lastTouch = now
    }

    fun fling(x: Float, y: Float, vx: Float, vy: Float) {
        // Angular speed from the finger's velocity across the radius.
        val rx = x - g.cx
        val ry = y - g.cy
        val r2 = max(400f, rx * rx + ry * ry)
        spin = max(-9f, min(9f, (rx * vy - ry * vx) / r2))
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
            val i = icScriptIndex(loopT)
            if (i != scriptAt) { scriptAt = i; text.value = IcingTextScript[i].second }
            val c = icChipPosition(loopT).roundToInt()
            if (c != icing.intValue) icing.intValue = c
        }
        if (mode == USER && auto && !focused && !touching && t - lastTouch > 2) {
            mode = RETURN
            modeAt = t
            // The turntable goes home by the shortest way.
            turnFrom = turn - (turn / (2 * PI.toFloat())).roundToInt() * 2 * PI.toFloat()
            text.value = REST_TEXT
            icing.intValue = 0
        }
        if (mode == RETURN) {
            val k = icEaseInOut(icSeg(t, modeAt, modeAt + RETURN_FOR))
            turn = turnFrom * (1 - k)
            spin = 0f
            if (k >= 1) {
                mode = AUTO
                loopT = 0f
                turn = 0f
                scriptAt = -1
            }
        }
        var moving = false
        if (mode == USER && !touching) {
            // The turntable coasts after a flick.
            turn += spin * dt
            spin *= exp(-dt * 2.4f)
            if (abs(spin) < 0.002f) spin = 0f else moving = true
        }
        val u = user
        if ((mode == USER || mode == RETURN) && u != null) {
            userFracs(u, t, reduced)
            userShift(u, t)
            userPen(u, target)
            val k = 1 - exp(-dt * if (target[2] > 0.5f) 30f else 8f)
            val dx = target[0] - pen[0]; val dy = target[1] - pen[1]
            pen[0] += dx * k
            pen[1] += dy * k
            val lift = if (target[2] > 0.5f) 0f else 1f
            pen[2] += (lift - pen[2]) * (1 - exp(-dt * 10))
            if (abs(dx) + abs(dy) > 0.05f || abs(lift - pen[2]) > 0.002f || target[2] > 0.5f) moving = true
        }
        // The chip ring springs to the picked icing (0.4 s, damping 0.75).
        if (chipUser != chipTarget || chipVel != 0f) {
            val w0 = 2 * PI.toFloat() / 0.4f
            var left = dt
            while (left > 0) {
                val h = min(left, 1 / 240f)
                chipVel += (-w0 * w0 * (chipUser - chipTarget) - 2 * 0.75f * w0 * chipVel) * h
                chipUser += chipVel * h
                left -= h
            }
            if (abs(chipUser - chipTarget) < 0.0005f && abs(chipVel) < 0.005f) { chipUser = chipTarget; chipVel = 0f }
            moving = true
        }
        compute()
        frame.intValue += 1
        return when (mode) {
            AUTO, RETURN -> true
            USER -> auto || focused || touching || moving || t < activeUntil || t < priceUser[2] + 0.5f
            else -> false
        }
    }

    fun compute() {
        val k = if (mode == RETURN) icEaseInOut(icSeg(now, modeAt, modeAt + RETURN_FOR)) else 0f
        chipPos.floatValue = when (mode) {
            AUTO -> icChipPosition(loopT)
            RETURN -> chipUser * (1 - k)
            STILL -> 0f
            else -> chipUser
        }
        when (mode) {
            AUTO -> icPriceAt(loopT, price)
            STILL -> { price[0] = 4450f; price[1] = 4450f; price[2] = 1f }
            RETURN -> { price[0] = priceUser[1]; price[1] = 4450f; price[2] = icEaseInOut(icSeg(now, modeAt, modeAt + PRICE_ROLL)) }
            else -> { price[0] = priceUser[0]; price[1] = priceUser[1]; price[2] = icEaseInOut(icSeg(now, priceUser[2], priceUser[2] + PRICE_ROLL)) }
        }
        priceFrom.floatValue = price[0]
        priceTo.floatValue = price[1]
        priceRoll.floatValue = price[2]
    }

    fun draw(c: android.graphics.Canvas, p: IcingPainter) {
        p.drawTable(c)
        val u = user
        if (mode == AUTO || mode == STILL || u == null) {
            p.drawLoopFrame(c, if (mode == AUTO) loopT else 0f, 1f, true)
            return
        }
        val t = now
        val k = if (mode == RETURN) icEaseInOut(icSeg(t, modeAt, modeAt + RETURN_FOR)) else 0f
        val a = turn
        p.drawCake(c, g.cx, g.cy, a)
        val flow = if (reduced) 1f else icEaseInOut(icSeg(t, u.colorAt, u.colorAt + FLOW_FOR))
        userFracs(u, t, reduced)
        userShift(u, t)
        p.drawWriting(c, u.l, g.cx, g.cy, a, fracs, shiftX, shiftY, u.colorFrom, u.colorTo, flow, 1 - k, -1e5f)
        if (k > 0) p.drawWriting(c, art.rest, g.cx, g.cy, a, null, null, null, 0, 0, 0f, k, -1e5f)
        // The nozzle works in the cake's frame while it pipes; at rest it hangs where it always does.
        val cs = cos(a); val sn = sin(a)
        val busy = 1 - pen[2]
        val lx = pen[0] * cs - pen[1] * sn
        val ly = pen[0] * sn + pen[1] * cs
        val x = lx * busy + g.parkX * (1 - busy)
        val y = ly * busy + g.parkY * (1 - busy)
        p.drawNozzle(c, g.cx + x + (g.parkX - x) * k, g.cy + y + (g.parkY - y) * k, max(pen[2], k), Icings[u.colorTo].rgb)
    }
}

/**
 * A bakery order form's message field: what is typed is piped onto the cake in buttercream, the
 * nozzle following each letter's strokes; chips re-pipe it in another icing; drag to turn the cake.
 *
 * @param autoPlay scrapes "Happy birthday" off, pipes "Noor" and turns it rose, then brings a fresh cake, every 9 s.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IcingNameForm(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
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
    val focusManager = LocalFocusManager.current

    BoxWithConstraints(modifier.fillMaxSize().background(Backdrop)) {
        val w = maxWidth.value
        val h = maxHeight.value
        var geo by remember { mutableStateOf<IcingGeo?>(null) }
        val coords = remember { arrayOfNulls<LayoutCoordinates>(3) }
        val fieldBottom = remember { mutableFloatStateOf(0f) }
        val measure = {
            val root = coords[0]
            val hero = coords[1]
            if (root != null && hero != null && root.isAttached && hero.isAttached) {
                val r = root.localBoundingBoxOf(hero, clipBounds = false)
                val d = density.density
                val next = IcingGeo(w, h, r.left / d, r.top / d, r.width / d, r.height / d)
                if (!next.same(geo)) geo = next
                val field = coords[2]
                if (field != null && field.isAttached) fieldBottom.floatValue = root.localBoundingBoxOf(field, clipBounds = false).bottom / d
            }
        }
        val art by produceState<IcingArt?>(null, geo) {
            val g = geo ?: return@produceState
            // Wait for the layout to settle before baking; the last art stays on screen meanwhile.
            delay(120)
            val scale = min(2f, density.density)
            value = withContext(Dispatchers.Default) {
                val rest = icingLayout(REST_TEXT, g.boxW, g.boxH)
                val second = icingLayout(SECOND_TEXT, g.boxW, g.boxH)
                val (t0, t1) = icingSchedule(second)
                IcingArt(g, icBakeTable(g, scale), icBakeCake(g, scale), rest, second, t0, t1)
            }
        }
        val a = art
        val state = remember(a, auto) { a?.let { IcState(it, auto, reduced) } }
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

        val painter = remember(a) { a?.let { IcingPainter(it) } }
        val placeholder = remember { IcPlaceholder() }
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
                        state.draw(c, painter)
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
                            .cssBoxShadow(Color(0x14462814), 6.dp, 16.dp, 20.dp)
                            .cssBoxShadow(Color(0x29462814), 1.dp, 2.dp, 20.dp)
                            .background(Color(0xD9FFFCF7), CircleShape)
                            .semantics { contentDescription = "Back" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = null, tint = Ink, modifier = Modifier.size(26.dp))
                    }
                    BasicText("Crumb & Co", style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink, letterSpacing = 0.2.sp))
                    BasicText(
                        "Step 2 of 3",
                        style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MutedInk, fontFeatureSettings = "tnum", textAlign = TextAlign.End),
                        modifier = Modifier.width(76.dp),
                    )
                }
                Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    BasicText("Write on the cake", style = TextStyle(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold, color = Ink, letterSpacing = (-0.4).sp))
                    BasicText("Piped by hand in Swiss meringue buttercream", style = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, color = MutedInk))
                }

                Box(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 240.dp)
                        .fillMaxWidth()
                        .onGloballyPositioned { coords[1] = it; measure() }
                        .semantics {
                            val t = state?.text?.value ?: REST_TEXT
                            contentDescription = "Cake preview with ${if (t.isNotEmpty()) "\"$t\"" else "no message"} piped on top. Drag to turn the cake."
                        }
                        .pointerInput(state) {
                            val s = state ?: return@pointerInput
                            val d = density.density
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val gx = s.g.heroX
                                val gy = s.g.heroY
                                s.down(gx + down.position.x / d, gy + down.position.y / d)
                                val tracker = VelocityTracker()
                                tracker.addPosition(down.uptimeMillis, down.position)
                                var active = false
                                var lastPos = down.position
                                try {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val ch = event.changes.firstOrNull { it.id == down.id } ?: break
                                        tracker.addPosition(ch.uptimeMillis, ch.position)
                                        lastPos = ch.position
                                        if (!ch.pressed) break
                                        if (!active && (ch.position - down.position).getDistance() > 2 * d) {
                                            active = true
                                            if (s.mode != USER) s.takeOver()
                                        }
                                        if (active) {
                                            s.drag(gx + ch.position.x / d, gy + ch.position.y / d)
                                            ch.consume()
                                        }
                                    }
                                    if (active) {
                                        val v = tracker.calculateVelocity()
                                        s.fling(gx + lastPos.x / d, gy + lastPos.y / d, v.x / d, v.y / d)
                                    }
                                } finally {
                                    s.up()
                                }
                            }
                        },
                )

                // The message field.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .onGloballyPositioned { coords[2] = it; measure() }
                        .cssBoxShadow(Color(0x12462814), 10.dp, 24.dp, 16.dp)
                        .cssBoxShadow(Color(0x24462814), 1.dp, 2.dp, 16.dp)
                        .background(Card, RoundedCornerShape(16.dp))
                        .drawBehind {
                            drawLine(Color(0xE6FFFFFF), Offset(16.dp.toPx(), 0.5f.dp.toPx()), Offset(size.width - 16.dp.toPx(), 0.5f.dp.toPx()), 1.dp.toPx())
                        }
                        .padding(start = 16.dp, end = 16.dp, top = 10.dp),
                ) {
                    val text = state?.text?.value ?: REST_TEXT
                    val icingIndex = state?.icing?.intValue ?: 0
                    Column {
                        BasicText("Message", style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MutedInk))
                        val style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                        BasicTextField(
                            value = text,
                            onValueChange = { state?.onType(it) },
                            singleLine = true,
                            textStyle = style,
                            cursorBrush = SolidColor(Color(0xFF000000 or Icings[icingIndex].hex)),
                            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 2.dp, bottom = 2.dp, end = 52.dp)
                                .focusRequester(focus)
                                .onFocusChanged { fs ->
                                    val st = state ?: return@onFocusChanged
                                    st.focused = fs.isFocused
                                    if (fs.isFocused && st.mode != USER) st.takeOver()
                                    st.lastTouch = st.now
                                    st.running.value = true
                                }
                                .semantics { contentDescription = "Message" },
                            decorationBox = { inner ->
                                Box {
                                    if (text.isEmpty()) BasicText("Write something sweet", style = style.copy(color = Color(0xFFBFA998)))
                                    inner()
                                }
                            },
                        )
                    }
                    BasicText(
                        "${icingChars(text).size}/$ICING_MAX_LETTERS",
                        style = TextStyle(fontSize = 13.sp, color = Color(0xFFB39C8A), fontFeatureSettings = "tnum"),
                        modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 12.dp),
                    )
                }

                Row(
                    Modifier.padding(top = 16.dp).fillMaxWidth().height(48.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        BasicText("ICING", style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MutedInk, letterSpacing = 0.4.sp))
                        val s = state
                        if (s != null) IcName(s.chipPos)
                        else BasicText(Icings[0].name, style = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold, color = Ink))
                    }
                    Box {
                        Row(horizontalArrangement = Arrangement.spacedBy(CHIP_GAP.dp)) {
                            Icings.forEachIndexed { i, ic ->
                                IcChip(ic, selected = (state?.icing?.intValue ?: 0) == i) {
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    state?.pickIcing(i)
                                }
                            }
                        }
                        Box(
                            Modifier
                                .offset((-5).dp, (-5).dp)
                                .graphicsLayer { translationX = (state?.chipPos?.floatValue ?: 0f) * (CHIP + CHIP_GAP) * this.density }
                                .size((CHIP + 10).dp)
                                .border(2.dp, Ink, CircleShape),
                        )
                    }
                }

                Row(Modifier.padding(top = 16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        BasicText("TOTAL", style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MutedInk, letterSpacing = 0.4.sp))
                        BasicText("Vanilla bean · 8 in · serves 10", style = TextStyle(fontSize = 14.sp, color = MutedInk))
                    }
                    val s = state
                    if (s != null) IcPrice(s.priceFrom, s.priceTo, s.priceRoll)
                    else BasicText("$44.50", style = TextStyle(fontSize = 26.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold, color = Ink))
                }
                IcCta()
            }
        }
    }
}

@Composable
private fun IcChip(ic: IcingKind, selected: Boolean, onPress: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Box(
        Modifier
            .size(CHIP.dp)
            .graphicsLayer { val k = if (pressed) 0.94f else 1f; scaleX = k; scaleY = k }
            .cssBoxShadow(Color(0x2432190F), 4.dp, 10.dp, (CHIP / 2).dp)
            .cssBoxShadow(Color(0x4D32190F), 1.dp, 2.dp, (CHIP / 2).dp)
            .background(Color(0xFF000000 or ic.hex), CircleShape)
            .drawBehind {
                // inset 0 -3 6 rgba(0,0,0,.2), inset 0 2 3 rgba(255,255,255,.35), and the shine.
                drawCircle(Brush.verticalGradient(0f to Color(0x59FFFFFF), 0.18f to Color(0x00FFFFFF), 0.72f to Color(0x00000000), 1f to Color(0x33000000)))
                rotate(-20f, pivot = Offset(14.5f.dp.toPx(), 9.5f.dp.toPx())) {
                    drawRoundRect(
                        Color(0x59FFFFFF),
                        topLeft = Offset(8.dp.toPx(), 6.dp.toPx()),
                        size = androidx.compose.ui.geometry.Size(13.dp.toPx(), 7.dp.toPx()),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()),
                    )
                }
            }
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onPress)
            .semantics {
                contentDescription = "${ic.name} icing"
                this.selected = selected
            },
    )
}

@Composable
private fun IcCta() {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Box(
        Modifier
            .padding(top = 16.dp)
            .fillMaxWidth()
            .height(56.dp)
            .graphicsLayer { val k = if (pressed) 0.97f else 1f; scaleX = k; scaleY = k }
            .cssBoxShadow(Color(0x2E3B2418), 12.dp, 24.dp, 28.dp)
            .cssBoxShadow(Color(0x403B2418), 2.dp, 4.dp, 28.dp)
            .background(Ink, RoundedCornerShape(28.dp))
            .drawBehind {
                val r = size.height / 2
                drawLine(Color(0x1FFFFFFF), Offset(r, 0.5f.dp.toPx()), Offset(size.width - r, 0.5f.dp.toPx()), 1.dp.toPx())
            }
            .clickable(interactionSource = source, indication = null, role = Role.Button) {},
        contentAlignment = Alignment.Center,
    ) {
        BasicText("Add to order", style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFFBF1E6)))
    }
}

/** What shows for the moment before the art is baked: the drum and the cake top as flat shapes. */
private class IcPlaceholder {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    fun draw(c: android.graphics.Canvas, g: IcingGeo) {
        p.color = 0xFFEFE6DC.toInt()
        c.drawRect(0f, 0f, g.w, g.h, p)
        p.color = 0xFFD9B862.toInt()
        c.drawCircle(g.cx, g.cy, g.boardR, p)
        p.color = 0xFFF4E8D9.toInt()
        c.drawCircle(g.cx, g.cy, g.r, p)
        p.color = 0xFFF0C3CB.toInt()
        for (k in 0 until 16) {
            val a = k / 16f * PI.toFloat() * 2
            c.drawCircle(g.cx + g.r * 0.84f * cos(a), g.cy + g.r * 0.84f * sin(a), g.r * 0.1f, p)
        }
    }
}
