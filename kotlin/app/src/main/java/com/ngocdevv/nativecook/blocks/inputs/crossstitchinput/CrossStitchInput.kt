package com.ngocdevv.nativecook.blocks.inputs.crossstitchinput

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RectF
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/*
 Cross Stitch Input — "Stitchbook · Name your sampler". A wooden hoop of cream aida cloth fills the
 middle of the screen; whatever is typed in the name field is cross-stitched onto the cloth by a
 needle and thread, one X at a time, in the colour of the chosen skein. The stitch counter and the
 thread's name roll as they change.

 Touch: typing stitches each new letter (0.15 s + 55 ms a stitch), the needle following the newest
 letter and going back to rest when idle; the name re-centres on the grid with a 0.35 s glide; a skein
 chip re-threads the whole name (0.3 s). Tapping the hoop focuses the field. With autoPlay, 2 s after
 the field loses focus the cloth cross-fades back to the loop's first frame (0.8 s) and the loop starts
 again; while the loop plays it types its script into the field without focusing it. Reduced motion:
 "Hi" stays stitched, nothing moves by itself, typed letters appear whole.

 One Canvas: the cloth, hoop and printed border are one bitmap baked off the main thread; per frame
 only the name's stitches, the needle and its thread are drawn, from one state object.
*/

private const val AUTO = 0
private const val USER = 1
private const val RETURN = 2
private const val STILL = 3
private const val RETURN_FOR = 0.8f
private const val GLIDE = 0.35f
private const val NONE = -100f
private const val CHIP = 40f
private const val CHIP_GAP = 14f

private val Ink = Color(0xFF2E2620)
private val MutedInk = Color(0xFF7D6D5C)
private val Card = Color(0xFFFBF7EF)
private val Backdrop = Color(0xFFEDE3D3)

private fun stitchDuration(count: Int) = 0.15f + 0.055f * count

private class CsArt(val geo: StitchGeo, val scene: Bitmap, val rest: StitchLayout, val second: StitchLayout, val borderCount: Int)

/** The name being typed by the user. */
private class CsUser(
    val layout: StitchLayout,
    val typedAt: FloatArray,
    /** Per glyph: where it was, in cells, relative to where it is now; it glides from movedAt. */
    val fromX: FloatArray,
    val fromY: FloatArray,
    val movedAt: Float,
    var thread: Int,
    var threadFrom: FloatArray,
    var threadAt: Float,
)

private class CsState(val art: CsArt, val auto: Boolean, val reduced: Boolean) {
    val g = art.geo
    val loop = StitchLoop(g, art.rest, art.second)
    var mode = if (auto) AUTO else STILL
    var now = 0f
    var loopT = 0f
    var modeAt = 0f
    var lastTouch = 0f
    var focused = false
    private var chipValue = REST_THREAD.toFloat()
    private var chipVel = 0f
    private var chipTarget = REST_THREAD.toFloat()
    val needle = FloatArray(5)
    var user = CsUser(art.rest, FloatArray(art.rest.count.size) { NONE }, FloatArray(art.rest.count.size), FloatArray(art.rest.count.size), NONE, REST_THREAD, Threads[REST_THREAD].rgb, NONE)

    val text = mutableStateOf(REST_TEXT)
    val thread = mutableIntStateOf(REST_THREAD)
    val frame = mutableIntStateOf(0)
    val chipPos = mutableFloatStateOf(REST_THREAD.toFloat())
    val done = mutableFloatStateOf(0f)
    val total = mutableFloatStateOf(0f)
    val running = mutableStateOf(true)
    private var scriptAt = -1

    // Scratch for drawing.
    val shiftX = FloatArray(MAX_LETTERS)
    val shiftY = FloatArray(MAX_LETTERS)
    val present = FloatArray(MAX_LETTERS)
    val rgb = FloatArray(3)
    private val target = FloatArray(5)
    private val tmp = FloatArray(2)
    private val auto6 = FloatArray(6)

    init { compute() }

    private fun glyphPresent(u: CsUser, t: Float, out: FloatArray) {
        for (i in u.layout.count.indices) {
            val c = u.layout.count[i]
            out[i] = c * min(1f, max(0f, (t - u.typedAt[i]) / stitchDuration(c)))
        }
    }

