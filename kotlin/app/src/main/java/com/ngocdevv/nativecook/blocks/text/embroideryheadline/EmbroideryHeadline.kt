package com.ngocdevv.nativecook.blocks.text.embroideryheadline

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RectF
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
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkBorder
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.cssBoxShadow
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 Embroidery Headline — the cover of "Thread & Needle", issue 9. The headline "Slow made." is
 satin-stitched on linen in a wooden hoop: madder red above, indigo below, every letter filled with
 parallel threads laid edge to edge at its own angle, so each catches the light differently; the
 full stop is a French knot. A needle with its thread lays the satin; the subheading follows.

 Touch: drag across the hoop to sew — moving right lays threads into the next unfinished letter
 (1.6 threads per pt), moving left unpicks the last ones; the needle rides the stitching front and
 leans with the drag; a tick as each letter is finished, a tap when the knot pops. 2 s after the
 finger lifts the headline finishes itself (≤ 0.9 s, ease-in-out) and the loop starts again from its
 first frame. Reduced motion: the finished cover, still; dragging still sews.

 One Canvas: the table, hoop and linen are baked once off the main thread; per frame the laid
 threads (a few paths per letter), the knot and the needle are drawn from one state object.
*/

private const val AUTO = 0
private const val USER = 1
private const val RETURN = 2
private const val STILL = 3
private const val PER_PT = 1.6f
private const val KNOT_POP = 0.4f
private const val KNOT_NONE = -100f
private const val KNOT_DONE = -50f

private val Cream = Color(0xFFEFE6D4)
private val Muted = Color(0xFFA7B2A4)
private val Accent = Color(0xFFD9A56B)
private val Table = Color(0xFF1E2822)

private class EmbArt(val geo: EmbGeo, val satin: EmbSatin, val scene: Bitmap)

private fun sumOf(a: FloatArray): Float { var s = 0f; for (v in a) s += v; return s }

private fun activeGlyph(p: FloatArray, counts: IntArray): Int {
    for (i in p.indices) if (p[i] < counts[i] - 0.001f) return i
    return p.size - 1
}

private fun finishedCount(p: FloatArray, counts: IntArray): Int {
    var n = 0
    for (i in p.indices) if (p[i] >= counts[i] - 0.001f) n++
    return n
}

private class EmbState(val art: EmbArt, val auto: Boolean) {
    val satin = art.satin
    val geo = art.geo
    val n = satin.glyphs.size
    val park = floatArrayOf(geo.cx + geo.fabricR * 0.5f, geo.cy + geo.fabricR * 0.52f, satin.knot[0] - 2, satin.knot[1] + satin.knot[2] + 2)

    var mode = if (auto) AUTO else STILL
    var now = 0f
    var loopT = 0f
    var modeAt = 0f
    var lastTouch = 0f
    var touching = false
    val userP = FloatArray(n)
    val returnFrom = FloatArray(n)
    var knotAt = KNOT_NONE
    var lean = 0f
    val needle = FloatArray(5)

    // What the canvas draws, filled by [compute].
    val drawP = FloatArray(n)
    val drawKnot = FloatArray(2)
    val drawNeedle = FloatArray(5)
    var drawLift = 0f
    private val front = FloatArray(4)
    val frame = mutableIntStateOf(0)
    val subAlpha = mutableFloatStateOf(1f)
    val subRise = mutableFloatStateOf(0f)
    val running = mutableStateOf(true)

    init { compute() }

    private fun loopNeedle(t: Float, out: FloatArray) {
        embGlyphPresent(t, satin.counts, drawP)
        val p = drawP
        var gi = 0
        if (t < 1.75f) {
            for (i in p.indices) if (p[i] < satin.counts[i] - 0.001f) gi = i
            for (i in p.indices) if (p[i] > 0.001f && p[i] < satin.counts[i] - 0.001f) { gi = i; break }
        } else gi = activeGlyph(p, satin.counts)
        embSatinFront(satin.glyphs[gi], min(satin.counts[gi] - 0.001f, p[gi]), front)
        val o = embNeedleOut(t)
        out[0] = park[0] + (front[0] - park[0]) * o
        out[1] = park[1] + (front[1] - park[1]) * o
        out[2] = if (o > 0.5f) front[2] else park[2]
        out[3] = if (o > 0.5f) front[3] else park[3]
        out[4] = o
    }