    private fun shift(u: CsUser, t: Float, outX: FloatArray, outY: FloatArray) {
        val k = 1 - csEaseInOut(csSeg(t, u.movedAt, u.movedAt + GLIDE))
        for (i in u.fromX.indices) { outX[i] = u.fromX[i] * k; outY[i] = u.fromY[i] * k }
    }

    private fun colour(u: CsUser, t: Float, out: FloatArray) = csMix(u.threadFrom, Threads[u.thread].rgb, csEaseInOut(csSeg(t, u.threadAt, u.threadAt + 0.3f)), out)

    private fun worked(u: CsUser, t: Float): Float {
        glyphPresent(u, t, present)
        var s = 0f
        for (i in u.layout.count.indices) s += present[i]
        return s
    }

    /** The needle while the user types: x, y, fromX, fromY, busy. */
    private fun needleTarget(u: CsUser, t: Float, out: FloatArray) {
        val l = u.layout
        glyphPresent(u, t, present)
        shift(u, t, shiftX, shiftY)
        val h = g.cell * 0.4f
        for (i in l.count.indices.reversed()) {
            if (l.count[i] > 0 && present[i] < l.count[i]) {
                val k = min(l.count[i] - 1, floor(present[i]).toInt())
                val s = l.first[i] + k
                val x = g.cx + (l.cx[s] + shiftX[i]) * g.cell
                val y = g.cy + (l.cy[s] + shiftY[i]) * g.cell
                csNeedlePoint(x, y, h, present[i] - k, tmp)
                out[0] = tmp[0]; out[1] = tmp[1]; out[2] = x - h; out[3] = y + h; out[4] = 1f
                return
            }
        }
        if (l.total == 0) {
            out[0] = g.parkX; out[1] = g.parkY; out[2] = g.parkX - 30; out[3] = g.parkY + 6; out[4] = 0f
            return
        }
        val last = l.total - 1
        val gi = l.glyphOf[last]
        out[0] = g.parkX; out[1] = g.parkY
        out[2] = g.cx + (l.cx[last] + shiftX[gi]) * g.cell - h
        out[3] = g.cy + (l.cy[last] + shiftY[gi]) * g.cell - h
        out[4] = 0f
    }

    /** Hands the screen to the user: the name on the cloth becomes theirs, mid-stitch if it was. */
    fun takeOver() {
        running.value = true
        lastTouch = now
        if (mode == USER) return
        val shown = text.value
        val l = stitchLayoutName(shown)
        val which = if (shown == SECOND_TEXT) 1 else if (shown == REST_TEXT) 0 else -1
        val current = thread.intValue
        val lt = loopT
        val p = if (mode == AUTO) when (which) {
            1 -> csSecondPresent(lt, l.total)
            0 -> csRestPresent(lt, l.total)
            else -> l.total.toFloat()
        } else l.total.toFloat()
        val n = l.count.size
        val typedAt = FloatArray(n) { i ->
            val frac = if (l.count[i] == 0) 1f else min(1f, max(0f, (p - l.first[i]) / l.count[i]))
            now - frac * stitchDuration(l.count[i]) - if (frac >= 1) 1 else 0
        }
        user = CsUser(l, typedAt, FloatArray(n), FloatArray(n), NONE, current, Threads[current].rgb, NONE)
        chipValue = current.toFloat(); chipTarget = chipValue; chipVel = 0f
        if (mode == AUTO) {
            loop.needle(lt, auto6)
            needle[0] = auto6[0]; needle[1] = auto6[1]; needle[2] = auto6[4]; needle[3] = auto6[5]; needle[4] = 0f
        } else if (mode != RETURN) {
            needle[0] = g.parkX; needle[1] = g.parkY; needle[2] = g.parkX - 30; needle[3] = g.parkY; needle[4] = 0f
        }
        mode = USER
    }