    fun begin() {
        touching = true
        lastTouch = now
        running.value = true
        if (mode == AUTO || mode == STILL) {
            if (mode == AUTO) {
                embGlyphPresent(loopT, satin.counts, userP)
                loopNeedle(loopT, needle)
                embKnotState(loopT, drawKnot)
                knotAt = if (drawKnot[1] >= 0.99f) KNOT_DONE else KNOT_NONE
            } else {
                for (i in 0 until n) userP[i] = satin.counts[i].toFloat()
                needle[0] = park[0]; needle[1] = park[1]; needle[2] = park[2]; needle[3] = park[3]; needle[4] = 0f
                knotAt = KNOT_DONE
            }
        }
        mode = USER
    }

    /** A drag of dx pt; returns 0 for nothing, 1 for a finished letter, 2 for the knot popping. */
    fun change(dx: Float): Int {
        val beforeSum = sumOf(userP)
        val beforeDone = finishedCount(userP, satin.counts)
        var left = dx * PER_PT
        if (left > 0) {
            var i = 0
            while (i < n && left > 0) {
                val take = min(satin.counts[i] - userP[i], left)
                userP[i] += take; left -= take; i++
            }
        } else {
            var i = n - 1
            while (i >= 0 && left < 0) {
                val take = min(userP[i], -left)
                userP[i] -= take; left += take; i--
            }
        }
        lean = max(-0.35f, min(0.35f, lean + dx * 0.004f))
        lastTouch = now
        val total = satin.total.toFloat()
        val after = sumOf(userP)
        var kind = if (finishedCount(userP, satin.counts) != beforeDone) 1 else 0
        if (after >= total - 0.001f && beforeSum < total - 0.001f) {
            knotAt = now
            kind = 2
        }
        if (after < total - 0.001f) knotAt = KNOT_NONE
        return kind
    }

    fun end() {
        touching = false
        lastTouch = now
    }

    /** One frame; returns whether the loop must keep running. */
    fun step(dt: Float): Boolean {
        val t = now + dt
        now = t
        if (mode == AUTO) loopT = (loopT + dt) % LOOP
        if (mode == USER && auto && !touching && t - lastTouch > 2) {
            System.arraycopy(userP, 0, returnFrom, 0, n)
            mode = RETURN
            modeAt = t
        }
        if (mode == RETURN) {
            val missing = 1 - sumOf(returnFrom) / satin.total
            val length = 0.3f + 0.6f * missing
            val k = embEaseInOut(embSeg(t, modeAt, modeAt + length))
            for (i in 0 until n) userP[i] = returnFrom[i] + (satin.counts[i] - returnFrom[i]) * k
            if (k >= 1) {
                // The knot pops last (unless it is already there), then the loop takes over at t = 0.
                if (knotAt == KNOT_NONE) knotAt = t
                else if (knotAt == KNOT_DONE || t - knotAt > KNOT_POP) {
                    mode = AUTO
                    loopT = 0f
                }
            }
        }
        var settling = false
        if (mode == USER || mode == RETURN) {
            // The needle chases the stitching front while sewing and goes back to rest when idle.
            val busy = touching
            val gi = activeGlyph(userP, satin.counts)
            embSatinFront(satin.glyphs[gi], min(satin.counts[gi] - 0.001f, userP[gi]), front)
            val tx = if (busy) front[0] else park[0]
            val ty = if (busy) front[1] else park[1]
            val k = 1 - exp(-dt * if (busy) 22f else 7f)
            val o = needle[4] + ((if (busy) 1f else 0f) - needle[4]) * (1 - exp(-dt * 8))
            val dxn = tx - needle[0]; val dyn = ty - needle[1]
            needle[0] += dxn * k
            needle[1] += dyn * k
            needle[2] = if (busy) front[2] else park[2]
            needle[3] = if (busy) front[3] else park[3]
            needle[4] = o
            lean *= exp(-dt * 6)
            settling = busy || kotlin.math.abs(dxn) + kotlin.math.abs(dyn) > 0.05f || o > 0.002f ||
                kotlin.math.abs(lean) > 0.002f || (knotAt >= 0 && now - knotAt < KNOT_POP)
        }
        compute()
        frame.intValue += 1
        return when (mode) {
            AUTO, RETURN -> true
            USER -> auto || settling
            else -> false
        }
    }