    fun onType(value: String) {
        if (mode != USER) takeOver()
        val chars = stitchChars(value).take(MAX_LETTERS)
        val next = chars.joinToString("")
        val prevChars = stitchChars(text.value)
        text.value = next
        val t = now
        val prev = user
        val l = stitchLayoutName(next)
        val k = 1 - csEaseInOut(csSeg(t, prev.movedAt, prev.movedAt + GLIDE))
        val n = l.count.size
        val typedAt = FloatArray(n)
        val fromX = FloatArray(n)
        val fromY = FloatArray(n)
        for (i in 0 until n) {
            val same = i < prev.layout.count.size && i < prevChars.size && prevChars[i] == chars[i]
            typedAt[i] = if (same) prev.typedAt[i] else t
            fromX[i] = if (same) prev.layout.col[i] + prev.fromX[i] * k - l.col[i] else 0f
            fromY[i] = if (same) prev.layout.row[i] + prev.fromY[i] * k - l.row[i] else 0f
        }
        user = CsUser(l, typedAt, fromX, fromY, t, prev.thread, prev.threadFrom, prev.threadAt)
        lastTouch = t
        running.value = true
    }

    fun pickThread(i: Int) {
        if (mode != USER) takeOver()
        thread.intValue = i
        val t = now
        val u = user
        u.threadFrom = colour(u, t, FloatArray(3))
        u.thread = i
        u.threadAt = t
        chipTarget = i.toFloat()
        lastTouch = t
        running.value = true
    }

    fun onFocus(f: Boolean) {
        focused = f
        if (f && mode != USER) takeOver()
        lastTouch = now
        running.value = true
    }

    fun step(dt: Float): Boolean {
        val t = now + dt
        now = t
        if (mode == AUTO) loopT = (loopT + dt) % LOOP
        if (mode == USER && auto && !focused && t - lastTouch > 2) {
            mode = RETURN
            modeAt = t
            text.value = REST_TEXT
            thread.intValue = REST_THREAD
        }
        if (mode == RETURN && t - modeAt >= RETURN_FOR) {
            mode = AUTO
            loopT = 0f
            scriptAt = -1
        }
        // While the loop plays, the field follows its script (a handful of updates per loop).
        if (mode == AUTO) {
            val i = csScriptIndex(loopT)
            if (i != scriptAt) { scriptAt = i; text.value = TextScript[i].second }
            val th = csChipPosition(loopT).roundToInt()
            if (th != thread.intValue) thread.intValue = th
        }
        var busy = false
        if (mode == USER || mode == RETURN) {
            // The needle chases its target on a critically damped follow.
            needleTarget(user, t, target)
            val k = 1 - exp(-dt * if (target[4] > 0.5f) 26f else 9f)
            val dx = target[0] - needle[0]; val dy = target[1] - needle[1]
            needle[0] += dx * k; needle[1] += dy * k
            needle[2] = target[2]; needle[3] = target[3]; needle[4] = target[4]
            busy = target[4] > 0.5f || abs(dx) + abs(dy) > 0.05f || t < user.movedAt + GLIDE || t < user.threadAt + 0.3f
        }
        // The chip ring springs to the picked skein (0.4 s, damping 0.75).
        if (chipValue != chipTarget || chipVel != 0f) {
            val w0 = 2 * Math.PI.toFloat() / 0.4f
            var left = dt
            while (left > 0) {
                val h = min(left, 1 / 240f)
                chipVel += (-w0 * w0 * (chipValue - chipTarget) - 2 * 0.75f * w0 * chipVel) * h
                chipValue += chipVel * h
                left -= h
            }
            if (abs(chipValue - chipTarget) < 0.0005f && abs(chipVel) < 0.005f) { chipValue = chipTarget; chipVel = 0f }
            busy = true
        }
        compute()
        frame.intValue += 1
        return when (mode) {
            AUTO, RETURN -> true
            USER -> auto || focused || busy
            else -> false
        }
    }

    fun compute() {
        val b = art.borderCount
        val restT = art.rest.total
        val secondT = art.second.total
        chipPos.floatValue = when (mode) {
            AUTO -> csChipPosition(loopT)
            RETURN -> {
                val k = csEaseInOut(csSeg(now, modeAt, modeAt + RETURN_FOR))
                chipValue + (REST_THREAD - chipValue) * k
            }
            else -> chipValue
        }
        when (mode) {
            AUTO -> {
                val t = loopT
                done.floatValue = b + csRestPresent(t, restT) + csSecondPresent(t, secondT)
                total.floatValue = b + restT + (secondT - restT) * csTotalBlend(t)
            }
            STILL -> { done.floatValue = (b + restT).toFloat(); total.floatValue = (b + restT).toFloat() }
            else -> {
                val d = b + worked(user, now)
                val tt = (b + user.layout.total).toFloat()
                if (mode == RETURN) {
                    val k = csEaseInOut(csSeg(now, modeAt, modeAt + RETURN_FOR))
                    done.floatValue = d + (b + restT - d) * k
                    total.floatValue = tt + (b + restT - tt) * k
                } else { done.floatValue = d; total.floatValue = tt }
            }
        }
    }

    fun draw(c: android.graphics.Canvas, p: StitchPainter) {
        if (mode == AUTO || mode == STILL) {
            loop.draw(c, p, if (mode == AUTO) loopT else 0f, 1f, true)
            return
        }
        val u = user
        val t = now
        val k = if (mode == RETURN) csEaseInOut(csSeg(t, modeAt, modeAt + RETURN_FOR)) else 0f
        shift(u, t, shiftX, shiftY)
        colour(u, t, rgb)
        if (reduced) for (i in u.layout.count.indices) present[i] = u.layout.count[i].toFloat() else glyphPresent(u, t, present)
        p.drawStitches(c, g, u.layout.cx, u.layout.cy, u.layout.total.toFloat(), rgb, 1 - k, u.layout, present, shiftX, shiftY)
        if (k > 0) loop.draw(c, p, 0f, k, false)
        val x = needle[0] + (g.parkX - needle[0]) * k
        val y = needle[1] + (g.parkY - needle[1]) * k
        p.drawNeedle(c, x, y, -0.95f, needle[4] * 0.5f, needle[2], needle[3], rgb, 1f)
    }
}

/**
 * A craft-kit app's name field: the name typed is cross-stitched onto aida cloth in a hoop, one X
 * at a time, in the chosen skein's colour.
 *
 * @param autoPlay unpicks "Hi", types and stitches "June" in madder, and works "Hi" again, every 9 s.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CrossStitchInput(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
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
    val keyboard = LocalSoftwareKeyboardController.current

    BoxWithConstraints(modifier.fillMaxSize().background(Backdrop)) {
        val w = maxWidth.value
        val h = maxHeight.value
        var geo by remember { mutableStateOf<StitchGeo?>(null) }
        val coords = remember { arrayOfNulls<LayoutCoordinates>(3) }
        val fieldBottom = remember { mutableFloatStateOf(0f) }
        val measure = {
            val root = coords[0]
            val hero = coords[1]
            if (root != null && hero != null && root.isAttached && hero.isAttached) {
                val r = root.localBoundingBoxOf(hero, clipBounds = false)
                val d = density.density
                val next = StitchGeo(w, h, r.left / d, r.top / d, r.width / d, r.height / d)
                if (!next.same(geo)) geo = next
                val field = coords[2]
                if (field != null && field.isAttached) fieldBottom.floatValue = root.localBoundingBoxOf(field, clipBounds = false).bottom / d
            }
        }
        val layouts = remember { stitchLayoutName(REST_TEXT) to stitchLayoutName(SECOND_TEXT) }
        val border = remember { stitchBorder() }
        val art by produceState<CsArt?>(null, geo) {
            val g = geo ?: return@produceState
            // Wait for the layout to settle before baking; the last art stays on screen meanwhile.
            delay(120)
            val scale = min(2f, density.density)
            value = withContext(Dispatchers.Default) {
                CsArt(g, csBakeScene(g, scale, border), layouts.first, layouts.second, border.first.size)
            }
        }
        val a = art
        val state = remember(a, auto) { a?.let { CsState(it, auto, reduced) } }
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

        val painter = remember { StitchPainter() }
        val placeholder = remember { CsPlaceholder() }
        val bitmapPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG) }
        val dst = remember { RectF() }
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
                    if (g != null && (state == null || f < 1f)) placeholder.draw(c, g, w, h)
                    if (state != null) {
                        if (f < 1f) c.saveLayerAlpha(0f, 0f, w, h, (f * 255).toInt())
                        dst.set(0f, 0f, state.g.w, state.g.h)
                        c.drawBitmap(state.art.scene, null, dst, bitmapPaint)
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
                            .cssBoxShadow(Color(0x144A3220), 6.dp, 16.dp, 20.dp)
                            .cssBoxShadow(Color(0x2E4A3220), 1.dp, 2.dp, 20.dp)
                            .background(Color(0xCCFFFBF3), CircleShape)
                            .semantics { contentDescription = "Back" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = null, tint = Ink, modifier = Modifier.size(26.dp))
                    }
                    BasicText("Stitchbook", style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink, letterSpacing = 0.2.sp))
                    Box(
                        Modifier.height(28.dp).border(1.dp, Color(0x2E2E2620), RoundedCornerShape(14.dp)).padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        BasicText("KIT 04", style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MutedInk, letterSpacing = 1.2.sp))
                    }
                }
                Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    BasicText("Name your sampler", style = TextStyle(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold, color = Ink, letterSpacing = (-0.4).sp))
                    BasicText("Two strands on 14-count aida · up to 10 letters", style = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, color = MutedInk))
                }

                Box(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 220.dp)
                        .fillMaxWidth()
                        .onGloballyPositioned { coords[1] = it; measure() }
                        .clickable(interactionSource = null, indication = null, role = Role.Button) {
                            focus.requestFocus()
                            keyboard?.show()
                        }
                        .semantics {
                            contentDescription = "Sampler preview reading ${state?.text?.value?.ifEmpty { "nothing yet" } ?: REST_TEXT}. Double tap to edit the name."
                        },
                )

                Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        BasicText("STITCHES", style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MutedInk, letterSpacing = 0.4.sp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val s = state
                            if (s != null) {
                                CsDigits(s.done, 3)
                                BasicText(" / ", style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = MutedInk, fontFeatureSettings = "tnum"))
                                CsDigits(s.total, 3)
                            } else {
                                BasicText(" ", style = TextStyle(fontSize = 18.sp, lineHeight = DIGIT_H.sp))
                            }
                        }
                    }
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        BasicText("THREAD", style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MutedInk, letterSpacing = 0.4.sp))
                        val s = state
                        if (s != null) CsThreadName(s.chipPos)
                        else BasicText(Threads[REST_THREAD].name, style = TextStyle(fontSize = 17.sp, lineHeight = DIGIT_H.sp, fontWeight = FontWeight.Bold, color = Ink))
                    }
                }

                // The field.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .onGloballyPositioned { coords[2] = it; measure() }
                        .cssBoxShadow(Color(0x144A3220), 10.dp, 24.dp, 16.dp)
                        .cssBoxShadow(Color(0x294A3220), 1.dp, 2.dp, 16.dp)
                        .background(Card, RoundedCornerShape(16.dp))
                        .drawBehind {
                            drawLine(Color(0xCCFFFFFF), Offset(16.dp.toPx(), 0.5f.dp.toPx()), Offset(size.width - 16.dp.toPx(), 0.5f.dp.toPx()), 1.dp.toPx())
                        }
                        .padding(start = 16.dp, end = 16.dp, top = 10.dp),
                ) {
                    val text = state?.text?.value ?: REST_TEXT
                    val threadIndex = state?.thread?.intValue ?: REST_THREAD
                    Column {
                        BasicText("Name on the sampler", style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MutedInk))
                        BasicTextField(
                            value = text,
                            onValueChange = { state?.onType(it) },
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Ink),
                            cursorBrush = SolidColor(Color(0xFF000000 or Threads[threadIndex].hex)),
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Words,
                                autoCorrectEnabled = false,
                                imeAction = ImeAction.Done,
                            ),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 2.dp, bottom = 2.dp, end = 48.dp)
                                .focusRequester(focus)
                                .onFocusChanged { state?.onFocus(it.isFocused) }
                                .semantics { contentDescription = "Name on the sampler" },
                            decorationBox = { inner ->
                                Box {
                                    if (text.isEmpty()) BasicText("Type a name", style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFB3A594)))
                                    inner()
                                }
                            },
                        )
                    }
                    BasicText(
                        "${stitchChars(text).size}/$MAX_LETTERS",
                        style = TextStyle(fontSize = 13.sp, color = Color(0xFFA8988A), fontFeatureSettings = "tnum"),
                        modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 12.dp),
                    )
                }

                // Skein chips with a ring that springs to the chosen one.
                Box(Modifier.padding(top = 16.dp).fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
                    Box {
                        Row(horizontalArrangement = Arrangement.spacedBy(CHIP_GAP.dp)) {
                            Threads.forEachIndexed { i, th ->
                                CsChip(th, selected = (state?.thread?.intValue ?: REST_THREAD) == i) {
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    state?.pickThread(i)
                                }
                            }
                        }
                        Box(
                            Modifier
                                .offset((-5).dp, (-5).dp)
                                .graphicsLayer { translationX = (state?.chipPos?.floatValue ?: REST_THREAD.toFloat()) * (CHIP + CHIP_GAP) * this.density }
                                .size((CHIP + 10).dp)
                                .border(2.dp, Ink, CircleShape),
                        )
                    }
                }

                CsCta()
            }
        }
    }
}

@Composable
private fun CsChip(th: StitchThread, selected: Boolean, onPress: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val colour = Color(0xFF000000 or th.hex)
    Box(
        Modifier
            .size(CHIP.dp)
            .graphicsLayer { val k = if (pressed) 0.94f else 1f; scaleX = k; scaleY = k }
            .cssBoxShadow(Color(0x242E1E14), 4.dp, 10.dp, (CHIP / 2).dp)
            .cssBoxShadow(Color(0x4D2E1E14), 1.dp, 2.dp, (CHIP / 2).dp)
            .background(colour, CircleShape)
            .drawBehind {
                // inset 0 -3 6 rgba(0,0,0,.22), inset 0 2 3 rgba(255,255,255,.28).
                drawCircle(Brush.verticalGradient(0f to Color(0x47FFFFFF), 0.18f to Color(0x00FFFFFF), 0.72f to Color(0x00000000), 1f to Color(0x38000000)))
                // The shine: a small soft lozenge at the upper left.
                rotate(-20f, pivot = Offset(15.dp.toPx(), 10.dp.toPx())) {
                    drawRoundRect(
                        Color(0x47FFFFFF),
                        topLeft = Offset(8.dp.toPx(), 6.dp.toPx()),
                        size = androidx.compose.ui.geometry.Size(14.dp.toPx(), 8.dp.toPx()),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()),
                    )
                }
            }
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onPress)
            .semantics {
                contentDescription = "${th.name} thread"
                this.selected = selected
            },
    )
}

@Composable
private fun CsCta() {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Box(
        Modifier
            .padding(top = 16.dp)
            .fillMaxWidth()
            .height(56.dp)
            .graphicsLayer { val k = if (pressed) 0.97f else 1f; scaleX = k; scaleY = k }
            .cssBoxShadow(Color(0x2E2E2620), 12.dp, 24.dp, 28.dp)
            .cssBoxShadow(Color(0x402E2620), 2.dp, 4.dp, 28.dp)
            .background(Ink, RoundedCornerShape(28.dp))
            .drawBehind {
                val r = size.height / 2
                drawLine(Color(0x1FFFFFFF), Offset(r, 0.5f.dp.toPx()), Offset(size.width - r, 0.5f.dp.toPx()), 1.dp.toPx())
            }
            .clickable(interactionSource = source, indication = null, role = Role.Button) {},
        contentAlignment = Alignment.Center,
    ) {
        BasicText("Save sampler", style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFF6EEDF)))
    }
}

/** What shows for the moment before the art is baked: the hoop and cloth as flat shapes and "Hi" as plain crosses. */
private class CsPlaceholder {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val layout = stitchLayoutName(REST_TEXT)

    fun draw(c: android.graphics.Canvas, g: StitchGeo, w: Float, h: Float) {
        p.style = Paint.Style.FILL
        p.color = 0xFFEADFCB.toInt()
        c.drawRect(0f, 0f, w, h, p)
        p.color = 0xFFF2EBDD.toInt()
        c.drawCircle(g.cx, g.cy, g.fabricR + 3, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = g.hoopR - g.fabricR - 3
        p.color = 0xFFD9AD73.toInt()
        c.drawCircle(g.cx, g.cy, (g.fabricR + 3 + g.hoopR) / 2, p)
        p.strokeWidth = g.cell * 0.33f
        p.strokeCap = Paint.Cap.ROUND
        p.color = 0xFF33477F.toInt()
        val hh = g.cell * 0.4f
        for (k in 0 until layout.total) {
            val x = g.cx + layout.cx[k] * g.cell
            val y = g.cy + layout.cy[k] * g.cell
            c.drawLine(x - hh, y + hh, x + hh, y - hh, p)
            c.drawLine(x + hh, y + hh, x - hh, y - hh, p)
        }
    }
}