    fun compute() {
        when (mode) {
            AUTO -> {
                loopNeedle(loopT, drawNeedle) // also fills drawP
                embKnotState(loopT, drawKnot)
                subAlpha.floatValue = embSubAlpha(loopT)
                subRise.floatValue = embSubRise(loopT)
                // A small bob while the needle works.
                drawLift = drawNeedle[4] * 0.55f + 0.22f * drawNeedle[4] * (1 + sin(loopT / LOOP * 2 * PI.toFloat() * 54))
            }
            STILL -> {
                for (i in 0 until n) drawP[i] = satin.counts[i].toFloat()
                drawKnot[0] = 1f; drawKnot[1] = 1f
                drawNeedle[0] = park[0]; drawNeedle[1] = park[1]; drawNeedle[2] = park[2]; drawNeedle[3] = park[3]; drawNeedle[4] = 0f
                drawLift = 0f
                subAlpha.floatValue = 1f
                subRise.floatValue = 0f
            }
            else -> {
                System.arraycopy(userP, 0, drawP, 0, n)
                val total = satin.total.toFloat()
                val ka = knotAt
                if (ka == KNOT_DONE) { drawKnot[0] = 1f; drawKnot[1] = 1f }
                else if (ka < 0) { drawKnot[0] = 0.55f; drawKnot[1] = 0f }
                else { drawKnot[0] = embPop(embSeg(now, ka, ka + KNOT_POP)); drawKnot[1] = min(1f, (now - ka) * 10) }
                val a = min(1f, max(0f, (sumOf(userP) - total + 40) / 40))
                subAlpha.floatValue = a
                subRise.floatValue = (1 - a) * 10
                System.arraycopy(needle, 0, drawNeedle, 0, 5)
                drawLift = needle[4] * 0.55f
            }
        }
    }

    fun needleAngle() = -0.92f + lean
}

/**
 * The cover of a craft magazine: "Slow made." satin-stitched onto linen in a hoop, laid by a needle
 * and thread letter by letter. Drag across the hoop to sew forward or unpick.
 *
 * @param autoPlay unpicks the headline and stitches it again, knot last, every 9 s.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EmbroideryHeadline(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val reduced = rememberReducedMotion()
    val auto = autoPlay && !reduced
    val density = LocalDensity.current
    val view = LocalView.current
    val top = maxOf(
        WindowInsets.statusBarsIgnoringVisibility.getTop(density),
        WindowInsets.displayCutout.getTop(density),
    ) / density.density
    val bottom = WindowInsets.navigationBarsIgnoringVisibility.getBottom(density) / density.density

    BoxWithConstraints(modifier.fillMaxSize().background(Table)) {
        val w = maxWidth.value
        val h = maxHeight.value
        var geo by remember { mutableStateOf<EmbGeo?>(null) }
        val coords = remember { arrayOfNulls<LayoutCoordinates>(2) }
        val measure = {
            val root = coords[0]
            val hero = coords[1]
            if (root != null && hero != null && root.isAttached && hero.isAttached) {
                val r = root.localBoundingBoxOf(hero, clipBounds = false)
                val d = density.density
                val next = embMakeGeo(w, h, r.left / d, r.top / d, r.width / d, r.height / d)
                if (!next.same(geo)) geo = next
            }
        }
        val art by produceState<EmbArt?>(null, geo) {
            val g = geo ?: return@produceState
            // Wait for the layout to settle before baking; the last art stays on screen meanwhile.
            delay(120)
            val scale = min(2f, density.density)
            value = withContext(Dispatchers.Default) {
                val satin = embBuildSatin(g, arrayOf("Slow", "made"))
                EmbArt(g, satin, embBakeScene(g, scale))
            }
        }
        val a = art
        val state = remember(a, auto) { a?.let { EmbState(it, auto) } }
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

        val painter = remember(a) { a?.let { EmbPainter(it.satin) } }
        val bitmapPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG) }
        val placeholder = remember { EmbPlaceholder() }
        val dst = remember { RectF() }
        Canvas(Modifier.fillMaxSize().onGloballyPositioned { coords[0] = it; measure() }) {
            state?.frame?.intValue
            val d = density.density
            drawIntoCanvas { canvas ->
                val c = canvas.nativeCanvas
                c.save()
                c.scale(d, d)
                val g = geo
                val f = fade.floatValue
                // Until the art is baked (and while it fades in), cheap shapes of the hoop and headline.
                if (g != null && (state == null || f < 1f)) placeholder.draw(c, g, w, h)
                if (state != null && painter != null) {
                    val ar = state.art
                    if (f < 1f) c.saveLayerAlpha(0f, 0f, w, h, (f * 255).toInt())
                    dst.set(0f, 0f, ar.geo.w, ar.geo.h)
                    c.drawBitmap(ar.scene, null, dst, bitmapPaint)
                    painter.drawSatin(c, ar.satin, state.drawP)
                    painter.drawKnot(c, ar.satin, state.drawKnot[0], state.drawKnot[1])
                    val nd = state.drawNeedle
                    val gi = activeGlyph(state.drawP, ar.satin.counts)
                    val rgb = if (nd[4] > 0.5f) ar.satin.glyphs[gi].rgb else ar.satin.knotRGB
                    painter.drawNeedle(c, nd[0], nd[1], state.needleAngle(), state.drawLift, nd[2], nd[3], rgb)
                    if (f < 1f) c.restore()
                }
                c.restore()
            }
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(start = 24.dp, end = 24.dp, top = (top + 8).dp, bottom = (bottom + 12).dp),
        ) {
            Row(Modifier.fillMaxWidth().height(36.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                BasicText("THREAD & NEEDLE", style = TextStyle(fontFamily = FontFamily.Serif, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Cream, letterSpacing = 2.6.sp))
                BasicText("No. 9 · Autumn", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Muted, fontFeatureSettings = "tnum"))
            }
            Box(Modifier.padding(top = 8.dp).fillMaxWidth().height(0.5.dp).background(Color(0x47EFE6D4)))
            Row(Modifier.padding(top = 12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                BasicText("COVER STORY", style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Accent, letterSpacing = 2.sp))
                BasicText("Hand embroidery", style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Muted))
            }

            // The hoop's slot: 12 pt wider than the column on each side; the canvas draws into it.
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 260.dp)
                    .fillMaxWidth()
                    .layout { m, c ->
                        val b = 12.dp.roundToPx()
                        val p = m.measure(c.copy(minWidth = c.maxWidth + 2 * b, maxWidth = c.maxWidth + 2 * b))
                        layout(c.maxWidth, p.height) { p.place(-b, 0) }
                    }
                    .onGloballyPositioned { coords[1] = it; measure() }
                    .semantics { contentDescription = "Headline: Slow made. Drag across the hoop to stitch or unpick it." }
                    .pointerInput(state) {
                        val s = state ?: return@pointerInput
                        val d = density.density
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            s.begin()
                            var lastX = down.position.x
                            var active = false
                            try {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val ch = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!ch.pressed) break
                                    if (!active && kotlin.math.abs(ch.position.x - down.position.x) + kotlin.math.abs(ch.position.y - down.position.y) > 4 * d) {
                                        active = true
                                        lastX = ch.position.x
                                    }
                                    if (active) {
                                        val kind = s.change((ch.position.x - lastX) / d)
                                        lastX = ch.position.x
                                        ch.consume()
                                        when (kind) {
                                            1 -> view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                            2 -> view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                        }
                                    }
                                }
                            } finally {
                                s.end()
                            }
                        }
                    },
            )

            Column(
                Modifier
                    .padding(bottom = 20.dp)
                    .graphicsLayer {
                        alpha = state?.subAlpha?.floatValue ?: 1f
                        translationY = (state?.subRise?.floatValue ?: 0f) * this.density
                    },
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BasicText(
                    "Twelve makers on the quiet patience of the needle",
                    style = TextStyle(fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic, fontSize = 23.sp, lineHeight = 29.sp, color = Cream),
                )
                BasicText("Words by Ines Rook · 18 min read", style = TextStyle(fontSize = 13.sp, color = Muted))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                EmbCta(Modifier.weight(1f))
                EmbSave()
            }
        }
    }
}

@Composable
private fun EmbCta(modifier: Modifier) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Box(
        modifier
            .height(52.dp)
            .graphicsLayer { val k = if (pressed) 0.97f else 1f; scaleX = k; scaleY = k }
            .cssBoxShadow(Color(0x40000000), 10.dp, 24.dp, 26.dp)
            .cssBoxShadow(Color(0x4D000000), 1.dp, 2.dp, 26.dp)
            .background(Cream, RoundedCornerShape(26.dp))
            .drawBehind {
                // inset 0 1px 0 rgba(255,255,255,0.7): a light top edge inside the pill.
                val r = size.height / 2
                drawArc(
                    Color(0xB3FFFFFF), 200f, 140f, false,
                    topLeft = Offset(0.5f.dp.toPx(), 0.5f.dp.toPx()),
                    size = androidx.compose.ui.geometry.Size(r * 2 - 1.dp.toPx(), r * 2 - 1.dp.toPx()),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()),
                )
                drawLine(Color(0xB3FFFFFF), Offset(r, 0.5f.dp.toPx()), Offset(size.width - r, 0.5f.dp.toPx()), 1.dp.toPx())
                drawArc(
                    Color(0xB3FFFFFF), 200f, 140f, false,
                    topLeft = Offset(size.width - r * 2 + 0.5f.dp.toPx(), 0.5f.dp.toPx()),
                    size = androidx.compose.ui.geometry.Size(r * 2 - 1.dp.toPx(), r * 2 - 1.dp.toPx()),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()),
                )
            }
            .clickable(interactionSource = source, indication = null, role = Role.Button) {},
        contentAlignment = Alignment.Center,
    ) {
        BasicText("Read the story", style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1F2A23)))
    }
}

@Composable
private fun EmbSave() {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Box(
        Modifier
            .size(52.dp)
            .graphicsLayer { val k = if (pressed) 0.94f else 1f; scaleX = k; scaleY = k }
            .border(1.dp, Color(0x4DEFE6D4), CircleShape)
            .clickable(interactionSource = source, indication = null, role = Role.Button) {}
            .semantics { contentDescription = "Save the story" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Outlined.BookmarkBorder, contentDescription = null, tint = Cream, modifier = Modifier.size(22.dp))
    }
}

/** What shows for the moment before the art is baked: the hoop as flat shapes and the headline as plain type. */
private class EmbPlaceholder {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = EmbSerifBold; textAlign = Paint.Align.CENTER }

    fun draw(c: android.graphics.Canvas, g: EmbGeo, w: Float, h: Float) {
        p.style = Paint.Style.FILL
        p.color = 0xFF2C3A31.toInt()
        c.drawRect(0f, 0f, w, h, p)
        p.color = 0xFFE6DAC4.toInt()
        c.drawCircle(g.cx, g.cy, g.fabricR + 3, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = g.hoopR - g.fabricR - 3
        p.color = 0xFFC29257.toInt()
        c.drawCircle(g.cx, g.cy, (g.fabricR + 3 + g.hoopR) / 2, p)
        val m = Paint(text)
        m.textSize = 100f
        val widest = max(m.measureText("Slow"), m.measureText("made."))
        val size = min(112f, 100 * g.fabricR * 1.5f / widest)
        text.textSize = size
        val base1 = g.cy - size * 0.14f
        text.color = 0xFFA3382B.toInt()
        c.drawText("Slow", g.cx, base1, text)
        text.color = 0xFF2B4D6B.toInt()
        c.drawText("made.", g.cx, base1 + size, text)
    }
}
